package com.grainmvp.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

// The GRANULAR field redesign (2026-09) is square-cornered everywhere --
// buttons, inputs, cards, tags, the segmented control. Overriding every
// Material3 shape slot to a zero-radius RoundedCornerShape here means any
// stock Material component (OutlinedTextField, Button, Surface, etc.)
// used on a redesigned screen is square by default too, instead of
// relying on every call site to pass a shape individually. (Material3's
// Shapes class requires CornerBasedShape specifically, which the plain
// RectangleShape object does not implement -- a 0.dp RoundedCornerShape
// is the correct way to get a visually-square corner here.)
private val SquareCorners = RoundedCornerShape(0.dp)
private val AppShapes = Shapes(
    extraSmall = SquareCorners,
    small = SquareCorners,
    medium = SquareCorners,
    large = SquareCorners,
    extraLarge = SquareCorners
)

@Composable
fun GrainMvpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}