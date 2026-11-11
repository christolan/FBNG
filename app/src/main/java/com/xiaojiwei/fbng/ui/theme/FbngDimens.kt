package com.xiaojiwei.fbng.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing, radii, and focus metrics. New layout code reads these instead of raw dp. */
internal object FbngDimens {
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space48 = 48.dp

    val compactMargin = space16
    val mediumMargin = space24
    val expandedMargin = space32

    val controlRadius = 8.dp
    val cardRadius = 12.dp
    val panelRadius = 24.dp

    val focusStroke = 2.dp
    const val focusScale = 1.05f
    const val focusScaleLarge = 1.025f

    val buttonHeight = space48
    val icon = space24
    val padKey = space32

    /** 5 × 32dp. Wide enough for a 16sp Chinese nav label plus the icon. */
    val railWidthMedium = 160.dp

    /** 4 × 48dp. */
    val railWidthExpanded = 192.dp

    /** 5 × 32dp. One grid cell; the column count is the width divided by this. */
    val cardMin = 160.dp

    /** 10 × 48dp. Below this, the banner collapses to one row so the grid stays on screen. */
    val shortWindow = 480.dp
}

internal val FbngShapes = Shapes(
    extraSmall = RoundedCornerShape(FbngDimens.controlRadius),
    small = RoundedCornerShape(FbngDimens.controlRadius),
    medium = RoundedCornerShape(FbngDimens.cardRadius),
    large = RoundedCornerShape(FbngDimens.panelRadius),
    extraLarge = RoundedCornerShape(FbngDimens.panelRadius),
)
