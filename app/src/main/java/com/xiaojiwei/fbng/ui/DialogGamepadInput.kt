package com.xiaojiwei.fbng.ui

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal fun DialogGamepadInput() {
    val input = LocalGamepadInput.current ?: return
    val view = LocalView.current
    val window = generateSequence(view) { it.parent as? View }
        .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window ?: return
    DisposableEffect(input, window) {
        val original = checkNotNull(window.callback)
        val callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                input.dispatchKey(event, window, allowPauseShortcut = false, original::dispatchKeyEvent)

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
                input.dispatchMotion(event, window, original::dispatchKeyEvent) || original.dispatchGenericMotionEvent(event)
        }
        window.callback = callback
        onDispose { if (window.callback === callback) window.callback = original }
    }
}
