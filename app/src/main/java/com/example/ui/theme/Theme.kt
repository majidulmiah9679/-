package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AlexDarkColorScheme = darkColorScheme(
    primary = AlexCyan,
    onPrimary = AlexBlack,
    primaryContainer = AlexCardNavy,
    onPrimaryContainer = AlexCyan,
    secondary = AlexElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = AlexSurfaceNavy,
    onSecondaryContainer = AlexTextPrimary,
    tertiary = AlexStatusReady,
    onTertiary = AlexBlack,
    background = AlexBlack,
    onBackground = AlexTextPrimary,
    surface = AlexDeepNavy,
    onSurface = AlexTextPrimary,
    surfaceVariant = AlexCardNavy,
    onSurfaceVariant = AlexTextSecondary,
    error = AlexStatusDisconnected,
    onError = Color.White,
    outline = AlexCardBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AlexDarkColorScheme,
        typography = Typography,
        content = content
    )
}
