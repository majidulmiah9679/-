package com.example.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.AlexConfig
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
import com.example.feature.DiagnosticUiState
import com.example.security.SecurityAuditManager
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexElectricBlue
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexStatusReady
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary

@Composable
fun SettingsScreen(
    settings: AlexSettings,
    onUpdateVoiceProfile: (String, Float, Float) -> Unit,
    onPreviewVoice: (AlexLanguage) -> Unit,
    onUpdatePreferredLanguage: (AlexLanguage) -> Unit,
    onUpdateAudioControls: (Boolean, Boolean, Boolean, Float) -> Unit,
    onUpdatePrivacySettings: (Boolean) -> Unit,
    onUpdateSpeakChatReplies: (Boolean) -> Unit,
    onUpdateUserTitle: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    var bossTitleDraft by remember(settings.userTitle) { mutableStateOf(settings.userTitle) }

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
                            text = "ALEX SYSTEM SETTINGS",
                            style = MaterialTheme.typography.headlineMedium,
                            color = AlexCyan
                        )
                        Text(
                            text = "VOICE • LANGUAGE • AUDIO DUPLEX • PRIVACY",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexTextSecondary
                        )
                    }
                }

                Row {
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

            Spacer(modifier = Modifier.height(14.dp))

            // 1. FEMALE VOICE CONFIGURATION
            SettingsSectionCard(title = "1. FEMALE HUMAN-LIKE VOICE PROFILE") {
                AlexConfig.FEMALE_VOICES.forEach { voice ->
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
                                Text(
                                    text = voice.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AlexTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
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
                    text = "Preview Female Voice in Language:",
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
                    subtitle = "Use ALEX's female voice to read responses in Text Chat mode",
                    checked = settings.speakChatReplies,
                    onCheckedChange = onUpdateSpeakChatReplies
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. PRIVACY & DATA CONTROLS
            SettingsSectionCard(title = "4. PRIVACY & CONVERSATION STORAGE") {
                SettingToggleRow(
                    title = "Save Text Conversation History",
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
                        Text("API & SECURITY")
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
    onUpdateModels: (String, String) -> Unit,
    onRunDiagnostic: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val securityDiagnostics by SecurityAuditManager.diagnostics.collectAsState()

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
                        text = "GEMINI API & SECURITY",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AlexCyan
                    )
                    Text(
                        text = "ZERO-TRUST FIREBASE AI LOGIC • APP CHECK",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. ZERO-TRUST SECURITY AUDIT CARD
            SettingsSectionCard(title = "ZERO-TRUST API SECURITY ATTESTATION") {
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
                            text = "No Unrestricted API Key Embedded in APK",
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

            Spacer(modifier = Modifier.height(14.dp))

            // 2. GEMINI LIVE MODEL CONFIGURATION
            SettingsSectionCard(title = "CENTRALIZED GEMINI LIVE AUDIO MODEL") {
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

            // 3. GEMINI BRAIN / CHAT MODEL CONFIGURATION
            SettingsSectionCard(title = "CENTRALIZED GEMINI BRAIN MODEL") {
                AlexConfig.AVAILABLE_CHAT_MODELS.forEach { modelId ->
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

            // 4. LIVE CONNECTION DIAGNOSTIC RUNNER
            SettingsSectionCard(title = "LIVE GEMINI CONNECTION DIAGNOSTIC") {
                Text(
                    text = "Verify real-time connectivity, App Check token handshake, and Gemini model response latency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextSecondary
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
                        text = "VERSION ${AlexConfig.APP_VERSION} • JARVIS EDITION",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SettingsSectionCard(title = "PERSONALITY & IDENTITY") {
                Text(
                    text = "ALEX is an intelligent, friendly, emotionally aware female personal AI assistant who addresses you as \"Boss\" and communicates naturally across live voice calls and text.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AlexTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSectionCard(title = "CORE TECHNICAL CAPABILITIES") {
                CapabilityBullet("Full-Duplex Gemini Live Voice Streaming (16kHz PCM In / 24kHz PCM Out)")
                CapabilityBullet("Natural Female Voice Profiles: Aoede, Kore, Leda & Zephyr")
                CapabilityBullet("Automatic Multilingual Intelligence: Bangla (বাংলা), English & Hindi (हिन्दी)")
                CapabilityBullet("Real-Time Voice Activity Detection (VAD) & Barge-In Interruption")
                CapabilityBullet("Hardware Acoustic Echo Cancellation (AEC) & Noise Suppression (NS)")
                CapabilityBullet("Local Room Database & DataStore Preferences with Privacy Controls")
                CapabilityBullet("Zero-Trust Firebase AI Logic & App Check Attestation")
            }
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
