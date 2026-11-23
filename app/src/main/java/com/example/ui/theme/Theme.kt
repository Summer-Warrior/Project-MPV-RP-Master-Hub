package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NexusDarkColorScheme = darkColorScheme(
    primary = NexusCyan,
    onPrimary = NexusBackground,
    primaryContainer = NexusCyanContainer,
    onPrimaryContainer = NexusCyan,
    secondary = NexusViolet,
    onSecondary = NexusBackground,
    secondaryContainer = NexusVioletContainer,
    onSecondaryContainer = NexusViolet,
    tertiary = NexusGold,
    onTertiary = NexusBackground,
    tertiaryContainer = NexusGoldContainer,
    onTertiaryContainer = NexusGold,
    background = NexusBackground,
    onBackground = NexusTextPrimary,
    surface = NexusSurface,
    onSurface = NexusTextPrimary,
    surfaceVariant = NexusCard,
    onSurfaceVariant = NexusTextSecondary,
    surfaceContainerLowest = NexusBackground,
    surfaceContainerLow = NexusSurface,
    surfaceContainer = NexusCard,
    surfaceContainerHigh = NexusCardElevated,
    surfaceContainerHighest = Color(0xFF28344E),
    outline = NexusBorder,
    outlineVariant = Color(0xFF1F2A40),
    error = NexusDanger,
    onError = Color.White
)

private val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun NexusMasterHubTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NexusDarkColorScheme,
        typography = NexusTypography,
        shapes = NexusShapes,
        content = content
    )
}
