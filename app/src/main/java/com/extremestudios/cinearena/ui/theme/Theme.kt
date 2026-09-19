package com.extremestudios.cinearena.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PremiumDarkColors = darkColorScheme(
    primary = Color(0xFFFF4D5E),
    onPrimary = Color(0xFF120508),
    primaryContainer = Color(0xFF54131B),
    onPrimaryContainer = Color(0xFFFFD7DC),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color.White,
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF16161D),
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF25252E),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF44444F)
)

@Composable
fun CineArenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PremiumDarkColors,
        typography = Typography,
        content = content
    )
}
