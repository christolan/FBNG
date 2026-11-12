package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.LibraryGame
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

@Composable
internal fun GameLibraryPage(
    games: List<LibraryGame>,
    selected: LibraryGame?,
    hasGames: Boolean,
    gridState: LazyGridState,
    cardRequesters: MutableMap<String, FocusRequester>,
    wide: Boolean,
    compactDetails: Boolean,
    margin: Dp,
    busy: Boolean,
    preparing: Boolean,
    launchError: Boolean,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onSelected: (String) -> Unit,
    onDetails: (String) -> Unit,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenImport: () -> Unit,
    search: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    fun control(tag: String) = Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
    val details: @Composable (LibraryGame, Modifier) -> Unit = { game, modifier ->
        GameDetails(
            game = game,
            modifier = modifier,
            actionModifier = control(ShellTags.Play),
            busy = busy,
            preparing = preparing,
            launchError = launchError,
            onPlay = { onPlay(game.name) },
            onOpenImport = onOpenImport,
        )
    }
    if (compactDetails && selected != null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(margin),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
        ) {
            FbngButton(onBack, control(ShellTags.DetailBack), kind = FbngButtonKind.Outline) {
                Text(stringResource(R.string.library_back))
            }
            details(selected, Modifier.fillMaxWidth())
        }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = margin),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = margin),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.shell_library), style = MaterialTheme.typography.headlineSmall)
            if (wide && games.isNotEmpty()) GameCount(games.size)
        }
        if (!hasGames) {
            Column(
                Modifier.fillMaxWidth()
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
                FbngButton(onOpenImport, control(ShellTags.EmptyImport)) { Text(stringResource(R.string.import_title)) }
            }
            return@Column
        }
        if (!wide) search()
        if (games.isEmpty()) {
            Text(stringResource(R.string.search_empty), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        if (!wide) GameCount(games.size)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
            LazyVerticalGrid(
                columns = if (wide) GridCells.Adaptive(FbngDimens.cardMin) else GridCells.Fixed(1),
                modifier = Modifier.weight(1f),
                state = gridState,
                contentPadding = PaddingValues(FbngDimens.space8),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
            ) {
                items(games, key = { it.name }) { game ->
                    val tag = ShellTags.game(game.name)
                    val requester = remember { FocusRequester() }
                    DisposableEffect(tag, requester) {
                        cardRequesters[tag] = requester
                        onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                    }
                    GameCard(
                        game = game,
                        selected = wide && game.name == selected?.name,
                        modifier = Modifier.libraryFocus(tag, requester) {
                            onFocused(it)
                            onSelected(game.name)
                        },
                        onClick = { onDetails(game.name) },
                    )
                }
            }
            if (wide && selected != null) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = margin),
                ) {
                    details(selected, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun GameCount(count: Int) {
    Text(
        pluralStringResource(R.plurals.library_game_count, count, count),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun GameCard(game: LibraryGame, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().fbngFocusTarget(
            onClick = onClick,
            container = if (selected) scheme.surfaceContainerHigh else scheme.surface,
            large = true,
        ).padding(FbngDimens.space12),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
    ) {
        Text(game.title(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(game.name, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        if (!game.canLaunch) {
            Text(stringResource(R.string.card_needs_dependencies), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
        }
    }
}

@Composable
private fun GameDetails(
    game: LibraryGame,
    modifier: Modifier,
    actionModifier: Modifier,
    busy: Boolean,
    preparing: Boolean,
    launchError: Boolean,
    onPlay: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.background(scheme.surface, RoundedCornerShape(FbngDimens.panelRadius))
            .padding(FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        Text(game.title(), style = MaterialTheme.typography.headlineSmall)
        if (game.name == "kovsh") {
            Text(game.description, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        }
        Text(game.name, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
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
    }
}

private fun Modifier.libraryFocus(tag: String, requester: FocusRequester, onFocused: (String) -> Unit) =
    testTag(tag).onFocusChanged { if (it.isFocused) onFocused(tag) }.focusRequester(requester)

@Composable
private fun LibraryGame.title() = if (name == "kovsh") stringResource(R.string.game_title) else description
