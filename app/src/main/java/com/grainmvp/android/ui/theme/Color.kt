package com.grainmvp.android.ui.theme

import androidx.compose.ui.graphics.Color

// Derived from the PhilRice / Department of Agriculture seal: deep
// green (rice plant, agriculture), golden amber (rice grains), warm
// off-white (not stark white -- matches the cream tone in the seal's
// stalks). Deliberately muted/limited palette rather than colorful --
// "agricultural but clean," per Fateful's direction.

val RiceGreenDark = Color(0xFF14401A)
val RiceGreen = Color(0xFF1B5E20)
val RiceGreenLight = Color(0xFF4C8C4A)

val RiceGold = Color(0xFFF9A825)
val RiceGoldLight = Color(0xFFFFD95B)

val WarmBackground = Color(0xFFFDFBF5)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFF1EEE4)

val TextPrimary = Color(0xFF1A1C18)
val TextSecondary = Color(0xFF49454F)

val ErrorRed = Color(0xFFBA1A1A)

// --- GRANULAR field redesign tokens (2026-09 UX pass) ---
// Extracted verbatim from the design system CSS in
// "GRANULAR Field Redesign.dc.html" -- do not re-derive these, they are
// the literal hex values the mockups use. See docs/IMPLEMENTATION.md for
// the dated section covering this pass.

val AccentGreen = Color(0xFF1B5E20)
val AccentGreen2 = Color(0xFF2F7A3A)
val Accent100 = Color(0xFFEEF6EF)
val Accent200 = Color(0xFFD5E8D7)
val Accent300 = Color(0xFFB2D3B6)
val Accent400 = Color(0xFF86B78D)
val Accent500 = Color(0xFF5F9A68)
val Accent600 = Color(0xFF17501B)
val Accent700 = Color(0xFF1F4A26)
val Accent800 = Color(0xFF17381C)
val Accent900 = Color(0xFF14301A)

// Deliberate departure from the app's existing warm cream background --
// the redesign's design system uses a neutral light gray everywhere.
val SurfaceBg = Color(0xFFF2F2F3)
val TextPrimaryGranular = Color(0xFF1D1F20)

// #1D1F20 at 16% alpha, used for every hairline divider/border.
val DividerColor = Color(0x291D1F20)

val Neutral100 = Color(0xFFF5F5F8)
val Neutral200 = Color(0xFFE7E7EA)
val Neutral300 = Color(0xFFD4D4D7)
val Neutral400 = Color(0xFFB7B7BA)
val Neutral500 = Color(0xFF98989B)
val Neutral600 = Color(0xFF7A7A7D)
val Neutral700 = Color(0xFF5D5D60)
val Neutral800 = Color(0xFF424244)
val Neutral900 = Color(0xFF2B2B2D)

// Correction screen (05) box states -- line style carries the meaning,
// these colors are a secondary cue, not the only one (colorblind
// accessibility per the design brief).
val DetectionBlue = Color(0xFF2F6FDB)
val AddedBoxDark = Accent900
val RemovedBoxLegendGray = Neutral500

val FailureBg = Color(0xFFFBE9E7)
val FailureBorder = Color(0xFFB3261E)
val FailureEyebrow = Color(0xFF8C1D18)

val ResultPlateBg = Color(0xFF3D7D46)
val ResultEyebrow = Color(0xFFDCEFDD)