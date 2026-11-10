package com.xiaojiwei.fbng.game

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView

/**
 * Forwards controller events to LibretroDroid. The view already swaps Android A/B and X/Y
 * onto the RetroPad layout and assigns ports from InputDevice.controllerNumber.
 * This phase does not remap buttons.
 */
class GamepadBridge {
    var target: GLRetroView? = null

    fun dispatchKey(event: KeyEvent): Boolean {
        if (target == null || event.keyCode !in GAMEPAD_KEYS) return false
        if (event.repeatCount > 0) return true
        val view = target ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> view.onKeyDown(event.keyCode, event)
            KeyEvent.ACTION_UP -> view.onKeyUp(event.keyCode, event)
            else -> false
        }
    }

    fun dispatchMotion(event: MotionEvent): Boolean {
        val view = target ?: return false
        val fromController = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!fromController) return false
        view.onGenericMotionEvent(event)
        return true
    }

    companion object {
        val GAMEPAD_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            KeyEvent.KEYCODE_DPAD_UP_RIGHT,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
        )
    }
}
