package com.example.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.config.AlexConfig
import com.example.core.config.NativeSystemBridge
import com.example.domain.model.AiProviderMode
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
import com.example.domain.model.ApiUsageMetrics
import com.example.domain.model.SocialPublishPlatform
import com.example.domain.model.VoiceEmotionStyle
import com.example.domain.model.VoiceGender
import com.example.feature.DiagnosticUiState
import com.example.security.SecurityAuditManager
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexStatusReady
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary

@Composable
fun SettingsScreen(
    settings: AlexSettings,
    onUpdateVoiceProfile: (String, Float, Float) -> Unit,
    onUpdateVoiceEmotionStyle: (VoiceEmotionStyle) -> Unit = {},
    onPreviewVoice: (AlexLanguage) -> Unit,
    onUpdatePreferredLanguage: (AlexLanguage) -> Unit,
    onUpdateAudioControls: (Boolean, Boolean, Boolean, Float) -> Unit,
    onUpdatePrivacySettings: (Boolean) -> Unit,
    onUpdateSpeakChatReplies: (Boolean) -> Unit,
    onUpdateUserTitle: (String) -> Unit,
    onUpdateNotificationVoiceSettings: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onUpdateAutomationSettings: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onUpdateSecuritySettings: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onUpdateGalleryDefaultPlatform: (SocialPublishPlatform) -> Unit = {},
    onClearAllHistory: () -> Unit,
    onNavigateToAutomationHub: () -> Unit = {},
    onNavigateToApiConfig: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    var bossTitleDraft by remember(settings.userTitle) { mutableStateOf(settings.userTitle) }
    var selectedGenderFilter by remember { mutableStateOf<VoiceGender?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AlexBlack, AlexDeepNavy, AlexBlack)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlexCyan
                        )
                    }
                    Column {
                        Text(
                            text = "ALEX 20-MODULE SETTINGS",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AlexCyan
                        )
                        Text(
                            text = "10 VOICES • AUTOMATION • SECURITY • AI BRAIN",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexTextSecondary
                        )
                    }
                }

                Row {
                    IconButton(onClick = onNavigateToAutomationHub) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Automation Hub",
                            tint = AlexCyan
                        )
                    }
                    IconButton(onClick = onNavigateToApiConfig) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Configuration",
                            tint = AlexCyan
                        )
                    }
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About ALEX",
                            tint = AlexTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Banner to Open Part 2 Automation, Gallery & Security Command Center
            Button(
                onClick = onNavigateToAutomationHub,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexCyan,
                    contentColor = AlexBlack
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_automation_hub_from_settings")
            ) {
                Icon(imageVector = Icons.Default.SmartToy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OPEN AUTOMATION, GALLERY & SECURITY HUB",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. 10 REALISTIC HUMAN VOICES (5 FEMALE + 5 MALE) & EMOTION STYLE
            SettingsSectionCard(title = "1. REALISTIC HUMAN VOICE SYSTEM (5 FEMALE + 5 MALE VOICES)") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGenderFilter == null,
                        onClick = { selectedGenderFilter = null },
                        label = { Text("ALL 10 VOICES") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlexCyan,
                            selectedLabelColor = AlexBlack
                        )
                    )
                    FilterChip(
                        selected = selectedGenderFilter == VoiceGender.FEMALE,
                        onClick = { selectedGenderFilter = VoiceGender.FEMALE },
                        label = { Text("5 FEMALE VOICES") },
                        modifier = Modifier.testTag("filter_female_voices"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlexCyan,
                            selectedLabelColor = AlexBlack
                        )
                    )
                    FilterChip(
                        selected = selectedGenderFilter == VoiceGender.MALE,
                        onClick = { selectedGenderFilter = VoiceGender.MALE },
                        label = { Text("5 MALE VOICES") },
                        modifier = Modifier.testTag("filter_male_voices"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AlexCyan,
                            selectedLabelColor = AlexBlack
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val displayedVoices = remember(selectedGenderFilter) {
                    when (selectedGenderFilter) {
                        VoiceGender.FEMALE -> AlexConfig.FEMALE_VOICES
                        VoiceGender.MALE -> AlexConfig.MALE_VOICES
                        null -> AlexConfig.ALL_HUMAN_VOICES
                    }
                }

                displayedVoices.forEach { voice ->
                    val isSelected = settings.selectedVoiceId == voice.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onUpdateVoiceProfile(
                                    voice.id,
                                    settings.speechRate,
                                    settings.voicePitch
                                )
                            }
                            .testTag("voice_option_${voice.id.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AlexSurfaceNavy else AlexDeepNavy,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) AlexCyan else AlexCardBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onUpdateVoiceProfile(
                                        voice.id,
                                        settings.speechRate,
                                        settings.voicePitch
                                    )
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AlexCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = voice.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = AlexTextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "[${voice.gender.displayName.uppercase()}]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AlexCyan
                                    )
                                }
                                Text(
                                    text = voice.toneDescription,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AlexTextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Voice Emotion & Delivery Style:",
                    style = MaterialTheme.typography.labelMedium,
                    color = AlexTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceEmotionStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.voiceEmotionStyle == style,
                            onClick = { onUpdateVoiceEmotionStyle(style) },
                            label = { Text(style.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AlexCyan,
                                selectedLabelColor = AlexBlack
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Speaking Speed: ${"%.2f".format(settings.speechRate)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextPrimary
                )
                Slider(
                    value = settings.speechRate,
                    onValueChange = { rate ->
                        onUpdateVoiceProfile(settings.selectedVoiceId, rate, settings.voicePitch)
                    },
                    valueRange = 0.8f..1.25f,
                    colors = SliderDefaults.colors(
                        thumbColor = AlexCyan,
                        activeTrackColor = AlexCyan
                    )
                )

                Text(
                    text = "Voice Pitch: ${"%.2f".format(settings.voicePitch)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextPrimary
                )
                Slider(
                    value = settings.voicePitch,
                    onValueChange = { pitch ->
                        onUpdateVoiceProfile(settings.selectedVoiceId, settings.speechRate, pitch)
                    },
                    valueRange = 0.8f..1.25f,
                    colors = SliderDefaults.colors(
                        thumbColor = AlexCyan,
                        activeTrackColor = AlexCyan
                    )
                )

                Text(
                    text = "Preview Selected Voice in Language:",
                    style = MaterialTheme.typography.labelMedium,
                    color = AlexTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPreviewVoice(AlexLanguage.BANGLA) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_voice_bangla"),
                        border = BorderStroke(1.dp, AlexCyan)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Test Bangla",
                            tint = AlexCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("বাংলা", color = AlexCyan)
                    }
                    OutlinedButton(
                        onClick = { onPreviewVoice(AlexLanguage.ENGLISH) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_voice_english"),
                        border = BorderStroke(1.dp, AlexCyan)
                    ) {
                        Text("ENGLISH", color = AlexCyan)
                    }
                    OutlinedButton(
                        onClick = { onPreviewVoice(AlexLanguage.HINDI) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preview_voice_hindi"),
                        border = BorderStroke(1.dp, AlexCyan)
                    ) {
                        Text("हिन्दी", color = AlexCyan)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. LANGUAGE INTELLIGENCE & BOSS ADDRESSING
            SettingsSectionCard(title = "2. MULTILINGUAL INTELLIGENCE & PERSONA") {
                AlexLanguage.entries.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdatePreferredLanguage(lang) }
                            .padding(vertical = 6.dp)
                            .testTag("lang_option_${lang.code.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.preferredLanguage == lang,
                            onClick = { onUpdatePreferredLanguage(lang) },
                            colors = RadioButtonDefaults.colors(selectedColor = AlexCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = AlexTextPrimary
                            )
                            Text(
                                text = lang.nativeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = AlexCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = bossTitleDraft,
                    onValueChange = {
                        bossTitleDraft = it
                        onUpdateUserTitle(it)
                    },
                    label = { Text("How ALEX Addresses You (Default: Boss)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("boss_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlexCyan,
                        unfocusedBorderColor = AlexCardBorder,
                        focusedTextColor = AlexTextPrimary,
                        unfocusedTextColor = AlexTextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. REAL-TIME AUDIO & BARGE-IN CONTROLS
            SettingsSectionCard(title = "3. FULL-DUPLEX AUDIO & VAD") {
                SettingToggleRow(
                    title = "Barge-In Immediate Interruption",
                    subtitle = "Automatically stop ALEX's speech the instant you start speaking",
                    checked = settings.autoBargeInEnabled,
                    onCheckedChange = {
                        onUpdateAudioControls(
                            it,
                            settings.echoCancellationEnabled,
                            settings.noiseSuppressionEnabled,
                            settings.vadSensitivity
                        )
                    }
                )
                SettingToggleRow(
                    title = "Hardware Acoustic Echo Cancellation (AEC)",
                    subtitle = "Prevents ALEX's speaker output from feeding back into the microphone",
                    checked = settings.echoCancellationEnabled,
                    onCheckedChange = {
                        onUpdateAudioControls(
                            settings.autoBargeInEnabled,
                            it,
                            settings.noiseSuppressionEnabled,
                            settings.vadSensitivity
                        )
                    }
                )
                SettingToggleRow(
                    title = "Hardware Noise Suppression (NS)",
                    subtitle = "Filters ambient background noise for clear Bangla, Hindi & English recognition",
                    checked = settings.noiseSuppressionEnabled,
                    onCheckedChange = {
                        onUpdateAudioControls(
                            settings.autoBargeInEnabled,
                            settings.echoCancellationEnabled,
                            it,
                            settings.vadSensitivity
                        )
                    }
                )
                SettingToggleRow(
                    title = "Speak Text Chat Replies Aloud",
                    subtitle = "Use ALEX's selected voice to read responses in Text Chat mode",
                    checked = settings.speakChatReplies,
                    onCheckedChange = onUpdateSpeakChatReplies
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. WHATSAPP, SOCIAL AUTOMATION & ACCESSIBILITY SETTINGS
            SettingsSectionCard(title = "4. WHATSAPP VOICE ALERTS & MOBILE AUTOMATION") {
                SettingToggleRow(
                    title = "WhatsApp Sent Message Voice Announcement",
                    subtitle = "Announce: \"Boss, your WhatsApp message has been sent successfully.\"",
                    checked = settings.whatsappVoiceNotificationEnabled,
                    onCheckedChange = {
                        onUpdateNotificationVoiceSettings(
                            it,
                            settings.announceIncomingMessagesEnabled,
                            settings.readIncomingMessageContentAloud
                        )
                    }
                )
                SettingToggleRow(
                    title = "Incoming Social Notification Voice Alert",
                    subtitle = "Announce incoming messages from WhatsApp, Messenger, Instagram & Telegram",
                    checked = settings.announceIncomingMessagesEnabled,
                    onCheckedChange = {
                        onUpdateNotificationVoiceSettings(
                            settings.whatsappVoiceNotificationEnabled,
                            it,
                            settings.readIncomingMessageContentAloud
                        )
                    }
                )
                SettingToggleRow(
                    title = "Accessibility Screen Automation",
                    subtitle = "Allow ALEX to click buttons, scroll, and execute multi-step screen tasks",
                    checked = settings.socialMediaAutomationEnabled,
                    onCheckedChange = {
                        onUpdateAutomationSettings(
                            it,
                            settings.socialMediaAutomationEnabled,
                            settings.backgroundAutomationServiceEnabled
                        )
                    }
                )
                SettingToggleRow(
                    title = "Background Personal Assistant Readiness",
                    subtitle = "Keep voice & notification automation responsive while multitasking",
                    checked = settings.backgroundAutomationServiceEnabled,
                    onCheckedChange = {
                        onUpdateAutomationSettings(
                            settings.socialMediaAutomationEnabled,
                            settings.socialMediaAutomationEnabled,
                            it
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. MASTER PASSWORD, BIOMETRIC & DELETE PROTECTION
            SettingsSectionCard(title = "5. MASTER PASSWORD, DELETE PROTECTION & PRIVACY") {
                SettingToggleRow(
                    title = "Master Password Delete Protection",
                    subtitle = "Never delete any photo, video, or file without Master Password verification",
                    checked = settings.deleteProtectionEnabled,
                    onCheckedChange = {
                        onUpdateSecuritySettings(
                            settings.biometricSecurityEnabled,
                            it,
                            settings.sensitiveActionProtectionEnabled
                        )
                    }
                )
                SettingToggleRow(
                    title = "Biometric Security Lock",
                    subtitle = "Enable biometric verification alongside Master Password",
                    checked = settings.biometricSecurityEnabled,
                    onCheckedChange = {
                        onUpdateSecuritySettings(
                            it,
                            settings.deleteProtectionEnabled,
                            settings.sensitiveActionProtectionEnabled
                        )
                    }
                )
                SettingToggleRow(
                    title = "Save Text Conversation History & Memory",
                    subtitle = "Store text transcripts locally in Room DB. Raw audio is never saved to disk.",
                    checked = settings.saveHistoryEnabled,
                    onCheckedChange = onUpdatePrivacySettings
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onClearAllHistory,
                        border = BorderStroke(1.dp, AlexStatusDisconnected),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("CLEAR ALL HISTORY", color = AlexStatusDisconnected)
                    }
                    Button(
                        onClick = onNavigateToApiConfig,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexCyan,
                            contentColor = AlexBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_api_config_from_settings")
                    ) {
                        Text("API & AI BRAIN")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ApiConfigScreen(
    settings: AlexSettings,
    diagnosticState: DiagnosticUiState,
    discoveredModels: List<String> = AlexConfig.AVAILABLE_CHAT_MODELS,
    apiUsageMetrics: ApiUsageMetrics = ApiUsageMetrics(),
    onSaveCustomApiKey: (String) -> Unit,
    onUpdateModels: (String, String) -> Unit,
    onUpdateAiProviderConfig: (AiProviderMode, Boolean, String) -> Unit = { _, _, _ -> },
    onDiscoverGeminiModels: () -> Unit = {},
    onRunDiagnostic: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val securityDiagnostics by SecurityAuditManager.diagnostics.collectAsState()
    var apiKeyDraft by remember(settings.customApiKey) { mutableStateOf(settings.customApiKey) }
    var groqKeyDraft by remember(settings.groqApiKey) { mutableStateOf(settings.groqApiKey) }
    var showApiKeyPlaintext by remember { mutableStateOf(false) }
    var savedConfirmationVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AlexBlack, AlexDeepNavy, AlexBlack)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("api_config_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("api_config_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AlexCyan
                    )
                }
                Column {
                    Text(
                        text = "GEMINI MULTI-MODEL AI BRAIN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AlexCyan
                    )
                    Text(
                        text = "API KEY • DYNAMIC MODELS • FALLBACK • METRICS",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. MANUAL GEMINI API KEY INPUT CARD
            SettingsSectionCard(title = "1. MANUAL GEMINI API KEY (নিজের API Key বসান)") {
                val hasActiveKey = settings.customApiKey.isNotBlank()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasActiveKey) {
                        AlexStatusReady.copy(alpha = 0.16f)
                    } else {
                        AlexSurfaceNavy
                    },
                    border = BorderStroke(
                        1.dp,
                        if (hasActiveKey) AlexStatusReady else AlexCyan.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Key Mode",
                            tint = if (hasActiveKey) AlexStatusReady else AlexCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (hasActiveKey) {
                                "STATUS: CUSTOM API KEY ACTIVE • ONLINE MODE"
                            } else {
                                "STATUS: FIREBASE DEFAULT MODE (ম্যানুয়াল API Key খালি আছে)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (hasActiveKey) AlexStatusReady else AlexCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "আপনার নিজস্ব Google AI Studio Gemini API Key (AIzaSy...) নিচে পেস্ট করে SAVE & GO ONLINE বাটনে চাপ দিন। সাথে সাথে অ্যাপ অনলাইন হয়ে লাইভ ভয়েস এবং চ্যাট কাজ করবে।",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = {
                        apiKeyDraft = it
                        savedConfirmationVisible = false
                    },
                    label = { Text("Enter Gemini API Key (AIzaSy...)") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (showApiKeyPlaintext) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showApiKeyPlaintext = !showApiKeyPlaintext },
                            modifier = Modifier.testTag("toggle_api_key_visibility")
                        ) {
                            Icon(
                                imageVector = if (showApiKeyPlaintext) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (showApiKeyPlaintext) "Hide API Key" else "Show API Key",
                                tint = AlexCyan
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlexCyan,
                        unfocusedBorderColor = AlexCardBorder,
                        focusedTextColor = AlexTextPrimary,
                        unfocusedTextColor = AlexTextPrimary,
                        focusedLabelColor = AlexCyan,
                        unfocusedLabelColor = AlexTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveCustomApiKey(apiKeyDraft.trim())
                            savedConfirmationVisible = true
                        },
                        enabled = apiKeyDraft.trim().isNotEmpty(),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(48.dp)
                            .testTag("save_api_key_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexStatusReady,
                            contentColor = AlexBlack
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE & GO ONLINE", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            apiKeyDraft = ""
                            savedConfirmationVisible = false
                            onSaveCustomApiKey("")
                        },
                        enabled = apiKeyDraft.isNotEmpty() || settings.customApiKey.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("clear_api_key_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AlexStatusDisconnected)
                    ) {
                        Text("CLEAR KEY", color = AlexStatusDisconnected)
                    }
                }

                if (savedConfirmationVisible && settings.customApiKey.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ API Key সংরক্ষিত হয়েছে! ALEX এখন আপনার নিজস্ব API Key দিয়ে অনলাইনে সংযুক্ত হচ্ছে...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlexStatusReady
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. AI PROVIDER MANAGER & GROQ FALLBACK
            SettingsSectionCard(title = "2. AI PROVIDER ROUTING & SMART MODEL SWITCHING") {
                AiProviderMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateAiProviderConfig(
                                    mode,
                                    settings.autoModelSwitchingEnabled,
                                    groqKeyDraft
                                )
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.aiProviderMode == mode,
                            onClick = {
                                onUpdateAiProviderConfig(
                                    mode,
                                    settings.autoModelSwitchingEnabled,
                                    groqKeyDraft
                                )
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = AlexCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexTextPrimary
                        )
                    }
                }

                SettingToggleRow(
                    title = "Smart Multi-Model Task Switching & Fallback",
                    subtitle = "Automatically route fast requests to Flash and complex reasoning to Pro with automatic fallback",
                    checked = settings.autoModelSwitchingEnabled,
                    onCheckedChange = {
                        onUpdateAiProviderConfig(
                            settings.aiProviderMode,
                            it,
                            groqKeyDraft
                        )
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = groqKeyDraft,
                    onValueChange = {
                        groqKeyDraft = it
                        onUpdateAiProviderConfig(
                            settings.aiProviderMode,
                            settings.autoModelSwitchingEnabled,
                            it
                        )
                    },
                    label = { Text("Optional Groq Fallback API Key (gsk_...)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlexCyan,
                        unfocusedBorderColor = AlexCardBorder,
                        focusedTextColor = AlexTextPrimary,
                        unfocusedTextColor = AlexTextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. LIVE CONNECTION DIAGNOSTIC & TOKEN USAGE METRICS
            SettingsSectionCard(title = "3. LIVE GEMINI CONNECTION DIAGNOSTIC & USAGE METRICS") {
                Text(
                    text = "Requests: ${apiUsageMetrics.totalRequests} (${apiUsageMetrics.successfulRequests} OK) • Est. Tokens: ${apiUsageMetrics.estimatedTokensUsed} • Last Latency: ${apiUsageMetrics.lastLatencyMs} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = AlexCyan
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRunDiagnostic,
                    enabled = diagnosticState !is DiagnosticUiState.Running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_diagnostic_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexCyan,
                        contentColor = AlexBlack
                    )
                ) {
                    if (diagnosticState is DiagnosticUiState.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = AlexBlack,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TESTING GEMINI CONNECTION...")
                    } else {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RUN GEMINI DIAGNOSTIC TEST")
                    }
                }

                if (diagnosticState is DiagnosticUiState.Completed) {
                    val res = diagnosticState.result
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diagnostic_result_card"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (res.success) {
                            AlexStatusReady.copy(alpha = 0.14f)
                        } else {
                            AlexStatusDisconnected.copy(alpha = 0.16f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (res.success) AlexStatusReady else AlexStatusDisconnected
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (res.success) {
                                    "STATUS: ONLINE (${res.latencyMs} ms) • ${res.modelTested}"
                                } else {
                                    "STATUS: DIAGNOSTIC ERROR"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (res.success) AlexStatusReady else AlexStatusDisconnected
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (res.success) res.responseSnippet else (res.errorDetail ?: "Error"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlexTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. GEMINI LIVE MODEL CONFIGURATION
            SettingsSectionCard(title = "4. CENTRALIZED GEMINI LIVE AUDIO MODEL") {
                AlexConfig.AVAILABLE_LIVE_MODELS.forEach { modelId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdateModels(modelId, settings.chatModelName) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.liveModelName == modelId,
                            onClick = { onUpdateModels(modelId, settings.chatModelName) },
                            colors = RadioButtonDefaults.colors(selectedColor = AlexCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = modelId,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlexTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. GEMINI BRAIN / CHAT MODEL CONFIGURATION + DYNAMIC DISCOVERY
            SettingsSectionCard(title = "5. CENTRALIZED GEMINI BRAIN MODEL (DYNAMIC DISCOVERY)") {
                OutlinedButton(
                    onClick = onDiscoverGeminiModels,
                    border = BorderStroke(1.dp, AlexCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_models_button")
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = AlexCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FETCH AVAILABLE GEMINI MODELS FROM API", color = AlexCyan)
                }
                Spacer(modifier = Modifier.height(6.dp))
                discoveredModels.forEach { modelId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUpdateModels(settings.liveModelName, modelId) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.chatModelName == modelId,
                            onClick = { onUpdateModels(settings.liveModelName, modelId) },
                            colors = RadioButtonDefaults.colors(selectedColor = AlexCyan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = modelId,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlexTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. ZERO-TRUST SECURITY AUDIT CARD
            SettingsSectionCard(title = "6. ZERO-TRUST API SECURITY ATTESTATION") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Verified Security",
                        tint = AlexStatusReady,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (settings.customApiKey.isNotBlank()) {
                                "User-Provided Gemini API Key Active (Local DataStore)"
                            } else {
                                "No Unrestricted API Key Embedded in APK"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = AlexStatusReady,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Project: ${securityDiagnostics.projectId} • Mode: ${securityDiagnostics.attestationMode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = securityDiagnostics.statusDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val hardwareSnapshot = remember(context) {
        NativeSystemBridge.captureHardwareSnapshot(context)
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
            .testTag("about_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("about_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AlexCyan
                    )
                }
                Column {
                    Text(
                        text = "ABOUT ALEX AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AlexCyan
                    )
                    Text(
                        text = "VERSION ${AlexConfig.APP_VERSION} • PART 1 & PART 2 COMPLETE",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSectionCard(title = "PERSONALITY & IDENTITY") {
                Text(
                    text = "ALEX is an intelligent, friendly, emotionally aware personal AI assistant & mobile automation commander who addresses you as \"Boss\" and communicates naturally across live voice calls, text, and system automation.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AlexTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSectionCard(title = "NATIVE JAVA + KOTLIN HARDWARE TELEMETRY") {
                CapabilityBullet("Device & OS: ${hardwareSnapshot.deviceModel} • Android ${hardwareSnapshot.androidVersion}")
                CapabilityBullet("CPU Architecture (ABI): ${hardwareSnapshot.primaryCpuAbi}")
                CapabilityBullet("Native Audio Output: ${hardwareSnapshot.nativeSampleRateHz} Hz • ${hardwareSnapshot.nativeBufferFrames} frames/buffer")
                CapabilityBullet("Low-Latency Audio Hardware: ${if (hardwareSnapshot.isLowLatencyAudioSupported) "SUPPORTED" else "STANDARD"}")
                CapabilityBullet("System Memory (RAM): ${hardwareSnapshot.availableMemoryMb} MB free / ${hardwareSnapshot.totalMemoryMb} MB total")
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSectionCard(title = "PART 1 & PART 2 FULL CAPABILITIES") {
                CapabilityBullet("Full-Duplex Gemini Live Voice Streaming (16kHz PCM In / 24kHz PCM Out)")
                CapabilityBullet("10 Realistic Human Voices: 5 Female (Aoede, Leda, Kore, Callirrhoe, Zephyr) + 5 Male (Charon, Puck, Fenrir, Orus, Enceladus)")
                CapabilityBullet("WhatsApp Message Sent Voice Alert: \"Boss, your WhatsApp message has been sent successfully.\"")
                CapabilityBullet("Universal Social Media Message Automation (WhatsApp, Messenger, Instagram, Telegram)")
                CapabilityBullet("Full Android Accessibility Screen Automation & Multi-Step Task Execution")
                CapabilityBullet("Phone Gallery Organizer, Visual Tag Search & Social Media Publisher")
                CapabilityBullet("Android Keystore Master Password + Mandatory Delete Protection Challenge")
                CapabilityBullet("Personal Acoustic Voice Biometric Authentication (3-Sample Enrollment)")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapabilityBullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AlexCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AlexTextPrimary
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AlexCardNavy.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, AlexCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = AlexCyan
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AlexTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = AlexTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
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
