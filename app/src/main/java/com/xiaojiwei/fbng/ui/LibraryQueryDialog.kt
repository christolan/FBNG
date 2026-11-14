package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens

private const val QueryCharacters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-"

@Composable
internal fun LibraryQueryDialog(query: String, resultCount: Int, onQuery: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = remember { FocusRequester() }
    val title = stringResource(R.string.library_jump_to_letter)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            initial.requestFocus()
        }
        Surface(
            modifier = Modifier.padding(FbngDimens.space8).widthIn(max = FbngDimens.libraryKeyboardWidth)
                .fillMaxWidth()
                .semantics { paneTitle = title }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonB) {
                        onDismiss()
                        true
                    } else false
                },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(FbngDimens.space12), verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            query.ifEmpty { title },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(R.plurals.library_game_count, resultCount, resultCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    ShellHeaderButton(onClick = { onQuery(query.dropLast(1)) }, enabled = query.isNotEmpty()) {
                        Text(stringResource(R.string.library_query_backspace), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    ShellHeaderButton(onClick = { onQuery("") }, enabled = query.isNotEmpty()) {
                        Text(stringResource(R.string.library_query_clear), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    FbngButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(FbngDimens.buttonHeight),
                        contentPadding = PaddingValues(horizontal = FbngDimens.space8),
                    ) {
                        Text(stringResource(R.string.library_query_done), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    val gap = FbngDimens.space4
                    val preferredSize = FbngDimens.buttonHeight
                    val rows = keyboardRows(maxWidth, maxHeight, gap, preferredSize)
                    val columns = keyboardColumns(rows)
                    val height = ((maxHeight - gap * (rows - 1)) / rows).coerceIn(Dp.Hairline, preferredSize)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        QueryCharacters.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { character ->
                                    val modifier = Modifier.weight(1f).height(height).testTag(ShellTags.letter(character))
                                    FbngButton(
                                        onClick = { onQuery(query + character) },
                                        modifier = if (character == 'A') modifier.focusRequester(initial) else modifier,
                                        kind = FbngButtonKind.Neutral,
                                        contentPadding = PaddingValues(),
                                    ) {
                                        Text(character.toString(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                }
                                repeat(columns - row.length) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun keyboardColumns(rows: Int): Int = (QueryCharacters.length + rows - 1) / rows

private fun keyboardRows(width: Dp, height: Dp, gap: Dp, preferredSize: Dp): Int {
    // Prefer the fewest rows that fit full-size keys. In smaller windows,
    // choose the arrangement that preserves the largest minimum key dimension.
    var bestRows = 1
    var bestSize = Dp.Hairline
    for (rows in 1..QueryCharacters.length) {
        val columns = keyboardColumns(rows)
        val keyWidth = (width - gap * (columns - 1)) / columns
        val keyHeight = (height - gap * (rows - 1)) / rows
        val size = minOf(keyWidth, keyHeight)
        if (size >= preferredSize) return rows
        if (size > bestSize) {
            bestRows = rows
            bestSize = size
        }
    }
    return bestRows
}
