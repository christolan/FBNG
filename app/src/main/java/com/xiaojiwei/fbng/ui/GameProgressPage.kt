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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
private fun snapshotTag(id: Int) = "snapshot-$id"

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
    var snapshotPage by remember(store) { mutableStateOf<SnapshotPage?>(null) }
    var snapshotFocusIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var working by remember { mutableStateOf(false) }
    var slotsRevision by remember { mutableStateOf(0) }
    var snapshotsRevision by remember { mutableStateOf(0) }
    var loadedSlotsRevision by remember(store) { mutableStateOf(-1) }
    var loadedSnapshotsRevision by remember(store) { mutableStateOf(-1) }
    var slotsError by remember(store) { mutableStateOf(false) }
    var snapshotsError by remember(store) { mutableStateOf(false) }
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
    val slotsLoaded = loadedSlotsRevision == slotsRevision && !slotsError
    val snapshotsLoaded = loadedSnapshotsRevision == snapshotsRevision && snapshotPage?.page == page && !snapshotsError
    val primaryNeedsSlots = !paused && entry.game.canLaunch
    val primaryEnabled = ready && !blocked && (!primaryNeedsSlots || slotsLoaded)
    val canOperateSlots = slotsLoaded && ready && !blocked
    val canOperateSnapshots = snapshotsLoaded && ready && !blocked
    val focusManager = LocalFocusManager.current
    val window = LocalWindowInfo.current
    val windowSize = window.containerSize
    val windowFocused = window.isWindowFocused

    LaunchedEffect(store, slotsRevision) {
        try {
            val (items, savedSelection) = withContext(Dispatchers.IO) {
                store.listSlots() to if (!paused && !initialSelection) store.selectedSlot() else null
            }
            slots = items
            if (savedSelection != null) selectedSlot = savedSelection
            initialSelection = true
            loadedSlotsRevision = slotsRevision
            slotsError = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("FBNG", "Could not read progress slots", error)
            slotsError = true
        }
    }
    LaunchedEffect(store, snapshotsOpen, snapshotsRevision, page) {
        if (!snapshotsOpen || snapshotsLoaded) return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) { store.snapshotPage(page) }
            val currentTags = result.items.map { snapshotTag(it.id) }.toSet()
            snapshotPage?.items.orEmpty().map { snapshotTag(it.id) }.filterNot(currentTags::contains)
                .forEach { requesters.remove(it) }
            snapshotPage = result
            page = result.page
            loadedSnapshotsRevision = snapshotsRevision
            snapshotsError = false
            val requestedIndex = snapshotFocusIndex
            if (requestedIndex != null) {
                val id = result.items.getOrNull(requestedIndex.coerceAtMost(result.items.lastIndex))?.id
                selectedSnapshot = id
                restore(id?.let(::snapshotTag) ?: if (onCreateSnapshot != null) "create-snapshot" else "snapshots-tab")
                snapshotFocusIndex = null
            } else if (result.items.none { it.id == selectedSnapshot }) {
                selectedSnapshot = result.items.firstOrNull()?.id
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("FBNG", "Could not read snapshots", error)
            snapshotsError = true
        }
    }
    LaunchedEffect(dialogOpen, working) {
        onDialogOpen(dialogOpen || working)
        if (dialogOpen) focusManager.clearFocus(force = true)
    }
    DisposableEffect(Unit) { onDispose { onDialogOpen(false) } }
    LaunchedEffect(blocked, primaryEnabled, slotsError, snapshotsLoaded, snapshotsError, dialogOpen, snapshotsOpen, windowSize, windowFocused, focusRevision, fold) {
        if (blocked || dialogOpen || !windowFocused) return@LaunchedEffect
        if (focused == primary && primaryNeedsSlots && ready && !slotsLoaded && !slotsError) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            val keys = listOfNotNull(
                restoreKey, focused,
                "retry-snapshots".takeIf { snapshotsOpen && snapshotsError },
                "retry-slots".takeIf { !snapshotsOpen && slotsError },
                "snapshots-tab".takeIf { snapshotsOpen },
                primary.takeIf { primaryEnabled },
                if (paused) ShellTags.Quit else ShellTags.DetailBack,
            ).distinct()
            for (key in keys) if (runCatching { requesters[key]?.requestFocus() == true }.getOrDefault(false)) {
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
        val current = snapshotPage ?: return
        if (blocked || !snapshotsLoaded || !snapshotsOpen) return
        val next = (page + delta).coerceIn(0, current.pages - 1)
        if (next == page) return
        snapshotFocusIndex = current.items.indexOfFirst { snapshotTag(it.id) == focused }.takeIf { it >= 0 }
            ?: current.items.indexOfFirst { it.id == selectedSnapshot }.coerceAtLeast(0)
        page = next
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
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val sidebarWidth = (maxWidth / 4).coerceIn(
                            FbngDimens.progressSidebarMinWidth, FbngDimens.detailCoverWidth,
                        ).coerceAtMost(maxWidth * 0.45f)
                        val showCover = maxHeight >= FbngDimens.shortShellHeight
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
                            Column(
                                Modifier.width(sidebarWidth).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                            ) {
                                FbngButton(
                                    onClick = { onPrimary(currentSlot) },
                                    modifier = target(primary).fillMaxWidth(),
                                    enabled = primaryEnabled,
                                    contentPadding = PaddingValues(horizontal = FbngDimens.space8),
                                ) {
                                    Text(stringResource(if (paused) R.string.resume else if (entry.game.canLaunch) R.string.play else R.string.folder_configure))
                                }
                                val slotHint = when {
                                    paused -> R.string.progress_current
                                    !slotsLoaded -> null
                                    slots.orEmpty().any { it.id == currentSlot && it.available } -> R.string.progress_selected
                                    else -> R.string.progress_empty_selected
                                }
                                if (slotHint != null) {
                                    Text(stringResource(slotHint, currentSlot), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                }
                                if (onQuit != null) {
                                    FbngButton(onQuit, target(ShellTags.Quit).fillMaxWidth(), enabled = !blocked, kind = FbngButtonKind.Neutral) {
                                        Text(stringResource(if (ready) R.string.progress_save_quit else R.string.quit))
                                    }
                                }
                                Column(
                                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                                ) {
                                    val feedback = if (messageIsError) message else localMessage.ifEmpty { message }
                                    if (feedback.isNotEmpty()) {
                                        Text(feedback, color = if (localError || messageIsError) scheme.error else scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    FbngButton(onToggleFavorite, target(ShellTags.ToggleFavorite).fillMaxWidth(), enabled = !blocked, kind = FbngButtonKind.Neutral) {
                                        Text(stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add))
                                    }
                                    if (!paused) {
                                        val game = entry.game
                                        if (game.fileNames.isNotEmpty() || game.missing.isNotEmpty()) FbngPanel {
                                            for (dependency in game.missing) Text(stringResource(if (dependency.isBios) R.string.library_missing_bios else R.string.library_missing_parent, dependency.name), color = scheme.error, style = MaterialTheme.typography.bodyLarge)
                                            if (game.fileNames.isNotEmpty()) Text(game.fileNames.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                    if (showCover && cover != null) {
                                        GameCover(entry.title, cover, Modifier.fillMaxWidth().aspectRatio(FbngDimens.coverAspectRatio).clip(MaterialTheme.shapes.medium))
                                    }
                                }
                            }

                            val displayedPage = snapshotPage
                            val listLoaded = if (snapshotsOpen) snapshotsLoaded else slotsLoaded
                            val listError = if (snapshotsOpen) snapshotsError else slotsError
                            val items = if (snapshotsOpen) displayedPage?.items.orEmpty() else slots.orEmpty()
                            val snapshot = items.firstOrNull { snapshotsOpen && it.id == selectedSnapshot }
                            Column(
                                Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                            ) {
                                FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4)) {
                                        FbngButton({ snapshotsOpen = false }, target("slots-tab"), enabled = !blocked, selected = !snapshotsOpen, kind = FbngButtonKind.Neutral) {
                                            Text(stringResource(R.string.progress_slots))
                                        }
                                        FbngButton({ snapshotsOpen = true }, target("snapshots-tab"), enabled = !blocked, selected = snapshotsOpen, kind = FbngButtonKind.Neutral) {
                                            Text(stringResource(R.string.progress_snapshots) + (displayedPage?.let { " ${it.total}" } ?: ""))
                                        }
                                    }
                                    if (snapshotsOpen && displayedPage != null && displayedPage.pages > 1) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4), verticalAlignment = Alignment.CenterVertically) {
                                            SnapshotPageButton(false, { changePage(-1) }, target("snapshot-prev"), canOperateSnapshots && page > 0)
                                            Text(stringResource(R.string.progress_page, page + 1, displayedPage.pages), style = MaterialTheme.typography.bodyLarge)
                                            SnapshotPageButton(true, { changePage(1) }, target("snapshot-next"), canOperateSnapshots && page + 1 < displayedPage.pages)
                                        }
                                    }
                                }
                                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                    val previewHeight = (maxHeight - FbngDimens.progressCardMetadataHeight)
                                        .coerceIn(Dp.Hairline, FbngDimens.progressPreviewMaxHeight)
                                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                        when {
                                            !listLoaded -> FbngPanel {
                                                val status = when {
                                                    !listError -> R.string.progress_loading
                                                    snapshotsOpen -> R.string.progress_snapshots_read_failed
                                                    else -> R.string.progress_slots_read_failed
                                                }
                                                Text(stringResource(status), style = MaterialTheme.typography.bodyLarge, color = if (listError) scheme.error else scheme.onSurfaceVariant)
                                                if (listError) {
                                                    val retry = if (snapshotsOpen) "retry-snapshots" else "retry-slots"
                                                    FbngButton({
                                                        if (snapshotsOpen) {
                                                            snapshotsError = false
                                                            snapshotsRevision++
                                                        } else {
                                                            slotsError = false
                                                            slotsRevision++
                                                        }
                                                    }, target(retry), enabled = !blocked) { Text(stringResource(R.string.progress_retry)) }
                                                }
                                            }
                                            items.isEmpty() -> Text(
                                                stringResource(R.string.progress_no_snapshots),
                                                Modifier.padding(FbngDimens.space8),
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            else -> Row(
                                                Modifier.fillMaxWidth().padding(FbngDimens.space4),
                                                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                                            ) {
                                                for (item in items) key(snapshotsOpen, item.id) {
                                                    val tag = if (snapshotsOpen) snapshotTag(item.id) else "progress-slot-${item.id}"
                                                    val selected = if (snapshotsOpen) item.id == selectedSnapshot else item.id == currentSlot
                                                    val caption = when {
                                                        snapshotsOpen -> item.sourceSlot?.let { stringResource(R.string.progress_snapshot_source, it) }
                                                            ?: stringResource(R.string.progress_legacy)
                                                        paused && !selected -> stringResource(R.string.progress_locked)
                                                        else -> ""
                                                    }
                                                    ProgressCard(
                                                        store, item, snapshot = snapshotsOpen, selected = selected,
                                                        revision = if (snapshotsOpen) snapshotsRevision else slotsRevision,
                                                        previewHeight = previewHeight,
                                                        modifier = target(tag).weight(1f),
                                                        enabled = if (snapshotsOpen) canOperateSnapshots else canOperateSlots && !paused,
                                                        caption = caption,
                                                        onClick = { if (snapshotsOpen) selectedSnapshot = item.id else selectedSlot = item.id },
                                                    )
                                                }
                                                repeat(GameProgressStore.SLOT_COUNT - items.size) { Spacer(Modifier.weight(1f)) }
                                            }
                                        }
                                    }
                                }
                                if (listLoaded) FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                                ) {
                                    if (!snapshotsOpen) {
                                        FbngButton(
                                            { pending = ProgressConfirmation(ProgressAction.DeleteSlot, currentSlot) },
                                            target("delete-progress"),
                                            enabled = canOperateSlots && (paused || items.any { it.id == currentSlot && it.available }),
                                            kind = FbngButtonKind.Neutral,
                                        ) { Text(stringResource(R.string.progress_delete)) }
                                    } else {
                                        if (onCreateSnapshot != null) {
                                            FbngButton({
                                                operate {
                                                    onCreateSnapshot()
                                                    page = 0
                                                    snapshotFocusIndex = 0
                                                    snapshotsRevision++
                                                    localMessage = resources.getString(R.string.progress_created)
                                                }
                                            }, target("create-snapshot"), enabled = canOperateSnapshots, kind = FbngButtonKind.Neutral) {
                                                Text(stringResource(R.string.progress_create_snapshot))
                                            }
                                        }
                                        if (snapshot != null) {
                                            FbngButton(
                                                { prepareApply(snapshot.id) }, target("apply-snapshot"),
                                                enabled = canOperateSnapshots && (paused || slotsLoaded),
                                                kind = FbngButtonKind.Neutral,
                                            ) {
                                                Text(if (paused) stringResource(R.string.progress_apply_slot, currentSlot) else stringResource(R.string.progress_apply))
                                            }
                                            FbngButton(
                                                { pending = ProgressConfirmation(ProgressAction.DeleteSnapshot, snapshot.id) },
                                                target("delete-snapshot"), enabled = canOperateSnapshots, kind = FbngButtonKind.Neutral,
                                            ) { Text(stringResource(R.string.progress_delete_snapshot)) }
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
                operate(primary.takeIf { confirmation.action != ProgressAction.DeleteSnapshot }) {
                    when (confirmation.action) {
                        ProgressAction.DeleteSlot -> {
                            if (paused) requireNotNull(onDeleteCurrent).invoke() else withContext(NonCancellable + Dispatchers.IO) { store.deleteSlot(confirmation.id) }
                            slotsRevision++
                            localMessage = resources.getString(R.string.progress_deleted)
                        }
                        ProgressAction.DeleteSnapshot -> {
                            val items = snapshotPage?.items.orEmpty()
                            val index = items.indexOfFirst { it.id == confirmation.id }
                            withContext(NonCancellable + Dispatchers.IO) { store.deleteSnapshot(confirmation.id) }
                            snapshotFocusIndex = if (items.size == 1 && page > 0) SnapshotPage.PAGE_SIZE - 1 else index.coerceAtLeast(0)
                            snapshotsRevision++
                            localMessage = resources.getString(R.string.progress_snapshot_deleted)
                        }
                        ProgressAction.Apply -> {
                            if (paused) requireNotNull(onApplySnapshot).invoke(confirmation.id) else withContext(NonCancellable + Dispatchers.IO) { store.applySnapshot(confirmation.id, confirmation.target) }
                            selectedSlot = confirmation.target
                            snapshotsOpen = false
                            slotsRevision++
                            localMessage = resources.getString(if (paused) R.string.progress_applied_running else R.string.progress_applied)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SnapshotPageButton(next: Boolean, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val label = stringResource(if (next) R.string.library_next_page else R.string.library_previous_page)
    FbngButton(
        onClick, modifier.width(FbngDimens.buttonHeight).semantics { contentDescription = label }, enabled,
        kind = FbngButtonKind.Neutral, contentPadding = PaddingValues(),
    ) {
        Text(if (next) "›" else "‹", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ProgressCard(
    store: GameProgressStore,
    slot: SaveSlotInfo,
    snapshot: Boolean,
    selected: Boolean,
    revision: Int,
    previewHeight: Dp,
    modifier: Modifier,
    enabled: Boolean,
    caption: String,
    onClick: () -> Unit,
) {
    val time = remember(slot.savedAtEpochMs) {
        if (slot.savedAtEpochMs > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(slot.savedAtEpochMs)) else ""
    }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fbngFocusTarget(onClick, if (selected) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh, enabled = enabled)
            .heightIn(min = FbngDimens.buttonHeight).padding(FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
    ) {
        Text(stringResource(if (snapshot) R.string.progress_snapshot else R.string.progress_slot, slot.id) + if (selected) " ✓" else "", style = MaterialTheme.typography.titleMedium)
        if (slot.available && time.isNotEmpty()) Text(time, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        if (!slot.available) Text(stringResource(R.string.slot_no_save), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.bodyLarge, color = scheme.secondary)
        if (slot.available && previewHeight >= FbngDimens.buttonHeight) {
            val image by produceState<ImageBitmap?>(null, store, snapshot, slot.id, slot.savedAtEpochMs, revision) {
                value = withContext(Dispatchers.IO) {
                    try {
                        (if (snapshot) store.snapshotCover(slot.id) else store.slotCover(slot.id))?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        null
                    }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(previewHeight).clip(MaterialTheme.shapes.medium).background(scheme.background),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = image
                if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text(stringResource(R.string.slot_no_cover), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
