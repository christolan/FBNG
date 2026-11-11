package com.xiaojiwei.fbng.ui

import android.view.KeyEvent
import kotlin.math.abs

/**
 * Turns a hat or the left stick into a single D-pad step.
 * The axis has to come back inside the deadzone before it can step again.
 * The activity only feeds this while the core is not running.
 */
internal class ShellPad {
    private var heldX = 0
    private var heldY = 0

    fun step(x: Float, y: Float): Int? {
        val nextX = digit(x)
        val nextY = digit(y)
        val fireX = nextX != 0 && heldX == 0
        val fireY = nextY != 0 && heldY == 0
        val key = when {
            fireX && fireY -> if (abs(x) >= abs(y)) horizontal(nextX) else vertical(nextY)
            fireX -> horizontal(nextX)
            fireY -> vertical(nextY)
            else -> null
        }
        heldX = nextX
        heldY = nextY
        return key
    }

    fun reset() {
        heldX = 0
        heldY = 0
    }

    private fun digit(value: Float): Int = when {
        value >= THRESHOLD -> 1
        value <= -THRESHOLD -> -1
        else -> 0
    }

    private fun horizontal(sign: Int): Int =
        if (sign < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT

    private fun vertical(sign: Int): Int =
        if (sign < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN

    companion object {
        const val THRESHOLD = 0.5f
    }
}
