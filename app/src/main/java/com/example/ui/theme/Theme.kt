package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = PureBlack,
    primaryContainer = ObsidianSurfaceVariant,
    onPrimaryContainer = JarvisCyanGlow,
    secondary = JarvisBlue,
    onSecondary = PureBlack,
    secondaryContainer = ObsidianSurface,
    onSecondaryContainer = JarvisCyan,
    tertiary = JarvisGold,
    onTertiary = PureBlack,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = PureBlack,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = JarvisCrimson,
    onError = Color.White
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
