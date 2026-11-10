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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private var receiveJob: Job? = null
    private var statusMonitorJob: Job? = null
    private var activeConversationId: Long? = null
    private var currentSettings: AlexSettings = AlexSettings()

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

        _errorMessage.value = null
        _liveStatus.value = AlexLiveStatus.CONNECTING
        _isLiveCallActive.value = true

        receiveJob = controllerScope.launch {
            try {
                // Create a conversation session in Room if history saving is enabled
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

                // 1. Connect to official Gemini Live API
                val session = brainService.connectLiveSession(settings)
                activeSession = session

                // 2. Start Foreground Service indicating active microphone
                AlexLiveForegroundService.startService(context)

                // 3. Start full-duplex AudioRecord (16kHz) + AudioTrack (24kHz)
                audioEngine.startFullDuplexStreaming(
                    enableEchoCancellation = settings.echoCancellationEnabled,
                    enableNoiseSuppression = settings.noiseSuppressionEnabled,
                    onPcmChunkCaptured = { pcmBytes ->
                        try {
                            val current = activeSession
                            if (current != null && !current.isClosed()) {
                                current.sendAudioRealtime(
                                    InlineData(pcmBytes, AlexConfig.AUDIO_MIME_TYPE)
                                )
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Realtime audio frame send warning: ${e.message}")
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

                // 4. Monitor real-time audio states to keep Status HUD synchronized
                startStatusStateMonitor()

                // 5. Trigger ALEX's initial warm female voice greeting
                val greetingPrompt = when (settings.preferredLanguage) {
                    AlexLanguage.BANGLA -> "Boss লাইভে যুক্ত হয়েছেন। বাংলায় সংক্ষেপে ও আন্তরিকভাবে Boss-কে স্বাগত জানাও।"
                    AlexLanguage.HINDI -> "Boss लाइव कॉल पर जुड़ गए हैं। हिंदी में संक्षेप और गर्मजोशी से Boss का स्वागत करें।"
                    else -> "Boss just connected to the live voice call. Greet Boss warmly in one short, natural sentence as ALEX."
                }
                runCatching {
                    session.send(greetingPrompt)
                }.onFailure {
                    // Fallback local greeting if session initial text frame is ignored
                    val greetingText = LanguageDetector.getLocalizedGreeting(
                        settings.preferredLanguage,
                        settings.userTitle
                    )
                    appendOrUpdateTurn(
                        id = UUID.randomUUID().toString(),
                        sender = MessageSender.ALEX,
                        text = greetingText,
                        isStreaming = false
                    )
                    voiceSynthesizer.speak(greetingText, settings.preferredLanguage)
                }

                // 6. Collect continuous bidirectional Gemini Live server messages
                session.receive().collect { serverMessage ->
                    handleServerMessage(serverMessage, session)
                }

                // If collect finishes while call was supposed to be active, mark disconnected
                if (_isLiveCallActive.value) {
                    _liveStatus.value = AlexLiveStatus.DISCONNECTED
                    _isLiveCallActive.value = false
                    _errorMessage.value = "Gemini Live session ended. Tap Reconnect to resume conversation."
                    audioEngine.stopFullDuplexStreaming()
                    AlexLiveForegroundService.stopService(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Live session error", e)
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
                Log.d(TAG, "Gemini Live SetupComplete received.")
                _liveStatus.value = AlexLiveStatus.LISTENING
            }
            is LiveServerContent -> {
                // 1. Check Barge-in Interruption
                if (message.interrupted) {
                    Log.d(TAG, "Gemini Live signaled barge-in interruption.")
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

                // 2. Process real-time user input audio transcription
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

                // 3. Process model parts (PCM audio chunks and/or text)
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

                // 4. Process output audio transcription (spoken words of ALEX)
                val outputTranscript = message.outputTranscription?.text.orEmpty()
                if (outputTranscript.isNotEmpty()) {
                    // Commit pending user turn if user finished speaking
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

                // 5. Handle Turn Complete
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
                Log.w(TAG, "Gemini Live ServerGoAway received; reconnecting automatically...")
                reconnectLiveSession()
            }
        }
    }

    /**
     * Allows sending a live utterance or quick voice prompt directly into the active Live session
     * (or via hybrid streaming voice if the user triggers a quick voice prompt).
     */
    fun sendVoicePromptInLiveSession(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty()) return

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
            val session = activeSession
            if (session != null && !session.isClosed() && _isLiveCallActive.value) {
                try {
                    _liveStatus.value = AlexLiveStatus.THINKING
                    session.send(clean)
                } catch (e: Exception) {
                    Log.w(TAG, "LiveSession.send fallback to streaming brain: ${e.message}")
                    speakWithStreamingFallback(clean)
                }
            } else {
                speakWithStreamingFallback(clean)
            }
        }
    }

    private suspend fun speakWithStreamingFallback(promptText: String) {
        val alexTurnId = UUID.randomUUID().toString()
        _liveStatus.value = AlexLiveStatus.THINKING
        var finalReply = ""
        try {
            brainService.streamChatResponse(
                prompt = promptText,
                historyTurns = _liveTranscript.value,
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
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = buildFriendlyErrorMessage(e)
            _liveStatus.value = AlexLiveStatus.DISCONNECTED
        }
    }

    fun reconnectLiveSession() {
        endLiveSession(keepTranscript = true)
        startLiveSession(currentSettings)
    }

    fun endLiveSession(keepTranscript: Boolean = true) {
        _isLiveCallActive.value = false
        receiveJob?.cancel()
        statusMonitorJob?.cancel()
        receiveJob = null
        statusMonitorJob = null

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
            else -> "Live connection interrupted: $raw"
        }
    }
}
