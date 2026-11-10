package com.example.feature.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.AlexSettings
import com.example.domain.model.TranscriptTurn
import com.example.feature.components.AlexHolographicOrb
import com.example.feature.components.AlexStatusBadge
import com.example.feature.components.LanguageChipBadge
import com.example.feature.components.LiveWaveformVisualizer
import com.example.feature.components.TranscriptTurnBubble
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexElectricBlue
import com.example.ui.theme.AlexStatusConnecting
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexStatusReady
import androidx.compose.material.icons.filled.SmartToy
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextMuted
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary

@Composable
fun HomeScreen(
    liveStatus: AlexLiveStatus,
    isLiveCallActive: Boolean,
    isMicMuted: Boolean,
    waveformBars: List<Float>,
    inputAmplitude: Float,
    outputAmplitude: Float,
    liveTranscript: List<TranscriptTurn>,
    detectedLanguage: AlexLanguage,
    errorMessage: String?,
    settings: AlexSettings,
    onStartLive: () -> Unit,
    onEndCall: () -> Unit,
    onReconnect: () -> Unit,
    onToggleMicMute: () -> Unit,
    onSendQuickVoicePrompt: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    onNavigateToLiveVoice: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAutomationHub: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var permissionDeniedBanner by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Proceed with live call regardless of optional notification permission
        onStartLive()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionDeniedBanner = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@rememberLauncherForActivityResult
                }
            }
            onStartLive()
        } else {
            permissionDeniedBanner = true
            onPermissionDenied()
        }
    }

    val triggerStartLiveWithPermission = {
        val hasMicPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPerm) {
            permissionDeniedBanner = false
            onStartLive()
        } else {
            showPermissionRationaleDialog = true
        }
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            containerColor = AlexCardNavy,
            titleContentColor = AlexCyan,
            textContentColor = AlexTextPrimary,
            title = {
                Text(
                    text = stringResource(R.string.permission_mic_title),
                    style = MaterialTheme.typography.headlineMedium
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.permission_mic_rationale),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleDialog = false
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexCyan,
                        contentColor = AlexBlack
                    ),
                    modifier = Modifier.testTag("grant_mic_permission_button")
                ) {
                    Text("ALLOW MICROPHONE")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleDialog = false
                        permissionDeniedBanner = true
                    }
                ) {
                    Text("NOT NOW", color = AlexTextSecondary)
                }
            }
        )
    }

    val listState = rememberLazyListState()
    LaunchedEffect(liveTranscript.size, liveTranscript.lastOrNull()?.text) {
        if (liveTranscript.isNotEmpty()) {
            listState.animateScrollToItem(liveTranscript.lastIndex)
        }
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
            .testTag("home_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. TOP FUTURISTIC HEADER & NAVIGATION BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ALEX AI",
                            style = MaterialTheme.typography.headlineLarge,
                            color = AlexCyan,
                            modifier = Modifier.testTag("alex_ai_logo")
                        )
                        LanguageChipBadge(language = detectedLanguage)
                    }
                    Text(
                        text = "VOICE: ${settings.selectedVoiceId.uppercase()} • ${settings.userTitle.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onNavigateToLiveVoice,
                        modifier = Modifier.testTag("nav_live_hud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SurroundSound,
                            contentDescription = "Full Screen Live Voice HUD",
                            tint = AlexCyan
                        )
                    }
                    IconButton(
                        onClick = onNavigateToChat,
                        modifier = Modifier.testTag("nav_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Text Chat Screen",
                            tint = AlexTextPrimary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToHistory,
                        modifier = Modifier.testTag("nav_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Conversation History Screen",
                            tint = AlexTextPrimary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToAutomationHub,
                        modifier = Modifier.testTag("nav_automation_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Automation & Gallery Hub",
                            tint = AlexCyan
                        )
                    }
                    IconButton(
                        onClick = onNavigateToApiConfig,
                        modifier = Modifier.testTag("nav_api_config_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "API Configuration Screen",
                            tint = AlexTextPrimary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToAbout,
                        modifier = Modifier.testTag("nav_about_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About ALEX Screen",
                            tint = AlexTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. STATUS INDICATOR BADGE + QUICK MANUAL API KEY BUTTON + AUTOMATION HUB CHIP
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlexStatusBadge(
                    status = liveStatus,
                    isMicMuted = isMicMuted
                )

                Surface(
                    modifier = Modifier
                        .clickable { onNavigateToApiConfig() }
                        .testTag("home_api_key_chip"),
                    shape = RoundedCornerShape(50),
                    color = if (settings.customApiKey.isNotBlank()) {
                        AlexStatusReady.copy(alpha = 0.18f)
                    } else {
                        AlexCardNavy
                    },
                    border = BorderStroke(
                        1.dp,
                        if (settings.customApiKey.isNotBlank()) AlexStatusReady else AlexCyan
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Configure Gemini API Key",
                            tint = if (settings.customApiKey.isNotBlank()) AlexStatusReady else AlexCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (settings.customApiKey.isNotBlank()) "API KEY: ONLINE" else "SET API KEY",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (settings.customApiKey.isNotBlank()) AlexStatusReady else AlexCyan
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clickable { onNavigateToAutomationHub() }
                        .testTag("home_automation_hub_chip"),
                    shape = RoundedCornerShape(50),
                    color = AlexCardNavy,
                    border = BorderStroke(1.dp, AlexCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Automation & Gallery Command Center",
                            tint = AlexCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "AUTOMATION & GALLERY",
                            style = MaterialTheme.typography.labelMedium,
                            color = AlexCyan
                        )
                    }
                }
            }

            // 3. ERROR OR PERMISSION BANNER WITH RECONNECT ACTION
            if (errorMessage != null || permissionDeniedBanner) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("error_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = AlexStatusDisconnected.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AlexStatusDisconnected)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage
                                ?: "Microphone permission denied. Grant microphone access to use Live Voice.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (permissionDeniedBanner) {
                                    triggerStartLiveWithPermission()
                                } else {
                                    onReconnect()
                                }
                            },
                            border = BorderStroke(1.dp, AlexCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("reconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reconnect",
                                tint = AlexCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.btn_reconnect),
                                style = MaterialTheme.typography.labelMedium,
                                color = AlexCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. LARGE ANIMATED CIRCULAR AI ASSISTANT ORB IN THE CENTER
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.clickable {
                    if (isLiveCallActive) onNavigateToLiveVoice() else triggerStartLiveWithPermission()
                }
            ) {
                AlexHolographicOrb(
                    status = liveStatus,
                    inputAmplitude = inputAmplitude,
                    outputAmplitude = outputAmplitude,
                    orbSize = 148.dp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 5. LIVE WAVEFORM VISUALIZATION
            LiveWaveformVisualizer(
                waveformBars = waveformBars,
                status = liveStatus,
                modifier = Modifier.height(52.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 6. QUICK MULTILINGUAL VOICE TRIGGERS (Bangla, English, Hindi)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickPrompts = listOf(
                    "হ্যালো অ্যালেক্স! কেমন আছো?",
                    "What are you doing?",
                    "আজ আমার মন ভালো নেই।",
                    "नमस्ते एलेक्स, आज क्या प्लान है?"
                )
                quickPrompts.forEachIndexed { idx, prompt ->
                    Surface(
                        modifier = Modifier
                            .clickable { onSendQuickVoicePrompt(prompt) }
                            .testTag("quick_prompt_$idx"),
                        shape = RoundedCornerShape(50),
                        color = AlexCardNavy,
                        border = BorderStroke(1.dp, AlexCardBorder)
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 7. LIVE CONVERSATION TRANSCRIPT PANEL
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("live_transcript_panel"),
                shape = RoundedCornerShape(18.dp),
                color = AlexSurfaceNavy.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, AlexCardBorder)
            ) {
                if (liveTranscript.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Live Voice Transcript Ready",
                                tint = AlexCyan.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Press START LIVE to talk with ALEX in real time",
                                style = MaterialTheme.typography.titleMedium,
                                color = AlexTextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Speak naturally in বাংলা, English, or हिन्दी. Interrupt ALEX anytime—full-duplex barge-in is active.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlexTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(liveTranscript, key = { it.id }) { turn ->
                            TranscriptTurnBubble(
                                turn = turn,
                                userTitle = settings.userTitle
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 8. PRIMARY ACTION BUTTONS: START LIVE, END CALL, MICROPHONE MUTE, SETTINGS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // START LIVE BUTTON
                Button(
                    onClick = {
                        if (!isLiveCallActive) {
                            triggerStartLiveWithPermission()
                        } else {
                            onNavigateToLiveVoice()
                        }
                    },
                    enabled = liveStatus != AlexLiveStatus.CONNECTING,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp)
                        .testTag("start_live_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLiveCallActive) AlexStatusReady else AlexCyan,
                        contentColor = AlexBlack
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.btn_start_live),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLiveCallActive) "LIVE HUD" else stringResource(R.string.btn_start_live),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // END CALL BUTTON
                Button(
                    onClick = onEndCall,
                    enabled = isLiveCallActive || liveStatus == AlexLiveStatus.SPEAKING || liveStatus == AlexLiveStatus.CONNECTING,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(52.dp)
                        .testTag("end_call_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexStatusDisconnected,
                        contentColor = Color.White,
                        disabledContainerColor = AlexCardNavy,
                        disabledContentColor = AlexTextMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = stringResource(R.string.btn_end_call),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_end_call),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // MICROPHONE MUTE BUTTON
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("mic_mute_button"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isMicMuted) AlexStatusDisconnected.copy(alpha = 0.25f) else AlexCardNavy,
                    border = BorderStroke(
                        1.dp,
                        if (isMicMuted) AlexStatusDisconnected else AlexCardBorder
                    )
                ) {
                    IconButton(onClick = onToggleMicMute) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) {
                                stringResource(R.string.btn_unmute_mic)
                            } else {
                                stringResource(R.string.btn_mute_mic)
                            },
                            tint = if (isMicMuted) AlexStatusDisconnected else AlexCyan
                        )
                    }
                }

                // SETTINGS BUTTON
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("settings_button"),
                    shape = RoundedCornerShape(14.dp),
                    color = AlexCardNavy,
                    border = BorderStroke(1.dp, AlexCardBorder)
                ) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.btn_settings),
                            tint = AlexTextPrimary
                        )
                    }
                }
            }
        }
    }
}
