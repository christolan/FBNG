package com.xiaojiwei.fbng.ui

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.xiaojiwei.fbng.game.GamepadBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.shadows.ShadowInputManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GamepadInputTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bridge = GamepadBridge()
    private val input = GamepadInput(context, bridge)
    private val shell = Any()
    private val dialog = Any()
    private val delivered = mutableListOf<Pair<Int, Int>>()

    @Before
    fun resetSettings() {
        input.resetSettings()
    }

    @Test
    fun heldConfirmCannotActivateAnotherWindowOrSurface() {
        sendKey(100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, shell)
        sendKey(100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, dialog)
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_A), delivered)
        delivered.clear()

        sendKey(200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, shell)
        input.shellSurfaceChanged()
        sendKey(200, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, shell)
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_A), delivered)
        delivered.clear()

        sendKey(300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, shell)
        sendKey(300, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, shell)
        assertEquals(step(KeyEvent.KEYCODE_BUTTON_A), delivered)
    }

    @Test
    fun directionUsesOneEventForDpadAndHatAndWaitsForNeutralAcrossWindows() {
        sendKey(100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, shell)
        sendAxes(110, shell, hatX = 1f)
        sendAxes(180, dialog, hatX = 1f)
        sendAxes(190, dialog)
        sendAxes(200, dialog, stickY = 1f)
        sendAxes(300, dialog, stickY = 1f)
        assertEquals(step(KeyEvent.KEYCODE_DPAD_RIGHT) + step(KeyEvent.KEYCODE_DPAD_DOWN), delivered)
    }

    @Test
    fun eachDeviceTracksItsOwnDeflection() {
        sendAxes(100, shell, stickY = -1f, device = 9)
        sendAxes(110, shell, stickY = -1f, device = 10)
        sendAxes(120, shell, stickY = -1f, device = 9)
        assertEquals(step(KeyEvent.KEYCODE_DPAD_UP) + step(KeyEvent.KEYCODE_DPAD_UP), delivered)
    }

    @Test
    fun onlyFreshConfiguredPausePressTogglesInActivity() {
        var toggles = 0
        bridge.onPauseToggle = { toggles++ }
        assertTrue(canBindPauseKey(KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertFalse(canBindPauseKey(KeyEvent.KEYCODE_BUTTON_START))
        input.bindPauseKey(KeyEvent.KEYCODE_BUTTON_THUMBR)
        sendKey(100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_MODE, shell)
        sendKey(100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_MODE, shell)
        sendKey(200, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBR, dialog)
        sendKey(200, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBR, dialog)
        sendKey(300, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBR, shell)
        assertEquals(0, toggles)
        sendKey(400, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_THUMBR, shell)
        sendKey(400, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_THUMBR, shell)
        assertEquals(1, toggles)
    }

    @Test
    fun menuLayoutPersistsAndMapsBothEdges() {
        input.changeConfirmLayout(true)
        assertTrue(GamepadInput(context, bridge).swapped)
        sendKey(100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B, dialog)
        sendKey(100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B, dialog)
        assertEquals(step(KeyEvent.KEYCODE_BUTTON_A), delivered)
        input.resetSettings()
        assertFalse(GamepadInput(context, bridge).swapped)
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, GamepadInput(context, bridge).pauseKey)
    }

    @Test
    fun menuButtonsSuppressPlatformFallbackEvenWhenTheViewIgnoresAnEdge() {
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)) {
            val down = KeyEvent(100, 100, KeyEvent.ACTION_DOWN, code, 0)
            val up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP)
            assertTrue(input.dispatchKey(down, shell, allowPauseShortcut = true) { false })
            assertTrue(input.dispatchKey(up, shell, allowPauseShortcut = true) { false })
        }
    }

    @Test
    @Suppress("DEPRECATION") // Robolectric's newer builder requires InputDevice.Builder, absent on API 33.
    fun removingThePrimaryControllerDropsItsHeldInputAndNotifiesTheSession() {
        val manager = Shadow.extract<ShadowInputManager>(context.getSystemService(InputManager::class.java))
        val primary = ShadowInputDevice.makeInputDeviceNamed("Primary controller")
        ReflectionHelpers.setField(primary, "mId", 9)
        ReflectionHelpers.setField(primary, "mControllerNumber", 1)
        manager.addInputDevice(primary)
        input.onInputDeviceAdded(primary.id)
        var disconnects = 0
        bridge.onPrimaryDisconnected = { disconnects++ }

        sendKey(100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, shell)
        manager.removeInputDevice(primary.id)
        input.onInputDeviceRemoved(primary.id)
        input.onInputDeviceRemoved(primary.id)
        sendKey(100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A, shell)

        assertEquals(1, disconnects)
        assertEquals(listOf(KeyEvent.ACTION_DOWN to KeyEvent.KEYCODE_BUTTON_A), delivered)
    }

    private fun sendKey(time: Long, action: Int, code: Int, window: Any) {
        val event = KeyEvent(time, time, action, code, 0, 0, 9, 0, 0, InputDevice.SOURCE_GAMEPAD)
        input.dispatchKey(event, window, window === shell, ::record)
    }

    private fun sendAxes(time: Long, window: Any, hatX: Float = 0f, stickY: Float = 0f, device: Int = 9) {
        val motion = controllerMotion(time, hatX, stickY, device)
        try {
            assertTrue(input.dispatchMotion(motion, window, ::record))
        } finally {
            motion.recycle()
        }
    }

    private fun record(event: KeyEvent): Boolean {
        delivered += event.action to event.keyCode
        return true
    }

    private fun step(code: Int) = listOf(KeyEvent.ACTION_DOWN to code, KeyEvent.ACTION_UP to code)
}

internal fun controllerMotion(time: Long, hatX: Float = 0f, stickY: Float = 0f, device: Int = 9): MotionEvent {
    val pointer = MotionEvent.PointerProperties().apply { id = 0 }
    val coords = MotionEvent.PointerCoords().apply {
        setAxisValue(MotionEvent.AXIS_HAT_X, hatX)
        setAxisValue(MotionEvent.AXIS_Y, stickY)
    }
    return MotionEvent.obtain(
        time, time, MotionEvent.ACTION_MOVE, 1, arrayOf(pointer), arrayOf(coords),
        0, 0, 1f, 1f, device, 0, InputDevice.SOURCE_JOYSTICK, 0,
    )
}
