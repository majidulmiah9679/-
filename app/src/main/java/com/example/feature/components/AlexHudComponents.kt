package com.example.feature.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexCyanGlow
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexElectricBlue
import com.example.ui.theme.AlexStatusConnecting
import com.example.ui.theme.AlexStatusDisconnected
import com.example.ui.theme.AlexStatusListening
import com.example.ui.theme.AlexStatusReady
import com.example.ui.theme.AlexStatusSpeaking
import com.example.ui.theme.AlexStatusThinking
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextMuted
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun statusColorFor(status: AlexLiveStatus): Color = when (status) {
    AlexLiveStatus.READY -> AlexStatusReady
    AlexLiveStatus.LISTENING -> AlexStatusListening
    AlexLiveStatus.THINKING -> AlexStatusThinking
    AlexLiveStatus.SPEAKING -> AlexStatusSpeaking
    AlexLiveStatus.CONNECTING -> AlexStatusConnecting
    AlexLiveStatus.DISCONNECTED -> AlexStatusDisconnected
}

@Composable
fun AlexStatusBadge(
    status: AlexLiveStatus,
    isMicMuted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor by animateColorAsState(
        targetValue = statusColorFor(status),
        animationSpec = tween(300),
        label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        modifier = modifier.testTag("status_badge_${status.name.lowercase()}"),
        shape = RoundedCornerShape(50),
        color = AlexCardNavy.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        activeColor.copy(
                            alpha = if (status == AlexLiveStatus.DISCONNECTED) 1f else dotAlpha
                        )
                    )
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelLarge,
                color = activeColor,
                modifier = Modifier.testTag("status_text")
            )
            if (isMicMuted) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "Microphone Muted",
                    tint = AlexStatusDisconnected,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun AlexHolographicOrb(
    status: AlexLiveStatus,
    inputAmplitude: Float,
    outputAmplitude: Float,
    orbSize: Dp = 210.dp,
    modifier: Modifier = Modifier
) {
    val primaryColor by animateColorAsState(
        targetValue = statusColorFor(status),
        animationSpec = tween(350),
        label = "orbPrimaryColor"
    )

    val activeLevel = when (status) {
        AlexLiveStatus.SPEAKING -> outputAmplitude.coerceIn(0.08f, 1f)
        AlexLiveStatus.LISTENING -> inputAmplitude.coerceIn(0.05f, 1f)
        AlexLiveStatus.THINKING -> 0.45f
        AlexLiveStatus.CONNECTING -> 0.35f
        AlexLiveStatus.READY -> 0.12f
        AlexLiveStatus.DISCONNECTED -> 0.0f
    }

    val smoothedAmplitude by animateFloatAsState(
        targetValue = activeLevel,
        animationSpec = tween(90),
        label = "smoothedAmplitude"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "orbInfinite")
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AlexLiveStatus.THINKING -> 3200
                    AlexLiveStatus.CONNECTING -> 2600
                    AlexLiveStatus.SPEAKING -> 6000
                    AlexLiveStatus.LISTENING -> 8500
                    else -> 14000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == AlexLiveStatus.READY) 2600 else 1200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    Box(
        modifier = modifier
            .size(orbSize)
            .testTag("alex_holographic_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f
            val dynamicScale = if (status == AlexLiveStatus.DISCONNECTED) {
                0.92f
            } else {
                breathScale + (smoothedAmplitude * 0.16f)
            }

            // 1. Deep Radial Ambient Hologram Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.34f * dynamicScale.coerceAtMost(1.2f)),
                        AlexCyanGlow.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 0.98f
                ),
                radius = maxRadius * 0.98f,
                center = center
            )

            // 2. Outer Segmented JARVIS HUD Ring
            val effectiveRotation = if (status == AlexLiveStatus.DISCONNECTED) 0f else outerRotation
            rotate(degrees = effectiveRotation, pivot = center) {
                val outerArcRadius = maxRadius * 0.88f
                val arcSize = Size(outerArcRadius * 2, outerArcRadius * 2)
                val topLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius)
                for (segment in 0 until 4) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.72f),
                        startAngle = segment * 90f + 10f,
                        sweepAngle = 58f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Counter-Rotating Telemetry Tick Ring
            rotate(degrees = -effectiveRotation * 0.75f, pivot = center) {
                val tickRadius = maxRadius * 0.76f
                val tickCount = 36
                for (i in 0 until tickCount) {
                    val angleRad = (i * (360f / tickCount)) * (PI / 180.0)
                    val isMajor = i % 3 == 0
                    val innerR = if (isMajor) tickRadius * 0.92f else tickRadius * 0.96f
                    val start = Offset(
                        x = center.x + (innerR * cos(angleRad)).toFloat(),
                        y = center.y + (innerR * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (tickRadius * cos(angleRad)).toFloat(),
                        y = center.y + (tickRadius * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = if (isMajor) primaryColor.copy(alpha = 0.8f) else AlexCyan.copy(alpha = 0.35f),
                        start = start,
                        end = end,
                        strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            // 4. Thinking / Active Particle Orbitals
            if (status == AlexLiveStatus.THINKING || status == AlexLiveStatus.CONNECTING || status == AlexLiveStatus.SPEAKING) {
                val particleCount = 8
                val orbitR = maxRadius * 0.64f
                for (p in 0 until particleCount) {
                    val angle = ((effectiveRotation * 1.6f) + (p * 45f)) * (PI / 180.0)
                    val px = center.x + (orbitR * cos(angle)).toFloat()
                    val py = center.y + (orbitR * sin(angle)).toFloat()
                    drawCircle(
                        color = primaryColor,
                        radius = (3.5f + (p % 2) * 1.5f).dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }

            // 5. Inner Reactive Voice Energy Core
            val coreRadius = (maxRadius * 0.44f * dynamicScale).coerceAtMost(maxRadius * 0.62f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        primaryColor.copy(alpha = 0.85f),
                        AlexElectricBlue.copy(alpha = 0.35f),
                        AlexDeepNavy.copy(alpha = 0.9f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            drawCircle(
                color = primaryColor,
                radius = coreRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center Core Label / Icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (status == AlexLiveStatus.DISCONNECTED) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Disconnected warning",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text(
                    text = "ALEX",
                    style = MaterialTheme.typography.labelLarge,
                    color = AlexBlack,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (status) {
                        AlexLiveStatus.LISTENING -> "LIVE MIC"
                        AlexLiveStatus.SPEAKING -> "VOICE OUT"
                        AlexLiveStatus.THINKING -> "NEURAL"
                        AlexLiveStatus.CONNECTING -> "LINKING"
                        AlexLiveStatus.READY -> "STANDBY"
                        AlexLiveStatus.DISCONNECTED -> "OFFLINE"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                    color = AlexBlack.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun LiveWaveformVisualizer(
    waveformBars: List<Float>,
    status: AlexLiveStatus,
    modifier: Modifier = Modifier
) {
    val activeColor by animateColorAsState(
        targetValue = statusColorFor(status),
        animationSpec = tween(250),
        label = "waveformColor"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .testTag("live_waveform_visualizer"),
        shape = RoundedCornerShape(16.dp),
        color = AlexSurfaceNavy.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, AlexCardBorder.copy(alpha = 0.7f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = waveformBars.size.coerceAtLeast(1)
                val spacing = 5.dp.toPx()
                val totalSpacing = spacing * (count - 1)
                val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(3.dp.toPx())
                val centerY = size.height / 2f

                waveformBars.forEachIndexed { index, rawLevel ->
                    val normalized = if (status == AlexLiveStatus.DISCONNECTED || status == AlexLiveStatus.READY) {
                        0.08f
                    } else {
                        rawLevel.coerceIn(0.08f, 1f)
                    }
                    val barHeight = (size.height * normalized).coerceAtLeast(4.dp.toPx())
                    val x = index * (barWidth + spacing) + barWidth / 2f
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                activeColor,
                                AlexCyan,
                                activeColor.copy(alpha = 0.6f)
                            )
                        ),
                        start = Offset(x, centerY - barHeight / 2f),
                        end = Offset(x, centerY + barHeight / 2f),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun TranscriptTurnBubble(
    turn: TranscriptTurn,
    userTitle: String = "Boss",
    modifier: Modifier = Modifier
) {
    val isAlex = turn.sender == MessageSender.ALEX
    val alignment = if (isAlex) Alignment.Start else Alignment.End
    val borderColor = if (isAlex) AlexCyan.copy(alpha = 0.45f) else AlexElectricBlue.copy(alpha = 0.45f)
    val containerColor = if (isAlex) AlexCardNavy.copy(alpha = 0.92f) else AlexSurfaceNavy.copy(alpha = 0.95f)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isAlex) 4.dp else 16.dp,
                bottomEnd = if (isAlex) 16.dp else 4.dp
            ),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAlex) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "ALEX voice indicator",
                            tint = AlexCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = if (isAlex) "ALEX AI" else userTitle.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAlex) AlexCyan else AlexElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                    LanguageChipBadge(language = turn.language)
                    if (turn.wasInterrupted) {
                        Text(
                            text = "INTERRUPTED",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                            color = AlexStatusConnecting
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (turn.isStreaming) "${turn.text} ▍" else turn.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AlexTextPrimary
                )
            }
        }
    }
}

@Composable
fun LanguageChipBadge(
    language: AlexLanguage,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = AlexDeepNavy,
        border = BorderStroke(0.5.dp, AlexCardBorder)
    ) {
        Text(
            text = when (language) {
                AlexLanguage.BANGLA -> "বাংলা"
                AlexLanguage.HINDI -> "हिन्दी"
                AlexLanguage.ENGLISH -> "EN"
                AlexLanguage.AUTO -> "AUTO"
            },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = AlexTextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
