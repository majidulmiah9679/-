package com.example.feature.automation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlexSettings
import com.example.domain.model.AutomationTaskLog
import com.example.domain.model.GalleryCategory
import com.example.domain.model.GalleryMediaItem
import com.example.domain.model.PendingGalleryPublishDraft
import com.example.domain.model.PendingProtectedDeletion
import com.example.domain.model.PendingSocialMessageDraft
import com.example.domain.model.SecurityAuditLogEntry
import com.example.domain.model.SocialMessagingApp
import com.example.domain.model.SocialNotificationEvent
import com.example.domain.model.SocialPublishPlatform
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexStatusConnecting
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexStatusReady
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextMuted
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary
import android.net.Uri

@Composable
fun AutomationHubScreen(
    settings: AlexSettings,
    galleryItems: List<GalleryMediaItem>,
    selectedGalleryCategory: GalleryCategory,
    gallerySearchQuery: String,
    previewGalleryItem: GalleryMediaItem?,
    pendingSocialDraft: PendingSocialMessageDraft?,
    pendingGalleryPublishDraft: PendingGalleryPublishDraft?,
    pendingDeletionChallenge: PendingProtectedDeletion?,
    automationLogs: List<AutomationTaskLog>,
    latestVoiceReport: String?,
    isMasterPasswordSet: Boolean,
    isVoiceProfileEnrolled: Boolean,
    voiceEnrollmentSampleCount: Int,
    isVoiceOwnerVerified: Boolean,
    securityAuditLogs: List<SecurityAuditLogEntry>,
    notificationEvents: List<SocialNotificationEvent>,
    isNotificationAccessConnected: Boolean,
    isAccessibilityConnected: Boolean,
    onExecuteVoiceAutomationCommand: (String) -> Unit,
    onComposeSocialMessageDraft: (SocialMessagingApp, String, String, String) -> Unit,
    onConfirmSocialMessage: () -> Unit,
    onCancelSocialMessage: () -> Unit,
    onSimulateWhatsAppSent: (String) -> Unit,
    onSimulateIncomingSocial: (String, String, String) -> Unit,
    onFilterGallery: (GalleryCategory, String) -> Unit,
    onSelectGalleryPreview: (GalleryMediaItem?) -> Unit,
    onImportPickedPhoto: (Uri) -> Unit,
    onPrepareGallerySocialPublish: (String, SocialPublishPlatform, String) -> Unit,
    onConfirmGalleryPublish: () -> Unit,
    onCancelGalleryPublish: () -> Unit,
    onRequestProtectedDeletion: (String) -> Unit,
    onVerifyPasswordForDeletion: (CharArray) -> Boolean,
    onConfirmPermanentDeletion: () -> Unit,
    onCancelPendingDeletion: () -> Unit,
    onSetOrChangeMasterPassword: (CharArray?, CharArray) -> Pair<Boolean, String>,
    onEnrollVoiceSample: () -> Unit,
    onVerifyOwnerVoice: () -> Pair<Boolean, Float>,
    onResetVoiceProfile: () -> Unit,
    onOpenSystemSettingsPanel: (String) -> Unit,
    onCreateQuickAlarm: (Int, Int, String) -> Unit,
    onAdjustVolume: (Boolean) -> Unit,
    onOpenYouTubeSearch: (String) -> Unit,
    onOpenChromeSearch: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onUpdateNotificationVoiceSettings: (Boolean, Boolean, Boolean) -> Unit,
    onUpdateSecuritySettings: (Boolean, Boolean, Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var deletePasswordError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportPickedPhoto(uri)
        }
    }

    // Master Password Delete Protection Modal
    if (pendingDeletionChallenge != null) {
        AlertDialog(
            onDismissRequest = {
                deletePasswordInput = ""
                deletePasswordError = null
                onCancelPendingDeletion()
            },
            modifier = Modifier.testTag("delete_protection_dialog"),
            containerColor = AlexCardNavy,
            titleContentColor = AlexCyan,
            textContentColor = AlexTextPrimary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Delete Protection",
                        tint = AlexStatusDisconnected
                    )
                    Text(
                        text = if (pendingDeletionChallenge.isPasswordVerified) {
                            "CONFIRM PERMANENT DELETION"
                        } else {
                            "MASTER PASSWORD REQUIRED"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!pendingDeletionChallenge.isPasswordVerified) {
                        Text(
                            text = "\"Boss, please provide your master password.\"\n\nProtected Item: ${pendingDeletionChallenge.itemTitle}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexTextPrimary
                        )
                        OutlinedTextField(
                            value = deletePasswordInput,
                            onValueChange = {
                                deletePasswordInput = it
                                deletePasswordError = null
                            },
                            label = { Text("Enter Master Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_password_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AlexCyan,
                                unfocusedBorderColor = AlexCardBorder,
                                focusedTextColor = AlexTextPrimary,
                                unfocusedTextColor = AlexTextPrimary
                            )
                        )
                        if (deletePasswordError != null) {
                            Text(
                                text = deletePasswordError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = AlexStatusDisconnected
                            )
                        }
                    } else {
                        Text(
                            text = "\"Password verified, Boss. Do you want me to permanently delete '${pendingDeletionChallenge.itemTitle}'?\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlexStatusReady,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                if (!pendingDeletionChallenge.isPasswordVerified) {
                    Button(
                        onClick = {
                            val ok = onVerifyPasswordForDeletion(deletePasswordInput.toCharArray())
                            deletePasswordInput = ""
                            if (!ok) {
                                deletePasswordError = "Incorrect Master Password, Boss. Deletion denied."
                            }
                        },
                        enabled = deletePasswordInput.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexCyan,
                            contentColor = AlexBlack
                        ),
                        modifier = Modifier.testTag("verify_delete_password_button")
                    ) {
                        Text("VERIFY PASSWORD", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            deletePasswordInput = ""
                            deletePasswordError = null
                            onConfirmPermanentDeletion()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexStatusDisconnected,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("confirm_permanent_delete_button")
                    ) {
                        Text("YES, DELETE PERMANENTLY", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deletePasswordInput = ""
                        deletePasswordError = null
                        onCancelPendingDeletion()
                    },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("CANCEL", color = AlexTextSecondary)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AlexBlack, AlexDeepNavy, AlexBlack)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("automation_hub_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("automation_hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlexCyan
                        )
                    }
                    Column {
                        Text(
                            text = "ALEX AUTOMATION & GALLERY",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AlexCyan
                        )
                        Text(
                            text = "PART 2 • WHATSAPP • SOCIAL • GALLERY • SECURITY",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexTextSecondary
                        )
                    }
                }
            }

            // Latest Voice Report Banner
            if (!latestVoiceReport.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("latest_voice_report_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = AlexSurfaceNavy,
                    border = BorderStroke(1.dp, AlexCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "ALEX Voice Report",
                            tint = AlexCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = latestVoiceReport,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HubTabButton(
                    title = "1. AUTOMATION",
                    selected = selectedTab == 0,
                    testTag = "tab_automation",
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                HubTabButton(
                    title = "2. GALLERY",
                    selected = selectedTab == 1,
                    testTag = "tab_gallery",
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
                HubTabButton(
                    title = "3. SECURITY",
                    selected = selectedTab == 2,
                    testTag = "tab_security",
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> AutomationAndSocialTab(
                        settings = settings,
                        pendingSocialDraft = pendingSocialDraft,
                        notificationEvents = notificationEvents,
                        automationLogs = automationLogs,
                        isNotificationAccessConnected = isNotificationAccessConnected,
                        isAccessibilityConnected = isAccessibilityConnected,
                        onExecuteVoiceAutomationCommand = onExecuteVoiceAutomationCommand,
                        onComposeSocialMessageDraft = onComposeSocialMessageDraft,
                        onConfirmSocialMessage = onConfirmSocialMessage,
                        onCancelSocialMessage = onCancelSocialMessage,
                        onSimulateWhatsAppSent = onSimulateWhatsAppSent,
                        onSimulateIncomingSocial = onSimulateIncomingSocial,
                        onOpenSystemSettingsPanel = onOpenSystemSettingsPanel,
                        onCreateQuickAlarm = onCreateQuickAlarm,
                        onAdjustVolume = onAdjustVolume,
                        onOpenYouTubeSearch = onOpenYouTubeSearch,
                        onOpenChromeSearch = onOpenChromeSearch,
                        onUpdateNotificationVoiceSettings = onUpdateNotificationVoiceSettings
                    )
                    1 -> GalleryAndPublisherTab(
                        galleryItems = galleryItems,
                        selectedCategory = selectedGalleryCategory,
                        searchQuery = gallerySearchQuery,
                        previewItem = previewGalleryItem,
                        pendingPublishDraft = pendingGalleryPublishDraft,
                        onFilterGallery = onFilterGallery,
                        onSelectGalleryPreview = onSelectGalleryPreview,
                        onLaunchPhotoPicker = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onPrepareSocialPublish = onPrepareGallerySocialPublish,
                        onConfirmGalleryPublish = onConfirmGalleryPublish,
                        onCancelGalleryPublish = onCancelGalleryPublish,
                        onRequestProtectedDeletion = onRequestProtectedDeletion
                    )
                    2 -> SecurityAndPermissionsTab(
                        settings = settings,
                        isMasterPasswordSet = isMasterPasswordSet,
                        isVoiceProfileEnrolled = isVoiceProfileEnrolled,
                        voiceEnrollmentSampleCount = voiceEnrollmentSampleCount,
                        isVoiceOwnerVerified = isVoiceOwnerVerified,
                        securityAuditLogs = securityAuditLogs,
                        isNotificationAccessConnected = isNotificationAccessConnected,
                        isAccessibilityConnected = isAccessibilityConnected,
                        onSetOrChangeMasterPassword = onSetOrChangeMasterPassword,
                        onEnrollVoiceSample = onEnrollVoiceSample,
                        onVerifyOwnerVoice = onVerifyOwnerVoice,
                        onResetVoiceProfile = onResetVoiceProfile,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                        onUpdateSecuritySettings = onUpdateSecuritySettings
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HubTabButton(
    title: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AlexCyan else AlexCardNavy,
        border = BorderStroke(1.dp, if (selected) AlexCyan else AlexCardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) AlexBlack else AlexTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AutomationAndSocialTab(
    settings: AlexSettings,
    pendingSocialDraft: PendingSocialMessageDraft?,
    notificationEvents: List<SocialNotificationEvent>,
    automationLogs: List<AutomationTaskLog>,
    isNotificationAccessConnected: Boolean,
    isAccessibilityConnected: Boolean,
    onExecuteVoiceAutomationCommand: (String) -> Unit,
    onComposeSocialMessageDraft: (SocialMessagingApp, String, String, String) -> Unit,
    onConfirmSocialMessage: () -> Unit,
    onCancelSocialMessage: () -> Unit,
    onSimulateWhatsAppSent: (String) -> Unit,
    onSimulateIncomingSocial: (String, String, String) -> Unit,
    onOpenSystemSettingsPanel: (String) -> Unit,
    onCreateQuickAlarm: (Int, Int, String) -> Unit,
    onAdjustVolume: (Boolean) -> Unit,
    onOpenYouTubeSearch: (String) -> Unit,
    onOpenChromeSearch: (String) -> Unit,
    onUpdateNotificationVoiceSettings: (Boolean, Boolean, Boolean) -> Unit
) {
    var voiceCommandText by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf(SocialMessagingApp.WHATSAPP) }
    var recipientName by remember { mutableStateOf("Rahul") }
    var recipientHandle by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("Hello Rahul! Just checking in.") }

    // 1. NATURAL VOICE AUTOMATION COMMAND RUNNER
    HubSectionCard(title = "1. NATURAL VOICE & MULTI-STEP AUTOMATION COMMANDER") {
        Text(
            text = "Execute Bangla, Banglish, Hindi, or English voice commands for WhatsApp/Social messaging, YouTube/Chrome search, alarms, and screen actions.",
            style = MaterialTheme.typography.bodyMedium,
            color = AlexTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presetCommands = listOf(
                "Alex, send a WhatsApp message to Rahul saying I am on my way",
                "Alex, Facebook-e Rahul-ke message pathao",
                "Alex, Instagram-e amar friend-ke message pathao",
                "Alex, open YouTube, search for a video, and play it",
                "Alex, open Chrome and search for Google",
                "Alex, set an alarm for 6 AM"
            )
            presetCommands.forEachIndexed { idx, cmd ->
                Surface(
                    modifier = Modifier
                        .clickable {
                            voiceCommandText = cmd
                            onExecuteVoiceAutomationCommand(cmd)
                        }
                        .testTag("automation_preset_$idx"),
                    shape = RoundedCornerShape(50),
                    color = AlexDeepNavy,
                    border = BorderStroke(1.dp, AlexCyan.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = cmd,
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = voiceCommandText,
                onValueChange = { voiceCommandText = it },
                label = { Text("Voice / Automation Command") },
                placeholder = { Text("Alex, send a WhatsApp message to Rahul...") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("automation_command_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlexCyan,
                    unfocusedBorderColor = AlexCardBorder,
                    focusedTextColor = AlexTextPrimary,
                    unfocusedTextColor = AlexTextPrimary
                )
            )
            Button(
                onClick = {
                    if (voiceCommandText.isNotBlank()) {
                        onExecuteVoiceAutomationCommand(voiceCommandText)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexCyan,
                    contentColor = AlexBlack
                ),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("run_automation_command_button")
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Execute")
                Spacer(modifier = Modifier.width(4.dp))
                Text("RUN", fontWeight = FontWeight.Bold)
            }
        }
    }

    // 2. PENDING SOCIAL MESSAGE CONFIRMATION STEP (7-Step Workflow Step 5 & 6)
    if (pendingSocialDraft != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pending_social_confirmation_card"),
            shape = RoundedCornerShape(16.dp),
            color = AlexStatusReady.copy(alpha = 0.15f),
            border = BorderStroke(1.5.dp, AlexStatusReady)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "CONFIRM SOCIAL MESSAGE BEFORE SENDING",
                    style = MaterialTheme.typography.labelLarge,
                    color = AlexStatusReady,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "App: ${pendingSocialDraft.targetApp.displayName} • Recipient: ${pendingSocialDraft.recipientName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AlexTextPrimary
                )
                Text(
                    text = "Message: \"${pendingSocialDraft.messageText}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConfirmSocialMessage,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexStatusReady,
                            contentColor = AlexBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_social_send_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONFIRM & SEND", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCancelSocialMessage,
                        border = BorderStroke(1.dp, AlexStatusDisconnected),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cancel_social_send_button")
                    ) {
                        Text("CANCEL", color = AlexStatusDisconnected)
                    }
                }
            }
        }
    }

    // 3. UNIVERSAL SOCIAL MEDIA MESSAGE AUTOMATION COMPOSER
    HubSectionCard(title = "2. UNIVERSAL SOCIAL MEDIA MESSAGE AUTOMATION") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialMessagingApp.entries.forEach { app ->
                FilterChip(
                    selected = selectedApp == app,
                    onClick = { selectedApp = app },
                    label = { Text(app.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlexCyan,
                        selectedLabelColor = AlexBlack
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = recipientName,
                onValueChange = { recipientName = it },
                label = { Text("Recipient Name") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("social_recipient_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlexCyan,
                    unfocusedBorderColor = AlexCardBorder,
                    focusedTextColor = AlexTextPrimary,
                    unfocusedTextColor = AlexTextPrimary
                )
            )
            OutlinedTextField(
                value = recipientHandle,
                onValueChange = { recipientHandle = it },
                label = { Text("Phone / Handle (Optional)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlexCyan,
                    unfocusedBorderColor = AlexCardBorder,
                    focusedTextColor = AlexTextPrimary,
                    unfocusedTextColor = AlexTextPrimary
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = messageBody,
            onValueChange = { messageBody = it },
            label = { Text("Message Content") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("social_message_body_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AlexCyan,
                unfocusedBorderColor = AlexCardBorder,
                focusedTextColor = AlexTextPrimary,
                unfocusedTextColor = AlexTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                onComposeSocialMessageDraft(
                    selectedApp,
                    recipientName,
                    recipientHandle,
                    messageBody
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = AlexCyan,
                contentColor = AlexBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("prepare_social_message_button")
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("PREPARE & READ FOR CONFIRMATION", fontWeight = FontWeight.Bold)
        }
    }

    // 4. WHATSAPP MESSAGE VOICE NOTIFICATION MONITOR
    HubSectionCard(title = "3. WHATSAPP MESSAGE VOICE NOTIFICATION") {
        Text(
            text = "When a WhatsApp message is sent, ALEX announces: \"Boss, your WhatsApp message has been sent successfully.\"",
            style = MaterialTheme.typography.bodyMedium,
            color = AlexTextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        HubToggleRow(
            title = "Announce WhatsApp Sent Confirmation",
            subtitle = "Voice alert: \"Boss, your WhatsApp message has been sent successfully.\"",
            checked = settings.whatsappVoiceNotificationEnabled,
            onCheckedChange = {
                onUpdateNotificationVoiceSettings(
                    it,
                    settings.announceIncomingMessagesEnabled,
                    settings.readIncomingMessageContentAloud
                )
            }
        )
        HubToggleRow(
            title = "Announce Incoming Social Notifications",
            subtitle = "Read sender & originating app (WhatsApp, Messenger, Instagram, Telegram)",
            checked = settings.announceIncomingMessagesEnabled,
            onCheckedChange = {
                onUpdateNotificationVoiceSettings(
                    settings.whatsappVoiceNotificationEnabled,
                    it,
                    settings.readIncomingMessageContentAloud
                )
            }
        )
        HubToggleRow(
            title = "Read Message Content Aloud",
            subtitle = "Speak incoming message body only when permitted by Boss",
            checked = settings.readIncomingMessageContentAloud,
            onCheckedChange = {
                onUpdateNotificationVoiceSettings(
                    settings.whatsappVoiceNotificationEnabled,
                    settings.announceIncomingMessagesEnabled,
                    it
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onSimulateWhatsAppSent(recipientName.ifBlank { "Rahul" }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexStatusReady,
                    contentColor = AlexBlack
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulate_whatsapp_sent_button")
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("TEST SENT ALERT", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    onSimulateIncomingSocial(
                        "WhatsApp",
                        recipientName.ifBlank { "Rahul" },
                        "Boss, I received your file!"
                    )
                },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier
                    .weight(1f)
                    .testTag("simulate_whatsapp_incoming_button")
            ) {
                Text("TEST INCOMING", color = AlexCyan)
            }
        }

        if (notificationEvents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            notificationEvents.take(4).forEach { ev ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = AlexDeepNavy,
                    border = BorderStroke(1.dp, AlexCardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${ev.appDisplayName} • ${if (ev.isSentConfirmation) "SENT TO" else "FROM"} ${ev.senderOrRecipient}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (ev.isSentConfirmation) AlexStatusReady else AlexCyan
                        )
                        Text(
                            text = ev.messageContent,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlexTextPrimary
                        )
                    }
                }
            }
        }
    }

    // 5. FULL MOBILE AUTOMATION & ACCESSIBILITY CONTROLS
    HubSectionCard(title = "4. FULL MOBILE & SCREEN AUTOMATION CONTROLS") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onOpenSystemSettingsPanel("wifi") },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Wifi, contentDescription = null, tint = AlexCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("WI-FI", color = AlexCyan)
            }
            OutlinedButton(
                onClick = { onOpenSystemSettingsPanel("bluetooth") },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = AlexCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("BLUETOOTH", color = AlexCyan)
            }
            OutlinedButton(
                onClick = { onOpenSystemSettingsPanel("camera") },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = AlexCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CAMERA", color = AlexCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onCreateQuickAlarm(6, 0, "ALEX 6 AM Boss Wakeup") },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = AlexCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("6 AM ALARM", color = AlexCyan)
            }
            OutlinedButton(
                onClick = { onAdjustVolume(true) },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Text("VOL +", color = AlexCyan)
            }
            OutlinedButton(
                onClick = { onAdjustVolume(false) },
                border = BorderStroke(1.dp, AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Text("VOL -", color = AlexCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onOpenYouTubeSearch("Futuristic AI Jarvis technology") },
                colors = ButtonDefaults.buttonColors(containerColor = AlexSurfaceNavy, contentColor = AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("YOUTUBE SEARCH")
            }
            Button(
                onClick = { onOpenChromeSearch("Google AI Studio") },
                colors = ButtonDefaults.buttonColors(containerColor = AlexSurfaceNavy, contentColor = AlexCyan),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CHROME SEARCH")
            }
        }

        if (automationLogs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "RECENT AUTOMATION EXECUTION LOG",
                style = MaterialTheme.typography.labelMedium,
                color = AlexCyan
            )
            automationLogs.take(5).forEach { log ->
                Text(
                    text = "• [${log.category}] ${log.voiceCommand} — ${log.spokenFeedback}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlexTextSecondary,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun GalleryAndPublisherTab(
    galleryItems: List<GalleryMediaItem>,
    selectedCategory: GalleryCategory,
    searchQuery: String,
    previewItem: GalleryMediaItem?,
    pendingPublishDraft: PendingGalleryPublishDraft?,
    onFilterGallery: (GalleryCategory, String) -> Unit,
    onSelectGalleryPreview: (GalleryMediaItem?) -> Unit,
    onLaunchPhotoPicker: () -> Unit,
    onPrepareSocialPublish: (String, SocialPublishPlatform, String) -> Unit,
    onConfirmGalleryPublish: () -> Unit,
    onCancelGalleryPublish: () -> Unit,
    onRequestProtectedDeletion: (String) -> Unit
) {
    var localQuery by remember(searchQuery) { mutableStateOf(searchQuery) }
    var captionDraft by remember { mutableStateOf("Captured & shared via ALEX AI Assistant #AlexAI") }
    var selectedPublishPlatform by remember { mutableStateOf(SocialPublishPlatform.INSTAGRAM_POST) }

    // 1. ZERO-PERMISSION PHOTO PICKER & SEARCH BAR
    HubSectionCard(title = "1. PHONE GALLERY ORGANIZER & VISUAL SEARCH") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = localQuery,
                onValueChange = {
                    localQuery = it
                    onFilterGallery(selectedCategory, it)
                },
                label = { Text("Search by filename, folder, or visual tag") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gallery_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlexCyan,
                    unfocusedBorderColor = AlexCardBorder,
                    focusedTextColor = AlexTextPrimary,
                    unfocusedTextColor = AlexTextPrimary
                )
            )
            Button(
                onClick = onLaunchPhotoPicker,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexCyan,
                    contentColor = AlexBlack
                ),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("import_photo_picker_button")
            ) {
                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "Pick Photo")
                Spacer(modifier = Modifier.width(4.dp))
                Text("PICK", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GalleryCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { onFilterGallery(cat, localQuery) },
                    label = { Text(cat.label) },
                    modifier = Modifier.testTag("gallery_category_${cat.name.lowercase()}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AlexCyan,
                        selectedLabelColor = AlexBlack
                    )
                )
            }
        }
    }

    // 2. PENDING SOCIAL MEDIA PUBLISH CONFIRMATION MODAL CARD
    if (pendingPublishDraft != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pending_gallery_publish_card"),
            shape = RoundedCornerShape(16.dp),
            color = AlexStatusReady.copy(alpha = 0.16f),
            border = BorderStroke(1.5.dp, AlexStatusReady)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "PREVIEW & CONFIRM SOCIAL MEDIA UPLOAD",
                    style = MaterialTheme.typography.labelLarge,
                    color = AlexStatusReady,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "File: ${pendingPublishDraft.mediaItem.displayName} → ${pendingPublishDraft.platform.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AlexTextPrimary
                )
                Text(
                    text = "Caption: \"${pendingPublishDraft.caption}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConfirmGalleryPublish,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexStatusReady,
                            contentColor = AlexBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_gallery_publish_button")
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONFIRM & PUBLISH", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCancelGalleryPublish,
                        border = BorderStroke(1.dp, AlexStatusDisconnected),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CANCEL", color = AlexStatusDisconnected)
                    }
                }
            }
        }
    }

    // 3. SELECTED PHOTO PREVIEW & SOCIAL MEDIA PUBLISHER
    val activeItem = previewItem ?: galleryItems.firstOrNull()
    if (activeItem != null) {
        HubSectionCard(title = "2. PHOTO PREVIEW & SOCIAL MEDIA PUBLISHER") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gallery_preview_card"),
                shape = RoundedCornerShape(12.dp),
                color = AlexDeepNavy,
                border = BorderStroke(1.dp, AlexCyan)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeItem.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = AlexTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeItem.albumName} • ${activeItem.width}x${activeItem.height} • ${activeItem.sizeBytes / 1024} KB",
                                style = MaterialTheme.typography.labelMedium,
                                color = AlexCyan
                            )
                        }
                        if (activeItem.isDuplicateCandidate) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = AlexStatusDisconnected.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AlexStatusDisconnected)
                            ) {
                                Text(
                                    text = "DUPLICATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AlexStatusDisconnected,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AI Visual Tags: ${activeItem.visualTags.joinToString(" • ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlexTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocialPublishPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = selectedPublishPlatform == platform,
                        onClick = { selectedPublishPlatform = platform },
                        label = { Text(platform.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlexCyan,
                            selectedLabelColor = AlexBlack
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = captionDraft,
                onValueChange = { captionDraft = it },
                label = { Text("Social Media Caption") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlexCyan,
                    unfocusedBorderColor = AlexCardBorder,
                    focusedTextColor = AlexTextPrimary,
                    unfocusedTextColor = AlexTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onPrepareSocialPublish(
                            activeItem.id,
                            selectedPublishPlatform,
                            captionDraft
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexCyan,
                        contentColor = AlexBlack
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("prepare_gallery_publish_button")
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PREVIEW & UPLOAD", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onRequestProtectedDeletion(activeItem.id) },
                    border = BorderStroke(1.dp, AlexStatusDisconnected),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_selected_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Delete with Master Password",
                        tint = AlexStatusDisconnected,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DELETE", color = AlexStatusDisconnected, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 4. GALLERY ITEMS LIST
    HubSectionCard(title = "3. INDEXED MEDIA ITEMS (${galleryItems.size})") {
        galleryItems.forEach { item ->
            val isSelected = activeItem?.id == item.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectGalleryPreview(item) }
                    .testTag("gallery_item_${item.id}"),
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) AlexSurfaceNavy else AlexDeepNavy,
                border = BorderStroke(1.dp, if (isSelected) AlexCyan else AlexCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = if (isSelected) AlexCyan else AlexTextSecondary
                        )
                        Column {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = AlexTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.albumName} • Tags: ${item.visualTags.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlexTextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRequestProtectedDeletion(item.id) },
                        modifier = Modifier.testTag("delete_gallery_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Protected Delete",
                            tint = AlexStatusDisconnected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityAndPermissionsTab(
    settings: AlexSettings,
    isMasterPasswordSet: Boolean,
    isVoiceProfileEnrolled: Boolean,
    voiceEnrollmentSampleCount: Int,
    isVoiceOwnerVerified: Boolean,
    securityAuditLogs: List<SecurityAuditLogEntry>,
    isNotificationAccessConnected: Boolean,
    isAccessibilityConnected: Boolean,
    onSetOrChangeMasterPassword: (CharArray?, CharArray) -> Pair<Boolean, String>,
    onEnrollVoiceSample: () -> Unit,
    onVerifyOwnerVoice: () -> Pair<Boolean, Float>,
    onResetVoiceProfile: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationAccessSettings: () -> Unit,
    onUpdateSecuritySettings: (Boolean, Boolean, Boolean) -> Unit
) {
    var currentPassDraft by remember { mutableStateOf("") }
    var newPassDraft by remember { mutableStateOf("") }
    var showPassPlaintext by remember { mutableStateOf(false) }
    var passwordFeedback by remember { mutableStateOf<String?>(null) }
    var voiceMatchScore by remember { mutableStateOf<Float?>(null) }

    // 1. MASTER PASSWORD SECURITY SYSTEM
    HubSectionCard(title = "1. MASTER PASSWORD & ANDROID KEYSTORE ENCRYPTION") {
        Text(
            text = if (isMasterPasswordSet) {
                "STATUS: MASTER PASSWORD ACTIVE (AES-256-GCM + PBKDF2-HMAC-SHA256) • Never spoken aloud"
            } else {
                "STATUS: DEFAULT INITIAL PASSWORD ('boss1234') ACTIVE • Set your custom Master Password below"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (isMasterPasswordSet) AlexStatusReady else AlexCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isMasterPasswordSet) {
            OutlinedTextField(
                value = currentPassDraft,
                onValueChange = { currentPassDraft = it },
                label = { Text("Current Master Password") },
                singleLine = true,
                visualTransformation = if (showPassPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_master_password_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AlexCyan,
                    unfocusedBorderColor = AlexCardBorder,
                    focusedTextColor = AlexTextPrimary,
                    unfocusedTextColor = AlexTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = newPassDraft,
            onValueChange = { newPassDraft = it },
            label = { Text(if (isMasterPasswordSet) "New Master Password (min 4 chars)" else "Create Master Password (min 4 chars)") },
            singleLine = true,
            visualTransformation = if (showPassPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassPlaintext = !showPassPlaintext }) {
                    Icon(
                        imageVector = if (showPassPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility",
                        tint = AlexCyan
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("master_password_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AlexCyan,
                unfocusedBorderColor = AlexCardBorder,
                focusedTextColor = AlexTextPrimary,
                unfocusedTextColor = AlexTextPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val res = onSetOrChangeMasterPassword(
                    if (isMasterPasswordSet) currentPassDraft.toCharArray() else null,
                    newPassDraft.toCharArray()
                )
                passwordFeedback = res.second
                if (res.first) {
                    currentPassDraft = ""
                    newPassDraft = ""
                }
            },
            enabled = newPassDraft.length >= 4,
            colors = ButtonDefaults.buttonColors(
                containerColor = AlexStatusReady,
                contentColor = AlexBlack
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_master_password_button")
        ) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isMasterPasswordSet) "UPDATE MASTER PASSWORD" else "SAVE MASTER PASSWORD",
                fontWeight = FontWeight.Bold
            )
        }

        if (passwordFeedback != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = passwordFeedback!!,
                style = MaterialTheme.typography.bodySmall,
                color = AlexCyan
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        HubToggleRow(
            title = "Delete Protection (Mandatory Password Challenge)",
            subtitle = "Require Master Password + final confirmation before deleting any photo, video, or file",
            checked = settings.deleteProtectionEnabled,
            onCheckedChange = {
                onUpdateSecuritySettings(
                    settings.biometricSecurityEnabled,
                    it,
                    settings.sensitiveActionProtectionEnabled
                )
            }
        )
        HubToggleRow(
            title = "Biometric Security Lock",
            subtitle = "Allow fingerprint / face biometric unlock alongside Master Password",
            checked = settings.biometricSecurityEnabled,
            onCheckedChange = {
                onUpdateSecuritySettings(
                    it,
                    settings.deleteProtectionEnabled,
                    settings.sensitiveActionProtectionEnabled
                )
            }
        )
        HubToggleRow(
            title = "Protect Sensitive System & Account Actions",
            subtitle = "Require Master Password before changing security settings or clearing records",
            checked = settings.sensitiveActionProtectionEnabled,
            onCheckedChange = {
                onUpdateSecuritySettings(
                    settings.biometricSecurityEnabled,
                    settings.deleteProtectionEnabled,
                    it
                )
            }
        )
    }

    // 2. PERSONAL VOICE AUTHENTICATION
    HubSectionCard(title = "2. PERSONAL VOICE BIOMETRIC AUTHENTICATION") {
        Text(
            text = "Enrolled Voice Samples: $voiceEnrollmentSampleCount / 3 • Status: ${if (isVoiceProfileEnrolled) "ENROLLED" else "NEEDS ENROLLMENT"}",
            style = MaterialTheme.typography.labelLarge,
            color = if (isVoiceProfileEnrolled) AlexStatusReady else AlexCyan
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Extracts fundamental pitch (F0), zero-crossing rate, and harmonic profile to recognize Boss's voice and reject spoofed recordings.",
            style = MaterialTheme.typography.bodySmall,
            color = AlexTextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onEnrollVoiceSample,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexCyan,
                    contentColor = AlexBlack
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("enroll_voice_sample_button")
            ) {
                Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("RECORD SAMPLE (${voiceEnrollmentSampleCount}/3)", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val res = onVerifyOwnerVoice()
                    voiceMatchScore = res.second
                },
                border = BorderStroke(1.dp, AlexStatusReady),
                modifier = Modifier
                    .weight(1f)
                    .testTag("verify_voice_signature_button")
            ) {
                Text("VERIFY VOICE", color = AlexStatusReady, fontWeight = FontWeight.Bold)
            }
        }

        if (voiceMatchScore != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Speaker Match Score: ${(voiceMatchScore!! * 100).toInt()}% • ${if (isVoiceOwnerVerified) "OWNER VERIFIED" else "FALLBACK TO MASTER PASSWORD"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isVoiceOwnerVerified) AlexStatusReady else AlexStatusDisconnected
            )
        }

        if (voiceEnrollmentSampleCount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onResetVoiceProfile) {
                Text("RESET VOICE PROFILE", color = AlexStatusDisconnected)
            }
        }
    }

    // 3. INTERACTIVE PERMISSION SETUP WIZARD
    HubSectionCard(title = "3. PERMISSION SETUP WIZARD (PLAY-POLICY COMPLIANT)") {
        PermissionWizardItem(
            title = "1. Android Accessibility Service",
            purpose = "Enables screen navigation, button clicking, scrolling, and multi-step UI automation.",
            isConnected = isAccessibilityConnected,
            actionLabel = "OPEN ACCESSIBILITY",
            testTag = "open_accessibility_settings_button",
            onClick = onOpenAccessibilitySettings
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionWizardItem(
            title = "2. Notification Listener Access",
            purpose = "Detects sent WhatsApp messages and announces \"Boss, your WhatsApp message has been sent successfully.\"",
            isConnected = isNotificationAccessConnected,
            actionLabel = "OPEN NOTIFICATIONS",
            testTag = "open_notification_access_button",
            onClick = onOpenNotificationAccessSettings
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionWizardItem(
            title = "3. Zero-Permission Android Photo Picker",
            purpose = "Complies with Google Play policy for selecting photos & videos without broad storage permissions.",
            isConnected = true,
            actionLabel = "READY",
            testTag = "photo_picker_status_badge",
            onClick = {}
        )
    }

    // 4. SECURITY ACTIVITY LOG
    if (securityAuditLogs.isNotEmpty()) {
        HubSectionCard(title = "4. SECURITY ACTIVITY AUDIT LOG") {
            securityAuditLogs.take(6).forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${if (entry.authorized) "✓" else "✗"} ${entry.eventType}: ${entry.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.authorized) AlexTextPrimary else AlexStatusDisconnected
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionWizardItem(
    title: String,
    purpose: String,
    isConnected: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AlexDeepNavy,
        border = BorderStroke(1.dp, if (isConnected) AlexStatusReady else AlexCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AlexTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlexTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onClick,
                border = BorderStroke(1.dp, if (isConnected) AlexStatusReady else AlexCyan),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(
                    text = if (isConnected) "ACTIVE" else actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) AlexStatusReady else AlexCyan
                )
            }
        }
    }
}

@Composable
private fun HubSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AlexCardNavy.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, AlexCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = AlexCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun HubToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = AlexTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AlexTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AlexBlack,
                checkedTrackColor = AlexCyan,
                uncheckedThumbColor = AlexTextSecondary,
                uncheckedTrackColor = AlexDeepNavy
            )
        )
    }
}
