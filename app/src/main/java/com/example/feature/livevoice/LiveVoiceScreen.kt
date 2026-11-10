package com.example.feature.livevoice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.AlexSettings
import com.example.domain.model.TranscriptTurn
import com.example.feature.components.AlexHolographicOrb
import com.example.feature.components.AlexStatusBadge
import com.example.feature.components.LiveWaveformVisualizer
import com.example.feature.components.TranscriptTurnBubble
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexStatusConnecting
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary

@Composable
fun LiveVoiceScreen(
    liveStatus: AlexLiveStatus,
    isLiveCallActive: Boolean,
    isMicMuted: Boolean,
    waveformBars: List<Float>,
    inputAmplitude: Float,
    outputAmplitude: Float,
    liveTranscript: List<TranscriptTurn>,
    detectedLanguage: AlexLanguage,
    bargeInCount: Int,
    errorMessage: String?,
    settings: AlexSettings,
    onStartLive: () -> Unit,
    onEndCall: () -> Unit,
    onReconnect: () -> Unit,
    onToggleMicMute: () -> Unit,
    onInterruptSpeech: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartLive()
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
            .testTag("live_voice_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("live_voice_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home",
                        tint = AlexCyan
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "GEMINI LIVE VOICE HUD",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AlexCyan
                    )
                    Text(
                        text = "FULL-DUPLEX • ${settings.selectedVoiceId.uppercase()} VOICE",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextSecondary
                    )
                }

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Voice Settings",
                        tint = AlexTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AlexStatusBadge(
                status = liveStatus,
                isMicMuted = isMicMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry HUD Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryMetricChip(
                    label = "LANGUAGE",
                    value = detectedLanguage.nativeLabel,
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricChip(
                    label = "AUDIO STREAM",
                    value = "16kHz IN / 24kHz OUT",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricChip(
                    label = "BARGE-IN",
                    value = if (settings.autoBargeInEnabled) "ACTIVE ($bargeInCount)" else "MANUAL",
                    modifier = Modifier.weight(1f)
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AlexStatusDisconnected.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, AlexStatusDisconnected)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onReconnect,
                            border = BorderStroke(1.dp, AlexCyan),
                            modifier = Modifier.testTag("live_reconnect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reconnect Live Session",
                                tint = AlexCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RECONNECT", color = AlexCyan)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AlexHolographicOrb(
                status = liveStatus,
                inputAmplitude = inputAmplitude,
                outputAmplitude = outputAmplitude,
                orbSize = 210.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            LiveWaveformVisualizer(
                waveformBars = waveformBars,
                status = liveStatus
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Live Bidirectional Transcript Stream
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = AlexSurfaceNavy.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, AlexCardBorder)
            ) {
                if (liveTranscript.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLiveCallActive) {
                                "Listening to you, ${settings.userTitle}... Speak naturally in বাংলা, English, or हिन्दी."
                            } else {
                                "Tap START LIVE below to initiate a real-time full-duplex voice call with ALEX."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlexTextSecondary,
                            textAlign = TextAlign.Center
                        )
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

            Spacer(modifier = Modifier.height(12.dp))

            // Call Control Deck
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLiveCallActive) {
                    Button(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                onStartLive()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(54.dp)
                            .testTag("live_screen_start_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexCyan,
                            contentColor = AlexBlack
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Start Live Call")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("START LIVE CALL", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = {
                            onEndCall()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(54.dp)
                            .testTag("live_screen_end_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlexStatusDisconnected,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CallEnd, contentDescription = "End Call")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("END CALL", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Barge-In / Interrupt Speech Button
                OutlinedButton(
                    onClick = onInterruptSpeech,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("barge_in_interrupt_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AlexStatusConnecting)
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = "Interrupt ALEX Speech",
                        tint = AlexStatusConnecting,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INTERRUPT",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexStatusConnecting
                    )
                }

                // Mic Mute Button
                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("live_screen_mute_button"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isMicMuted) AlexStatusDisconnected.copy(alpha = 0.25f) else AlexCardNavy,
                    border = BorderStroke(
                        1.dp,
                        if (isMicMuted) AlexStatusDisconnected else AlexCardBorder
                    )
                ) {
                    IconButton(onClick = onToggleMicMute) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone",
                            tint = if (isMicMuted) AlexStatusDisconnected else AlexCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = AlexCardNavy.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, AlexCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AlexTextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = AlexCyan,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
