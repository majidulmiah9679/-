package com.example.audio

import android.content.Context
import android.util.Log
import com.example.core.config.AlexConfig
import com.example.data.repository.ConversationRepository
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.AlexSettings
import com.example.domain.model.ConversationModality
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
import com.example.network.DirectGeminiLiveWebSocket
import com.example.network.GeminiBrainService
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerMessage
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GeminiLiveController"

@OptIn(PublicPreviewAPI::class)
class GeminiLiveSessionController(
    private val context: Context,
    private val brainService: GeminiBrainService,
    private val audioEngine: RealTimeAudioEngine,
    private val voiceSynthesizer: FemaleVoiceSynthesizer,
    private val conversationRepository: ConversationRepository
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeSession: LiveSession? = null
    private var directWebSocket: DirectGeminiLiveWebSocket? = null
    private var receiveJob: Job? = null
    private var statusMonitorJob: Job? = null
    private var activeConversationId: Long? = null
    private var currentSettings: AlexSettings = AlexSettings()
    private val isProcessingAudioTurn = AtomicBoolean(false)

    private val _liveStatus = MutableStateFlow(AlexLiveStatus.READY)
    val liveStatus: StateFlow<AlexLiveStatus> = _liveStatus.asStateFlow()

    private val _isLiveCallActive = MutableStateFlow(false)
    val isLiveCallActive: StateFlow<Boolean> = _isLiveCallActive.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _detectedLanguage = MutableStateFlow(AlexLanguage.ENGLISH)
    val detectedLanguage: StateFlow<AlexLanguage> = _detectedLanguage.asStateFlow()

    private val _liveTranscript = MutableStateFlow<List<TranscriptTurn>>(emptyList())
    val liveTranscript: StateFlow<List<TranscriptTurn>> = _liveTranscript.asStateFlow()

    private val _bargeInCount = MutableStateFlow(0)
    val bargeInCount: StateFlow<Int> = _bargeInCount.asStateFlow()

    private val currentUserTurnBuffer = StringBuilder()
    private val currentAlexTurnBuffer = StringBuilder()
    private var currentUserTurnId: String = UUID.randomUUID().toString()
    private var currentAlexTurnId: String = UUID.randomUUID().toString()

    init {
        AlexLiveForegroundService.onStopRequestedFromNotification = {
            endLiveSession()
        }

        audioEngine.onUserBargeInDetected = {
            voiceSynthesizer.stopImmediately()
            _bargeInCount.value += 1
            if (_liveStatus.value == AlexLiveStatus.SPEAKING) {
                _liveStatus.value = AlexLiveStatus.LISTENING
            }
        }

        audioEngine.onVadUtteranceCompleted = { wavBytes ->
            // In Real-Time VAD Multimodal Audio Duplex mode (when WebSocket is not active),
            // send captured spoken utterance WAV directly to Gemini (using custom API key if set, or Firebase AI Logic)!
            val wsSession = activeSession
            val directWs = directWebSocket
            val isAnyWebSocketActive = (wsSession != null && !wsSession.isClosed()) ||
                (directWs != null && directWs.isSetupDone.get())

            if (!isAnyWebSocketActive &&
                _isLiveCallActive.value &&
                !voiceSynthesizer.isSpeaking.value &&
                isProcessingAudioTurn.compareAndSet(false, true)
            ) {
                controllerScope.launch {
                    try {
                        _liveStatus.value = AlexLiveStatus.THINKING
                        val result = brainService.processLiveAudioTurn(
                            wavBytes = wavBytes,
                            historyTurns = _liveTranscript.value,
                            settings = currentSettings
                        )
                        if (result.heardSpeech && _isLiveCallActive.value) {
                            _detectedLanguage.value = result.language
                            val uId = UUID.randomUUID().toString()
                            appendOrUpdateTurn(
                                id = uId,
                                sender = MessageSender.USER,
                                text = result.userTranscript,
                                languageOverride = result.language,
                                isStreaming = false
                            )
                            persistTurnIfEnabled(
                                sender = MessageSender.USER,
                                text = result.userTranscript,
                                wasInterrupted = false
                            )

                            val aId = UUID.randomUUID().toString()
                            appendOrUpdateTurn(
                                id = aId,
                                sender = MessageSender.ALEX,
                                text = result.alexReply,
                                languageOverride = result.language,
                                isStreaming = false
                            )
                            persistTurnIfEnabled(
                                sender = MessageSender.ALEX,
                                text = result.alexReply,
                                wasInterrupted = false
                            )

                            _liveStatus.value = AlexLiveStatus.SPEAKING
                            voiceSynthesizer.speak(result.alexReply, result.language)
                        } else if (_isLiveCallActive.value) {
                            _liveStatus.value = AlexLiveStatus.LISTENING
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio turn processing warning: ${e.message}")
                        if (_isLiveCallActive.value) {
                            _liveStatus.value = AlexLiveStatus.LISTENING
                        }
                    } finally {
                        isProcessingAudioTurn.set(false)
                    }
                }
            }
        }
    }

    fun startLiveSession(settings: AlexSettings) {
        currentSettings = settings
        audioEngine.updateAudioSettings(
            vadSensitivity = settings.vadSensitivity,
            autoBargeIn = settings.autoBargeInEnabled
        )
        voiceSynthesizer.updateVoiceConfig(
            voiceId = settings.selectedVoiceId,
            speechRate = settings.speechRate,
            pitch = settings.voicePitch
        )

        if (!brainService.isNetworkConnected()) {
            _liveStatus.value = AlexLiveStatus.DISCONNECTED
            _isLiveCallActive.value = false
            _errorMessage.value = "No internet connection. Please connect to Wi-Fi or mobile data and tap Reconnect."
            return
        }

        receiveJob?.cancel()
        statusMonitorJob?.cancel()
        directWebSocket?.close()
        directWebSocket = null

        _errorMessage.value = null
        _liveStatus.value = AlexLiveStatus.CONNECTING
        _isLiveCallActive.value = true

        receiveJob = controllerScope.launch {
            try {
                // 1. Create a conversation session in Room if history saving is enabled
                if (settings.saveHistoryEnabled && activeConversationId == null) {
                    val initialLang = if (settings.preferredLanguage == AlexLanguage.AUTO) {
                        AlexLanguage.ENGLISH
                    } else {
                        settings.preferredLanguage
                    }
                    activeConversationId = conversationRepository.createConversation(
                        title = "Live Call with ALEX (${settings.selectedVoiceId})",
                        modality = ConversationModality.LIVE_VOICE,
                        language = initialLang
                    )
                }

                // 2. Start Foreground Service indicating active microphone
                AlexLiveForegroundService.startService(context)

                // 3. Connect via DirectGeminiLiveWebSocket if custom API key is set, else try Firebase LiveSession
                var wsSession: LiveSession? = null
                if (settings.customApiKey.isNotBlank()) {
                    val customWs = DirectGeminiLiveWebSocket(
                        apiKey = settings.customApiKey,
                        settings = settings,
                        onAudioChunkReceived = { pcmBytes ->
                            _liveStatus.value = AlexLiveStatus.SPEAKING
                            audioEngine.enqueueModelAudioChunk(pcmBytes)
                        },
                        onTextReceived = { textChunk, turnComplete ->
                            controllerScope.launch {
                                if (textChunk.isNotBlank()) {
                                    currentAlexTurnBuffer.append(textChunk)
                                    val fullText = currentAlexTurnBuffer.toString().trim()
                                    val lang = LanguageDetector.detectLanguage(fullText)
                                    _detectedLanguage.value = lang
                                    appendOrUpdateTurn(
                                        id = currentAlexTurnId,
                                        sender = MessageSender.ALEX,
                                        text = fullText,
                                        languageOverride = lang,
                                        isStreaming = !turnComplete
                                    )
                                }
                                if (turnComplete && currentAlexTurnBuffer.isNotEmpty()) {
                                    val finalizedAlex = currentAlexTurnBuffer.toString().trim()
                                    appendOrUpdateTurn(
                                        id = currentAlexTurnId,
                                        sender = MessageSender.ALEX,
                                        text = finalizedAlex,
                                        isStreaming = false
                                    )
                                    persistTurnIfEnabled(
                                        sender = MessageSender.ALEX,
                                        text = finalizedAlex,
                                        wasInterrupted = false
                                    )
                                    currentAlexTurnBuffer.clear()
                                    currentAlexTurnId = UUID.randomUUID().toString()
                                    if (!audioEngine.isModelPlayingAudio.value) {
                                        _liveStatus.value = AlexLiveStatus.LISTENING
                                    }
                                }
                            }
                        },
                        onInterrupted = {
                            controllerScope.launch {
                                audioEngine.flushPlaybackForBargeIn()
                                voiceSynthesizer.stopImmediately()
                                _bargeInCount.value += 1
                                _liveStatus.value = AlexLiveStatus.LISTENING
                            }
                        },
                        onSetupComplete = {
                            controllerScope.launch {
                                _liveStatus.value = AlexLiveStatus.LISTENING
                            }
                        },
                        onClosedOrFailed = {
                            directWebSocket = null
                        }
                    )
                    customWs.connect()
                    directWebSocket = customWs
                } else {
                    wsSession = brainService.tryConnectLiveWebSocketSession(settings)
                    activeSession = wsSession
                }

                // 4. Start full-duplex AudioRecord (16kHz) + AudioTrack (24kHz) + VAD
                audioEngine.startFullDuplexStreaming(
                    enableEchoCancellation = settings.echoCancellationEnabled,
                    enableNoiseSuppression = settings.noiseSuppressionEnabled,
                    onPcmChunkCaptured = { pcmBytes ->
                        val dWs = directWebSocket
                        if (dWs != null && dWs.isSetupDone.get()) {
                            dWs.sendPcmAudioChunk(pcmBytes)
                        } else {
                            val current = activeSession
                            if (current != null && !current.isClosed()) {
                                try {
                                    current.sendAudioRealtime(
                                        InlineData(pcmBytes, AlexConfig.AUDIO_MIME_TYPE)
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                    onAudioError = { audioErr ->
                        _errorMessage.value = audioErr
                        _liveStatus.value = AlexLiveStatus.DISCONNECTED
                        _isLiveCallActive.value = false
                        AlexLiveForegroundService.stopService(context)
                    }
                )

                _liveStatus.value = AlexLiveStatus.LISTENING

                // 5. Monitor real-time audio states to keep Status HUD synchronized
                startStatusStateMonitor()

                // 6. Greet Boss using ALEX's warm female voice
                val greetingText = LanguageDetector.getLocalizedGreeting(
                    settings.preferredLanguage,
                    settings.userTitle
                )
                appendOrUpdateTurn(
                    id = UUID.randomUUID().toString(),
                    sender = MessageSender.ALEX,
                    text = greetingText,
                    languageOverride = if (settings.preferredLanguage == AlexLanguage.AUTO) {
                        AlexLanguage.ENGLISH
                    } else {
                        settings.preferredLanguage
                    },
                    isStreaming = false
                )
                persistTurnIfEnabled(MessageSender.ALEX, greetingText, wasInterrupted = false)

                if (wsSession != null && !wsSession.isClosed()) {
                    // Collect WebSocket messages if Firebase WebSocket transport is active
                    voiceSynthesizer.speak(greetingText, settings.preferredLanguage)
                    try {
                        wsSession.receive().collect { serverMessage ->
                            handleServerMessage(serverMessage, wsSession)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.i(TAG, "WebSocket stream switched to VAD Multimodal Audio Duplex: ${e.message}")
                        activeSession = null
                    }
                } else {
                    // Speak initial greeting via FemaleVoiceSynthesizer
                    _liveStatus.value = AlexLiveStatus.SPEAKING
                    voiceSynthesizer.speak(greetingText, settings.preferredLanguage)
                }

                // Keep live call active until user ends call
                while (isActive && _isLiveCallActive.value) {
                    delay(250)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live session fatal error", e)
                audioEngine.stopFullDuplexStreaming()
                AlexLiveForegroundService.stopService(context)
                _liveStatus.value = AlexLiveStatus.DISCONNECTED
                _isLiveCallActive.value = false
                _errorMessage.value = buildFriendlyErrorMessage(e)
            }
        }
    }

    private fun startStatusStateMonitor() {
        statusMonitorJob?.cancel()
        statusMonitorJob = controllerScope.launch {
            while (_isLiveCallActive.value) {
                val isPlaying = audioEngine.isModelPlayingAudio.value || voiceSynthesizer.isSpeaking.value
                val isUserSpeaking = audioEngine.isUserSpeakingVad.value
                when {
                    isPlaying -> {
                        if (_liveStatus.value != AlexLiveStatus.SPEAKING) {
                            _liveStatus.value = AlexLiveStatus.SPEAKING
                        }
                        if (voiceSynthesizer.isSpeaking.value && !audioEngine.isModelPlayingAudio.value) {
                            audioEngine.updateWaveformFromLevel(
                                voiceSynthesizer.ttsAmplitude.value,
                                isSpeaking = true
                            )
                        }
                    }
                    isProcessingAudioTurn.get() -> {
                        if (_liveStatus.value != AlexLiveStatus.THINKING) {
                            _liveStatus.value = AlexLiveStatus.THINKING
                        }
                    }
                    isUserSpeaking -> {
                        if (_liveStatus.value != AlexLiveStatus.LISTENING) {
                            _liveStatus.value = AlexLiveStatus.LISTENING
                        }
                    }
                    _liveStatus.value == AlexLiveStatus.SPEAKING -> {
                        _liveStatus.value = AlexLiveStatus.LISTENING
                    }
                }
                delay(60)
            }
        }
    }

    private suspend fun handleServerMessage(
        message: LiveServerMessage,
        session: LiveSession
    ) {
        when (message) {
            is LiveServerSetupComplete -> {
                _liveStatus.value = AlexLiveStatus.LISTENING
            }
            is LiveServerContent -> {
                if (message.interrupted) {
                    audioEngine.flushPlaybackForBargeIn()
                    voiceSynthesizer.stopImmediately()
                    _bargeInCount.value += 1
                    if (currentAlexTurnBuffer.isNotEmpty()) {
                        appendOrUpdateTurn(
                            id = currentAlexTurnId,
                            sender = MessageSender.ALEX,
                            text = currentAlexTurnBuffer.toString(),
                            isStreaming = false,
                            wasInterrupted = true
                        )
                        persistTurnIfEnabled(
                            sender = MessageSender.ALEX,
                            text = currentAlexTurnBuffer.toString(),
                            wasInterrupted = true
                        )
                        currentAlexTurnBuffer.clear()
                        currentAlexTurnId = UUID.randomUUID().toString()
                    }
                    _liveStatus.value = AlexLiveStatus.LISTENING
                    return
                }

                val inputTranscript = message.inputTranscription?.text.orEmpty()
                if (inputTranscript.isNotEmpty()) {
                    currentUserTurnBuffer.append(inputTranscript)
                    val fullUserText = currentUserTurnBuffer.toString().trim()
                    val lang = LanguageDetector.detectLanguage(fullUserText)
                    _detectedLanguage.value = lang
                    appendOrUpdateTurn(
                        id = currentUserTurnId,
                        sender = MessageSender.USER,
                        text = fullUserText,
                        languageOverride = lang,
                        isStreaming = true
                    )
                    _liveStatus.value = AlexLiveStatus.THINKING
                }

                val parts = message.content?.parts.orEmpty()
                for (part in parts) {
                    when (part) {
                        is InlineDataPart -> {
                            if (part.mimeType.startsWith("audio/pcm")) {
                                _liveStatus.value = AlexLiveStatus.SPEAKING
                                audioEngine.enqueueModelAudioChunk(part.inlineData)
                            }
                        }
                        is TextPart -> {
                            val textChunk = part.text
                            if (textChunk.isNotBlank()) {
                                currentAlexTurnBuffer.append(textChunk)
                                appendOrUpdateTurn(
                                    id = currentAlexTurnId,
                                    sender = MessageSender.ALEX,
                                    text = currentAlexTurnBuffer.toString().trim(),
                                    isStreaming = true
                                )
                            }
                        }
                    }
                }

                val outputTranscript = message.outputTranscription?.text.orEmpty()
                if (outputTranscript.isNotEmpty()) {
                    if (currentUserTurnBuffer.isNotEmpty()) {
                        val finalizedUser = currentUserTurnBuffer.toString().trim()
                        appendOrUpdateTurn(
                            id = currentUserTurnId,
                            sender = MessageSender.USER,
                            text = finalizedUser,
                            isStreaming = false
                        )
                        persistTurnIfEnabled(
                            sender = MessageSender.USER,
                            text = finalizedUser,
                            wasInterrupted = false
                        )
                        currentUserTurnBuffer.clear()
                        currentUserTurnId = UUID.randomUUID().toString()
                    }

                    currentAlexTurnBuffer.append(outputTranscript)
                    val fullAlexText = currentAlexTurnBuffer.toString().trim()
                    val lang = LanguageDetector.detectLanguage(fullAlexText)
                    _detectedLanguage.value = lang
                    appendOrUpdateTurn(
                        id = currentAlexTurnId,
                        sender = MessageSender.ALEX,
                        text = fullAlexText,
                        languageOverride = lang,
                        isStreaming = true
                    )
                }

                if (message.turnComplete) {
                    if (currentUserTurnBuffer.isNotEmpty()) {
                        val finalizedUser = currentUserTurnBuffer.toString().trim()
                        appendOrUpdateTurn(
                            id = currentUserTurnId,
                            sender = MessageSender.USER,
                            text = finalizedUser,
                            isStreaming = false
                        )
                        persistTurnIfEnabled(
                            sender = MessageSender.USER,
                            text = finalizedUser,
                            wasInterrupted = false
                        )
                        currentUserTurnBuffer.clear()
                        currentUserTurnId = UUID.randomUUID().toString()
                    }

                    if (currentAlexTurnBuffer.isNotEmpty()) {
                        val finalizedAlex = currentAlexTurnBuffer.toString().trim()
                        appendOrUpdateTurn(
                            id = currentAlexTurnId,
                            sender = MessageSender.ALEX,
                            text = finalizedAlex,
                            isStreaming = false
                        )
                        persistTurnIfEnabled(
                            sender = MessageSender.ALEX,
                            text = finalizedAlex,
                            wasInterrupted = false
                        )
                        currentAlexTurnBuffer.clear()
                        currentAlexTurnId = UUID.randomUUID().toString()
                    }

                    if (!audioEngine.isModelPlayingAudio.value) {
                        _liveStatus.value = AlexLiveStatus.LISTENING
                    }
                }
            }
            is LiveServerToolCall -> {
                _liveStatus.value = AlexLiveStatus.THINKING
                val toolResults = message.functionCalls.joinToString("\n") { call ->
                    brainService.executeToolCall(call)
                }
                if (toolResults.isNotEmpty()) {
                    session.send(toolResults)
                }
            }
            is LiveServerGoAway -> {
                activeSession = null
            }
        }
    }

    fun syncSettings(settings: AlexSettings) {
        currentSettings = settings
    }

    /**
     * Allows sending a live utterance or quick voice prompt directly into the active Live session.
     */
    fun sendVoicePromptInLiveSession(promptText: String, latestSettings: AlexSettings? = null) {
        if (latestSettings != null) {
            currentSettings = latestSettings
        }
        val clean = promptText.trim()
        if (clean.isEmpty()) return

        voiceSynthesizer.stopImmediately()
        val lang = LanguageDetector.detectLanguage(clean)
        _detectedLanguage.value = lang

        val userTurnId = UUID.randomUUID().toString()
        appendOrUpdateTurn(
            id = userTurnId,
            sender = MessageSender.USER,
            text = clean,
            languageOverride = lang,
            isStreaming = false
        )

        controllerScope.launch {
            persistTurnIfEnabled(MessageSender.USER, clean, wasInterrupted = false)
            val dWs = directWebSocket
            val session = activeSession
            if (dWs != null && dWs.isSetupDone.get() && _isLiveCallActive.value) {
                try {
                    _liveStatus.value = AlexLiveStatus.THINKING
                    dWs.sendClientText(clean)
                } catch (_: Exception) {
                    directWebSocket = null
                    speakWithStreamingBrain(clean)
                }
            } else if (session != null && !session.isClosed() && _isLiveCallActive.value) {
                try {
                    _liveStatus.value = AlexLiveStatus.THINKING
                    session.send(clean)
                } catch (_: Exception) {
                    activeSession = null
                    speakWithStreamingBrain(clean)
                }
            } else {
                speakWithStreamingBrain(clean)
            }
        }
    }

    private suspend fun speakWithStreamingBrain(promptText: String) {
        val alexTurnId = UUID.randomUUID().toString()
        val previousStatus = _liveStatus.value
        _liveStatus.value = AlexLiveStatus.THINKING
        var finalReply = ""
        try {
            brainService.streamChatResponse(
                prompt = promptText,
                historyTurns = _liveTranscript.value.dropLast(1),
                settings = currentSettings
            ).collect { accumulated ->
                finalReply = accumulated
                appendOrUpdateTurn(
                    id = alexTurnId,
                    sender = MessageSender.ALEX,
                    text = accumulated,
                    isStreaming = true
                )
            }
            if (finalReply.isNotBlank()) {
                val replyLang = LanguageDetector.detectLanguage(finalReply)
                _detectedLanguage.value = replyLang
                appendOrUpdateTurn(
                    id = alexTurnId,
                    sender = MessageSender.ALEX,
                    text = finalReply,
                    languageOverride = replyLang,
                    isStreaming = false
                )
                persistTurnIfEnabled(MessageSender.ALEX, finalReply, wasInterrupted = false)
                _liveStatus.value = AlexLiveStatus.SPEAKING
                voiceSynthesizer.speak(finalReply, replyLang)
                if (!_isLiveCallActive.value && previousStatus == AlexLiveStatus.READY) {
                    _liveStatus.value = AlexLiveStatus.READY
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = buildFriendlyErrorMessage(e)
            if (!_isLiveCallActive.value) {
                _liveStatus.value = AlexLiveStatus.DISCONNECTED
            }
        }
    }

    fun reconnectLiveSession() {
        endLiveSession(keepTranscript = true)
        startLiveSession(currentSettings)
    }

    fun endLiveSession(keepTranscript: Boolean = true) {
        _isLiveCallActive.value = false
        isProcessingAudioTurn.set(false)
        receiveJob?.cancel()
        statusMonitorJob?.cancel()
        receiveJob = null
        statusMonitorJob = null

        directWebSocket?.close()
        directWebSocket = null

        val sessionToClose = activeSession
        activeSession = null
        if (sessionToClose != null) {
            controllerScope.launch {
                runCatching { sessionToClose.close() }
            }
        }

        audioEngine.stopFullDuplexStreaming()
        voiceSynthesizer.stopImmediately()
        AlexLiveForegroundService.stopService(context)

        currentUserTurnBuffer.clear()
        currentAlexTurnBuffer.clear()

        if (!keepTranscript) {
            _liveTranscript.value = emptyList()
            activeConversationId = null
        }

        _liveStatus.value = AlexLiveStatus.READY
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun appendOrUpdateTurn(
        id: String,
        sender: MessageSender,
        text: String,
        languageOverride: AlexLanguage? = null,
        isStreaming: Boolean,
        wasInterrupted: Boolean = false
    ) {
        val lang = languageOverride ?: LanguageDetector.detectLanguage(text)
        val currentList = _liveTranscript.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == id }
        val turn = TranscriptTurn(
            id = id,
            sender = sender,
            text = text,
            language = lang,
            timestamp = if (existingIndex >= 0) currentList[existingIndex].timestamp else System.currentTimeMillis(),
            isStreaming = isStreaming,
            wasInterrupted = wasInterrupted
        )
        if (existingIndex >= 0) {
            currentList[existingIndex] = turn
        } else {
            currentList.add(turn)
        }
        _liveTranscript.value = currentList
    }

    private suspend fun persistTurnIfEnabled(
        sender: MessageSender,
        text: String,
        wasInterrupted: Boolean
    ) {
        if (!currentSettings.saveHistoryEnabled) return
        val convId = activeConversationId ?: conversationRepository.createConversation(
            title = text.take(42).ifBlank { "Live Call with ALEX" },
            modality = ConversationModality.LIVE_VOICE,
            language = LanguageDetector.detectLanguage(text)
        ).also { activeConversationId = it }

        conversationRepository.appendTurn(
            conversationId = convId,
            sender = sender,
            text = text,
            language = LanguageDetector.detectLanguage(text),
            wasInterrupted = wasInterrupted
        )
    }

    private fun buildFriendlyErrorMessage(e: Throwable): String {
        val raw = e.localizedMessage ?: e.message ?: "Unknown connection error"
        return when {
            raw.contains("403") || raw.contains("AppCheck", ignoreCase = true) ->
                "Firebase App Check verification error (403). Please verify API Configuration or tap Reconnect."
            raw.contains("429") || raw.contains("quota", ignoreCase = true) ->
                "Gemini API quota limit reached. Please wait a moment and tap Reconnect."
            raw.contains("network", ignoreCase = true) || raw.contains("Unable to resolve host", ignoreCase = true) ->
                "Network connection lost. Check your internet connection and tap Reconnect."
            else -> "Live connection error: $raw"
        }
    }
}
