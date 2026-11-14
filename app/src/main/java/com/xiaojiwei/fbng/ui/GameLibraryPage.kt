package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.xiaojiwei.fbng.ui.theme.FbngEmptyState
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import com.xiaojiwei.fbng.ui.theme.fbngIsland

private const val LibraryColumns = 2
private const val LibraryRows = 5
internal const val LibraryPageSize = LibraryColumns * LibraryRows

@Composable
internal fun GameLibraryPage(
    results: LibraryResults,
    selected: LibraryEntry?,
    totalGames: Int,
    playableOnly: Boolean,
    emptyMessage: Int,
    pageIndex: Int,
    onPage: (Int) -> Unit,
    cardRequesters: MutableMap<String, FocusRequester>,
    wide: Boolean,
    compactBottomBar: Boolean,
    showDetails: Boolean,
    margin: Dp,
    busy: Boolean,
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
    query: String,
    covers: CoverUiState? = null,
) {
    val scheme = MaterialTheme.colorScheme
    fun control(tag: String) = Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
    val libraryTitle = stringResource(R.string.shell_library)
    if (showDetails && selected != null) {
        ShellPage(
            title = stringResource(R.string.shell_page_path, libraryTitle, selected.title),
            margin = margin,
            leadingAction = {
                ShellHeaderBack(
                    label = stringResource(R.string.library_back),
                    onClick = onBack,
                    modifier = Modifier.libraryFocus(
                        ShellTags.DetailBack, requesters.getValue(ShellTags.DetailBack), onFocused,
                    ),
                )
            },
        ) {
            GameDetails(
                entry = selected,
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                actionModifier = control(ShellTags.Play),
                favoriteModifier = control(ShellTags.ToggleFavorite),
                isFavorite = selected.game.name in favoriteNames,
                busy = busy,
                launchError = launchError,
                onPlay = { onPlay(selected.game.name) },
                onToggleFavorite = { onToggleFavorite(selected.game.name) },
                onOpenImport = onOpenImport,
                covers = covers,
            )
        }
        return
    }

    val games = results.entries.drop(pageIndex * LibraryPageSize).take(LibraryPageSize)
    val pages = if (results.entries.isEmpty()) 0 else (results.entries.size - 1) / LibraryPageSize + 1
    val rowGap = if (compactBottomBar) FbngDimens.space4 else FbngDimens.space8
    ShellPage(
        title = libraryTitle,
        margin = margin,
        contentGap = if (compactBottomBar) FbngDimens.space4 else FbngDimens.space12,
        headerAction = { width ->
            val labelWidth = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.fontSize.toDp() } * 4
            val buttonWidth = if (width < FbngDimens.libraryHeaderNarrow) {
                FbngDimens.libraryHeaderNarrowButton
            } else FbngDimens.buttonHeight
            LibraryFilters(
                query = query,
                playableOnly = playableOnly,
                wide = width >= FbngDimens.libraryHeaderWide &&
                    labelWidth + FbngDimens.space8 <= FbngDimens.libraryStatusFilterWidth,
                compactWidth = buttonWidth,
                requesters = requesters,
                onFocused = onFocused,
                onOpenLetters = onOpenLetters,
                onOpenFilter = onOpenFilter,
            )
            LibraryPagination(pageIndex, pages, buttonWidth, busy, requesters, onFocused, onPage)
        },
    ) {
        if (totalGames == 0) {
            FbngEmptyState {
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
            FbngEmptyState {
                Text(stringResource(emptyMessage), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
        } else {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            ) {
                // Fill down each column so up/down follows the list order.
                repeat(LibraryColumns) { column ->
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(rowGap),
                    ) {
                        repeat(LibraryRows) { row ->
                            val entry = games.getOrNull(column * LibraryRows + row)
                            if (entry == null) {
                                Spacer(Modifier.weight(1f).fillMaxWidth())
                            } else {
                                val name = entry.game.name
                                key(name) {
                                    val tag = ShellTags.game(name)
                                    val requester = remember { FocusRequester() }
                                    DisposableEffect(tag, requester) {
                                        cardRequesters[tag] = requester
                                        onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                    }
                                    val cardModifier = Modifier.weight(1f).fillMaxWidth().focusProperties {
                                        if (column == 0 && wide) left = requesters.getValue(ShellTags.NavLibrary)
                                    }.libraryFocus(tag, requester) {
                                        onFocused(it)
                                        onSelected(name)
                                    }.fbngFocusTarget(
                                        onClick = { onDetails(name) },
                                        container = scheme.surfaceContainerHigh,
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
    buttonWidth: Dp,
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
        ShellHeaderButton(
            onClick = { onPage(pageIndex - 1) },
            enabled = !busy && pageIndex > 0,
            modifier = Modifier
                .width(buttonWidth)
                .libraryFocus(ShellTags.PreviousPage, requesters.getValue(ShellTags.PreviousPage), onFocused)
                .semantics { contentDescription = previous },
        ) { Text("‹", style = MaterialTheme.typography.titleMedium) }
        val number = if (pages == 0) 0 else pageIndex + 1
        val pageDescription = stringResource(R.string.library_page_number, number, pages)
        Text(
            stringResource(R.string.library_page_short, number, pages),
            modifier = Modifier.clearAndSetSemantics { contentDescription = pageDescription },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        ShellHeaderButton(
            onClick = { onPage(pageIndex + 1) },
            enabled = !busy && pageIndex + 1 < pages,
            modifier = Modifier
                .width(buttonWidth)
                .libraryFocus(ShellTags.NextPage, requesters.getValue(ShellTags.NextPage), onFocused)
                .semantics { contentDescription = next },
        ) { Text("›", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
internal fun gameCardTextHeight(): Dp {
    val type = MaterialTheme.typography
    return with(LocalDensity.current) {
        type.titleMedium.lineHeight.toDp() + type.bodySmall.lineHeight.toDp() +
            FbngDimens.space16 + FbngDimens.space4
    }
}

@Composable
internal fun GameCardText(entry: LibraryEntry, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val title: @Composable (Modifier) -> Unit = { textModifier ->
        Text(
            entry.title,
            modifier = textModifier,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val file: @Composable (Modifier) -> Unit = { textModifier ->
        Row(textModifier, horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
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
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    BoxWithConstraints(modifier.padding(horizontal = FbngDimens.space12)) {
        if (maxHeight < gameCardTextHeight()) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title(Modifier.weight(1f))
                file(Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(vertical = FbngDimens.space8),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space4, Alignment.CenterVertically),
            ) {
                title(Modifier.fillMaxWidth())
                file(Modifier.fillMaxWidth())
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
            Text(entry.title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
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

    BoxWithConstraints(modifier.fbngIsland().padding(FbngDimens.space16)) {
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
