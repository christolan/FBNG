package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.xiaojiwei.fbng.library.LibraryResults
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngEmptyState
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

private const val LibraryColumns = 2
private const val LibraryRows = 5
internal const val LibraryPageSize = LibraryColumns * LibraryRows

@Composable
internal fun GameLibraryPage(
    results: LibraryResults,
    totalGames: Int,
    playableOnly: Boolean,
    emptyMessage: Int,
    pageIndex: Int,
    onPage: (Int) -> Unit,
    cardRequesters: MutableMap<String, FocusRequester>,
    wide: Boolean,
    compactBottomBar: Boolean,
    margin: Dp,
    busy: Boolean,
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
    val libraryTitle = stringResource(R.string.shell_library)
    val games = results.entries.drop(pageIndex * LibraryPageSize).take(LibraryPageSize)
    val pages = if (results.entries.isEmpty()) 0 else (results.entries.size - 1) / LibraryPageSize + 1
    val rowGap = if (compactBottomBar) FbngDimens.space4 else FbngDimens.space8
    ShellPage(
        title = libraryTitle,
        margin = margin,
        contentGap = if (compactBottomBar) FbngDimens.space4 else FbngDimens.space12,
        headerAction = { width ->
            val labelWidth = with(LocalDensity.current) { MaterialTheme.typography.labelLarge.fontSize.toDp() } * 4
            val wideHeader = width >= FbngDimens.libraryHeaderWide
            val buttonWidth = if (wideHeader) FbngDimens.shellHeaderHeight else FbngDimens.buttonHeight
            LibraryFilters(
                query = query,
                playableOnly = playableOnly,
                wide = wideHeader && labelWidth + FbngDimens.space8 <= FbngDimens.libraryStatusFilterWidth,
                requesters = requesters,
                onFocused = onFocused,
                onOpenLetters = onOpenLetters,
                onOpenFilter = onOpenFilter,
            )
            ShellPagination(pageIndex, pages, buttonWidth, busy, requesters, onFocused, onPage)
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val rowHeight = (maxHeight - rowGap * (LibraryRows - 1)) / LibraryRows
                val inlineText = rowHeight < gameCardTextHeight()
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                ) {
                    // Fill down each column so up/down follows the list order.
                    repeat(LibraryColumns) { column ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(rowGap),
                        ) {
                            repeat(LibraryRows) { row ->
                                val entry = games.getOrNull(column * LibraryRows + row)
                                val rowModifier = Modifier.weight(1f).fillMaxWidth()
                                if (entry == null) {
                                    Spacer(rowModifier)
                                } else {
                                    val name = entry.game.name
                                    key(name) {
                                        val tag = ShellTags.game(name)
                                        val requester = remember { FocusRequester() }
                                        DisposableEffect(tag, requester) {
                                            cardRequesters[tag] = requester
                                            onDispose { if (cardRequesters[tag] == requester) cardRequesters.remove(tag) }
                                        }
                                        val cardModifier = rowModifier.focusProperties {
                                            if (column == 0 && wide) left = requesters.getValue(ShellTags.NavLibrary)
                                        }.libraryFocus(tag, requester) {
                                            onFocused(it)
                                            onSelected(name)
                                        }.fbngFocusTarget(
                                            onClick = { onDetails(name) },
                                            container = scheme.surfaceContainerHigh,
                                        )
                                        GameCardText(entry, cardModifier, inlineText = inlineText)
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
internal fun gameCardTextHeight(): Dp {
    val type = MaterialTheme.typography
    return with(LocalDensity.current) {
        (type.titleMedium.lineHeight.roundToPx() + type.bodySmall.lineHeight.roundToPx() +
            FbngDimens.space8.roundToPx() * 2 + FbngDimens.space4.roundToPx()).toDp()
    }
}

@Composable
internal fun GameCardText(entry: LibraryEntry, modifier: Modifier, inlineText: Boolean = false) {
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
    val fileDetails: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4)) {
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
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (inlineText) {
        Row(
            modifier.padding(horizontal = FbngDimens.space8),
            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            title(Modifier.weight(3f))
            fileDetails(Modifier.weight(2f))
        }
    } else {
        Column(
            modifier.padding(horizontal = FbngDimens.space8, vertical = FbngDimens.space8),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space4, Alignment.CenterVertically),
        ) {
            title(Modifier.fillMaxWidth())
            fileDetails(Modifier.fillMaxWidth())
        }
    }
}

internal fun Modifier.libraryFocus(tag: String, requester: FocusRequester, onFocused: (String) -> Unit) =
    testTag(tag).onFocusChanged { if (it.isFocused) onFocused(tag) }.focusRequester(requester)
