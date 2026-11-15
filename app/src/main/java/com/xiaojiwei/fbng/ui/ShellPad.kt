package com.xiaojiwei.fbng.ui

import android.view.KeyEvent
import kotlin.math.abs

/** One direction per deflection, with a smaller threshold for returning to neutral. */
internal class ShellPad {
    private var held = false

    fun step(x: Float, y: Float): Int? {
        val horizontal = abs(x)
        val vertical = abs(y)
        val distance = maxOf(horizontal, vertical)
        if (held) {
            if (distance < .25f) held = false
            return null
        }
        if (distance < .5f) return null
        held = true
        return if (horizontal >= vertical) {
            if (x < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            if (y < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        }
    }
}
