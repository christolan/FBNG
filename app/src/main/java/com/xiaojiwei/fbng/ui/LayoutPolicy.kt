package com.xiaojiwei.fbng.ui

import kotlin.math.roundToInt

data class FoldSnapshot(
    val orientationVertical: Boolean,
    val separating: Boolean,
    val hingeStartPx: Int,
    val hingeEndPx: Int,
)

/**
 * Where the always-visible save/load/quit controls sit.
 * [Sides] and [Ends] draw them inside the pillarbox or letterbox bars.
 * [Overlay] is the thin-bar fallback: a compact control that does not resize
 * or offset the game.
 */
enum class HudBand {
    Sides,
    Ends,
    Overlay,
}

/** Which axis has the unused bars, even when those bars are too thin to use. */
enum class BarAxis {
    None,
    Sides,
    Ends,
}

data class GameHudLayout(
    val band: HudBand,
    val axis: BarAxis,
    /** Thickness of one natural bar, in px. The game rect is not reduced by this. */
    val barPx: Int,
    val gameLeftPx: Int,
    val gameTopPx: Int,
    val gameWidthPx: Int,
    val gameHeightPx: Int,
    /** Aspect actually fitted. [FALLBACK_GAME_ASPECT] when [reportedAspect] was unusable. */
    val aspectUsed: Float,
    val usedFallback: Boolean,
)

/** Last resort when LibretroDroid has not reported a usable AV aspect yet. */
const val FALLBACK_GAME_ASPECT = 4f / 3f

private const val MIN_GAME_ASPECT = 0.05f
private const val MAX_GAME_ASPECT = 20f

/**
 * Accept a core-reported width/height. Non-finite, non-positive, and extreme
 * values fall back to 4:3; ordinary FBNeo aspects (including vertical games)
 * pass through.
 */
fun usableGameAspect(reported: Float): Float {
    if (!reported.isFinite() || reported < MIN_GAME_ASPECT || reported > MAX_GAME_ASPECT) {
        return FALLBACK_GAME_ASPECT
    }
    return reported
}

/**
 * Largest centered rect of [gameAspect] (width/height) inside the play area.
 *
 * This matches LibretroDroid 0.13.2 `VideoLayout` with a full-bleed viewport:
 * the core's aspect decides the bars, and the game is not inset to make room
 * for controls. Rotation in that version spins the frame inside the same rect,
 * so the bars follow the aspect returned by `LibretroDroid::getAspectRatio()`.
 *
 * [minBarPx] is the thinnest bar that can hold a 48dp control plus padding.
 * The play screen passes 56dp. A thinner bar selects [HudBand.Overlay] and
 * leaves [gameWidthPx]/[gameHeightPx] unchanged.
 */
fun layoutGameHud(
    screenWidthPx: Int,
    screenHeightPx: Int,
    gameAspect: Float,
    minBarPx: Int,
): GameHudLayout {
    val aspect = usableGameAspect(gameAspect)
    val usedFallback = aspect != gameAspect
    if (screenWidthPx <= 0 || screenHeightPx <= 0) {
        return GameHudLayout(
            band = HudBand.Overlay,
            axis = BarAxis.None,
            barPx = 0,
            gameLeftPx = 0,
            gameTopPx = 0,
            gameWidthPx = 0,
            gameHeightPx = 0,
            aspectUsed = aspect,
            usedFallback = usedFallback,
        )
    }
    val screenAspect = screenWidthPx.toFloat() / screenHeightPx.toFloat()
    val gameWidth: Int
    val gameHeight: Int
    if (aspect >= screenAspect) {
        gameWidth = screenWidthPx
        gameHeight = (screenWidthPx.toFloat() / aspect).roundToInt().coerceIn(1, screenHeightPx)
    } else {
        gameHeight = screenHeightPx
        gameWidth = (screenHeightPx.toFloat() * aspect).roundToInt().coerceIn(1, screenWidthPx)
    }
    val left = (screenWidthPx - gameWidth) / 2
    val top = (screenHeightPx - gameHeight) / 2
    val right = screenWidthPx - gameWidth - left
    val bottom = screenHeightPx - gameHeight - top
    val sideBar = minOf(left, right)
    val endBar = minOf(top, bottom)
    val axis = when {
        sideBar == 0 && endBar == 0 -> BarAxis.None
        sideBar >= endBar -> BarAxis.Sides
        else -> BarAxis.Ends
    }
    val natural = when (axis) {
        BarAxis.Sides -> sideBar
        BarAxis.Ends -> endBar
        BarAxis.None -> 0
    }
    val comfortable = minBarPx.coerceAtLeast(0)
    val band = when {
        axis == BarAxis.Sides && natural >= comfortable -> HudBand.Sides
        axis == BarAxis.Ends && natural >= comfortable -> HudBand.Ends
        else -> HudBand.Overlay
    }
    return GameHudLayout(
        band = band,
        axis = axis,
        barPx = natural,
        gameLeftPx = left,
        gameTopPx = top,
        gameWidthPx = gameWidth,
        gameHeightPx = gameHeight,
        aspectUsed = aspect,
        usedFallback = usedFallback,
    )
}

fun libraryUsesTwoPanes(widthDp: Int, fold: FoldSnapshot?): Boolean {
    if (fold != null && fold.separating && fold.orientationVertical) return true
    return widthDp >= 600
}
