package com.xiaojiwei.fbng

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.ui.FbngApp

class MainActivity : ComponentActivity() {
    val gamepadBridge = GamepadBridge()

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
        if (gamepadBridge.dispatchMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
