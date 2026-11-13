package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.library.LibraryResults
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

@Composable
internal fun GameLibraryPage(
    results: LibraryResults,
    selected: LibraryEntry?,
    totalGames: Int,
    playableOnly: Boolean,
    emptyMessage: Int,
    pageIndex: Int,
    pageSize: Int,
    onPage: (Int) -> Unit,
    onPageSize: (Int) -> Unit,
    cardRequesters: MutableMap<String, FocusRequester>,
    wide: Boolean,
    showDetails: Boolean,
    margin: Dp,
    busy: Boolean,
    preparing: Boolean,
    launchError: Boolean,
    favoriteNames: Set<String>,
    onToggleFavorite: (String) -> Unit,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onSelected: (String) -> Unit,
    onDetails: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenLetters: () -> Unit,
    onOpenFilter: () -> Unit,
    search: @Composable () -> Unit,
    covers: CoverUiState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    fun control(tag: String) = Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
    val details: @Composable (LibraryEntry, Modifier) -> Unit = { entry, modifier ->
        GameDetails(
            entry = entry,
            modifier = modifier,
            actionModifier = control(ShellTags.Play),
            favoriteModifier = control(ShellTags.ToggleFavorite),
            isFavorite = entry.game.name in favoriteNames,
            busy = busy,
            preparing = preparing,
            launchError = launchError,
            onPlay = { onPlay(entry.game.name) },
            onToggleFavorite = { onToggleFavorite(entry.game.name) },
            onOpenImport = onOpenImport,
            covers = covers,
        )
    }
    if (showDetails && selected != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(margin),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
        ) {
            FbngButton(onBack, Modifier.libraryFocus(ShellTags.DetailBack, requesters.getValue(ShellTags.DetailBack), onFocused), kind = FbngButtonKind.Outline) {
                Text(stringResource(R.string.library_back))
            }
            details(selected, Modifier.fillMaxWidth())
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(margin)) {
        val inlineSearch = maxWidth >= FbngDimens.libraryToolbarBreakpoint
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.shell_library), style = MaterialTheme.typography.headlineSmall)
                if (totalGames > 0) GameCount(results.entries.size, totalGames)
            }
            if (totalGames == 0) {
                Column(Modifier.fillMaxWidth()
                    .background(scheme.surface, RoundedCornerShape(FbngDimens.panelRadius))
                    .padding(FbngDimens.space16), verticalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                    Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.library_empty_hint), style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant)
                    FbngButton(onOpenImport, control(ShellTags.EmptyImport)) { Text(stringResource(R.string.import_title)) }
                }
                return@Column
            }
            val tools: @Composable () -> Unit = {
                LibraryTools(totalGames > 0, playableOnly, requesters, onFocused, onOpenLetters, onOpenFilter)
            }
            if (inlineSearch) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { search() }
                    Box(Modifier.width(FbngDimens.libraryToolsWidth)) { tools() }
                }
            } else {
                search()
                tools()
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val density = LocalDensity.current
                val titleHeight = with(density) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
                val filenameHeight = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
                val rowHeight = maxOf(FbngDimens.libraryRowHeight,
                    titleHeight + filenameHeight + FbngDimens.space16 + FbngDimens.space4)
                val gap = FbngDimens.space8
                val columns = ((maxWidth + gap) / (FbngDimens.libraryCellWidth + gap)).toInt().coerceAtLeast(1)
                val rows = ((maxHeight + gap) / (rowHeight + gap)).toInt().coerceAtLeast(1)
                val capacity = columns * rows
                LaunchedEffect(columns, rows) { onPageSize(capacity) }
                val games = results.entries.drop(pageIndex * pageSize).take(pageSize)
                if (games.isEmpty()) {
                    Text(stringResource(emptyMessage), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        // Fill down each column, matching the controller's natural up/down movement.
                        repeat(columns) { column ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                                repeat(rows) { row ->
                                    val entry = games.getOrNull(column * rows + row)
                                    if (entry != null) key(entry.game.name) {
                                        val name = entry.game.name
                                        val tag = ShellTags.game(name)
                                        val requester = remember { FocusRequester() }
                                        DisposableEffect(tag, requester) {
                                            cardRequesters[tag] = requester
                                            onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                        }
                                        GameRow(entry, selected?.game?.name == name,
                                            Modifier.height(rowHeight).focusProperties {
                                                if (column == 0 && wide) left = requesters.getValue(ShellTags.NavLibrary)
                                            }.libraryFocus(tag, requester) {
                                                onFocused(it)
                                                onSelected(name)
                                            }, onClick = { onDetails(name) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val pages = if (results.entries.isEmpty()) 0 else (results.entries.size - 1) / pageSize + 1
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
                verticalAlignment = Alignment.CenterVertically) {
                FbngButton(onClick = { onPage(pageIndex - 1) }, enabled = !busy && pageIndex > 0,
                    kind = FbngButtonKind.Neutral,
                    modifier = Modifier.libraryFocus(ShellTags.PreviousPage, requesters.getValue(ShellTags.PreviousPage), onFocused)) {
                    Text(stringResource(R.string.library_previous_page))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_page_number, if (pages == 0) 0 else pageIndex + 1, pages),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.library_page_keys), style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                }
                FbngButton(onClick = { onPage(pageIndex + 1) }, enabled = !busy && pageIndex + 1 < pages,
                    kind = FbngButtonKind.Neutral,
                    modifier = Modifier.libraryFocus(ShellTags.NextPage, requesters.getValue(ShellTags.NextPage), onFocused)) {
                    Text(stringResource(R.string.library_next_page))
                }
            }
        }
    }
}

@Composable
private fun GameCount(count: Int, total: Int) {
    Text(
        if (count == total) pluralStringResource(R.plurals.library_game_count, total, total)
        else stringResource(R.string.library_result_count, count, total),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GameRow(entry: LibraryEntry, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().fbngFocusTarget(
            onClick = onClick,
            container = if (selected) scheme.surfaceContainerHigh else scheme.surface,
            large = true,
        ).heightIn(min = FbngDimens.buttonHeight)
            .padding(horizontal = FbngDimens.space12, vertical = FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4, Alignment.CenterVertically),
    ) {
        Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
            Text(entry.game.fileNames.firstOrNull() ?: "${entry.game.name}.zip", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!entry.game.canLaunch) Text(stringResource(R.string.card_needs_dependencies),
                style = MaterialTheme.typography.bodySmall, color = scheme.error, maxLines = 1)
        }
    }
}

@Composable
private fun GameDetails(
    entry: LibraryEntry,
    modifier: Modifier,
    actionModifier: Modifier,
    favoriteModifier: Modifier,
    isFavorite: Boolean,
    busy: Boolean,
    preparing: Boolean,
    launchError: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenImport: () -> Unit,
    covers: CoverUiState? = null,
) {
    val game = entry.game
    val scheme = MaterialTheme.colorScheme
    val information: @Composable (Modifier) -> Unit = { infoModifier ->
        Column(
            infoModifier,
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        ) {
            Text(entry.title, style = MaterialTheme.typography.headlineSmall)
            if (entry.title != game.description) {
                Text(game.description, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
            Text(game.fileNames.joinToString(" · "), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            if (game.canLaunch) {
                Text(stringResource(R.string.card_ready), style = MaterialTheme.typography.bodyLarge, color = scheme.secondary)
            }
            for (dependency in game.missing) {
                Text(
                    stringResource(
                        if (dependency.isBios) R.string.library_missing_bios else R.string.library_missing_parent,
                        dependency.name,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.error,
                )
            }
            if (preparing) Text(stringResource(R.string.preparing_game), style = MaterialTheme.typography.bodyLarge)
            if (launchError) {
                Text(stringResource(R.string.preparing_game_failed), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
            }
            FbngButton(
                onClick = if (game.canLaunch) onPlay else onOpenImport,
                modifier = actionModifier,
                enabled = !busy,
            ) {
                Text(stringResource(if (game.canLaunch) R.string.play else R.string.import_title))
            }
            FbngButton(
                onClick = onToggleFavorite,
                modifier = favoriteModifier,
                kind = FbngButtonKind.Neutral,
            ) {
                Text(stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
            }
        }
    }

    BoxWithConstraints(modifier.background(scheme.surface, RoundedCornerShape(FbngDimens.cardRadius))
        .padding(FbngDimens.space16)) {
        val image = covers?.images?.get(game.name)
        if (maxWidth >= FbngDimens.libraryToolbarBreakpoint && image != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space24)) {
                GameCover(entry.title, image, Modifier.weight(1f))
                information(Modifier.weight(1.5f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
                if (image != null) GameCover(entry.title, image,
                    Modifier.widthIn(max = FbngDimens.favoriteCardWidth).fillMaxWidth())
                information(Modifier.fillMaxWidth())
            }
        }
    }

}

internal fun Modifier.libraryFocus(tag: String, requester: FocusRequester, onFocused: (String) -> Unit) =
    testTag(tag).onFocusChanged { if (it.isFocused) onFocused(tag) }.focusRequester(requester)
