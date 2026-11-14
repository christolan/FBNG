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

    val buttonHeight = space48
    val shellHeaderHeight = buttonHeight + space8
    val shortShellHeight = space48 * 8
    val icon = space24
    val detailCoverWidth = space48 * 5
    val libraryHeaderWide = space48 * 12
    val libraryHeaderNarrow = space48 * 7
    val libraryHeaderNarrowButton = space32 + space8
    val libraryLetterFilterWidth = space48 * 3
    val libraryStatusFilterWidth = space48 * 2 + space16
    val libraryToolbarBreakpoint = space48 * 13
    val railItemHeight = space48 + space16
    val hudActionWidth = space48 + space24
    val hudActionsWidth = space48 * 12
    const val coverAspectRatio = 4f / 3f
    val saveCardWidth = space48 * 4
    val saveCardMinWidth = space48 * 2 + space24
    val saveDialogWidth = space48 * 24

    /** Compact icon-over-label navigation leaves the width for game pages. */
    val railWidthMedium = space48 * 2

    val railWidthExpanded = space48 * 2

    val libraryPickerWidth = space48 * 9
    val libraryKeyboardWidth = space48 * 18
}

internal val FbngShapes = Shapes(
    extraSmall = RoundedCornerShape(FbngDimens.controlRadius),
    small = RoundedCornerShape(FbngDimens.controlRadius),
    medium = RoundedCornerShape(FbngDimens.cardRadius),
    large = RoundedCornerShape(FbngDimens.panelRadius),
    extraLarge = RoundedCornerShape(FbngDimens.panelRadius),
)
