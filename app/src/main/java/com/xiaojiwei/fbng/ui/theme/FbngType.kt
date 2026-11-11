package com.xiaojiwei.fbng.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Sans = FontFamily.SansSerif

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = Sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

/**
 * One sans family. Body and labels stay at 16sp so a gamepad-distance read
 * does not depend on Material's 12sp defaults. Titles used as the focused
 * name are at least 20sp.
 */
internal val FbngTypography = Typography(
    displaySmall = style(36, 44, FontWeight.Bold),
    headlineMedium = style(28, 36, FontWeight.Bold),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(20, 28, FontWeight.Medium),
    titleSmall = style(16, 24, FontWeight.Medium),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(16, 24, FontWeight.Normal),
    bodySmall = style(16, 22, FontWeight.Normal),
    labelLarge = style(16, 24, FontWeight.Medium),
    labelMedium = style(16, 20, FontWeight.Medium),
    labelSmall = style(16, 20, FontWeight.Medium),
)
