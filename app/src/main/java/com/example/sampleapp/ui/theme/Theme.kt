package com.example.sampleapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF1ED760),
    onPrimary = Color(0xFF00210B),
    secondary = Color(0xFF53D6C4),
    background = Color(0xFF0E0E10),
    surface = Color(0xFF1A1A1D),
    surfaceVariant = Color(0xFF26262B),
    onBackground = Color(0xFFECECEC),
    onSurface = Color(0xFFECECEC),
    error = Color(0xFFFF6B6B)
)

@Composable
fun SampleAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}
