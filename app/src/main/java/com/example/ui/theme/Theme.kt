package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkStoreColorPalette = darkColorScheme(
    primary = NeonMint,
    onPrimary = Color(0xFF02140D),
    secondary = NeonBlue,
    onSecondary = Color(0xFF001B3D),
    tertiary = WarningOrange,
    onTertiary = Color(0xFF2C1600),
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkOnSurface,
    error = ErrorRed,
    onError = Color.Black
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkStoreColorPalette,
        typography = Typography,
        content = content
    )
}
