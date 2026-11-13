package com.xiaojiwei.fbng.game

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.abs

/**
 * Forwards controller events to LibretroDroid. The view already swaps Android A/B and X/Y
 * onto the RetroPad layout and assigns ports from InputDevice.controllerNumber.
 * This phase does not remap buttons.
 */
class GamepadBridge {
    var onPauseToggle: (() -> Unit)? = null
    private val heldKeys = mutableMapOf<Pair<Int, Int>, KeyEvent>()
    private val pendingReleases = mutableSetOf<Pair<Int, Int>>()
    private val activePorts = mutableSetOf<Int>()
    private val deflectedDevices = mutableSetOf<Int>()
    private val pendingMotionCenters = mutableSetOf<Int>()
    var target: GLRetroView? = null
        set(value) {
            if (field === value) return
            field?.let { previous ->
                pendingReleases += heldKeys.keys
                pendingMotionCenters += deflectedDevices
                heldKeys.values.forEach { previous.onKeyUp(it.keyCode, it) }
                activePorts.forEach { port ->
                    previous.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, port)
                    previous.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, port)
                    previous.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, port)
                }
            }
            heldKeys.clear()
            activePorts.clear()
            deflectedDevices.clear()
            field = value
        }

    fun dispatchKey(event: KeyEvent): Boolean {
        if (onPauseToggle != null && event.keyCode in setOf(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU)) {
            if (event.action == KeyEvent.ACTION_UP) onPauseToggle?.invoke()
            return true
        }
        val key = event.deviceId to event.keyCode
        if (key in pendingReleases) {
            if (event.action == KeyEvent.ACTION_UP) pendingReleases.remove(key)
            return true
        }
        if (target == null || event.keyCode !in GAMEPAD_KEYS) return false
        if (event.repeatCount > 0) return true
        val view = target ?: return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> view.onKeyDown(event.keyCode, event).also { handled ->
                if (handled) heldKeys[event.deviceId to event.keyCode] = KeyEvent(event)
            }
            KeyEvent.ACTION_UP -> view.onKeyUp(event.keyCode, event).also {
                heldKeys.remove(event.deviceId to event.keyCode)
            }
            else -> false
        }
    }

    fun dispatchMotion(event: MotionEvent): Boolean {
        val fromController = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!fromController) return false
        val centered = MOTION_AXES.all { abs(event.getAxisValue(it)) < 0.25f }
        if (event.deviceId in pendingMotionCenters) {
            if (centered) pendingMotionCenters.remove(event.deviceId)
            return true
        }
        val view = target ?: return false
        if (centered) deflectedDevices.remove(event.deviceId) else deflectedDevices.add(event.deviceId)
        val port = (event.device?.controllerNumber ?: 0) - 1
        if (port >= 0) activePorts += port
        view.onGenericMotionEvent(event)
        return true
    }

    companion object {
        private val MOTION_AXES = intArrayOf(
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        )
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
