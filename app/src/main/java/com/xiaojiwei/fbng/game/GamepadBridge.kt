package com.xiaojiwei.fbng.game

import android.view.KeyEvent
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView

class GamepadBridge {
    var onPauseToggle: (() -> Unit)? = null
    var onPrimaryDisconnected: (() -> Unit)? = null
    var generation = 0
        private set

    private val heldKeys = mutableMapOf<Pair<Int, Int>, Int>()
    private val motionPorts = mutableMapOf<Int, Int>()
    var target: GLRetroView? = null
        set(value) {
            if (field === value) return
            releaseInputs()
            field = value
            generation++
        }

    fun releaseInputs(deviceId: Int? = null) {
        val view = target ?: return
        val released = heldKeys.keys.filter { deviceId == null || it.first == deviceId }
        for (key in released) {
            val port = heldKeys.remove(key) ?: continue
            val mapped = retroPadKeyCode(key.second)
            view.sendKeyEvent(KeyEvent.ACTION_UP, mapped, port)
        }
        val devices = motionPorts.keys.filter { deviceId == null || it == deviceId }
        for (device in devices) {
            val port = motionPorts.remove(device) ?: continue
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, port)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, port)
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, port)
        }
    }

    fun dispatchKey(event: KeyEvent): Boolean {
        val view = target ?: return false
        if (event.keyCode !in GAMEPAD_KEYS) return false
        val port = (event.device?.controllerNumber ?: 0) - 1
        if (port < 0) return false
        if (event.repeatCount > 0) return true
        val key = event.deviceId to event.keyCode
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> view.onKeyDown(event.keyCode, event).also { handled ->
                if (handled) heldKeys[key] = port
            }
            KeyEvent.ACTION_UP -> view.onKeyUp(event.keyCode, event).also { heldKeys.remove(key) }
            else -> false
        }
    }

    fun dispatchMotion(event: MotionEvent): Boolean {
        val view = target ?: return false
        val port = (event.device?.controllerNumber ?: 0) - 1
        if (port >= 0) motionPorts[event.deviceId] = port
        view.onGenericMotionEvent(event)
        return true
    }

    companion object {
        private val GAMEPAD_KEYS = setOf(
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

internal fun retroPadKeyCode(code: Int): Int = when (code) {
    KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
    KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
    KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
    KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
    else -> code
}
