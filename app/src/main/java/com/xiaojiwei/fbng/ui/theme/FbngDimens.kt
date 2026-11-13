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
    val favoriteCardWidth = space48 * 5
    val shelfCoverWidth = space48 * 2
    val favoriteCardHeight = space48 * 2
    val libraryCellWidth = space48 * 5
    val libraryRowHeight = space48 + space24
    val libraryToolsWidth = space48 * 5
    val libraryToolbarBreakpoint = space48 * 13
    val railItemHeight = space48 + space16
    val hudActionWidth = space48 + space24
    val hudActionsWidth = space48 * 12
    const val coverAspectRatio = 4f / 3f
    val saveCardWidth = space48 * 4
    val saveDialogWidth = space48 * 24

    /** Compact icon-over-label navigation leaves the width for game pages. */
    val railWidthMedium = space48 * 2

    val railWidthExpanded = space48 * 2

    /** Enough for seven letter keys at their normal size with gaps and panel padding. */
    val libraryPickerWidth = space48 * 9

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
