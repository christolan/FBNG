package com.xiaojiwei.fbng

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.ui.FbngApp
import com.xiaojiwei.fbng.ui.GamepadInput
import com.xiaojiwei.fbng.ui.LocalGamepadInput

class MainActivity : ComponentActivity() {
    val gamepadBridge = GamepadBridge()
    private val gamepadInput by lazy { GamepadInput(this, gamepadBridge) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gamepadInput.start()
        // The UI is always dark. auto() follows the system night mode and draws
        // dark status icons on the ink background when the phone is in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            CompositionLocalProvider(LocalGamepadInput provides gamepadInput) {
                FbngApp(gamepadBridge)
            }
        }
    }

    // Window.Callback's public hook is required to route controller input before Compose.
    // Lint inherits the restriction on androidx.core.app.ComponentActivity, an internal
    // ancestor of the public androidx.activity.ComponentActivity we extend.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        gamepadInput.dispatchKey(event, window, allowPauseShortcut = true, ::deliverKey)

    @SuppressLint("RestrictedApi")
    private fun deliverKey(event: KeyEvent) = super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        gamepadInput.dispatchMotion(event, window, ::deliverKey) || super.dispatchGenericMotionEvent(event)

    override fun onPause() {
        gamepadInput.shellSurfaceChanged()
        super.onPause()
    }

    override fun onDestroy() {
        gamepadInput.stop()
        super.onDestroy()
    }
}
