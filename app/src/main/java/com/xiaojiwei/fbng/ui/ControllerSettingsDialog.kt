package com.xiaojiwei.fbng.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens

private enum class CaptureMode { Test, Pause }

@Composable
internal fun ControllerSettingsDialog(input: GamepadInput, onDismiss: () -> Unit) {
    var capture by remember { mutableStateOf<CaptureMode?>(null) }
    var lastKey by remember { mutableStateOf<Int?>(null) }
    var lastDevice by remember { mutableStateOf<String?>(null) }
    val initial = remember { FocusRequester() }
    fun back() {
        if (capture == null) {
            onDismiss()
        } else {
            input.shellSurfaceChanged()
            capture = null
        }
    }
    fun startCapture(mode: CaptureMode) {
        input.shellSurfaceChanged()
        lastKey = null
        lastDevice = null
        capture = mode
    }

    DisposableEffect(input, capture) {
        val mode = capture
        if (mode != null) {
            input.onInspect = { event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    lastKey = event.keyCode
                    lastDevice = event.device?.name
                } else if (event.action == KeyEvent.ACTION_UP) {
                    when {
                        menuKeyCode(event.keyCode, input.swapped) == KeyEvent.KEYCODE_BUTTON_B -> back()
                        mode == CaptureMode.Pause && canBindPauseKey(event.keyCode) -> {
                            input.bindPauseKey(event.keyCode)
                            back()
                        }
                    }
                }
                true
            }
            input.onInspectDirection = { code, device ->
                lastKey = code
                lastDevice = device?.name
            }
        }
        onDispose {
            input.onInspect = null
            input.onInspectDirection = null
        }
    }

    Dialog(
        onDismissRequest = ::back,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogGamepadInput()
        LaunchedEffect(initial) { initial.requestFocus() }
        Surface(
            modifier = Modifier.padding(FbngDimens.space16).widthIn(max = FbngDimens.libraryPickerWidth)
                .fillMaxWidth()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonB) {
                        back()
                        true
                    } else false
                },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(FbngDimens.space16),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            ) {
                Text(stringResource(R.string.gamepad_settings), style = MaterialTheme.typography.titleLarge)
                FbngButton(
                    onClick = { input.changeConfirmLayout(!input.swapped) },
                    modifier = Modifier.fillMaxWidth().focusRequester(initial),
                    kind = FbngButtonKind.Neutral,
                ) { Text(stringResource(if (input.swapped) R.string.gamepad_swap_on else R.string.gamepad_swap_off)) }
                FbngButton(
                    onClick = { if (capture != null) back() else startCapture(CaptureMode.Test) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = FbngButtonKind.Neutral,
                ) {
                    Text(stringResource(if (capture == CaptureMode.Test) R.string.gamepad_end_test else R.string.gamepad_test))
                }
                FbngButton(
                    onClick = { if (capture != null) back() else startCapture(CaptureMode.Pause) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = FbngButtonKind.Neutral,
                ) {
                    Text(stringResource(
                        R.string.gamepad_pause_key,
                        if (input.pauseKey == KeyEvent.KEYCODE_UNKNOWN) stringResource(R.string.gamepad_default_pause)
                        else keyName(input.pauseKey),
                    ))
                }
                val prompt = when (capture) {
                    CaptureMode.Test -> R.string.gamepad_test_prompt
                    CaptureMode.Pause -> R.string.gamepad_pause_prompt
                    null -> null
                }
                if (prompt != null) Text(stringResource(prompt), style = MaterialTheme.typography.bodyLarge)
                lastKey?.let { code ->
                    Text(
                        listOfNotNull(lastDevice, keyName(code)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    if (capture == CaptureMode.Pause && !canBindPauseKey(code)) {
                        Text(stringResource(R.string.gamepad_pause_unavailable), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                FbngButton(
                    onClick = {
                        input.resetSettings()
                        capture = null
                        lastKey = null
                        lastDevice = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    kind = FbngButtonKind.Neutral,
                ) { Text(stringResource(R.string.gamepad_reset)) }
                FbngButton(onClick = ::back, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (capture == null) R.string.close else R.string.cancel))
                }
            }
        }
    }
}

private fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
