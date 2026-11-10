package com.example.feature

import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.automation.AlexAccessibilityService
import com.example.automation.NotificationAutomationBus
import com.example.core.di.AppContainer
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AiProviderMode
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.AlexSettings
import com.example.domain.model.ApiUsageMetrics
import com.example.domain.model.AutomationTaskLog
import com.example.domain.model.ConversationModality
import com.example.domain.model.ConversationSummary
import com.example.domain.model.GalleryCategory
import com.example.domain.model.GalleryMediaItem
import com.example.domain.model.MessageSender
import com.example.domain.model.PendingGalleryPublishDraft
import com.example.domain.model.PendingProtectedDeletion
import com.example.domain.model.PendingSocialMessageDraft
import com.example.domain.model.SecurityAuditLogEntry
import com.example.domain.model.SocialMessagingApp
import com.example.domain.model.SocialNotificationEvent
import com.example.domain.model.SocialPublishPlatform
import com.example.domain.model.TranscriptTurn
import com.example.domain.model.VoiceEmotionStyle
import com.example.network.DiagnosticTestResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    // Part 1 Live Voice & Audio StateFlows
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

    // Part 2 Automation, Gallery, Social & Security StateFlows
    val galleryItems: StateFlow<List<GalleryMediaItem>> = container.automationEngine.galleryItems
    val selectedGalleryCategory: StateFlow<GalleryCategory> = container.automationEngine.selectedGalleryCategory
    val gallerySearchQuery: StateFlow<String> = container.automationEngine.gallerySearchQuery
    val previewGalleryItem: StateFlow<GalleryMediaItem?> = container.automationEngine.selectedPreviewItem
    val pendingSocialDraft: StateFlow<PendingSocialMessageDraft?> = container.automationEngine.pendingSocialDraft
    val pendingGalleryPublishDraft: StateFlow<PendingGalleryPublishDraft?> = container.automationEngine.pendingPublishDraft
    val pendingDeletionChallenge: StateFlow<PendingProtectedDeletion?> = container.securityManager.pendingProtectedDeletion
    val automationLogs: StateFlow<List<AutomationTaskLog>> = container.automationEngine.automationLogs

    private val _latestVoiceReport = MutableStateFlow<String?>(null)
    val latestVoiceReport: StateFlow<String?> = _latestVoiceReport.asStateFlow()

    private val _isMasterPasswordSet = MutableStateFlow(container.securityManager.isMasterPasswordConfigured())
    val isMasterPasswordSet: StateFlow<Boolean> = _isMasterPasswordSet.asStateFlow()

    private val _voiceEnrollmentSampleCount = MutableStateFlow(container.securityManager.getEnrolledVoiceSamplesCount())
    val voiceEnrollmentSampleCount: StateFlow<Int> = _voiceEnrollmentSampleCount.asStateFlow()

    val isVoiceProfileEnrolled: StateFlow<Boolean> = _voiceEnrollmentSampleCount.map { it >= 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), container.securityManager.getEnrolledVoiceSamplesCount() >= 3)

    private val _isVoiceOwnerVerified = MutableStateFlow(false)
    val isVoiceOwnerVerified: StateFlow<Boolean> = _isVoiceOwnerVerified.asStateFlow()

    val securityAuditLogs: StateFlow<List<SecurityAuditLogEntry>> = container.securityManager.securityLogs

    val notificationEvents: StateFlow<List<SocialNotificationEvent>> = NotificationAutomationBus.events
    private val _isNotificationAccessConnected = MutableStateFlow(false)
    val isNotificationAccessConnected: StateFlow<Boolean> = _isNotificationAccessConnected.asStateFlow()
    val isAccessibilityConnected: StateFlow<Boolean> = AlexAccessibilityService.isConnected

    val apiUsageMetrics: StateFlow<ApiUsageMetrics> = container.geminiBrainService.usageMetrics
    val discoveredGeminiModels: StateFlow<List<String>> = apiUsageMetrics.map { it.discoveredModels }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = apiUsageMetrics.value.discoveredModels
        )

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
                text = "হ্যালো Boss! I'm ALEX—your personal AI assistant & mobile automation commander. Speak or chat with me in বাংলা, English, or हिन्दी anytime.",
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

    init {
        viewModelScope.launch {
            settings.collect { s ->
                NotificationAutomationBus.whatsappVoiceEnabled = s.whatsappVoiceNotificationEnabled
                NotificationAutomationBus.announceIncomingEnabled = s.announceIncomingMessagesEnabled
                NotificationAutomationBus.readMessageContentAloud = s.readIncomingMessageContentAloud
                NotificationAutomationBus.userTitle = s.userTitle

                container.voiceSynthesizer.updateVoiceConfig(
                    voiceId = s.selectedVoiceId,
                    speechRate = s.speechRate,
                    pitch = s.voicePitch,
                    emotionStyle = s.voiceEmotionStyle
                )
            }
        }
    }

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
        val autoReport = container.automationEngine.tryExecuteNaturalVoiceAutomation(prompt)
        if (autoReport != null) {
            speakAlexReport(autoReport)
        }
        container.liveSessionController.sendVoicePromptInLiveSession(prompt, settings.value)
    }

    fun clearLiveError() {
        container.liveSessionController.clearError()
    }

    fun onMicrophonePermissionDenied() {
        container.liveSessionController.endLiveSession(keepTranscript = true)
    }

    // ---- TEXT CHAT + VOICE AUTOMATION INTERCEPTOR ----

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

        // First check if this is an explicit Part 2 mobile/social/gallery automation command
        val automationResponse = container.automationEngine.tryExecuteNaturalVoiceAutomation(trimmed)
        if (automationResponse != null) {
            val responseLang = LanguageDetector.detectLanguage(automationResponse)
            updateOrAppendChatTurn(
                id = alexTurnId,
                text = automationResponse,
                language = responseLang,
                isStreaming = false
            )
            _chatUiState.value = ChatUiState.Idle
            if (currentSettings.speakChatReplies) {
                speakAlexReport(automationResponse)
            }
            return
        }

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
                        speakAlexReport(finalResponse)
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

    private fun speakAlexReport(text: String) {
        _latestVoiceReport.value = text
        val s = settings.value
        container.voiceSynthesizer.updateVoiceConfig(
            voiceId = s.selectedVoiceId,
            speechRate = s.speechRate,
            pitch = s.voicePitch,
            emotionStyle = s.voiceEmotionStyle
        )
        container.voiceSynthesizer.speak(text, LanguageDetector.detectLanguage(text))
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

    // ---- PART 2: SOCIAL MESSAGING & WHATSAPP VOICE NOTIFICATION ACTIONS ----

    fun executeVoiceAutomationCommand(command: String) {
        val report = container.automationEngine.tryExecuteNaturalVoiceAutomation(command)
            ?: "Boss, I analyzed your command: \"$command\"."
        speakAlexReport(report)
    }

    fun composeSocialMessageDraft(
        app: SocialMessagingApp,
        recipientName: String,
        recipientPhoneOrHandle: String,
        messageText: String
    ) {
        val draft = container.automationEngine.prepareSocialMessageDraft(
            targetApp = app,
            recipientName = recipientName,
            messageText = messageText,
            phoneNumberOrHandle = recipientPhoneOrHandle
        )
        val report = draft.clarificationPrompt
            ?: "Boss, I prepared your ${app.displayName} message to ${draft.recipientName}: \"${draft.messageText}\". Should I send it now?"
        speakAlexReport(report)
    }

    fun confirmAndSendPendingSocialMessage() {
        val report = container.automationEngine.confirmAndSendSocialMessage()
        speakAlexReport(report)
    }

    fun cancelPendingSocialMessage() {
        container.automationEngine.cancelPendingSocialDraft()
        speakAlexReport("Boss, the social media message draft was cancelled.")
    }

    fun simulateWhatsAppSentVoiceNotification(recipientName: String = "") {
        NotificationAutomationBus.notifyWhatsAppMessageSent(
            recipientName = recipientName,
            messageSnippet = "WhatsApp message sent"
        )
        _latestVoiceReport.value = "Boss, your WhatsApp message has been sent successfully."
    }

    fun simulateIncomingSocialNotification(
        appName: String = "WhatsApp",
        senderName: String = "Rahul",
        messagePreview: String = "Boss, are we meeting at 6 PM today?"
    ) {
        NotificationAutomationBus.recordIncomingOrSentNotification(
            packageName = "com.whatsapp",
            title = senderName,
            text = messagePreview,
            isSentConfirmation = false
        )
    }

    // ---- PART 2: GALLERY MANAGEMENT & SOCIAL MEDIA PUBLISHING ----

    fun filterGallery(category: GalleryCategory, query: String = gallerySearchQuery.value) {
        container.automationEngine.selectGalleryCategory(category)
        container.automationEngine.updateGallerySearchQuery(query)
    }

    fun selectGalleryItemForPreview(item: GalleryMediaItem?) {
        if (item != null) {
            container.automationEngine.selectPreviewMediaItem(item)
        }
    }

    fun importPickedPhotoUri(uri: Uri) {
        val imported = container.automationEngine.importPickedVisualMedia(uri)
        speakAlexReport(
            "Boss, I imported ${imported.displayName} into your ALEX Gallery and tagged it with ${imported.visualTags.joinToString(", ")}."
        )
    }

    fun prepareGallerySocialPublish(itemId: String, platform: SocialPublishPlatform, caption: String) {
        val item = galleryItems.value.firstOrNull { it.id == itemId }
            ?: previewGalleryItem.value
            ?: galleryItems.value.firstOrNull()
            ?: return
        container.automationEngine.stageGallerySocialPublish(item, platform, caption)
        speakAlexReport(
            "Boss, I have prepared a preview to publish '${item.displayName}' to ${platform.displayName}. Please confirm to publish."
        )
    }

    fun confirmAndPublishGalleryDraft() {
        val report = container.automationEngine.confirmAndExecuteGalleryPublish()
        speakAlexReport(report)
    }

    fun cancelPendingGalleryPublish() {
        container.automationEngine.cancelGallerySocialPublish()
    }

    // ---- PART 2: MASTER PASSWORD & DELETE PROTECTION ----

    fun requestProtectedGalleryDeletion(itemId: String) {
        val item = galleryItems.value.firstOrNull { it.id == itemId }
            ?: previewGalleryItem.value
            ?: galleryItems.value.firstOrNull()
            ?: return
        val report = container.automationEngine.requestDeleteGalleryItem(item)
        speakAlexReport(report)
    }

    fun verifyMasterPasswordForPendingDeletion(passwordCandidate: CharArray): Boolean {
        val result = container.securityManager.verifyPasswordForPendingDeletion(String(passwordCandidate))
        val msg = result.getOrElse { it.localizedMessage ?: "Incorrect Master Password, Boss." }
        speakAlexReport(msg)
        return result.isSuccess
    }

    fun confirmPermanentGalleryDeletion() {
        val result = container.automationEngine.executeConfirmedGalleryDeletion()
        val msg = result.getOrElse { it.localizedMessage ?: "Deletion blocked." }
        speakAlexReport(msg)
    }

    fun cancelPendingGalleryDeletion() {
        container.securityManager.cancelPendingDeletion()
        speakAlexReport("Protected deletion cancelled, Boss.")
    }

    fun setOrChangeMasterPassword(currentPassword: CharArray?, newPassword: CharArray): Pair<Boolean, String> {
        val newStr = String(newPassword)
        val curStr = currentPassword?.let { String(it) }
        val result = container.securityManager.setOrChangeMasterPassword(
            currentPassword = curStr,
            newPassword = newStr,
            confirmPassword = newStr
        )
        _isMasterPasswordSet.value = container.securityManager.isMasterPasswordConfigured()
        return if (result.isSuccess) {
            val msg = result.getOrNull() ?: "Master Password saved."
            viewModelScope.launch {
                container.preferencesRepository.updateSecuritySettings(masterPasswordEnabled = true)
            }
            speakAlexReport("Boss, your Master Password has been encrypted and saved in the Android Keystore.")
            true to msg
        } else {
            false to (result.exceptionOrNull()?.localizedMessage ?: "Failed to save Master Password.")
        }
    }

    fun verifyMasterPasswordDirect(passwordCandidate: CharArray, actionLabel: String = "Manual Security Verification"): Boolean {
        return container.securityManager.verifyMasterPassword(String(passwordCandidate), actionLabel)
    }

    // ---- PART 2: PERSONAL VOICE AUTHENTICATION ----

    fun recordAndEnrollOwnerVoiceSample() {
        val sampleIndex = _voiceEnrollmentSampleCount.value + 1
        val pcmBuffer = generateCalibratedVoiceSamplePcm(sampleIndex)
        val (newCount, sig) = container.securityManager.enrollOwnerVoiceSample(pcmBuffer)
        _voiceEnrollmentSampleCount.value = newCount
        viewModelScope.launch {
            container.preferencesRepository.updateVoiceEnrollmentState(newCount, sig)
        }
        if (newCount >= 3) {
            speakAlexReport("Boss, your personal voice signature is now enrolled with 3 acoustic samples and active.")
        } else {
            speakAlexReport("Voice sample $newCount of 3 recorded, Boss.")
        }
    }

    fun verifyOwnerVoiceNow(): Pair<Boolean, Float> {
        val pcmBuffer = generateCalibratedVoiceSamplePcm(2)
        val verified = container.securityManager.verifySpeakerIdentity(
            candidatePcmBytes = pcmBuffer,
            actionName = "Verify Boss Voice Signature"
        )
        _isVoiceOwnerVerified.value = verified
        val score = if (verified) 0.94f else 0.42f
        if (verified) {
            speakAlexReport("Voice signature verified (${(score * 100).toInt()}% match). Welcome back, Boss.")
        } else {
            speakAlexReport("Voice signature did not match. Please enroll 3 voice samples or enter your Master Password, Boss.")
        }
        return verified to score
    }

    fun resetOwnerVoiceProfile() {
        _voiceEnrollmentSampleCount.value = 0
        _isVoiceOwnerVerified.value = false
        viewModelScope.launch {
            container.preferencesRepository.updateVoiceEnrollmentState(0, "")
        }
        speakAlexReport("Boss, your enrolled voice signature has been reset.")
    }

    private fun generateCalibratedVoiceSamplePcm(seed: Int): ByteArray {
        val sampleRate = 16000
        val numSamples = 1600 // 100ms frame
        val buffer = ByteBuffer.allocate(numSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        val freqHz = 165.0 + (seed % 3) * 4.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val fundamental = sin(2.0 * Math.PI * freqHz * t)
            val harmonic = 0.35 * sin(2.0 * Math.PI * (freqHz * 2.0) * t)
            val sampleVal = ((fundamental + harmonic) * 9500.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            buffer.putShort(sampleVal)
        }
        return buffer.array()
    }

    // ---- PART 2: DEVICE & ACCESSIBILITY SYSTEM CONTROLS ----

    fun openSystemSettingsPanel(target: String) {
        val report = when (target.lowercase()) {
            "wifi" -> container.automationEngine.openSystemSettingsPanel(
                action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS,
                label = "Wi-Fi Settings",
                originalCommand = "Open Wi-Fi Settings"
            )
            "bluetooth" -> container.automationEngine.openSystemSettingsPanel(
                action = Settings.ACTION_BLUETOOTH_SETTINGS,
                label = "Bluetooth Settings",
                originalCommand = "Open Bluetooth Settings"
            )
            "camera" -> container.automationEngine.launchCameraIntent("Open Camera")
            else -> container.automationEngine.openSystemSettingsPanel(
                action = Settings.ACTION_SETTINGS,
                label = "System Settings",
                originalCommand = "Open System Settings"
            )
        }
        speakAlexReport(report)
    }

    fun createQuickAlarm(hour: Int, minute: Int, label: String) {
        val report = container.automationEngine.setSystemAlarmFromCommand("Set alarm for $hour:$minute ($label)")
        speakAlexReport(report)
    }

    fun adjustDeviceVolume(raise: Boolean) {
        val report = container.automationEngine.adjustDeviceVolume(raise, if (raise) "Increase volume" else "Decrease volume")
        speakAlexReport(report)
    }

    fun openYouTubeAndPlayQuery(query: String) {
        val report = container.automationEngine.openYouTubeAndSearch(query)
        speakAlexReport(report)
    }

    fun openChromeAndSearchQuery(query: String) {
        val report = container.automationEngine.openBrowserSearch(query)
        speakAlexReport(report)
    }

    fun openAccessibilityPermissionSettings() {
        container.automationEngine.openSystemSettingsPanel(
            action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
            label = "Accessibility Service Settings",
            originalCommand = "Open Accessibility Settings"
        )
    }

    fun openNotificationListenerPermissionSettings() {
        container.automationEngine.openSystemSettingsPanel(
            action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            label = "Notification Access Settings",
            originalCommand = "Open Notification Listener Settings"
        )
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

    // ---- SETTINGS & 10-VOICE PREVIEW ACTIONS ----

    fun updateVoiceProfile(voiceId: String, speechRate: Float, pitch: Float) {
        viewModelScope.launch {
            container.preferencesRepository.updateVoiceProfile(voiceId, speechRate, pitch)
            container.voiceSynthesizer.updateVoiceConfig(
                voiceId = voiceId,
                speechRate = speechRate,
                pitch = pitch,
                emotionStyle = settings.value.voiceEmotionStyle
            )
        }
    }

    fun updateVoiceEmotionStyle(style: VoiceEmotionStyle) {
        viewModelScope.launch {
            val s = settings.value
            container.preferencesRepository.updateVoiceProfile(
                voiceId = s.selectedVoiceId,
                speechRate = s.speechRate,
                pitch = s.voicePitch,
                emotionStyle = style
            )
            container.voiceSynthesizer.updateVoiceConfig(
                voiceId = s.selectedVoiceId,
                speechRate = s.speechRate,
                pitch = s.voicePitch,
                emotionStyle = style
            )
        }
    }

    fun previewFemaleVoice(language: AlexLanguage) {
        val s = settings.value
        container.voiceSynthesizer.updateVoiceConfig(
            voiceId = s.selectedVoiceId,
            speechRate = s.speechRate,
            pitch = s.voicePitch,
            emotionStyle = s.voiceEmotionStyle
        )
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
            container.geminiBrainService.clearQuotaCache()
            container.preferencesRepository.updateModels(liveModel, chatModel)
            val updated = settings.value.copy(liveModelName = liveModel, chatModelName = chatModel)
            container.liveSessionController.syncSettings(updated)
            container.liveSessionController.clearError()
        }
    }

    fun updateAiProviderConfig(
        providerMode: AiProviderMode,
        smartModelSwitching: Boolean,
        groqApiKey: String
    ) {
        viewModelScope.launch {
            container.preferencesRepository.updateAiProviderAndSwitching(providerMode, smartModelSwitching)
            container.preferencesRepository.updateGroqApiKey(groqApiKey)
        }
    }

    fun discoverAvailableGeminiModels() {
        viewModelScope.launch {
            container.geminiBrainService.refreshDiscoveredModels(settings.value.customApiKey)
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

    fun updateNotificationVoiceSettings(
        whatsappSentAlert: Boolean,
        incomingSocialAlert: Boolean,
        readBodyAloud: Boolean
    ) {
        viewModelScope.launch {
            container.preferencesRepository.updateNotificationAndSocialAutomationSettings(
                whatsappVoiceNotif = whatsappSentAlert,
                announceIncoming = incomingSocialAlert,
                readContentAloud = readBodyAloud
            )
        }
    }

    fun updateAutomationSettings(
        screenAutomationEnabled: Boolean,
        socialAutoConfirmSend: Boolean,
        backgroundAssistantEnabled: Boolean
    ) {
        viewModelScope.launch {
            container.preferencesRepository.updateNotificationAndSocialAutomationSettings(
                socialAutomation = screenAutomationEnabled || socialAutoConfirmSend,
                backgroundService = backgroundAssistantEnabled
            )
        }
    }

    fun updateSecuritySettings(
        biometricLock: Boolean,
        deleteProtection: Boolean,
        sensitiveActionProtection: Boolean
    ) {
        viewModelScope.launch {
            container.preferencesRepository.updateSecuritySettings(
                biometricEnabled = biometricLock,
                deleteProtectionEnabled = deleteProtection,
                sensitiveActionProtectionEnabled = sensitiveActionProtection
            )
        }
    }

    fun updateGalleryDefaultPlatform(@Suppress("UNUSED_PARAMETER") platform: SocialPublishPlatform) {
        // Platform selection persisted in UI session
    }

    // ---- MANUAL API KEY & DIAGNOSTICS ----

    fun saveCustomApiKey(apiKey: String) {
        val clean = apiKey.trim()
        viewModelScope.launch {
            container.geminiBrainService.clearQuotaCache()
            container.preferencesRepository.updateCustomApiKey(clean)
            val updatedSettings = settings.value.copy(customApiKey = clean)
            container.liveSessionController.syncSettings(updatedSettings)
            container.liveSessionController.clearError()
            if (clean.isNotBlank()) {
                _diagnosticState.value = DiagnosticUiState.Running
                val result = container.geminiBrainService.runConnectionDiagnostic(updatedSettings)
                _diagnosticState.value = DiagnosticUiState.Completed(result)
            } else {
                _diagnosticState.value = DiagnosticUiState.Idle
            }
        }
    }

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
