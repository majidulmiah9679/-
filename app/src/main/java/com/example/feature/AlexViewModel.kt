package com.example.feature

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.AppContainer
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.AlexSettings
import com.example.domain.model.ConversationModality
import com.example.domain.model.ConversationSummary
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
import com.example.network.DiagnosticTestResult
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AlexViewModel"

sealed interface ChatUiState {
    data object Idle : ChatUiState
    data object Thinking : ChatUiState
    data class Streaming(val partialText: String) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

sealed interface DiagnosticUiState {
    data object Idle : DiagnosticUiState
    data object Running : DiagnosticUiState
    data class Completed(val result: DiagnosticTestResult) : DiagnosticUiState
}

class AlexViewModel(
    private val container: AppContainer
) : ViewModel() {

    val settings: StateFlow<AlexSettings> = container.preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AlexSettings()
        )

    val liveStatus: StateFlow<AlexLiveStatus> = container.liveSessionController.liveStatus
    val isLiveCallActive: StateFlow<Boolean> = container.liveSessionController.isLiveCallActive
    val liveErrorMessage: StateFlow<String?> = container.liveSessionController.errorMessage
    val liveTranscript: StateFlow<List<TranscriptTurn>> = container.liveSessionController.liveTranscript
    val detectedLanguage: StateFlow<AlexLanguage> = container.liveSessionController.detectedLanguage
    val bargeInCount: StateFlow<Int> = container.liveSessionController.bargeInCount

    val isMicMuted: StateFlow<Boolean> = container.audioEngine.isMicMuted
    val waveformBars: StateFlow<List<Float>> = container.audioEngine.waveformBars
    val inputAmplitude: StateFlow<Float> = container.audioEngine.inputAmplitude
    val outputAmplitude: StateFlow<Float> = container.audioEngine.outputAmplitude
    val isTtsSpeaking: StateFlow<Boolean> = container.voiceSynthesizer.isSpeaking

    val conversations: StateFlow<List<ConversationSummary>> =
        container.conversationRepository.allConversations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _chatTurns = MutableStateFlow<List<TranscriptTurn>>(
        listOf(
            TranscriptTurn(
                id = "welcome_turn",
                sender = MessageSender.ALEX,
                text = "হ্যালো Boss! I'm ALEX—your personal AI assistant. You can speak or chat with me in বাংলা, English, or हिन्दी anytime.",
                language = AlexLanguage.ENGLISH
            )
        )
    )
    val chatTurns: StateFlow<List<TranscriptTurn>> = _chatTurns.asStateFlow()

    private val _chatUiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _selectedConversationTurns = MutableStateFlow<List<TranscriptTurn>>(emptyList())
    val selectedConversationTurns: StateFlow<List<TranscriptTurn>> = _selectedConversationTurns.asStateFlow()

    private val _diagnosticState = MutableStateFlow<DiagnosticUiState>(DiagnosticUiState.Idle)
    val diagnosticState: StateFlow<DiagnosticUiState> = _diagnosticState.asStateFlow()

    private var activeChatConversationId: Long? = null
    private var chatStreamJob: Job? = null

    fun completeOnboarding() {
        viewModelScope.launch {
            container.preferencesRepository.setOnboardingCompleted(true)
        }
    }

    // ---- LIVE VOICE ACTIONS ----

    fun startLiveCall() {
        container.liveSessionController.startLiveSession(settings.value)
    }

    fun endLiveCall() {
        container.liveSessionController.endLiveSession(keepTranscript = true)
    }

    fun reconnectLiveCall() {
        container.liveSessionController.reconnectLiveSession()
    }

    fun toggleMicMute() {
        container.audioEngine.toggleMicMute()
    }

    fun sendLiveVoiceQuickPrompt(prompt: String) {
        container.liveSessionController.sendVoicePromptInLiveSession(prompt)
    }

    fun clearLiveError() {
        container.liveSessionController.clearError()
    }

    fun onMicrophonePermissionDenied() {
        container.liveSessionController.endLiveSession(keepTranscript = true)
    }

    // ---- TEXT CHAT ACTIONS ----

    fun sendChatMessage(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) return

        chatStreamJob?.cancel()
        container.voiceSynthesizer.stopImmediately()

        val currentSettings = settings.value
        val detectedLang = LanguageDetector.detectLanguage(trimmed)
        val userTurn = TranscriptTurn(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = trimmed,
            language = detectedLang,
            isStreaming = false
        )
        val alexTurnId = UUID.randomUUID().toString()

        _chatTurns.value = _chatTurns.value + userTurn
        _chatUiState.value = ChatUiState.Thinking

        chatStreamJob = viewModelScope.launch {
            try {
                if (currentSettings.saveHistoryEnabled) {
                    val convId = activeChatConversationId ?: container.conversationRepository.createConversation(
                        title = trimmed.take(48),
                        modality = ConversationModality.TEXT_CHAT,
                        language = detectedLang
                    ).also { activeChatConversationId = it }

                    container.conversationRepository.appendTurn(
                        conversationId = convId,
                        sender = MessageSender.USER,
                        text = trimmed,
                        language = detectedLang
                    )
                }

                var finalResponse = ""
                container.geminiBrainService.streamChatResponse(
                    prompt = trimmed,
                    historyTurns = _chatTurns.value.dropLast(1),
                    settings = currentSettings
                ).collect { partial ->
                    finalResponse = partial
                    _chatUiState.value = ChatUiState.Streaming(partial)
                    updateOrAppendChatTurn(
                        id = alexTurnId,
                        text = partial,
                        language = LanguageDetector.detectLanguage(partial),
                        isStreaming = true
                    )
                }

                if (finalResponse.isNotBlank()) {
                    val responseLang = LanguageDetector.detectLanguage(finalResponse)
                    updateOrAppendChatTurn(
                        id = alexTurnId,
                        text = finalResponse,
                        language = responseLang,
                        isStreaming = false
                    )
                    _chatUiState.value = ChatUiState.Idle

                    if (currentSettings.saveHistoryEnabled && activeChatConversationId != null) {
                        container.conversationRepository.appendTurn(
                            conversationId = activeChatConversationId!!,
                            sender = MessageSender.ALEX,
                            text = finalResponse,
                            language = responseLang
                        )
                    }

                    if (currentSettings.speakChatReplies) {
                        container.voiceSynthesizer.updateVoiceConfig(
                            voiceId = currentSettings.selectedVoiceId,
                            speechRate = currentSettings.speechRate,
                            pitch = currentSettings.voicePitch
                        )
                        container.voiceSynthesizer.speak(finalResponse, responseLang)
                    }
                } else {
                    _chatUiState.value = ChatUiState.Error("ALEX returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat streaming timed out", e)
                _chatUiState.value = ChatUiState.Error("Request timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat streaming failed", e)
                _chatUiState.value = ChatUiState.Error(
                    e.localizedMessage ?: "Unable to reach Gemini AI. Check connection."
                )
            }
        }
    }

    fun startNewChatSession() {
        chatStreamJob?.cancel()
        container.voiceSynthesizer.stopImmediately()
        activeChatConversationId = null
        _chatUiState.value = ChatUiState.Idle
        _chatTurns.value = listOf(
            TranscriptTurn(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.ALEX,
                text = LanguageDetector.getLocalizedGreeting(
                    settings.value.preferredLanguage,
                    settings.value.userTitle
                ),
                language = settings.value.preferredLanguage
            )
        )
    }

    fun stopSpeaking() {
        container.voiceSynthesizer.stopImmediately()
        container.audioEngine.flushPlaybackForBargeIn()
    }

    private fun updateOrAppendChatTurn(
        id: String,
        text: String,
        language: AlexLanguage,
        isStreaming: Boolean
    ) {
        val current = _chatTurns.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        val turn = TranscriptTurn(
            id = id,
            sender = MessageSender.ALEX,
            text = text,
            language = language,
            timestamp = if (idx >= 0) current[idx].timestamp else System.currentTimeMillis(),
            isStreaming = isStreaming
        )
        if (idx >= 0) {
            current[idx] = turn
        } else {
            current.add(turn)
        }
        _chatTurns.value = current
    }

    // ---- HISTORY ACTIONS ----

    fun loadConversationDetails(conversationId: Long) {
        viewModelScope.launch {
            _selectedConversationTurns.value =
                container.conversationRepository.getTranscriptSnapshot(conversationId)
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            container.conversationRepository.deleteConversation(conversationId)
            if (activeChatConversationId == conversationId) {
                activeChatConversationId = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            container.conversationRepository.clearAllHistory()
            activeChatConversationId = null
        }
    }

    // ---- SETTINGS & VOICE PREVIEW ACTIONS ----

    fun updateVoiceProfile(voiceId: String, speechRate: Float, pitch: Float) {
        viewModelScope.launch {
            container.preferencesRepository.updateVoiceProfile(voiceId, speechRate, pitch)
            container.voiceSynthesizer.updateVoiceConfig(voiceId, speechRate, pitch)
        }
    }

    fun previewFemaleVoice(language: AlexLanguage) {
        val s = settings.value
        container.voiceSynthesizer.updateVoiceConfig(s.selectedVoiceId, s.speechRate, s.voicePitch)
        val sampleText = LanguageDetector.getLocalizedGreeting(language, s.userTitle)
        container.voiceSynthesizer.speak(sampleText, language)
    }

    fun updatePreferredLanguage(language: AlexLanguage) {
        viewModelScope.launch {
            container.preferencesRepository.updatePreferredLanguage(language)
        }
    }

    fun updateModels(liveModel: String, chatModel: String) {
        viewModelScope.launch {
            container.preferencesRepository.updateModels(liveModel, chatModel)
        }
    }

    fun updatePrivacySettings(saveHistory: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updatePrivacySettings(saveHistory)
        }
    }

    fun updateAudioControls(
        autoBargeIn: Boolean,
        echoCancellation: Boolean,
        noiseSuppression: Boolean,
        vadSensitivity: Float
    ) {
        viewModelScope.launch {
            container.preferencesRepository.updateAudioControls(
                autoBargeIn = autoBargeIn,
                echoCancellation = echoCancellation,
                noiseSuppression = noiseSuppression,
                vadSensitivity = vadSensitivity
            )
            container.audioEngine.updateAudioSettings(vadSensitivity, autoBargeIn)
        }
    }

    fun updateSpeakChatReplies(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.updateSpeakChatReplies(enabled)
        }
    }

    fun updateUserTitle(title: String) {
        viewModelScope.launch {
            container.preferencesRepository.updateUserTitle(title)
        }
    }

    // ---- API DIAGNOSTICS ----

    fun runGeminiDiagnosticTest() {
        viewModelScope.launch {
            _diagnosticState.value = DiagnosticUiState.Running
            val result = container.geminiBrainService.runConnectionDiagnostic(settings.value)
            _diagnosticState.value = DiagnosticUiState.Completed(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        container.liveSessionController.endLiveSession(keepTranscript = false)
        container.voiceSynthesizer.release()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlexViewModel(container) as T
                }
            }
    }
}
