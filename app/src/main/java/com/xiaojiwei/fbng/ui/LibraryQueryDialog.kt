package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens

/** Controller and touch input share one multi-character query, including empty results. */
@Composable
internal fun LibraryQueryDialog(query: String, resultCount: Int, onQuery: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(4) {
            withFrameNanos { }
            if (runCatching { initial.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = FbngDimens.libraryPickerWidth).fillMaxWidth(.94f).fillMaxHeight(.94f)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonB) {
                        onDismiss()
                        true
                    } else false
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(FbngDimens.space16), verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                    Text(stringResource(R.string.library_jump_to_letter), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge)
                    FbngButton(onClick = onDismiss) { Text(stringResource(R.string.library_query_done)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                    Text(query.ifEmpty { stringResource(R.string.library_query_hint) }, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pluralStringResource(R.plurals.library_game_count, resultCount, resultCount),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyVerticalGrid(GridCells.Adaptive(FbngDimens.buttonHeight), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                    items("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-".toList(), key = { it }) { character ->
                        FbngButton(onClick = { onQuery(query + character) }, kind = FbngButtonKind.Neutral,
                            modifier = Modifier.then(if (character == 'A') Modifier.focusRequester(initial) else Modifier)) {
                            Text(character.toString())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                    FbngButton(onClick = { onQuery(query.dropLast(1)) }, enabled = query.isNotEmpty(),
                        kind = FbngButtonKind.Neutral, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_query_backspace))
                    }
                    FbngButton(onClick = { onQuery("") }, enabled = query.isNotEmpty(),
                        kind = FbngButtonKind.Neutral, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.library_query_clear))
                    }
                }
            }
        }
    }
}
