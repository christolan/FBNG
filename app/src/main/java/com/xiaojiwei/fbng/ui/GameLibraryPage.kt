package com.xiaojiwei.fbng.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngEmptyState
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

internal const val LibraryColumns = 1
internal const val MaxLibraryRows = 5

@Composable
internal fun GameLibraryPage(
    games: List<LibraryEntry>,
    totalGames: Int,
    playableOnly: Boolean,
    emptyMessage: Int,
    pageIndex: Int,
    pages: Int,
    rows: Int,
    onRows: (Int) -> Unit,
    onPage: (Int) -> Unit,
    cardRequesters: MutableMap<String, FocusRequester>,
    navigation: @Composable () -> Unit,
    covers: CoverUiState?,
    selectedName: String?,
    scanning: Boolean,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onSelected: (String) -> Unit,
    onDetails: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenLetters: () -> Unit,
    onOpenFilter: () -> Unit,
    onClearFilters: () -> Unit,
    query: String,
) {
    val scheme = MaterialTheme.colorScheme
    fun control(tag: String) = Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
    val rowGap = FbngDimens.space8
    ShellPage(
        title = "",
        leadingAction = navigation,
        footer = { BrowseFooter(pageIndex, pages, true, scanning, requesters, onFocused, onPage) },
        margin = margin,
        headerAction = { width ->
            val labelWidth = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.fontSize.toDp() } * 4
            val wideHeader = width >= FbngDimens.libraryHeaderWide
            LibraryFilters(
                query = query,
                playableOnly = playableOnly,
                wide = wideHeader && labelWidth + FbngDimens.space8 <= FbngDimens.libraryStatusFilterWidth,
                requesters = requesters,
                onFocused = onFocused,
                onOpenLetters = onOpenLetters,
                onOpenFilter = onOpenFilter,
            )
        },
    ) {
        if (totalGames == 0) {
            FbngEmptyState(scrollable = false) {
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.library_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
                FbngButton(onOpenImport, control(ShellTags.EmptyImport)) { Text(stringResource(R.string.folder_configure)) }
            }
            return@ShellPage
        }
        if (games.isEmpty()) {
            FbngEmptyState(scrollable = false) {
                Text(stringResource(emptyMessage), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                    FbngButton(
                        onOpenLetters,
                        Modifier.libraryFocus(ShellTags.EditSearch, requesters.getValue(ShellTags.EditSearch), onFocused),
                    ) { Text(stringResource(R.string.library_edit_search)) }
                    FbngButton(
                        onClearFilters,
                        Modifier.libraryFocus(ShellTags.ClearFilters, requesters.getValue(ShellTags.ClearFilters), onFocused),
                        kind = FbngButtonKind.Neutral,
                    ) { Text(stringResource(R.string.library_clear_filters)) }
                }
            }
        } else {
            val selected = games.firstOrNull { it.game.name == selectedName } ?: games.first()
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                BoxWithConstraints(
                    Modifier.weight(5f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                ) {
                    val minRow = maxOf(FbngDimens.space32, with(LocalDensity.current) {
                        MaterialTheme.typography.titleMedium.lineHeight.toDp()
                    })
                    val fits = ((maxHeight + rowGap) / (minRow + rowGap)).toInt().coerceIn(1, MaxLibraryRows)
                    LaunchedEffect(fits, rows) { if (fits != rows) onRows(fits) }
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(rowGap)) {
                        repeat(rows) { row ->
                            val entry = games.getOrNull(row)
                            val cell = Modifier.weight(1f).fillMaxWidth()
                            if (entry == null) {
                                Spacer(cell)
                            } else key(entry.game.name) {
                                val name = entry.game.name
                                val tag = ShellTags.game(name)
                                val requester = remember { FocusRequester() }
                                DisposableEffect(tag, requester) {
                                    cardRequesters[tag] = requester
                                    onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                }
                                GameBrowseRow(
                                    entry,
                                    cell.focusProperties {
                                        if (row == 0) up = requesters.getValue(ShellTags.NavLibrary)
                                    }.libraryFocus(tag, requester) {
                                        onFocused(it)
                                        onSelected(name)
                                    }.fbngFocusTarget({ onDetails(name) }, scheme.surface),
                                )
                            }
                        }
                    }
                }
                Column(
                    Modifier.weight(3f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                ) {
                    GameCover(covers?.images?.get(selected.game.name),
                        Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                    GameCardText(selected, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun GameBrowseRow(entry: LibraryEntry, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val missing = entry.game.missing
    val status = when {
        missing.any { it.isBios } -> stringResource(R.string.library_missing_bios_short)
        !entry.game.canLaunch -> stringResource(R.string.card_needs_dependencies)
        else -> entry.game.fileNames.firstOrNull() ?: "${entry.game.name}.zip"
    }
    Row(
        modifier.padding(horizontal = FbngDimens.space12),
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(status, Modifier.weight(0.7f), style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End, color = if (entry.game.canLaunch) scheme.onSurfaceVariant else androidx.compose.ui.graphics.Color(0xFFF1C484),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun GameCardText(entry: LibraryEntry, modifier: Modifier) {
    Column(modifier.padding(horizontal = FbngDimens.space8, vertical = FbngDimens.space4),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4)) {
        Text(entry.title, style = MaterialTheme.typography.headlineSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(entry.game.fileNames.firstOrNull() ?: "${entry.game.name}.zip",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun Modifier.libraryFocus(tag: String, requester: FocusRequester, onFocused: (String) -> Unit) =
    testTag(tag).onFocusChanged { if (it.isFocused) onFocused(tag) }.focusRequester(requester)
