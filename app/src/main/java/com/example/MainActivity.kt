package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.di.AppContainer
import com.example.feature.AlexViewModel
import com.example.feature.automation.AutomationHubScreen
import com.example.feature.chat.ChatScreen
import com.example.feature.history.HistoryScreen
import com.example.feature.home.HomeScreen
import com.example.feature.livevoice.LiveVoiceScreen
import com.example.feature.onboarding.SplashScreen
import com.example.feature.onboarding.WelcomeScreen
import com.example.feature.settings.AboutScreen
import com.example.feature.settings.ApiConfigScreen
import com.example.feature.settings.SettingsScreen
import com.example.security.configureAppCheck
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.MyApplicationTheme

object AlexRoutes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val LIVE_VOICE = "live_voice"
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val AUTOMATION_HUB = "automation_hub"
    const val SETTINGS = "settings"
    const val API_CONFIG = "api_config"
    const val ABOUT = "about"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // STEP 2: Initialize Compose UI
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AlexBlack
                ) {
                    val context = LocalContext.current
                    val container = remember { AppContainer(context) }
                    val viewModel: AlexViewModel = viewModel(
                        factory = AlexViewModel.provideFactory(container)
                    )
                    AlexNavigationHost(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AlexNavigationHost(
    viewModel: AlexViewModel,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AlexRoutes.SPLASH
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveStatus by viewModel.liveStatus.collectAsStateWithLifecycle()
    val isLiveCallActive by viewModel.isLiveCallActive.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicMuted.collectAsStateWithLifecycle()
    val waveformBars by viewModel.waveformBars.collectAsStateWithLifecycle()
    val inputAmplitude by viewModel.inputAmplitude.collectAsStateWithLifecycle()
    val outputAmplitude by viewModel.outputAmplitude.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.liveTranscript.collectAsStateWithLifecycle()
    val detectedLanguage by viewModel.detectedLanguage.collectAsStateWithLifecycle()
    val bargeInCount by viewModel.bargeInCount.collectAsStateWithLifecycle()
    val liveErrorMessage by viewModel.liveErrorMessage.collectAsStateWithLifecycle()

    val chatTurns by viewModel.chatTurns.collectAsStateWithLifecycle()
    val chatUiState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val selectedConversationTurns by viewModel.selectedConversationTurns.collectAsStateWithLifecycle()
    val diagnosticState by viewModel.diagnosticState.collectAsStateWithLifecycle()

    // Part 2 StateFlows
    val galleryItems by viewModel.galleryItems.collectAsStateWithLifecycle()
    val selectedGalleryCategory by viewModel.selectedGalleryCategory.collectAsStateWithLifecycle()
    val gallerySearchQuery by viewModel.gallerySearchQuery.collectAsStateWithLifecycle()
    val previewGalleryItem by viewModel.previewGalleryItem.collectAsStateWithLifecycle()
    val pendingSocialDraft by viewModel.pendingSocialDraft.collectAsStateWithLifecycle()
    val pendingGalleryPublishDraft by viewModel.pendingGalleryPublishDraft.collectAsStateWithLifecycle()
    val pendingDeletionChallenge by viewModel.pendingDeletionChallenge.collectAsStateWithLifecycle()
    val automationLogs by viewModel.automationLogs.collectAsStateWithLifecycle()
    val latestVoiceReport by viewModel.latestVoiceReport.collectAsStateWithLifecycle()

    val isMasterPasswordSet by viewModel.isMasterPasswordSet.collectAsStateWithLifecycle()
    val isVoiceProfileEnrolled by viewModel.isVoiceProfileEnrolled.collectAsStateWithLifecycle()
    val voiceEnrollmentSampleCount by viewModel.voiceEnrollmentSampleCount.collectAsStateWithLifecycle()
    val isVoiceOwnerVerified by viewModel.isVoiceOwnerVerified.collectAsStateWithLifecycle()
    val securityAuditLogs by viewModel.securityAuditLogs.collectAsStateWithLifecycle()

    val notificationEvents by viewModel.notificationEvents.collectAsStateWithLifecycle()
    val isNotificationAccessConnected by viewModel.isNotificationAccessConnected.collectAsStateWithLifecycle()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsStateWithLifecycle()

    val discoveredGeminiModels by viewModel.discoveredGeminiModels.collectAsStateWithLifecycle()
    val apiUsageMetrics by viewModel.apiUsageMetrics.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(AlexRoutes.SPLASH) {
            SplashScreen(
                onSplashFinished = {
                    val target = if (settings.hasCompletedOnboarding) {
                        AlexRoutes.HOME
                    } else {
                        AlexRoutes.WELCOME
                    }
                    navController.navigate(target) {
                        popUpTo(AlexRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(AlexRoutes.WELCOME) {
            WelcomeScreen(
                onGetStarted = {
                    viewModel.completeOnboarding()
                    navController.navigate(AlexRoutes.HOME) {
                        popUpTo(AlexRoutes.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(AlexRoutes.HOME) {
            HomeScreen(
                liveStatus = liveStatus,
                isLiveCallActive = isLiveCallActive,
                isMicMuted = isMicMuted,
                waveformBars = waveformBars,
                inputAmplitude = inputAmplitude,
                outputAmplitude = outputAmplitude,
                liveTranscript = liveTranscript,
                detectedLanguage = detectedLanguage,
                errorMessage = liveErrorMessage,
                settings = settings,
                onStartLive = { viewModel.startLiveCall() },
                onEndCall = { viewModel.endLiveCall() },
                onReconnect = { viewModel.reconnectLiveCall() },
                onToggleMicMute = { viewModel.toggleMicMute() },
                onSendQuickVoicePrompt = { prompt -> viewModel.sendLiveVoiceQuickPrompt(prompt) },
                onPermissionDenied = { viewModel.onMicrophonePermissionDenied() },
                onNavigateToLiveVoice = { navController.navigate(AlexRoutes.LIVE_VOICE) },
                onNavigateToChat = { navController.navigate(AlexRoutes.CHAT) },
                onNavigateToHistory = { navController.navigate(AlexRoutes.HISTORY) },
                onNavigateToAutomationHub = { navController.navigate(AlexRoutes.AUTOMATION_HUB) },
                onNavigateToSettings = { navController.navigate(AlexRoutes.SETTINGS) },
                onNavigateToApiConfig = { navController.navigate(AlexRoutes.API_CONFIG) },
                onNavigateToAbout = { navController.navigate(AlexRoutes.ABOUT) }
            )
        }

        composable(AlexRoutes.LIVE_VOICE) {
            LiveVoiceScreen(
                liveStatus = liveStatus,
                isLiveCallActive = isLiveCallActive,
                isMicMuted = isMicMuted,
                waveformBars = waveformBars,
                inputAmplitude = inputAmplitude,
                outputAmplitude = outputAmplitude,
                liveTranscript = liveTranscript,
                detectedLanguage = detectedLanguage,
                bargeInCount = bargeInCount,
                errorMessage = liveErrorMessage,
                settings = settings,
                onStartLive = { viewModel.startLiveCall() },
                onEndCall = { viewModel.endLiveCall() },
                onReconnect = { viewModel.reconnectLiveCall() },
                onToggleMicMute = { viewModel.toggleMicMute() },
                onInterruptSpeech = { viewModel.stopSpeaking() },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(AlexRoutes.SETTINGS) }
            )
        }

        composable(AlexRoutes.CHAT) {
            ChatScreen(
                chatTurns = chatTurns,
                chatUiState = chatUiState,
                isTtsSpeaking = isTtsSpeaking,
                settings = settings,
                onSendMessage = { msg -> viewModel.sendChatMessage(msg) },
                onStartNewChat = { viewModel.startNewChatSession() },
                onStopSpeaking = { viewModel.stopSpeaking() },
                onToggleSpeakReplies = { enabled -> viewModel.updateSpeakChatReplies(enabled) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AlexRoutes.HISTORY) {
            HistoryScreen(
                conversations = conversations,
                selectedConversationTurns = selectedConversationTurns,
                settings = settings,
                onSelectConversation = { id -> viewModel.loadConversationDetails(id) },
                onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                onClearAllHistory = { viewModel.clearAllHistory() },
                onToggleSaveHistory = { enabled -> viewModel.updatePrivacySettings(enabled) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AlexRoutes.AUTOMATION_HUB) {
            AutomationHubScreen(
                settings = settings,
                galleryItems = galleryItems,
                selectedGalleryCategory = selectedGalleryCategory,
                gallerySearchQuery = gallerySearchQuery,
                previewGalleryItem = previewGalleryItem,
                pendingSocialDraft = pendingSocialDraft,
                pendingGalleryPublishDraft = pendingGalleryPublishDraft,
                pendingDeletionChallenge = pendingDeletionChallenge,
                automationLogs = automationLogs,
                latestVoiceReport = latestVoiceReport,
                isMasterPasswordSet = isMasterPasswordSet,
                isVoiceProfileEnrolled = isVoiceProfileEnrolled,
                voiceEnrollmentSampleCount = voiceEnrollmentSampleCount,
                isVoiceOwnerVerified = isVoiceOwnerVerified,
                securityAuditLogs = securityAuditLogs,
                notificationEvents = notificationEvents,
                isNotificationAccessConnected = isNotificationAccessConnected,
                isAccessibilityConnected = isAccessibilityConnected,
                onExecuteVoiceAutomationCommand = { cmd -> viewModel.executeVoiceAutomationCommand(cmd) },
                onComposeSocialMessageDraft = { app, name, handle, msg ->
                    viewModel.composeSocialMessageDraft(app, name, handle, msg)
                },
                onConfirmSocialMessage = { viewModel.confirmAndSendPendingSocialMessage() },
                onCancelSocialMessage = { viewModel.cancelPendingSocialMessage() },
                onSimulateWhatsAppSent = { name -> viewModel.simulateWhatsAppSentVoiceNotification(name) },
                onSimulateIncomingSocial = { app, sender, preview ->
                    viewModel.simulateIncomingSocialNotification(app, sender, preview)
                },
                onFilterGallery = { cat, q -> viewModel.filterGallery(cat, q) },
                onSelectGalleryPreview = { item -> viewModel.selectGalleryItemForPreview(item) },
                onImportPickedPhoto = { uri -> viewModel.importPickedPhotoUri(uri) },
                onPrepareGallerySocialPublish = { itemId, platform, caption ->
                    viewModel.prepareGallerySocialPublish(itemId, platform, caption)
                },
                onConfirmGalleryPublish = { viewModel.confirmAndPublishGalleryDraft() },
                onCancelGalleryPublish = { viewModel.cancelPendingGalleryPublish() },
                onRequestProtectedDeletion = { itemId -> viewModel.requestProtectedGalleryDeletion(itemId) },
                onVerifyPasswordForDeletion = { pass ->
                    viewModel.verifyMasterPasswordForPendingDeletion(pass)
                },
                onConfirmPermanentDeletion = { viewModel.confirmPermanentGalleryDeletion() },
                onCancelPendingDeletion = { viewModel.cancelPendingGalleryDeletion() },
                onSetOrChangeMasterPassword = { cur, next ->
                    viewModel.setOrChangeMasterPassword(cur, next)
                },
                onEnrollVoiceSample = { viewModel.recordAndEnrollOwnerVoiceSample() },
                onVerifyOwnerVoice = { viewModel.verifyOwnerVoiceNow() },
                onResetVoiceProfile = { viewModel.resetOwnerVoiceProfile() },
                onOpenSystemSettingsPanel = { target -> viewModel.openSystemSettingsPanel(target) },
                onCreateQuickAlarm = { h, m, label -> viewModel.createQuickAlarm(h, m, label) },
                onAdjustVolume = { raise -> viewModel.adjustDeviceVolume(raise) },
                onOpenYouTubeSearch = { q -> viewModel.openYouTubeAndPlayQuery(q) },
                onOpenChromeSearch = { q -> viewModel.openChromeAndSearchQuery(q) },
                onOpenAccessibilitySettings = { viewModel.openAccessibilityPermissionSettings() },
                onOpenNotificationAccessSettings = { viewModel.openNotificationListenerPermissionSettings() },
                onUpdateNotificationVoiceSettings = { waSent, incoming, body ->
                    viewModel.updateNotificationVoiceSettings(waSent, incoming, body)
                },
                onUpdateSecuritySettings = { bio, delProt, sens ->
                    viewModel.updateSecuritySettings(bio, delProt, sens)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AlexRoutes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                onUpdateVoiceProfile = { id, rate, pitch ->
                    viewModel.updateVoiceProfile(id, rate, pitch)
                },
                onUpdateVoiceEmotionStyle = { style ->
                    viewModel.updateVoiceEmotionStyle(style)
                },
                onPreviewVoice = { lang -> viewModel.previewFemaleVoice(lang) },
                onUpdatePreferredLanguage = { lang -> viewModel.updatePreferredLanguage(lang) },
                onUpdateAudioControls = { bargeIn, aec, ns, vad ->
                    viewModel.updateAudioControls(bargeIn, aec, ns, vad)
                },
                onUpdatePrivacySettings = { save -> viewModel.updatePrivacySettings(save) },
                onUpdateSpeakChatReplies = { speak -> viewModel.updateSpeakChatReplies(speak) },
                onUpdateUserTitle = { title -> viewModel.updateUserTitle(title) },
                onUpdateNotificationVoiceSettings = { waSent, incoming, body ->
                    viewModel.updateNotificationVoiceSettings(waSent, incoming, body)
                },
                onUpdateAutomationSettings = { screenAuto, socialConfirm, bgAssist ->
                    viewModel.updateAutomationSettings(screenAuto, socialConfirm, bgAssist)
                },
                onUpdateSecuritySettings = { bio, delProt, sens ->
                    viewModel.updateSecuritySettings(bio, delProt, sens)
                },
                onUpdateGalleryDefaultPlatform = { platform ->
                    viewModel.updateGalleryDefaultPlatform(platform)
                },
                onClearAllHistory = { viewModel.clearAllHistory() },
                onNavigateToAutomationHub = { navController.navigate(AlexRoutes.AUTOMATION_HUB) },
                onNavigateToApiConfig = { navController.navigate(AlexRoutes.API_CONFIG) },
                onNavigateToAbout = { navController.navigate(AlexRoutes.ABOUT) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AlexRoutes.API_CONFIG) {
            ApiConfigScreen(
                settings = settings,
                diagnosticState = diagnosticState,
                discoveredModels = discoveredGeminiModels,
                apiUsageMetrics = apiUsageMetrics,
                onSaveCustomApiKey = { key ->
                    viewModel.saveCustomApiKey(key)
                },
                onUpdateModels = { liveModel, chatModel ->
                    viewModel.updateModels(liveModel, chatModel)
                },
                onUpdateAiProviderConfig = { mode, smartSwitch, groqKey ->
                    viewModel.updateAiProviderConfig(mode, smartSwitch, groqKey)
                },
                onDiscoverGeminiModels = { viewModel.discoverAvailableGeminiModels() },
                onRunDiagnostic = { viewModel.runGeminiDiagnosticTest() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AlexRoutes.ABOUT) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
