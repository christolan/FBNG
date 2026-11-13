package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
    val libraryTitle = stringResource(R.string.shell_library)
    if (showDetails && selected != null) {
        ShellPage(
            title = stringResource(R.string.shell_page_path, libraryTitle, selected.title),
            margin = margin,
            headerAction = {
                FbngButton(
                    onClick = onBack,
                    modifier = Modifier.libraryFocus(
                        ShellTags.DetailBack, requesters.getValue(ShellTags.DetailBack), onFocused,
                    ),
                    kind = FbngButtonKind.Neutral,
                ) { Text(stringResource(R.string.library_back)) }
            },
        ) {
            GameDetails(
                entry = selected,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                actionModifier = control(ShellTags.Play),
                favoriteModifier = control(ShellTags.ToggleFavorite),
                isFavorite = selected.game.name in favoriteNames,
                busy = busy,
                preparing = preparing,
                launchError = launchError,
                onPlay = { onPlay(selected.game.name) },
                onToggleFavorite = { onToggleFavorite(selected.game.name) },
                onOpenImport = onOpenImport,
                covers = covers,
            )
        }
        return
    }

    if (totalGames == 0) {
        ShellPage(title = libraryTitle, margin = margin) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .background(scheme.surface, RoundedCornerShape(FbngDimens.panelRadius))
                    .padding(FbngDimens.space16),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
            ) {
                Text(stringResource(R.string.library_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.library_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
                FbngButton(onOpenImport, control(ShellTags.EmptyImport)) { Text(stringResource(R.string.folder_configure)) }
            }
        }
        return
    }

    val games = results.entries.drop(pageIndex * pageSize).take(pageSize)
    val pages = if (results.entries.isEmpty()) 0 else (results.entries.size - 1) / pageSize + 1
    ShellPage(
        title = libraryTitle,
        margin = margin,
        headerAction = {
            LibraryPagination(pageIndex, pages, busy, requesters, onFocused, onPage)
        },
        toolbar = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tools: @Composable () -> Unit = {
                    LibraryTools(playableOnly, requesters, onFocused, onOpenLetters, onOpenFilter)
                }
                if (maxWidth >= FbngDimens.librarySearchWidth + FbngDimens.libraryToolsWidth + FbngDimens.space8) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                    ) {
                        Box(Modifier.weight(1f)) { search() }
                        Box(Modifier.width(FbngDimens.libraryToolsWidth)) { tools() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                        search()
                        tools()
                    }
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val rowHeight = gameCardTextHeight()
            val gap = FbngDimens.space8
            val cardWidth = minOf(maxWidth, FbngDimens.libraryCellWidth)
            val columns = ((maxWidth + gap) / (cardWidth + gap)).toInt().coerceAtLeast(1)
            val rows = ((maxHeight + gap) / (rowHeight + gap)).toInt().coerceAtLeast(1)
            LaunchedEffect(columns, rows) { onPageSize(columns * rows) }
            if (games.isEmpty()) {
                Text(stringResource(emptyMessage), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            } else {
                Row(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // Fill down each column so up/down follows the list order.
                    games.take(columns * rows).chunked(rows).forEachIndexed { column, entries ->
                        Column(Modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(gap)) {
                            entries.forEach { entry ->
                                val name = entry.game.name
                                key(name) {
                                    val tag = ShellTags.game(name)
                                    val requester = remember { FocusRequester() }
                                    DisposableEffect(tag, requester) {
                                        cardRequesters[tag] = requester
                                        onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                    }
                                    val cardModifier = Modifier.fillMaxWidth().focusProperties {
                                        if (column == 0 && wide) left = requesters.getValue(ShellTags.NavLibrary)
                                    }.libraryFocus(tag, requester) {
                                        onFocused(it)
                                        onSelected(name)
                                    }.fbngFocusTarget(
                                        onClick = { onDetails(name) },
                                        container = if (selected?.game?.name == name) scheme.surfaceContainerHigh else scheme.surface,
                                    )
                                    GameCardText(entry, cardModifier)
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
private fun LibraryPagination(
    pageIndex: Int,
    pages: Int,
    busy: Boolean,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onPage: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val previous = stringResource(R.string.library_previous_page)
        val next = stringResource(R.string.library_next_page)
        FbngButton(
            onClick = { onPage(pageIndex - 1) },
            enabled = !busy && pageIndex > 0,
            kind = FbngButtonKind.Neutral,
            modifier = Modifier
                .widthIn(min = FbngDimens.buttonHeight)
                .libraryFocus(ShellTags.PreviousPage, requesters.getValue(ShellTags.PreviousPage), onFocused)
                .semantics { contentDescription = previous },
        ) { Text("‹", style = MaterialTheme.typography.titleMedium) }
        Text(
            stringResource(R.string.library_page_number, if (pages == 0) 0 else pageIndex + 1, pages),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
        FbngButton(
            onClick = { onPage(pageIndex + 1) },
            enabled = !busy && pageIndex + 1 < pages,
            kind = FbngButtonKind.Neutral,
            modifier = Modifier
                .widthIn(min = FbngDimens.buttonHeight)
                .libraryFocus(ShellTags.NextPage, requesters.getValue(ShellTags.NextPage), onFocused)
                .semantics { contentDescription = next },
        ) { Text("›", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun gameCardTextHeight(): Dp {
    val type = MaterialTheme.typography
    return with(LocalDensity.current) {
        type.titleMedium.lineHeight.toDp() * 2 + type.bodySmall.lineHeight.toDp() +
            FbngDimens.space16 + FbngDimens.space4
    }
}

@Composable
internal fun GameCardText(entry: LibraryEntry, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.height(gameCardTextHeight())
            .padding(horizontal = FbngDimens.space12, vertical = FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
    ) {
        Text(
            entry.title,
            style = MaterialTheme.typography.titleMedium,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
            Text(
                entry.game.fileNames.firstOrNull() ?: "${entry.game.name}.zip",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.game.canLaunch) {
                Text(
                    stringResource(R.string.card_needs_dependencies),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    maxLines = 1,
                )
            }
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
                Text(stringResource(if (game.canLaunch) R.string.play else R.string.folder_configure))
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
                GameCover(entry.title, image, Modifier.weight(1f)
                    .aspectRatio(FbngDimens.coverAspectRatio).clip(MaterialTheme.shapes.medium))
                information(Modifier.weight(1.5f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
                if (image != null) GameCover(entry.title, image,
                    Modifier.widthIn(max = FbngDimens.detailCoverWidth).fillMaxWidth()
                        .aspectRatio(FbngDimens.coverAspectRatio).clip(MaterialTheme.shapes.medium))
                information(Modifier.fillMaxWidth())
            }
        }
    }

}

internal fun Modifier.libraryFocus(tag: String, requester: FocusRequester, onFocused: (String) -> Unit) =
    testTag(tag).onFocusChanged { if (it.isFocused) onFocused(tag) }.focusRequester(requester)
