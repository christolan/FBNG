package com.xiaojiwei.fbng.ui

import androidx.compose.material3.AlertDialog
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
) {
    AlertDialog(
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonB) {
                onDismiss()
                true
            } else false
        },
        onDismissRequest = onDismiss,
        title = {
            DialogGamepadInput()
            Text(title)
        },
        text = { Text(body) },
        confirmButton = { FbngButton(onClick = onConfirm) { Text(actionLabel) } },
        dismissButton = {
            val cancel = remember { FocusRequester() }
            LaunchedEffect(cancel) { cancel.requestFocus() }
            FbngButton(
                onClick = onDismiss,
                kind = FbngButtonKind.Neutral,
                modifier = Modifier.focusRequester(cancel),
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}
