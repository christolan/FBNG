package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.artwork.CoverImage
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngEmptyState
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

internal const val ShelfPageSize = 4

@Composable
internal fun GameShelfPage(
    entries: List<LibraryEntry>,
    hasSavedGames: Boolean,
    scanning: Boolean,
    margin: Dp,
    pageIndex: Int,
    pages: Int,
    onPage: (Int) -> Unit,
    cardRequesters: MutableMap<String, FocusRequester>,
    requesters: Map<String, FocusRequester>,
    onPageControlFocused: (String) -> Unit,
    onFocused: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onChoose: (LibraryEntry) -> Unit,
    recent: Boolean = false,
    covers: CoverUiState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ShellPage(
        title = stringResource(if (recent) R.string.recent_title else R.string.favorites_title),
        margin = margin,
        headerAction = {
            ShellPagination(pageIndex, pages, FbngDimens.shellHeaderHeight, false, requesters, onPageControlFocused, onPage)
        },
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
        ) {
            if (entries.isEmpty()) {
                FbngEmptyState {
                    val title = when {
                        !hasSavedGames -> if (recent) R.string.recent_empty else R.string.favorites_empty
                        scanning -> R.string.scanning
                        else -> if (recent) R.string.recent_unavailable else R.string.favorites_unavailable
                    }
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    if (!hasSavedGames || !scanning) {
                        Text(
                            stringResource(
                                if (hasSavedGames) R.string.favorites_unavailable_hint
                                else if (recent) R.string.recent_empty_hint else R.string.favorites_empty_hint,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    FbngButton(
                        onClick = onOpenLibrary,
                        modifier = Modifier.libraryFocus(
                            ShellTags.OpenLibrary, requesters.getValue(ShellTags.OpenLibrary), onFocused,
                        ),
                    ) { Text(stringResource(R.string.favorites_open_library)) }
                }
                return@Column
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val textHeight = gameCardTextHeight()
                val gap = FbngDimens.space12
                val twoRowsHeight = (textHeight + FbngDimens.space48) * 2 + gap
                val fourColumnsWidth = FbngDimens.shelfCardMinWidth * ShelfPageSize + gap * (ShelfPageSize - 1)
                val wideEnoughForFour = maxWidth >= fourColumnsWidth
                val columns = if (maxHeight < twoRowsHeight || (wideEnoughForFour && maxWidth >= maxHeight * 2)) {
                    ShelfPageSize
                } else 2
                val rows = ShelfPageSize / columns
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(rows) { row ->
                        Row(
                            Modifier.weight(1f).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            repeat(columns) { column ->
                                val entry = entries.getOrNull(row * columns + column)
                                val cell = Modifier.weight(1f).fillMaxHeight()
                                if (entry == null) {
                                    Spacer(cell)
                                } else {
                                    val tag = if (recent) ShellTags.recent(entry.game.name) else ShellTags.favorite(entry.game.name)
                                    key(tag) {
                                        ShelfGameCard(
                                            entry = entry,
                                            tag = tag,
                                            textHeight = textHeight,
                                            cover = covers?.images?.get(entry.game.name),
                                            cardRequesters = cardRequesters,
                                            onFocused = onFocused,
                                            onChoose = onChoose,
                                            modifier = cell,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfGameCard(
    entry: LibraryEntry,
    tag: String,
    textHeight: Dp,
    cover: CoverImage?,
    cardRequesters: MutableMap<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onChoose: (LibraryEntry) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val requester = remember { FocusRequester() }
    DisposableEffect(tag, requester) {
        cardRequesters[tag] = requester
        onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
    }
    Column(
        modifier.libraryFocus(tag, requester, onFocused)
            .fbngFocusTarget(onClick = { onChoose(entry) }, container = scheme.surfaceContainerHigh),
    ) {
        GameCover(entry.title, cover, Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Crop)
        GameCardText(entry, Modifier.fillMaxWidth().height(textHeight).background(scheme.surface))
    }
}
