package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind

@Composable
internal fun GameConfirmationDialog(
    title: String,
    body: String,
    actionLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    immersive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogGamepadInput()
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (window != null) {
                window.setDimAmount(.42f)
                if (immersive) WindowCompat.getInsetsController(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        val cancel = remember { FocusRequester() }
        LaunchedEffect(cancel) {
            withFrameNanos { }
            cancel.requestFocus()
        }
        Column(
            Modifier.padding(FbngDimens.space24).widthIn(max = 460.dp).fillMaxWidth()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonB) {
                        onDismiss()
                        true
                    } else false
                }
                .fbngIsland().padding(FbngDimens.space24),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8, Alignment.End)) {
                FbngButton(onDismiss, Modifier.focusRequester(cancel), kind = FbngButtonKind.Outline) {
                    Text(stringResource(R.string.cancel))
                }
                FbngButton(onConfirm, kind = FbngButtonKind.Destructive) { Text(actionLabel) }
            }
        }
    }
}
