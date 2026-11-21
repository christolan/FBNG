package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.Modifier
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
    navigation: @Composable () -> Unit,
    metadata: Map<String, String>,
    onFocused: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onChoose: (LibraryEntry) -> Unit,
    recent: Boolean = false,
    covers: CoverUiState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val hero = remember { FocusRequester() }
    val firstSide = remember { FocusRequester() }
    ShellPage(
        title = "",
        margin = margin,
        leadingAction = navigation,
        footer = { BrowseFooter(pageIndex, pages, false, scanning, requesters, onFocused, onPage) },
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
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                for (group in 0..1) {
                    Column(
                        Modifier.weight(if (group == 0) 6f else 5f).fillMaxHeight()
                            .fbngIsland().padding(FbngDimens.panelInset),
                        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                    ) {
                        val indices = if (group == 0) 0..0 else 1 until ShelfPageSize
                        for (index in indices) {
                            val entry = entries.getOrNull(index)
                            val cell = Modifier.weight(1f).fillMaxWidth()
                            if (entry == null) {
                                Spacer(cell)
                            } else {
                                val tag = if (recent) ShellTags.recent(entry.game.name) else ShellTags.favorite(entry.game.name)
                                key(tag) {
                                    val requester = when (index) {
                                        0 -> hero
                                        1 -> firstSide
                                        else -> remember { FocusRequester() }
                                    }
                                    DisposableEffect(tag, requester) {
                                        cardRequesters[tag] = requester
                                        onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                    }
                                    val target = cell.focusProperties {
                                        if (index == 0) {
                                            up = requesters.getValue(if (recent) ShellTags.NavRecent else ShellTags.NavFavorites)
                                            if (entries.size > 1) down = firstSide
                                        }
                                        if (index == 1) up = hero
                                    }.libraryFocus(tag, requester, onFocused)
                                        .fbngFocusTarget({ onChoose(entry) }, if (index == 0) androidx.compose.ui.graphics.Color.Transparent else scheme.surface)
                                    val image = covers?.images?.get(entry.game.name)
                                    val caption = metadata[entry.game.name]
                                    if (index == 0) {
                                        Column(target, verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                            GameCover(image, Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                                            ShelfLabel(entry, caption, true, Modifier.fillMaxWidth().padding(FbngDimens.space8))
                                        }
                                    } else {
                                        Row(target.padding(FbngDimens.space12), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            if (image != null) GameCover(image, Modifier.width(78.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                                            ShelfLabel(entry, caption, false, Modifier.weight(1f))
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
}

@Composable
private fun ShelfLabel(entry: LibraryEntry, caption: String?, hero: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FbngDimens.space4)) {
            Text(entry.title, style = if (hero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = scheme.secondary, style = MaterialTheme.typography.titleMedium)
    }
}
