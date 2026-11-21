package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngEmptyState
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

internal const val FavoritesPageSize = 4
internal const val RecentPageSize = 5

@Composable
internal fun GameShelfPage(
    entries: List<LibraryEntry>,
    hasSavedGames: Boolean,
    scanning: Boolean,
    margin: Dp,
    pageIndex: Int,
    selectedTag: String?,
    pages: Int,
    onPage: (Int) -> Unit,
    cardRequesters: MutableMap<String, FocusRequester>,
    requesters: Map<String, FocusRequester>,
    navigation: @Composable () -> Unit,
    metadata: Map<String, String>,
    onFocused: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onChoose: (LibraryEntry) -> Unit,
    recent: Boolean = false,
    covers: CoverUiState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val navTab = if (recent) ShellTags.NavRecent else ShellTags.NavFavorites
    ShellPage(
        title = "",
        margin = margin,
        leadingAction = navigation,
        footer = { BrowseFooter(pageIndex, pages, navTab, scanning, requesters, onFocused, onPage) },
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
            val coverCard: @Composable (LibraryEntry, Modifier) -> Unit = { entry, modifier ->
                Column(
                    modifier.padding(FbngDimens.panelInset),
                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                ) {
                    val shape = MaterialTheme.shapes.medium
                    GameCover(
                        covers?.images?.get(entry.game.name),
                        Modifier.fillMaxWidth().weight(1f).clip(shape),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = FbngDimens.space8)) {
                        Text(
                            entry.title, style = MaterialTheme.typography.titleMedium,
                            minLines = if (recent) 1 else 2, maxLines = if (recent) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            metadata[entry.game.name].orEmpty(), style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val item: @Composable (Int, Modifier) -> Unit = { index, modifier ->
                val entry = entries.getOrNull(index)
                if (entry == null) {
                    Spacer(modifier)
                } else {
                    val tag = if (recent) ShellTags.recent(entry.game.name) else ShellTags.favorite(entry.game.name)
                    key(tag) {
                        val requester = remember { FocusRequester() }
                        DisposableEffect(tag, requester) {
                            cardRequesters[tag] = requester
                            onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                        }
                        val target = modifier.focusProperties {
                            if (!recent || index == 0) up = requesters.getValue(navTab)
                        }.libraryFocus(tag, requester, onFocused)
                        if (recent) {
                            Row(
                                target.fbngFocusTarget({ onChoose(entry) }, scheme.surface)
                                    .padding(horizontal = FbngDimens.space12),
                                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(entry.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                metadata[entry.game.name]?.let { caption ->
                                    Text(caption, Modifier.weight(.6f, fill = false), style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            coverCard(
                                entry,
                                target.fbngFocusTarget(
                                    { onChoose(entry) }, Color.Transparent, shape = MaterialTheme.shapes.large,
                                ).fbngIsland(),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                if (recent) {
                    Column(
                        Modifier.weight(5f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                    ) {
                        repeat(RecentPageSize) { item(it, Modifier.weight(1f).fillMaxWidth()) }
                    }
                    val selected = entries.firstOrNull { ShellTags.recent(it.game.name) == selectedTag } ?: entries.first()
                    coverCard(selected, Modifier.weight(3f).fillMaxHeight().fbngIsland())
                } else {
                    repeat(FavoritesPageSize) { item(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}
