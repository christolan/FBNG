package com.xiaojiwei.fbng.ui


data class FoldSnapshot(
    val orientationVertical: Boolean,
    val separating: Boolean,
    val hingeStartPx: Int,
    val hingeEndPx: Int,
)

fun libraryUsesTwoPanes(widthDp: Int, fold: FoldSnapshot?): Boolean {
    if (fold != null && fold.separating && fold.orientationVertical) return true
    return widthDp >= SHELL_MEDIUM_DP
}

const val SHELL_MEDIUM_DP = 600
const val SHELL_EXPANDED_DP = 840

enum class ShellWidth {
    Compact,
    Medium,
    Expanded,
}

fun shellWidth(widthDp: Int): ShellWidth = when {
    widthDp < SHELL_MEDIUM_DP -> ShellWidth.Compact
    widthDp < SHELL_EXPANDED_DP -> ShellWidth.Medium
    else -> ShellWidth.Expanded
}

fun shellUsesNavigationRail(widthDp: Int, fold: FoldSnapshot?): Boolean =
    libraryUsesTwoPanes(widthDp, fold)
