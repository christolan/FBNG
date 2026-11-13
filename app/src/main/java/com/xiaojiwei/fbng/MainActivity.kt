package com.xiaojiwei.fbng

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.ui.FbngApp
import com.xiaojiwei.fbng.ui.ShellPad

class MainActivity : ComponentActivity() {
    val gamepadBridge = GamepadBridge()
    private val shellPad = ShellPad()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always dark. auto() follows the system night mode and draws
        // dark status icons on the ink background when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            FbngApp(gamepadBridge)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepadBridge.dispatchKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadBridge.target != null) {
            // The core owns the stick. Drop any latched shell step so leaving
            // the game does not immediately move focus.
            shellPad.reset()
        }
        if (gamepadBridge.dispatchMotion(event)) return true
        val fromPad = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_DPAD)
        if (!fromPad) return super.dispatchGenericMotionEvent(event)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x: Float
        val y: Float
        if (hatX != 0f || hatY != 0f) {
            x = hatX
            y = hatY
        } else {
            x = event.getAxisValue(MotionEvent.AXIS_X)
            y = event.getAxisValue(MotionEvent.AXIS_Y)
        }
        val key = shellPad.step(x, y)
        if (key != null) {
            val now = SystemClock.uptimeMillis()
            dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
            dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
        }
        return true
    }
}
