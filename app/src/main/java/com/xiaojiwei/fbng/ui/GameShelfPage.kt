package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngEmptyState
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

@Composable
internal fun GameShelfPage(
    entries: List<LibraryEntry>,
    hasSavedGames: Boolean,
    scanning: Boolean,
    launchError: Boolean,
    margin: Dp,
    listState: LazyListState,
    cardRequesters: MutableMap<String, FocusRequester>,
    libraryRequester: FocusRequester,
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
                        modifier = Modifier.libraryFocus(ShellTags.OpenLibrary, libraryRequester, onFocused),
                    ) { Text(stringResource(R.string.favorites_open_library)) }
                }
                return@Column
            }
            if (launchError) {
                Text(
                    stringResource(R.string.preparing_game_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.error,
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val textHeight = gameCardTextHeight()
                val coverHeight = (maxHeight - textHeight).coerceAtLeast(FbngDimens.space48)
                val gap = FbngDimens.space12
                val threeCardWidth = with(LocalDensity.current) {
                    ((maxWidth.roundToPx() - gap.roundToPx() * 2) / 3).toDp()
                }
                val cardWidth = minOf(
                    maxWidth,
                    FbngDimens.shelfCardMaxWidth,
                    threeCardWidth.coerceAtLeast(FbngDimens.shelfCardMinWidth),
                    coverHeight.coerceAtLeast(FbngDimens.shelfCardMinWidth),
                )
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items(entries, key = { if (recent) ShellTags.recent(it.game.name) else ShellTags.favorite(it.game.name) }) { entry ->
                        val tag = if (recent) ShellTags.recent(entry.game.name) else ShellTags.favorite(entry.game.name)
                        val requester = remember { FocusRequester() }
                        DisposableEffect(tag, requester) {
                            cardRequesters[tag] = requester
                            onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                        }
                        Column(
                            Modifier.width(cardWidth).fillMaxHeight()
                                .libraryFocus(tag, requester, onFocused)
                                .fbngFocusTarget(onClick = { onChoose(entry) }, container = scheme.surfaceContainerHigh),
                        ) {
                            GameCover(
                                entry.title,
                                covers?.images?.get(entry.game.name),
                                Modifier.fillMaxWidth().weight(1f),
                                contentScale = ContentScale.Crop,
                            )
                            GameCardText(entry, Modifier.fillMaxWidth().height(textHeight).background(scheme.surface))
                        }
                    }
                }
            }
        }
    }
}
