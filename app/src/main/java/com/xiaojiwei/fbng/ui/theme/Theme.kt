package com.xiaojiwei.fbng.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(FbngPalette.primary),
    onPrimary = Color(FbngPalette.onPrimary),
    primaryContainer = Color(FbngPalette.primaryContainer),
    onPrimaryContainer = Color(FbngPalette.onPrimaryContainer),
    inversePrimary = Color(FbngPalette.inversePrimary),
    secondary = Color(FbngPalette.secondary),
    onSecondary = Color(FbngPalette.onSecondary),
    secondaryContainer = Color(FbngPalette.secondaryContainer),
    onSecondaryContainer = Color(FbngPalette.onSecondaryContainer),
    tertiary = Color(FbngPalette.tertiary),
    onTertiary = Color(FbngPalette.onTertiary),
    tertiaryContainer = Color(FbngPalette.tertiaryContainer),
    onTertiaryContainer = Color(FbngPalette.onTertiaryContainer),
    background = Color(FbngPalette.background),
    onBackground = Color(FbngPalette.onBackground),
    surface = Color(FbngPalette.surface),
    onSurface = Color(FbngPalette.onSurface),
    surfaceVariant = Color(FbngPalette.surfaceVariant),
    onSurfaceVariant = Color(FbngPalette.onSurfaceVariant),
    surfaceTint = Color(FbngPalette.surfaceTint),
    inverseSurface = Color(FbngPalette.inverseSurface),
    inverseOnSurface = Color(FbngPalette.inverseOnSurface),
    error = Color(FbngPalette.error),
    onError = Color(FbngPalette.onError),
    errorContainer = Color(FbngPalette.errorContainer),
    onErrorContainer = Color(FbngPalette.onErrorContainer),
    outline = Color(FbngPalette.outline),
    outlineVariant = Color(FbngPalette.outlineVariant),
    scrim = Color(FbngPalette.scrim),
    surfaceBright = Color(FbngPalette.surfaceBright),
    surfaceDim = Color(FbngPalette.surfaceDim),
    surfaceContainer = Color(FbngPalette.surfaceContainer),
    surfaceContainerHigh = Color(FbngPalette.surfaceContainerHigh),
    surfaceContainerHighest = Color(FbngPalette.surfaceContainerHighest),
    surfaceContainerLow = Color(FbngPalette.surfaceContainerLow),
    surfaceContainerLowest = Color(FbngPalette.surfaceContainerLowest),
    primaryFixed = Color(FbngPalette.primaryFixed),
    primaryFixedDim = Color(FbngPalette.primaryFixedDim),
    onPrimaryFixed = Color(FbngPalette.onPrimaryFixed),
    onPrimaryFixedVariant = Color(FbngPalette.onPrimaryFixedVariant),
    secondaryFixed = Color(FbngPalette.secondaryFixed),
    secondaryFixedDim = Color(FbngPalette.secondaryFixedDim),
    onSecondaryFixed = Color(FbngPalette.onSecondaryFixed),
    onSecondaryFixedVariant = Color(FbngPalette.onSecondaryFixedVariant),
    tertiaryFixed = Color(FbngPalette.tertiaryFixed),
    tertiaryFixedDim = Color(FbngPalette.tertiaryFixedDim),
    onTertiaryFixed = Color(FbngPalette.onTertiaryFixed),
    onTertiaryFixedVariant = Color(FbngPalette.onTertiaryFixedVariant),
)

@Composable
fun FbngTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = FbngTypography,
        shapes = FbngShapes,
    ) {
        // Text with no explicit color uses LocalContentColor, whose default is black.
        // These screens draw their own backgrounds instead of a root Surface, so without
        // this the titles and status lines were black on ink.
        CompositionLocalProvider(LocalContentColor provides Scheme.onBackground, content = content)
    }
}
