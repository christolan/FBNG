package com.xiaojiwei.fbng.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.x contrast for the pairs the UI actually paints.
 * Body text needs 4.5:1. Large text and non-text marks (the cinnabar control, the
 * progress indicator, the outline) need 3:1.
 */
class ContrastTest {
    @Test
    fun bodyTextClearsAaOnEverySurfaceItSitsOn() {
        val surfaces = listOf(
            "background" to FbngPalette.background,
            "surface" to FbngPalette.surface,
            "surfaceVariant" to FbngPalette.surfaceVariant,
            "surfaceContainerHighest" to FbngPalette.surfaceContainerHighest,
            "surfaceBright" to FbngPalette.surfaceBright,
        )
        for ((name, surface) in surfaces) {
            assertRatio("onSurface/$name", FbngPalette.onSurface, surface, 4.5)
            assertRatio("onSurfaceVariant/$name", FbngPalette.onSurfaceVariant, surface, 4.5)
            assertRatio("secondary/$name", FbngPalette.secondary, surface, 4.5)
            assertRatio("error/$name", FbngPalette.error, surface, 4.5)
        }
        assertRatio("onBackground/background", FbngPalette.onBackground, FbngPalette.background, 4.5)
    }

    @Test
    fun buttonAndContainerPairsClearAa() {
        assertRatio("onPrimary/primary", FbngPalette.onPrimary, FbngPalette.primary, 4.5)
        assertRatio("onSecondary/secondary", FbngPalette.onSecondary, FbngPalette.secondary, 4.5)
        assertRatio("onTertiary/tertiary", FbngPalette.onTertiary, FbngPalette.tertiary, 4.5)
        assertRatio(
            "onPrimaryContainer/primaryContainer",
            FbngPalette.onPrimaryContainer,
            FbngPalette.primaryContainer,
            4.5,
        )
        assertRatio(
            "onSecondaryContainer/secondaryContainer",
            FbngPalette.onSecondaryContainer,
            FbngPalette.secondaryContainer,
            4.5,
        )
        assertRatio(
            "onTertiaryContainer/tertiaryContainer",
            FbngPalette.onTertiaryContainer,
            FbngPalette.tertiaryContainer,
            4.5,
        )
        assertRatio("onError/error", FbngPalette.onError, FbngPalette.error, 4.5)
        assertRatio(
            "onErrorContainer/errorContainer",
            FbngPalette.onErrorContainer,
            FbngPalette.errorContainer,
            4.5,
        )
        assertRatio(
            "inverseOnSurface/inverseSurface",
            FbngPalette.inverseOnSurface,
            FbngPalette.inverseSurface,
            4.5,
        )
        assertRatio(
            "inversePrimary/inverseSurface",
            FbngPalette.inversePrimary,
            FbngPalette.inverseSurface,
            4.5,
        )
        // Disabled FbngButton: opaque onSurfaceVariant on surfaceContainerHighest.
        assertRatio(
            "disabledLabel/disabledContainer",
            FbngPalette.onSurfaceVariant,
            FbngPalette.surfaceContainerHighest,
            4.5,
        )
    }

    @Test
    fun fixedAccentPairsClearAa() {
        assertRatio("onPrimaryFixed/primaryFixed", FbngPalette.onPrimaryFixed, FbngPalette.primaryFixed, 4.5)
        assertRatio(
            "onPrimaryFixed/primaryFixedDim",
            FbngPalette.onPrimaryFixed,
            FbngPalette.primaryFixedDim,
            4.5,
        )
        assertRatio(
            "onPrimaryFixedVariant/primaryFixed",
            FbngPalette.onPrimaryFixedVariant,
            FbngPalette.primaryFixed,
            4.5,
        )
        assertRatio(
            "onPrimaryFixedVariant/primaryFixedDim",
            FbngPalette.onPrimaryFixedVariant,
            FbngPalette.primaryFixedDim,
            4.5,
        )
        assertRatio(
            "onSecondaryFixed/secondaryFixed",
            FbngPalette.onSecondaryFixed,
            FbngPalette.secondaryFixed,
            4.5,
        )
        assertRatio(
            "onSecondaryFixed/secondaryFixedDim",
            FbngPalette.onSecondaryFixed,
            FbngPalette.secondaryFixedDim,
            4.5,
        )
        assertRatio(
            "onSecondaryFixedVariant/secondaryFixed",
            FbngPalette.onSecondaryFixedVariant,
            FbngPalette.secondaryFixed,
            4.5,
        )
        assertRatio(
            "onSecondaryFixedVariant/secondaryFixedDim",
            FbngPalette.onSecondaryFixedVariant,
            FbngPalette.secondaryFixedDim,
            4.5,
        )
        assertRatio(
            "onTertiaryFixed/tertiaryFixed",
            FbngPalette.onTertiaryFixed,
            FbngPalette.tertiaryFixed,
            4.5,
        )
        assertRatio(
            "onTertiaryFixed/tertiaryFixedDim",
            FbngPalette.onTertiaryFixed,
            FbngPalette.tertiaryFixedDim,
            4.5,
        )
        assertRatio(
            "onTertiaryFixedVariant/tertiaryFixed",
            FbngPalette.onTertiaryFixedVariant,
            FbngPalette.tertiaryFixed,
            4.5,
        )
        assertRatio(
            "onTertiaryFixedVariant/tertiaryFixedDim",
            FbngPalette.onTertiaryFixedVariant,
            FbngPalette.tertiaryFixedDim,
            4.5,
        )
    }

    @Test
    fun marksAndButtonsClearNonTextContrast() {
        assertRatio("primary/background", FbngPalette.primary, FbngPalette.background, 3.0)
        assertRatio("primary/surface", FbngPalette.primary, FbngPalette.surface, 3.0)
        // Linear progress: primary indicator on the secondaryContainer track.
        assertRatio(
            "primary/secondaryContainer",
            FbngPalette.primary,
            FbngPalette.secondaryContainer,
            3.0,
        )
        assertRatio("outline/background", FbngPalette.outline, FbngPalette.background, 3.0)
        assertRatio("outline/surface", FbngPalette.outline, FbngPalette.surface, 3.0)
    }
}

private fun assertRatio(name: String, foreground: Long, background: Long, minimum: Double) {
    val ratio = contrast(foreground, background)
    assertTrue("$name contrast is ${"%.2f".format(ratio)}:1, need $minimum:1", ratio >= minimum)
}

private fun contrast(first: Long, second: Long): Double {
    val lighter = max(luminance(first), luminance(second))
    val darker = min(luminance(first), luminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun luminance(argb: Long): Double {
    val rgb = argb.toInt()
    val red = channel((rgb shr 16) and 0xFF)
    val green = channel((rgb shr 8) and 0xFF)
    val blue = channel(rgb and 0xFF)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private fun channel(component: Int): Double {
    val s = component / 255.0
    return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}
