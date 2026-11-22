package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.contentDescription
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngShapes

private const val QueryCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-"

@Composable
internal fun LibraryQueryDialog(
    query: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(query) { mutableStateOf(query) }
    val requesters = remember {
        (QueryCharacters.map(Char::toString) + listOf("backspace", "clear", "done", "back"))
            .associateWith { FocusRequester() }
    }
    var focused by rememberSaveable { mutableStateOf("A") }
    fun target(id: String) = Modifier.focusRequester(requesters.getValue(id))
        .onFocusChanged { if (it.isFocused) focused = id }
    val title = stringResource(R.string.library_jump_to_letter)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        DialogGamepadInput()
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                .semantics { paneTitle = title }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonB) {
                        onDismiss()
                        true
                    } else false
                },
        ) {
            ShellPage(
                title = title,
                margin = FbngDimens.compactMargin,
                leadingAction = { ShellHeaderBack(stringResource(R.string.back), onDismiss, target("back")) },
                footer = {
                    val action = when (focused) {
                        "back" -> R.string.back
                        "backspace" -> R.string.library_query_backspace
                        "clear" -> R.string.library_query_clear
                        "done" -> R.string.library_query_done
                        else -> R.string.shell_hint_input
                    }
                    ShellFooter(listOf("A" to action, "B" to R.string.cancel))
                },
            ) {
                Column(
                    Modifier.fillMaxSize().fbngIsland().padding(FbngDimens.panelInset),
                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(FbngDimens.shellHeaderHeight)
                            .background(MaterialTheme.colorScheme.background, FbngShapes.Button)
                            .padding(horizontal = FbngDimens.space12),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(draft + "│", style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                        val keys = QueryCharacters.map(Char::toString) + "backspace"
                        for (row in keys.chunked(10)) {
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                for (id in row) {
                                    val backspace = id == "backspace"
                                    val label = if (backspace) stringResource(R.string.library_query_backspace) else id
                                    FbngButton(
                                        onClick = { draft = if (backspace) draft.dropLast(1) else draft + id },
                                        modifier = target(id).weight(1f).fillMaxHeight()
                                            .then(if (backspace) Modifier.semantics { contentDescription = label } else Modifier.testTag(ShellTags.letter(id.single()))),
                                        kind = FbngButtonKind.Outline,
                                        contentPadding = PaddingValues(),
                                    ) {
                                        Text(if (backspace) "⌫" else id, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QueryEditButton(R.string.library_query_clear, target("clear"), true) { draft = "" }
                        FbngButton({ onConfirm(draft) }, target("done").height(FbngDimens.shellHeaderHeight)) {
                            Text(stringResource(R.string.library_query_done), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                requesters.getValue(focused).requestFocus()
            }
        }
    }
}

@Composable
private fun QueryEditButton(label: Int, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    FbngButton(
        onClick = onClick,
        modifier = modifier.height(FbngDimens.shellHeaderHeight),
        enabled = enabled,
        kind = FbngButtonKind.Toolbar,
        contentPadding = PaddingValues(horizontal = FbngDimens.space16),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
