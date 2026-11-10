package com.example.feature.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AlexLiveStatus
import com.example.feature.components.AlexHolographicOrb
import com.example.ui.theme.AlexBlack
import com.example.ui.theme.AlexCardBorder
import com.example.ui.theme.AlexCardNavy
import com.example.ui.theme.AlexCyan
import com.example.ui.theme.AlexDeepNavy
import com.example.ui.theme.AlexElectricBlue
import com.example.ui.theme.AlexStatusReady
import com.example.ui.theme.AlexSurfaceNavy
import com.example.ui.theme.AlexTextPrimary
import com.example.ui.theme.AlexTextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    var bootProgress by remember { mutableFloatStateOf(0.15f) }
    val animatedProgress by animateFloatAsState(
        targetValue = bootProgress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bootProgress"
    )

    LaunchedEffect(Unit) {
        delay(250)
        bootProgress = 0.55f
        delay(350)
        bootProgress = 1.0f
        delay(350)
        onSplashFinished()
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
            .clickable { onSplashFinished() }
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SYSTEM BOOT // JARVIS PROTOCOL",
                style = MaterialTheme.typography.labelMedium,
                color = AlexCyan
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "ALEX AI",
                style = MaterialTheme.typography.displayLarge,
                color = AlexTextPrimary
            )
            Text(
                text = "PERSONAL LIVE AI VOICE ASSISTANT",
                style = MaterialTheme.typography.labelLarge,
                color = AlexTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            AlexHolographicOrb(
                status = AlexLiveStatus.CONNECTING,
                inputAmplitude = 0.35f,
                outputAmplitude = 0.45f,
                orbSize = 200.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = AlexCyan,
                trackColor = AlexCardNavy
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = AlexSurfaceNavy.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, AlexCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "• GEMINI LIVE AUDIO DUPLEX: ONLINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexStatusReady
                    )
                    Text(
                        text = "• VOICE ENGINE: AOEDE FEMALE NEURAL",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexCyan
                    )
                    Text(
                        text = "• LANGUAGES: বাংলা • ENGLISH • हिन्दी",
                        style = MaterialTheme.typography.labelMedium,
                        color = AlexTextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AlexBlack, AlexDeepNavy, AlexBlack)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("welcome_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 580.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = AlexCardNavy,
                border = BorderStroke(1.dp, AlexCyan.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "WELCOME, BOSS • NEURAL LINK READY",
                    style = MaterialTheme.typography.labelMedium,
                    color = AlexCyan,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "MEET ALEX AI",
                style = MaterialTheme.typography.displayMedium,
                color = AlexTextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your emotionally aware, multilingual personal live voice assistant powered by Google Gemini Live.",
                style = MaterialTheme.typography.bodyLarge,
                color = AlexTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, AlexCyan.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_alex_hero_banner_1794274440802),
                        contentDescription = "ALEX AI futuristic holographic HUD banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        AlexBlack.copy(alpha = 0.15f),
                                        AlexBlack.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "\"হ্যালো Boss! কেমন আছো? I'm right here, ready to help you with anything.\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlexTextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            WelcomeFeatureCard(
                icon = Icons.Default.GraphicEq,
                title = "Full-Duplex Gemini Live Voice",
                description = "Talk naturally like a real phone call with continuous microphone streaming, Voice Activity Detection (VAD), and instant barge-in interruption."
            )
            Spacer(modifier = Modifier.height(10.dp))
            WelcomeFeatureCard(
                icon = Icons.Default.RecordVoiceOver,
                title = "Warm Human-Like Female Voice",
                description = "Powered by Gemini Live's Aoede, Kore, Leda & Zephyr female voice profiles with natural intonation and zero robotic sound."
            )
            Spacer(modifier = Modifier.height(10.dp))
            WelcomeFeatureCard(
                icon = Icons.Default.Language,
                title = "Bangla, English & Hindi Fluency",
                description = "Speak in বাংলা, English, or हिन्दी—ALEX automatically detects your language and supports natural code-switching."
            )
            Spacer(modifier = Modifier.height(10.dp))
            WelcomeFeatureCard(
                icon = Icons.Default.Security,
                title = "Zero-Trust App Check Security",
                description = "Authenticated via Firebase App Check without exposing permanent API keys inside the APK."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("welcome_continue_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlexCyan,
                    contentColor = AlexBlack
                )
            ) {
                Text(
                    text = "LAUNCH ALEX AI HUD",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Launch ALEX AI"
                )
            }
        }
    }
}

@Composable
private fun WelcomeFeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AlexCardNavy.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, AlexCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = AlexSurfaceNavy,
                border = BorderStroke(1.dp, AlexCyan.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = AlexCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AlexTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlexTextSecondary
                )
            }
        }
    }
}
