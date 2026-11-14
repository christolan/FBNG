package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngIsland

@Composable
internal fun LibraryFilters(
    query: String,
    playableOnly: Boolean,
    wide: Boolean,
    compactWidth: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onOpenLetters: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val letters = stringResource(R.string.library_jump_to_letter)
    val status = stringResource(filterLabel(playableOnly))
    val statusFilter = stringResource(R.string.library_filter_title)
    val scheme = MaterialTheme.colorScheme
    val statusColor = if (playableOnly) scheme.secondary else scheme.onSurface
    ShellHeaderButton(
        onClick = onOpenLetters,
        selected = query.isNotEmpty(),
        modifier = Modifier
            .width(if (wide) FbngDimens.libraryLetterFilterWidth else compactWidth)
            .libraryFocus(ShellTags.JumpToLetter, requesters.getValue(ShellTags.JumpToLetter), onFocused)
            .semantics {
                contentDescription = letters
                if (query.isNotEmpty()) stateDescription = query
            },
    ) {
        Text(
            if (wide) query.ifEmpty { letters } else stringResource(R.string.library_letters_short),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ShellHeaderButton(
        onClick = onOpenFilter,
        selected = playableOnly,
        modifier = Modifier
            .width(if (wide) FbngDimens.libraryStatusFilterWidth else compactWidth)
            .libraryFocus(ShellTags.LibraryFilter, requesters.getValue(ShellTags.LibraryFilter), onFocused)
            .semantics {
                contentDescription = statusFilter
                stateDescription = status
            },
    ) {
        if (wide) {
            Text(
                status,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Canvas(Modifier.size(FbngDimens.icon)) {
                val stroke = size.minDimension / 10
                drawLine(statusColor, Offset(size.width * .15f, size.height * .24f),
                    Offset(size.width * .85f, size.height * .24f), stroke, StrokeCap.Round)
                drawLine(statusColor, Offset(size.width * .28f, size.height * .5f),
                    Offset(size.width * .72f, size.height * .5f), stroke, StrokeCap.Round)
                drawLine(statusColor, Offset(size.width * .42f, size.height * .76f),
                    Offset(size.width * .58f, size.height * .76f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun LibraryStatusPicker(
    playableOnly: Boolean,
    onDismiss: () -> Unit,
    onChoose: (Boolean) -> Unit,
) {
    val current = remember { FocusRequester() }
    LibraryPickerPanel(R.string.library_filter_title, onDismiss) {
        LaunchedEffect(Unit) { current.requestFocus() }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(FbngDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        ) {
            for (option in listOf(false, true)) {
                val modifier = if (option == playableOnly) Modifier.focusRequester(current) else Modifier
                FbngButton(
                    onClick = { onChoose(option) },
                    modifier = modifier.fillMaxWidth().semantics { selected = option == playableOnly },
                    kind = FbngButtonKind.Neutral,
                    selected = option == playableOnly,
                ) {
                    Text(
                        stringResource(filterLabel(option)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private fun filterLabel(playableOnly: Boolean) =
    if (playableOnly) R.string.library_filter_playable else R.string.library_filter_all

@Composable
private fun LibraryPickerPanel(title: Int, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val closeLabel = stringResource(R.string.library_close_picker)
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            .semantics {
                onClick(label = closeLabel) {
                    onDismiss()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        LibraryPickerCard(
            title,
            Modifier.padding(FbngDimens.space16).widthIn(max = FbngDimens.libraryPickerWidth).fillMaxWidth(),
            content,
        )
    }
}

@Composable
private fun LibraryPickerCard(title: Int, modifier: Modifier, content: @Composable () -> Unit) {
    val panelTitle = stringResource(title)
    Column(
        modifier.focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = panelTitle }
            .fbngIsland()
            .padding(FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
    ) {
        Text(
            panelTitle,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}
