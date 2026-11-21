package com.xiaojiwei.fbng.ui.theme

import androidx.compose.ui.unit.Dp
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

    val compactMargin = space24
    val mediumMargin = space24
    val expandedMargin = space32

    val focusStroke = 2.dp
    val focusGap = 2.dp
    val panelInset = space8
    val glassBlur = 18.dp
    val rp6BarInset = 24.dp

    val buttonHeight = space48
    val shellHeaderHeight = 40.dp
    val shellHeaderTitleMinWidth = space48 + space32 + space8
    val shortShellHeight = space48 * 8
    val icon = space24
    val detailCoverWidth = space48 * 5
    val libraryHeaderWide = space48 * 12
    val libraryLetterFilterWidth = space48 + space32
    val libraryStatusFilterWidth = space48 + space32
    val libraryToolbarBreakpoint = space48 * 13
    val hudActionWidth = space48 + space24
    val hudActionsWidth = space48 * 12
    const val coverAspectRatio = 4f / 3f
    val saveCardWidth = space48 * 4
    val saveCardMinWidth = space48 * 2 + space24
    val saveDialogWidth = space48 * 24

    val libraryPickerWidth = space48 * 9
}

internal enum class CornerRadiusOption(val radius: Dp) {
    Smaller(8.dp),
    Default(12.dp),
    Larger(16.dp),
}
