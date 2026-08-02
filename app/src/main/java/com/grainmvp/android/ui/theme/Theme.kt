package com.grainmvp.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


private val LightColors = lightColorScheme(
    primary = RiceGreen,
    onPrimary = Color.White,
    primaryContainer = RiceGreenLight,
    onPrimaryContainer = Color.White,
    secondary = RiceGold,
    onSecondary = TextPrimary,
    secondaryContainer = RiceGoldLight,
    onSecondaryContainer = TextPrimary,
    background = WarmBackground,
    onBackground = TextPrimary,
    surface = WarmSurface,
    onSurface = TextPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Color.White
)

// Only a light theme for now -- field technicians using this outdoors
// in daylight is the primary use case, and a dark theme isn't a spec
// requirement. Revisit if that assumption turns out wrong.

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp)
)

@Composable
fun GrainMvpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}