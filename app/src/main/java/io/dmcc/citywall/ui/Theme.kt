package io.dmcc.citywall.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Cartographic dark palette. The map is the brand; chrome stays near-black and quiet,
// with a single restrained slate-mono accent.
val CwBackground = Color(0xFF0E1116)
val CwSurface = Color(0xFF161A22)
val CwSurfaceVariant = Color(0xFF1F2430)
val CwAccent = Color(0xFF7E8AA0)
val CwOnAccent = Color(0xFF11151C)
val CwOnBackground = Color(0xFFE6E9EF)
val CwMuted = Color(0xFF8A93A3)
val CwOutline = Color(0xFF2C3340)

/** The map background colour, reused for the preview surface so they read as one. */
val MapSlate = Color(0xFF1A1E27)

private val CwColorScheme = darkColorScheme(
    primary = CwAccent,
    onPrimary = CwOnAccent,
    secondary = CwAccent,
    onSecondary = CwOnAccent,
    background = CwBackground,
    onBackground = CwOnBackground,
    surface = CwSurface,
    onSurface = CwOnBackground,
    surfaceVariant = CwSurfaceVariant,
    onSurfaceVariant = CwMuted,
    outline = CwOutline,
)

// Monospace section labels and wordmark give the technical, cartographic feel.
val MonoLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    letterSpacing = 3.sp,
)

@Composable
fun CityWallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CwColorScheme,
        typography = Typography(),
        content = content,
    )
}
