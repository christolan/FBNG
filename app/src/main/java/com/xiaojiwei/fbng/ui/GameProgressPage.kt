package com.xiaojiwei.fbng.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.artwork.CoverImage
import com.xiaojiwei.fbng.game.GameProgressStore
import com.xiaojiwei.fbng.game.SaveSlotInfo
import com.xiaojiwei.fbng.game.SavedSlot
import com.xiaojiwei.fbng.game.SnapshotPage
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProgressAction { DeleteSlot, DeleteSnapshot, Apply }
private data class ProgressConfirmation(val action: ProgressAction, val id: Int, val target: Int = 1, val state: SavedSlot? = null)

/** Shared launch/pause page. Browsing snapshots never changes a running session's slot. */
@Composable
internal fun GameProgressPage(
    entry: LibraryEntry,
    cover: CoverImage?,
    fold: FoldSnapshot?,
    favorite: Boolean,
    store: GameProgressStore,
    busy: Boolean,
    ready: Boolean,
    message: String,
    onPrimary: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    messageIsError: Boolean = false,
    activeSlot: Int? = null,
    onCreateSnapshot: (suspend () -> Int)? = null,
    onApplySnapshot: (suspend (Int) -> SavedSlot)? = null,
    onDeleteCurrent: (suspend () -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
    onDialogOpen: (Boolean) -> Unit = {},
    onOperationError: (Exception) -> Unit = {},
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var selectedSlot by rememberSaveable(entry.game.name) { mutableStateOf(1) }
    var selectedSnapshot by rememberSaveable(entry.game.name) { mutableStateOf<Int?>(null) }
    var snapshotsOpen by rememberSaveable(entry.game.name) { mutableStateOf(false) }
    var page by rememberSaveable(entry.game.name) { mutableStateOf(0) }
    var slots by remember(store) { mutableStateOf<List<SaveSlotInfo>?>(null) }
    var snapshotPage by remember(store) { mutableStateOf(SnapshotPage(emptyList(), 0, 0)) }
    var working by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0) }
    var loadedRevision by remember { mutableStateOf(-1) }
    var listingError by remember { mutableStateOf(false) }
    var localMessage by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ProgressConfirmation?>(null) }
    var targetSnapshot by remember { mutableStateOf<Pair<Int, SavedSlot>?>(null) }
    var targetSlot by remember { mutableStateOf(1) }
    var initialSelection by remember(store) { mutableStateOf(false) }
    val paused = activeSlot != null
    val currentSlot = activeSlot ?: selectedSlot
    val primary = if (paused) ShellTags.Pause else ShellTags.Play
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(primary) }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var focusRevision by remember { mutableStateOf(0) }
    fun restore(key: String) { restoreKey = key; focusRevision++ }
    fun target(key: String): Modifier = Modifier.libraryFocus(key, requester(key)) { focused = it }
    val dialogOpen = pending != null || targetSnapshot != null
    val blocked = busy || working
    val loaded = slots != null && loadedRevision == revision && !listingError
    val canOperate = loaded && ready && !blocked
    val focusManager = LocalFocusManager.current
    val window = LocalWindowInfo.current
    val windowSize = window.containerSize
    val windowFocused = window.isWindowFocused

    LaunchedEffect(store, revision, page) {
        try {
            val data = withContext(Dispatchers.IO) {
                Triple(store.listSlots(), store.snapshotPage(page), if (!initialSelection) store.selectedSlot() else selectedSlot)
            }
            slots = data.first
            snapshotPage = data.second
            if (!initialSelection) { selectedSlot = data.third; initialSelection = true }
            page = data.second.page
            if (data.second.items.none { it.id == selectedSnapshot }) selectedSnapshot = data.second.items.firstOrNull()?.id
            loadedRevision = revision
            listingError = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            listingError = true
            localError = true
            localMessage = resources.getString(R.string.progress_error, error.message.orEmpty())
        }
    }
    LaunchedEffect(dialogOpen, working) {
        onDialogOpen(dialogOpen || working)
        if (dialogOpen) focusManager.clearFocus(force = true)
    }
    DisposableEffect(Unit) { onDispose { onDialogOpen(false) } }
    LaunchedEffect(blocked, ready, loaded, dialogOpen, snapshotsOpen, page, windowSize, windowFocused, focusRevision, fold) {
        if (blocked || dialogOpen || !windowFocused) return@LaunchedEffect
        if (ready && !loaded && !listingError) return@LaunchedEffect
        val fallback = if (ready && loaded) primary else if (paused) ShellTags.Quit else ShellTags.DetailBack
        val keys = listOfNotNull(restoreKey, focused, fallback).distinct()
        repeat(4) {
            withFrameNanos { }
            for (key in keys) if (runCatching { requester(key).requestFocus() }.getOrDefault(false)) {
                restoreKey = null
                return@LaunchedEffect
            }
        }
    }
    fun operate(focusAfter: String? = null, action: suspend () -> Unit) {
        if (blocked) return
        val originalFocus = focused
        working = true
        localError = false
        scope.launch {
            try { action() } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("FBNG", "Progress operation failed", error)
                localError = true
                localMessage = resources.getString(R.string.progress_error, error.message.orEmpty())
                onOperationError(error)
            } finally {
                working = false
                revision++
                restore(if (localError) originalFocus else focusAfter ?: originalFocus)
            }
        }
    }
    fun dismiss() {
        if (blocked) return
        pending = null
        targetSnapshot = null
        restore(focused)
    }
    fun back() {
        if (blocked) return
        if (dialogOpen) dismiss() else onBack()
    }
    BackHandler(onBack = ::back)
    fun prepareApply(id: Int) = operate {
        val state = withContext(Dispatchers.IO) { store.readSnapshot(id) }
        if (activeSlot != null) pending = ProgressConfirmation(ProgressAction.Apply, id, activeSlot, state)
        else { targetSlot = selectedSlot; targetSnapshot = id to state }
    }
    fun changePage(delta: Int) {
        if (blocked || !loaded || !snapshotsOpen) return
        val next = (page + delta).coerceIn(0, snapshotPage.pages - 1)
        if (next == page) return
        page = next
        loadedRevision = -1
        restore("snapshot-first")
    }
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.background, contentColor = scheme.onBackground,
        modifier = Modifier.fillMaxSize().onKeyEvent { event ->
            when {
                event.key == Key.ButtonB && event.type == KeyEventType.KeyUp -> { back(); true }
                !dialogOpen && snapshotsOpen && event.type == KeyEventType.KeyUp && event.key == Key.ButtonL1 -> { changePage(-1); true }
                !dialogOpen && snapshotsOpen && event.type == KeyEventType.KeyUp && event.key == Key.ButtonR1 -> { changePage(1); true }
                else -> false
            }
        },
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
                    if (left >= maxWidth - rightStart) Modifier.absolutePadding(right = maxWidth - left) else Modifier.absolutePadding(left = rightStart)
                }
                else -> with(density) { Modifier.padding(top = (hinge.hingeEndPx - origin.y).toDp().coerceIn(Dp.Hairline, maxHeight)) }
            }
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }.then(pane)) {
                ShellPage(title = entry.title, margin = FbngDimens.compactMargin, leadingAction = {
                    ShellHeaderBack(stringResource(if (paused) R.string.resume else R.string.back), ::back, target(ShellTags.DetailBack), enabled = !blocked)
                }) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            val showCover = maxHeight >= FbngDimens.shortShellHeight
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                                if (showCover) GameCover(entry.title, cover, Modifier.fillMaxWidth().aspectRatio(FbngDimens.coverAspectRatio).clip(MaterialTheme.shapes.medium))
                                FbngPanel {
                                    FbngButton(onClick = { onPrimary(currentSlot) }, modifier = target(primary).fillMaxWidth(), enabled = ready && !blocked && loaded) {
                                        Text(stringResource(if (paused) R.string.resume else if (entry.game.canLaunch) R.string.play else R.string.folder_configure))
                                    }
                                    Text(stringResource(when {
                                        paused -> R.string.progress_current
                                        slots?.firstOrNull { it.id == currentSlot }?.available == true -> R.string.progress_selected
                                        else -> R.string.progress_empty_selected
                                    }, currentSlot), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                }
                                if (onQuit != null) FbngButton(onQuit, target(ShellTags.Quit).fillMaxWidth(), enabled = !blocked, kind = FbngButtonKind.Neutral) {
                                    Text(stringResource(if (ready) R.string.progress_save_quit else R.string.quit))
                                }
                                FbngButton(onToggleFavorite, target(ShellTags.ToggleFavorite).fillMaxWidth(), enabled = !blocked, kind = FbngButtonKind.Neutral) {
                                    Text(stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add))
                                }
                                if (!paused) FbngPanel {
                                    val game = entry.game
                                    if (game.fileNames.isNotEmpty()) Text(game.fileNames.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                                    for (dependency in game.missing) Text(stringResource(if (dependency.isBios) R.string.library_missing_bios else R.string.library_missing_parent, dependency.name), color = scheme.error, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        Column(Modifier.weight(3f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8), verticalAlignment = Alignment.CenterVertically) {
                                FbngButton({ snapshotsOpen = false }, target("slots-tab"), enabled = !blocked, selected = !snapshotsOpen, kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.progress_slots)) }
                                FbngButton({ snapshotsOpen = true }, target("snapshots-tab"), enabled = !blocked, selected = snapshotsOpen, kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.progress_snapshots) + " ${snapshotPage.total}") }
                                Text(stringResource(if (paused) R.string.progress_auto_target else R.string.progress_choose_hint, currentSlot), modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (!loaded) {
                                FbngPanel {
                                    Text(if (listingError) localMessage else stringResource(R.string.progress_loading), style = MaterialTheme.typography.bodyLarge)
                                    if (listingError) FbngButton({ revision++ }, target("retry"), enabled = !blocked) { Text(stringResource(R.string.progress_retry)) }
                                }
                            } else if (!snapshotsOpen) {
                                Row(Modifier.fillMaxWidth().padding(FbngDimens.space4), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                                    slots.orEmpty().forEach { slot ->
                                        ProgressCard(store, slot, snapshot = false, selected = slot.id == currentSlot, revision = revision,
                                            modifier = target("progress-slot-${slot.id}").weight(1f), enabled = canOperate && !paused,
                                            caption = if (paused && slot.id != activeSlot) stringResource(R.string.progress_locked) else if (slot.id == currentSlot) stringResource(R.string.progress_chosen) else "",
                                            onClick = { selectedSlot = slot.id })
                                    }
                                }
                                FbngPanel {
                                    Text(stringResource(R.string.progress_slot, currentSlot), style = MaterialTheme.typography.titleLarge)
                                    Text(stringResource(if (paused || slots.orEmpty().any { it.id == currentSlot && it.available }) R.string.progress_slot_hint else R.string.progress_empty_selected, currentSlot), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                    FbngButton({ pending = ProgressConfirmation(ProgressAction.DeleteSlot, currentSlot) }, target("delete-progress"), enabled = canOperate && (paused || slots.orEmpty().any { it.id == currentSlot && it.available }), kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.progress_delete)) }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8), verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.progress_snapshot_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                    if (onCreateSnapshot != null) FbngButton({ operate("create-snapshot") { onCreateSnapshot(); page = 0; selectedSnapshot = null; localMessage = resources.getString(R.string.progress_created) } }, target("create-snapshot"), enabled = canOperate, kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.progress_create_snapshot)) }
                                }
                                Row(Modifier.fillMaxWidth().padding(FbngDimens.space4), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                                    if (snapshotPage.items.isEmpty()) Text(stringResource(R.string.progress_no_snapshots), Modifier.padding(FbngDimens.space16), style = MaterialTheme.typography.bodyLarge)
                                    snapshotPage.items.forEachIndexed { index, snapshot ->
                                        ProgressCard(store, snapshot, snapshot = true, selected = selectedSnapshot == snapshot.id, revision = revision,
                                            modifier = target(if (index == 0) "snapshot-first" else "snapshot-${snapshot.id}").weight(1f), enabled = canOperate,
                                            caption = snapshot.sourceSlot?.let { stringResource(R.string.progress_snapshot_source, it) } ?: stringResource(R.string.progress_legacy),
                                            onClick = { selectedSnapshot = snapshot.id })
                                    }
                                    if (snapshotPage.items.isNotEmpty()) repeat(SnapshotPage.PAGE_SIZE - snapshotPage.items.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                    FbngButton({ changePage(-1) }, target("snapshot-prev"), enabled = canOperate && page > 0, kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.library_previous_page)) }
                                    Text(stringResource(R.string.progress_page, page + 1, snapshotPage.pages), style = MaterialTheme.typography.bodyLarge)
                                    FbngButton({ changePage(1) }, target("snapshot-next"), enabled = canOperate && page + 1 < snapshotPage.pages, kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.library_next_page)) }
                                }
                                selectedSnapshot?.takeIf { id -> snapshotPage.items.any { it.id == id } }?.let { id ->
                                    FbngPanel {
                                        Text(stringResource(R.string.progress_snapshot, id), style = MaterialTheme.typography.titleLarge)
                                        Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                            FbngButton({ prepareApply(id) }, target("apply-snapshot"), enabled = canOperate, kind = FbngButtonKind.Neutral) { Text(if (paused) stringResource(R.string.progress_apply_slot, currentSlot) else stringResource(R.string.progress_apply)) }
                                            FbngButton({ pending = ProgressConfirmation(ProgressAction.DeleteSnapshot, id) }, target("delete-snapshot"), enabled = canOperate, kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.progress_delete_snapshot)) }
                                        }
                                    }
                                }
                            }
                            val text = if (localMessage.isNotEmpty()) localMessage else message
                            if (text.isNotEmpty()) Text(text, color = if (localError || (localMessage.isEmpty() && messageIsError)) scheme.error else scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
    targetSnapshot?.let { (id, state) ->
        val cancel = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; cancel.requestFocus() }
        AlertDialog(onDismissRequest = ::dismiss, shape = MaterialTheme.shapes.medium,
            title = { DialogGamepadInput(); Text(stringResource(R.string.progress_target_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                    Text(stringResource(R.string.progress_target_hint))
                    Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                        slots.orEmpty().forEach { slot ->
                            FbngButton({ targetSlot = slot.id }, Modifier.weight(1f), selected = targetSlot == slot.id, kind = FbngButtonKind.Neutral) {
                                Text(stringResource(R.string.progress_slot, slot.id) + if (slot.available) "" else " · " + stringResource(R.string.slot_no_save))
                            }
                        }
                    }
                }
            },
            dismissButton = { FbngButton(::dismiss, Modifier.then(Modifier.libraryFocus("target-cancel", cancel) {}), kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { FbngButton({ targetSnapshot = null; pending = ProgressConfirmation(ProgressAction.Apply, id, targetSlot, state) }) { Text(stringResource(R.string.progress_next)) } },
            modifier = Modifier.onKeyEvent { if (it.key == Key.ButtonB && it.type == KeyEventType.KeyUp) { dismiss(); true } else false },
        )
    }
    pending?.let { confirmation ->
        val title = when (confirmation.action) {
            ProgressAction.DeleteSlot -> stringResource(if (paused) R.string.progress_delete_running_title else R.string.progress_delete_title, confirmation.id)
            ProgressAction.DeleteSnapshot -> stringResource(R.string.progress_delete_snapshot_title, confirmation.id)
            ProgressAction.Apply -> stringResource(R.string.progress_apply_title, confirmation.id, confirmation.target)
        }
        val body = when (confirmation.action) {
            ProgressAction.DeleteSlot -> stringResource(if (paused) R.string.progress_delete_running_body else R.string.progress_delete_body)
            ProgressAction.DeleteSnapshot -> stringResource(R.string.progress_delete_snapshot_body)
            ProgressAction.Apply -> stringResource(if (paused) R.string.progress_apply_running_body else R.string.progress_apply_body)
        } + (confirmation.state?.takeIf { it.coreCommit.isNotEmpty() && it.coreCommit != BuildConfig.FBNEO_COMMIT }?.let {
            "\n" + stringResource(R.string.load_version_warn, it.coreCommit.take(12), BuildConfig.FBNEO_COMMIT_SHORT)
        } ?: "")
        GameConfirmationDialog(title, body,
            actionLabel = when (confirmation.action) {
                ProgressAction.DeleteSlot -> stringResource(if (paused) R.string.progress_delete_running else R.string.progress_delete)
                ProgressAction.DeleteSnapshot -> stringResource(R.string.progress_delete_snapshot)
                ProgressAction.Apply -> stringResource(R.string.progress_apply_slot, confirmation.target)
            }, onDismiss = ::dismiss,
            onConfirm = {
                pending = null
                operate(if (confirmation.action == ProgressAction.Apply || confirmation.action == ProgressAction.DeleteSlot) primary else "snapshots-tab") {
                    when (confirmation.action) {
                        ProgressAction.DeleteSlot -> {
                            if (paused) requireNotNull(onDeleteCurrent).invoke() else withContext(NonCancellable + Dispatchers.IO) { store.deleteSlot(confirmation.id) }
                            localMessage = resources.getString(R.string.progress_deleted)
                        }
                        ProgressAction.DeleteSnapshot -> {
                            withContext(NonCancellable + Dispatchers.IO) { store.deleteSnapshot(confirmation.id) }
                            selectedSnapshot = null
                            localMessage = resources.getString(R.string.progress_snapshot_deleted)
                        }
                        ProgressAction.Apply -> {
                            if (paused) requireNotNull(onApplySnapshot).invoke(confirmation.id) else withContext(NonCancellable + Dispatchers.IO) { store.applySnapshot(confirmation.id, confirmation.target) }
                            selectedSlot = confirmation.target
                            snapshotsOpen = false
                            localMessage = resources.getString(if (paused) R.string.progress_applied_running else R.string.progress_applied)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ProgressCard(
    store: GameProgressStore,
    slot: SaveSlotInfo,
    snapshot: Boolean,
    selected: Boolean,
    revision: Int,
    modifier: Modifier,
    enabled: Boolean,
    caption: String,
    onClick: () -> Unit,
) {
    val image by produceState<ImageBitmap?>(null, store, snapshot, slot.id, slot.savedAtEpochMs, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (if (snapshot) store.snapshotCover(slot.id) else store.slotCover(slot.id))?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val time = remember(slot.savedAtEpochMs) {
        if (slot.savedAtEpochMs > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(slot.savedAtEpochMs)) else ""
    }
    Column(modifier.fbngFocusTarget(onClick, if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh, enabled = enabled)
        .padding(FbngDimens.space8), verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
        Box(Modifier.fillMaxWidth().aspectRatio(FbngDimens.coverAspectRatio).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            val bitmap = image
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(stringResource(if (slot.available) R.string.slot_no_cover else R.string.slot_no_save), Modifier.padding(FbngDimens.space8), style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(if (snapshot) R.string.progress_snapshot else R.string.progress_slot, slot.id) + if (selected) " ✓" else "", style = MaterialTheme.typography.titleLarge)
        if (time.isNotEmpty() && slot.available) Text(time, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
    }
}
