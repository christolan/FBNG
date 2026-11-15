package com.xiaojiwei.fbng.ui

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.xiaojiwei.fbng.game.GamepadBridge
import kotlin.math.abs

internal val LocalGamepadInput = staticCompositionLocalOf<GamepadInput?> { null }

internal class GamepadInput(context: Context, private val bridge: GamepadBridge) : InputManager.InputDeviceListener {
    private val preferences = context.getSharedPreferences("gamepad", Context.MODE_PRIVATE)
    private val manager = context.getSystemService(InputManager::class.java)
    private val ports = mutableMapOf<Int, Int>()
    private val directions = mutableMapOf<Int, Directions>()
    private val keys = mutableMapOf<Pair<Int, Int>, Press>()
    private val axes = mutableMapOf<Int, Route>()
    private var shellGeneration = 0

    var swapped by mutableStateOf(preferences.getBoolean("swap-confirm", false))
        private set
    var pauseKey by mutableStateOf(preferences.getInt("pause-key", KeyEvent.KEYCODE_UNKNOWN))
        private set
    var onInspect: ((KeyEvent) -> Boolean)? = null
    var onInspectDirection: ((Int, InputDevice?) -> Unit)? = null

    fun start() {
        manager.registerInputDeviceListener(this, null)
        manager.inputDeviceIds.forEach(::onInputDeviceAdded)
    }

    fun stop() {
        manager.unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val port = manager.getInputDevice(deviceId)?.controllerNumber ?: return
        if (port > 0) ports[deviceId] = port else ports.remove(deviceId)
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        val previous = ports[deviceId]
        onInputDeviceAdded(deviceId)
        if (previous != null && previous != ports[deviceId]) clearDeviceInput(deviceId, previous)
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        clearDeviceInput(deviceId, ports.remove(deviceId))
    }

    private fun clearDeviceInput(deviceId: Int, port: Int?) {
        bridge.releaseInputs(deviceId)
        keys.keys.removeAll { it.first == deviceId }
        axes.remove(deviceId)
        directions.remove(deviceId)
        if (port == 1) bridge.onPrimaryDisconnected?.invoke()
    }

    fun changeConfirmLayout(value: Boolean) {
        swapped = value
        shellSurfaceChanged()
        preferences.edit().putBoolean("swap-confirm", value).apply()
    }

    fun bindPauseKey(keyCode: Int) {
        pauseKey = keyCode
        preferences.edit().putInt("pause-key", keyCode).apply()
    }

    fun resetSettings() {
        swapped = false
        pauseKey = KeyEvent.KEYCODE_UNKNOWN
        shellSurfaceChanged()
        preferences.edit().clear().apply()
    }

    fun shellSurfaceChanged() {
        shellGeneration++
        directions.clear()
    }

    private fun route(window: Any) = Route(window, bridge.generation, shellGeneration)

    fun dispatchKey(
        event: KeyEvent,
        window: Any,
        allowPauseShortcut: Boolean,
        deliver: (KeyEvent) -> Boolean,
    ): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK || (!event.isController() && code != KeyEvent.KEYCODE_MENU)) {
            return deliver(event)
        }
        val current = route(window)
        val key = event.deviceId to code
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0 || keys[key]?.downTime == event.downTime) return true
                keys[key] = Press(current, event.downTime)
            }
            KeyEvent.ACTION_UP -> {
                val press = keys[key] ?: return true
                if (press.downTime != event.downTime) return true
                keys.remove(key)
                if (press.route != current) return true
                if (event.isCanceled) {
                    bridge.dispatchKey(event)
                    return true
                }
            }
            else -> return deliver(event)
        }
        if (onInspect?.invoke(event) == true) return true
        val toggle = bridge.onPauseToggle
        if (allowPauseShortcut && toggle != null && matchesPauseKey(code, pauseKey)) {
            if (event.action == KeyEvent.ACTION_UP) toggle()
            return true
        }
        if (bridge.target != null) return bridge.dispatchKey(event) || deliver(event)

        if (code in CardinalKeys) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                move(event.deviceId, event.eventTime, code, fromHat = false, deliver)
            }
            return true
        }
        val mapped = menuKeyCode(code, swapped)
        val handled = deliver(if (mapped == code) event else event.withKeyCode(mapped))
        // The local action runs on key-up. Owning both edges also suppresses Android's fallback action.
        return handled || mapped == KeyEvent.KEYCODE_BUTTON_A || mapped == KeyEvent.KEYCODE_BUTTON_B
    }

    fun dispatchMotion(event: MotionEvent, window: Any, deliverKey: (KeyEvent) -> Boolean): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_MOVE || !event.isController()) return false
        val device = event.deviceId
        val current = route(window)
        val previous = axes[device]
        if (isCentered(event)) {
            axes.remove(device)
        } else if (previous == null) {
            axes[device] = current
        }
        if (previous != null && previous != current) {
            directions.remove(device)
            return true
        }
        if (bridge.dispatchMotion(event)) return true
        val direction = directions.getOrPut(device, ::Directions)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val hat = direction.hat.step(hatX, hatY)
        val stick = direction.stick.step(
            event.getAxisValue(MotionEvent.AXIS_X),
            event.getAxisValue(MotionEvent.AXIS_Y),
        )
        val inspectDirection = onInspectDirection
        if (inspectDirection != null) {
            (hat ?: stick)?.let { inspectDirection(it, event.device) }
            return true
        }
        if (hat != null) move(device, event.eventTime, hat, fromHat = true, deliverKey)
        if (stick != null && hatX == 0f && hatY == 0f) sendStep(event.eventTime, stick, deliverKey)
        return true
    }

    private fun move(device: Int, time: Long, code: Int, fromHat: Boolean, deliver: (KeyEvent) -> Boolean) {
        val directions = directions.getOrPut(device, ::Directions)
        if (directions.dpad.accept(code, time, fromHat)) sendStep(time, code, deliver)
    }

    private fun sendStep(time: Long, code: Int, deliver: (KeyEvent) -> Boolean) {
        deliver(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        deliver(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
    }

    private class Directions {
        val hat = ShellPad()
        val stick = ShellPad()
        val dpad = DpadInput()
    }

    private data class Route(val window: Any, val coreGeneration: Int, val shellGeneration: Int)
    private data class Press(val route: Route, val downTime: Long)
}

internal class DpadInput {
    private data class Step(val time: Long, val fromHat: Boolean)
    private val recent = mutableMapOf<Int, Step>()

    fun accept(code: Int, time: Long, fromHat: Boolean): Boolean {
        val previous = recent[code]
        if (previous != null && previous.fromHat != fromHat && time - previous.time in 0L..48L) return false
        recent[code] = Step(time, fromHat)
        return true
    }
}

internal fun canBindPauseKey(code: Int) = code == KeyEvent.KEYCODE_MENU ||
    code == KeyEvent.KEYCODE_BUTTON_MODE || code == KeyEvent.KEYCODE_BUTTON_THUMBL ||
    code == KeyEvent.KEYCODE_BUTTON_THUMBR || code in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16

internal fun matchesPauseKey(code: Int, configured: Int) = if (configured == KeyEvent.KEYCODE_UNKNOWN) {
    code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BUTTON_MODE
} else code == configured

internal fun menuKeyCode(code: Int, swapped: Boolean): Int = when {
    !swapped -> code
    code == KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
    code == KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
    else -> code
}

private val CardinalKeys = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
)

private val StickAxes = intArrayOf(
    MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
    MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
)

private fun isCentered(event: MotionEvent): Boolean = StickAxes.all { abs(event.getAxisValue(it)) < .25f }

private fun KeyEvent.withKeyCode(code: Int) =
    KeyEvent(downTime, eventTime, action, code, repeatCount, metaState, deviceId, scanCode, flags, source)

private fun KeyEvent.isController() = KeyEvent.isGamepadButton(keyCode) ||
    isFromSource(InputDevice.SOURCE_DPAD) || isFromSource(InputDevice.SOURCE_GAMEPAD) ||
    isFromSource(InputDevice.SOURCE_JOYSTICK)

private fun MotionEvent.isController() =
    isFromSource(InputDevice.SOURCE_DPAD) || isFromSource(InputDevice.SOURCE_GAMEPAD) ||
    isFromSource(InputDevice.SOURCE_JOYSTICK)
