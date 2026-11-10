package com.xiaojiwei.fbng.ui

data class FoldSnapshot(
    val orientationVertical: Boolean,
    val separating: Boolean,
    val hingeStartPx: Int,
    val hingeEndPx: Int,
)

enum class GameChrome {
    BottomBar,
    SideBar,
    SplitVertical,
    SplitHorizontal,
}

fun chooseGameChrome(
    widthDp: Int,
    heightDp: Int,
    widthPx: Int,
    heightPx: Int,
    fold: FoldSnapshot?,
): GameChrome {
    if (fold != null && fold.separating && fold.hingeEndPx > fold.hingeStartPx) {
        val verticalHinge = fold.orientationVertical &&
            fold.hingeStartPx > widthPx / 10 &&
            fold.hingeEndPx < widthPx - widthPx / 10
        if (verticalHinge) return GameChrome.SplitVertical
        val horizontalHinge = !fold.orientationVertical &&
            fold.hingeStartPx > heightPx / 10 &&
            fold.hingeEndPx < heightPx - heightPx / 10
        if (horizontalHinge) return GameChrome.SplitHorizontal
    }
    return if (widthDp >= 600 && widthDp >= heightDp) GameChrome.SideBar else GameChrome.BottomBar
}

fun libraryUsesTwoPanes(widthDp: Int, fold: FoldSnapshot?): Boolean {
    if (fold != null && fold.separating && fold.orientationVertical) return true
    return widthDp >= 600
}
