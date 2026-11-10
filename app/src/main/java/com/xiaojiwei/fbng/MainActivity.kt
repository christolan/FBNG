package com.xiaojiwei.fbng

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.ui.FbngApp

class MainActivity : ComponentActivity() {
    val gamepadBridge = GamepadBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
