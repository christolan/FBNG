package com.xiaojiwei.fbng.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.artwork.CoverImage
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GameLaunchPage(
    entry: LibraryEntry,
    cover: CoverImage?,
    fold: FoldSnapshot?,
    favorite: Boolean,
    busy: Boolean,
    launchError: Boolean,
    onPlay: (Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onConfigureFolders: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context, entry.game.name) {
        com.xiaojiwei.fbng.game.GameProgressStore(AppStorage(context.filesDir).stateDir(entry.game.name))
    }
    GameProgressPage(
        entry = entry, cover = cover, fold = fold, favorite = favorite, store = store,
        busy = busy, ready = true,
        message = when {
            launchError -> stringResource(R.string.preparing_game_failed)
            busy -> stringResource(R.string.preparing_game)
            else -> ""
        },
        messageIsError = launchError,
        onPrimary = { if (entry.game.canLaunch) onPlay(it) else onConfigureFolders() },
        onToggleFavorite = onToggleFavorite, onBack = onBack,
    )
}

@Composable
internal fun GameMenuPage(
    entry: LibraryEntry,
    cover: CoverImage?,
    fold: FoldSnapshot?,
    favorite: Boolean,
    busy: Boolean,
    dialogOpen: Boolean,
    ready: Boolean,
    message: String,
    onPrimary: () -> Unit,
    onLoad: (() -> Unit)?,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    messageIsError: Boolean = false,
    failed: Boolean = false,
    hasAutoSave: Boolean? = null,
    onSave: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
) {
    val paused = onQuit != null
    val primary = if (paused) ShellTags.Pause else ShellTags.Play
    val requesters = remember {
        listOf(primary, ShellTags.DetailBack, ShellTags.ToggleFavorite, ShellTags.Save, ShellTags.Load,
            ShellTags.Reset, ShellTags.Quit).associateWith { FocusRequester() }
    }
    var focused by rememberSaveable { mutableStateOf(primary) }
    val focusManager = LocalFocusManager.current
    val window = LocalWindowInfo.current
    val windowFocused = window.isWindowFocused
    val windowSize = window.containerSize
    fun target(tag: String) = Modifier.libraryFocus(tag, requesters.getValue(tag)) { focused = it }
    LaunchedEffect(ready, busy, dialogOpen, windowFocused, windowSize, fold) {
        if (dialogOpen) focusManager.clearFocus(force = true)
        if (busy || dialogOpen || !windowFocused) return@LaunchedEffect
        val fallback = if (ready) primary else ShellTags.Quit
        val preferred = if (!ready && focused == primary) fallback else focused
        repeat(4) {
            withFrameNanos { }
            for (tag in listOf(preferred, fallback).distinct()) {
                if (runCatching { requesters.getValue(tag).requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.key == Key.ButtonB && event.type == KeyEventType.KeyUp) {
                if (!dialogOpen && !busy) onBack()
                true
            } else false
        },
        color = scheme.background,
        contentColor = scheme.onBackground,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            var origin by remember { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current
            val hinge = fold?.takeIf { it.separating }
            val pane = when {
                hinge == null -> Modifier
                hinge.orientationVertical -> with(density) {
                    val left = (hinge.hingeStartPx - origin.x).toDp().coerceIn(Dp.Hairline, maxWidth)
                    val rightStart = (hinge.hingeEndPx - origin.x).toDp().coerceIn(left, maxWidth)
                    if (left >= maxWidth - rightStart) Modifier.absolutePadding(right = maxWidth - left)
                    else Modifier.absolutePadding(left = rightStart)
                }
                else -> with(density) {
                    val bottomStart = (hinge.hingeEndPx - origin.y).toDp().coerceIn(Dp.Hairline, maxHeight)
                    Modifier.padding(top = bottomStart)
                }
            }
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }.then(pane)) {
                ShellPage(
                    title = entry.title,
                    margin = FbngDimens.compactMargin,
                    leadingAction = if (paused) null else ({
                        ShellHeaderBack(stringResource(R.string.back), onBack, target(ShellTags.DetailBack), enabled = !busy)
                    }),
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val sideCover = cover != null && maxWidth >= FbngDimens.libraryToolbarBreakpoint
                        val artwork: @Composable (Modifier) -> Unit = { modifier ->
                            GameCover(entry.title, cover, modifier.aspectRatio(FbngDimens.coverAspectRatio)
                                .clip(MaterialTheme.shapes.medium))
                        }
                        Row(
                            Modifier.widthIn(max = FbngDimens.hudActionsWidth + FbngDimens.detailCoverWidth)
                                .fillMaxWidth().verticalScroll(scroll),
                            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16),
                        ) {
                            if (sideCover) artwork(Modifier.weight(1f).widthIn(max = FbngDimens.detailCoverWidth))
                            Column(
                                Modifier.weight(if (sideCover) 2f else 1f),
                                verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
                            ) {
                                FbngPanel {
                                    FbngButton(onPrimary, target(primary).fillMaxWidth(), enabled = ready && !busy) {
                                        Text(stringResource(when {
                                            paused -> R.string.resume
                                            entry.game.canLaunch -> R.string.play
                                            else -> R.string.folder_configure
                                        }))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                        if (onLoad != null) {
                                            FbngButton(
                                                onClick = {
                                                    focused = ShellTags.Load
                                                    onLoad()
                                                },
                                                modifier = target(ShellTags.Load).weight(1f),
                                                enabled = ready && !busy,
                                                kind = FbngButtonKind.Neutral,
                                            ) { Text(stringResource(R.string.load)) }
                                        }
                                        FbngButton(onToggleFavorite, target(ShellTags.ToggleFavorite).weight(1f),
                                            enabled = !busy, kind = FbngButtonKind.Neutral) {
                                            Text(stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add))
                                        }
                                    }
                                    if (onSave != null && onReset != null && onQuit != null) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                            FbngButton(
                                                onClick = {
                                                    focused = ShellTags.Save
                                                    onSave()
                                                },
                                                modifier = target(ShellTags.Save).weight(1f),
                                                enabled = ready && !busy,
                                                kind = FbngButtonKind.Neutral,
                                            ) { Text(stringResource(R.string.save)) }
                                            FbngButton(
                                                onClick = {
                                                    focused = ShellTags.Reset
                                                    onReset()
                                                },
                                                modifier = target(ShellTags.Reset).weight(1f),
                                                enabled = ready && !busy,
                                                kind = FbngButtonKind.Neutral,
                                            ) { Text(stringResource(R.string.reset)) }
                                        }
                                        FbngButton(onQuit, target(ShellTags.Quit).fillMaxWidth(), enabled = !busy,
                                            kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.quit)) }
                                    }
                                }
                                FbngPanel {
                                    if (paused) {
                                        Text(stringResource(when {
                                            failed -> R.string.game_stopped
                                            ready -> R.string.game_paused
                                            else -> R.string.pause_loading
                                        }), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                    } else {
                                        val game = entry.game
                                        if (entry.title != game.description) Text(game.description,
                                            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                        if (game.fileNames.isNotEmpty()) Text(game.fileNames.joinToString(" · "),
                                            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                        if (game.canLaunch) Text(stringResource(R.string.card_ready),
                                            style = MaterialTheme.typography.bodyLarge, color = scheme.secondary)
                                        for (dependency in game.missing) Text(stringResource(
                                            if (dependency.isBios) R.string.library_missing_bios else R.string.library_missing_parent,
                                            dependency.name,
                                        ), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
                                    }
                                    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyLarge,
                                        color = if (messageIsError) scheme.error else scheme.onSurface)
                                    if (hasAutoSave != null && !failed) Text(
                                        stringResource(if (hasAutoSave) R.string.slot_ready else R.string.slot_empty),
                                        style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                                    )
                                }
                                if (!sideCover && cover != null) artwork(Modifier.widthIn(max = FbngDimens.detailCoverWidth).fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}
