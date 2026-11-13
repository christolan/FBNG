package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
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
    listState: LazyListState,
    cardRequesters: MutableMap<String, FocusRequester>,
    wide: Boolean,
    compactDetails: Boolean,
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
    letterPicker: (@Composable () -> Unit)?,
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
            if (wide && totalGames > 0) GameCount(results.entries.size, totalGames)
        }
        if (totalGames == 0) {
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
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
            Column(
                Modifier.weight(if (wide) 1.2f else 1f),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            ) {
                LibraryTools(
                    hasResults = results.entries.isNotEmpty(),
                    playableOnly = playableOnly,
                    requesters = requesters,
                    onFocused = onFocused,
                    onOpenLetters = onOpenLetters,
                    onOpenFilter = onOpenFilter,
                )
                if (!wide) GameCount(results.entries.size, totalGames)
                if (results.entries.isEmpty()) {
                    Text(
                        stringResource(emptyMessage),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().then(
                                if (letterPicker != null) Modifier.semantics { hideFromAccessibility() } else Modifier,
                            ),
                            state = listState,
                            contentPadding = PaddingValues(FbngDimens.space8),
                            verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                        ) {
                            for ((letter, group) in results.groups) {
                                item(key = "letter-$letter", contentType = "heading") {
                                    val other = if (letter == '#') stringResource(R.string.library_letter_other) else null
                                    Text(
                                        letter.toString(),
                                        modifier = Modifier.padding(horizontal = FbngDimens.space12)
                                            .semantics {
                                                heading()
                                                if (other != null) contentDescription = other
                                            },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = scheme.secondary,
                                    )
                                }
                                items(group, key = { ShellTags.game(it.game.name) }, contentType = { "game" }) { entry ->
                                    val name = entry.game.name
                                    val tag = ShellTags.game(name)
                                    val requester = remember { FocusRequester() }
                                    DisposableEffect(tag, requester) {
                                        cardRequesters[tag] = requester
                                        onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                    }
                                    GameRow(
                                        entry = entry,
                                        selected = wide && name == selected?.game?.name,
                                        modifier = Modifier
                                            .focusProperties {
                                                if (letterPicker != null) canFocus = false
                                                left = requesters.getValue(ShellTags.JumpToLetter)
                                                if (wide && !busy) right = requesters.getValue(ShellTags.Play)
                                            }
                                            .libraryFocus(tag, requester) {
                                                onFocused(it)
                                                onSelected(name)
                                            },
                                        onClick = { onDetails(name) },
                                    )
                                }
                            }
                        }
                        letterPicker?.invoke()
                    }
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
        Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!entry.game.canLaunch) {
            Text(stringResource(R.string.card_needs_dependencies), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
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
) {
    val game = entry.game
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.background(scheme.surface, RoundedCornerShape(FbngDimens.panelRadius))
            .padding(FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        Text(entry.title, style = MaterialTheme.typography.headlineSmall)
        if (entry.title != game.description) {
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
        FbngButton(
            onClick = onToggleFavorite,
            modifier = favoriteModifier,
            kind = FbngButtonKind.Neutral,
        ) {
            Text(stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
        }
    }
}

internal fun Modifier.libraryFocus(tag: String, requester: FocusRequester, onFocused: (String) -> Unit) =
    testTag(tag).onFocusChanged { if (it.isFocused) onFocused(tag) }.focusRequester(requester)
