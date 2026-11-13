package com.xiaojiwei.fbng.ui

import android.view.InputDevice
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import kotlin.math.max

@Composable
internal fun LibraryTools(
    playableOnly: Boolean,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onOpenLetters: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val jumpLabel = stringResource(R.string.library_jump_to_letter)
    val statusLabel = stringResource(filterLabel(playableOnly))
    val textStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val widestLabel = max(
        textMeasurer.measure(jumpLabel, textStyle).size.width,
        textMeasurer.measure(statusLabel, textStyle).size.width,
    )
    val controls: @Composable (Modifier) -> Unit = { modifier ->
        FbngButton(
            onOpenLetters,
            modifier.libraryFocus(ShellTags.JumpToLetter, requesters.getValue(ShellTags.JumpToLetter), onFocused),
            kind = FbngButtonKind.Neutral,
        ) {
            Text(jumpLabel, style = textStyle)
        }
        FbngButton(
            onOpenFilter,
            modifier.libraryFocus(ShellTags.LibraryFilter, requesters.getValue(ShellTags.LibraryFilter), onFocused),
            kind = FbngButtonKind.Neutral,
        ) {
            Text(
                statusLabel,
                style = textStyle,
                color = if (playableOnly) scheme.secondary else scheme.onSurface,
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = with(LocalDensity.current) { widestLabel.toDp() }
        val horizontal = maxWidth >= (labelWidth + FbngDimens.space32) * 2 + FbngDimens.space8
        if (horizontal) {
            Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                controls(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                controls(Modifier.fillMaxWidth())
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
    val scheme = MaterialTheme.colorScheme
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
                ) {
                    Text(
                        stringResource(filterLabel(option)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (option == playableOnly) scheme.secondary else scheme.onSurface,
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
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

@Composable
internal fun LibrarySearch(
    query: String,
    onQuery: (String) -> Unit,
    onSearchFocus: (Boolean) -> Unit,
    onExitSearch: () -> Unit,
    onClear: () -> Unit,
    requester: FocusRequester,
    clearRequester: FocusRequester,
    onFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val clearIcon: (@Composable () -> Unit)? = if (query.isEmpty()) null else {
        {
            val label = stringResource(R.string.search_clear)
            Box(
                Modifier.size(FbngDimens.buttonHeight)
                    .focusProperties { left = requester }
                    .libraryFocus(ShellTags.ClearSearch, clearRequester, onFocused)
                    .semantics { contentDescription = label }
                    .fbngFocusTarget(onClick = onClear, container = scheme.surface, shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                LibrarySearchGlyph(scheme.onSurfaceVariant, clear = true)
            }
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                onSearchFocus(it.isFocused)
                if (it.isFocused) onFocused(ShellTags.Search)
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyUp &&
                        (event.key == Key.ButtonB || event.key == Key.Back || event.key == Key.Escape) -> {
                        onExitSearch()
                        true
                    }
                    event.key == Key.ButtonA -> {
                        if (event.type == KeyEventType.KeyUp) keyboard?.show()
                        true
                    }
                    query.isNotEmpty() && event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown &&
                        (!event.nativeKeyEvent.isFromSource(InputDevice.SOURCE_KEYBOARD) ||
                            event.nativeKeyEvent.isFromSource(InputDevice.SOURCE_DPAD)) -> clearRequester.requestFocus()
                    else -> false
                }
            }
            .focusProperties { if (query.isNotEmpty()) right = clearRequester }
            .testTag(ShellTags.Search)
            .focusRequester(requester),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(stringResource(R.string.search_label)) },
        leadingIcon = { LibrarySearchGlyph(scheme.onSurfaceVariant, clear = false) },
        trailingIcon = clearIcon,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onExitSearch() }),
        shape = RoundedCornerShape(FbngDimens.cardRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            cursorColor = scheme.primary,
            focusedLabelColor = scheme.secondary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
        ),
    )
}

@Composable
private fun LibrarySearchGlyph(color: Color, clear: Boolean) {
    Canvas(Modifier.size(FbngDimens.icon)) {
        val stroke = size.minDimension / 12
        if (clear) {
            val left = size.width * 0.25f
            val right = size.width - left
            val top = size.height * 0.25f
            val bottom = size.height - top
            drawLine(color, Offset(left, top), Offset(right, bottom), stroke, StrokeCap.Round)
            drawLine(color, Offset(right, top), Offset(left, bottom), stroke, StrokeCap.Round)
        } else {
            drawCircle(
                color,
                radius = size.minDimension / 4,
                center = Offset(size.width * 0.42f, size.height * 0.42f),
                style = Stroke(stroke),
            )
            drawLine(
                color,
                Offset(size.width * 0.60f, size.height * 0.60f),
                Offset(size.width * 0.80f, size.height * 0.80f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}
