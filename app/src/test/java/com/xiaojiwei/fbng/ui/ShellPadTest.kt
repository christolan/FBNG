package com.xiaojiwei.fbng.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShellPadTest {
    private val pad = ShellPad()

    @Test
    fun oneDeflectionStepsOnceUntilTheAxisRecenters() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, pad.step(0f, -1f))
        assertNull(pad.step(0f, -1f))
        assertNull(pad.step(0f, -0.2f))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, pad.step(0f, -0.8f))
    }

    @Test
    fun aReversalWithoutRecenteringDoesNotStep() {
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, pad.step(1f, 0f))
        assertNull(pad.step(-1f, 0f))
        assertNull(pad.step(0f, 0f))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, pad.step(-1f, 0f))
    }

    @Test
    fun aFreshDiagonalStepsOnTheStrongerAxisOnly() {
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, pad.step(0.2f, 0.9f))
        pad.reset()
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, pad.step(0.9f, 0.9f))
    }

    @Test
    fun valuesInsideTheDeadzoneDoNothing() {
        assertNull(pad.step(0.49f, -0.49f))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, pad.step(-0.5f, 0f))
    }
}
