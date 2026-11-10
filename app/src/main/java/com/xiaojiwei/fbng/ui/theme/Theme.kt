package com.xiaojiwei.fbng.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF12140F)
private val InkRaised = Color(0xFF1C2118)
private val Paper = Color(0xFFF3F0E6)
private val Muted = Color(0xFFB7B2A6)
private val Cinnabar = Color(0xFFD84A2F)
private val OnCinnabar = Color(0xFF2A0C07)

private val Scheme = darkColorScheme(
    primary = Cinnabar,
    onPrimary = OnCinnabar,
    secondary = Color(0xFFE0B15A),
    onSecondary = Color(0xFF2A210C),
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = Color(0xFF2A3126),
    onSurfaceVariant = Muted,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun FbngTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
