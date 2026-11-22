package com.xiaojiwei.fbng.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.game.GameProgressStore
import com.xiaojiwei.fbng.game.SaveSlotInfo
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ProgressAction { DeleteSlot, DeleteSnapshot, Overwrite }
private data class ProgressConfirmation(val action: ProgressAction, val id: Int, val coreCommit: String = "")
private const val Rp6DesignDensity = 369f / 160f
private const val VisibleSnapshotRows = 4
private fun slotTag(id: Int) = "progress-slot-$id"
private fun snapshotTag(id: Int) = "snapshot-$id"
private fun targetTag(id: Int) = "snapshot-target-$id"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameProgressPage(
    entry: LibraryEntry,
    preview: GamePreviewArtwork,
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
    onApplySnapshot: (suspend (Int, Int) -> Unit)? = null,
    onDeleteCurrent: (suspend () -> Unit)? = null,
    onQuit: (() -> Unit)? = null,
    onDialogOpen: (Boolean) -> Unit = {},
    onOperationError: (Exception) -> Unit = {},
    onConfigureFolders: (() -> Unit)? = null,
    initialSelectedSlot: Int? = null,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var selectedSlot by rememberSaveable(entry.game.name) { mutableStateOf(initialSelectedSlot ?: activeSlot) }
    var selectedSnapshot by rememberSaveable(entry.game.name) { mutableStateOf<Int?>(null) }
    var snapshotsOpen by rememberSaveable(entry.game.name) { mutableStateOf(false) }
    var slots by remember(store) { mutableStateOf<List<SaveSlotInfo>?>(null) }
    var snapshots by remember(store) { mutableStateOf(emptyList<SaveSlotInfo>()) }
    val snapshotListState = key(entry.game.name) { rememberLazyListState() }
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
    var targetSnapshot by remember { mutableStateOf<Int?>(null) }
    var targetSlot by remember { mutableStateOf(1) }
    var lastPlayedSlot by remember(store) { mutableStateOf<Int?>(null) }
    val paused = activeSlot != null
    val savedSlot = activeSlot ?: lastPlayedSlot
    val selectingTarget = targetSnapshot != null
    val blocked = busy || working
    val slotsLoaded = loadedSlotsRevision == slotsRevision && !slotsError
    val snapshotsLoaded = loadedSnapshotsRevision == snapshotsRevision && !snapshotsError
    val canOperate = ready && !blocked && pending == null
    val canApply = canOperate && slotsLoaded && (!paused || onApplySnapshot != null)

    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focused by remember { mutableStateOf<String?>(null) }
    var restoreKey by remember { mutableStateOf<String?>(null) }
    var focusRevision by remember { mutableIntStateOf(0) }
    fun restore(tag: String?) {
        restoreKey = tag
        focusRevision++
    }
    fun requester(tag: String) = requesters.getOrPut(tag) { FocusRequester() }
    fun target(tag: String, onFocus: () -> Unit = {}): Modifier =
        Modifier.libraryFocus(tag, requester(tag)) {
            focused = it
            onFocus()
        }
    val focusManager = LocalFocusManager.current
    val gamepad = LocalGamepadInput.current
    val window = LocalWindowInfo.current
    val windowSize = window.containerSize
    val windowFocused = window.isWindowFocused
    val backTag = if (paused && !selectingTarget) ShellTags.Pause else ShellTags.DetailBack
    val manageTag = "progress-manage-folders"

    LaunchedEffect(store, slotsRevision) {
        try {
            val (items, current) = withContext(Dispatchers.IO) { store.listSlots() to store.currentSlot() }
            slots = items
            lastPlayedSlot = current?.takeIf { id -> items.any { it.id == id && it.available } }
            if (selectedSlot == null) selectedSlot = activeSlot ?: lastPlayedSlot ?: 1
            loadedSlotsRevision = slotsRevision
            slotsError = false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("FBNG", "Could not read progress slots", error)
            slotsError = true
        }
    }
    LaunchedEffect(store, snapshotsOpen, snapshotsRevision) {
        if (!snapshotsOpen || snapshotsLoaded) return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) { store.listSnapshots() }
            val currentIds = result.map(SaveSlotInfo::id).toSet()
            snapshots.filterNot { it.id in currentIds }.forEach {
                requesters.remove(snapshotTag(it.id))
            }
            snapshots = result
            snapshotsError = false
            if (selectedSnapshot !in currentIds) {
                selectedSnapshot = result.firstOrNull()?.id
            }
            loadedSnapshotsRevision = snapshotsRevision
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w("FBNG", "Could not read snapshots", error)
            snapshotsError = true
        }
    }
    val inputBlocked = selectingTarget || pending != null || working
    LaunchedEffect(inputBlocked) { onDialogOpen(inputBlocked) }
    LaunchedEffect(snapshotsOpen, selectingTarget, pending != null) { gamepad?.shellSurfaceChanged() }
    LaunchedEffect(pending) { if (pending != null) focusManager.clearFocus(force = true) }
    DisposableEffect(Unit) { onDispose { onDialogOpen(false) } }
    LaunchedEffect(blocked, ready, slotsLoaded, slotsError, snapshotsLoaded, snapshotsError, pending, targetSnapshot,
        snapshotsOpen, windowSize, windowFocused, focusRevision, fold) {
        if (blocked || pending != null || !windowFocused) return@LaunchedEffect
        val default = when {
            selectingTarget -> targetTag(targetSlot)
            !ready -> if (onQuit != null) ShellTags.Quit else ShellTags.ToggleFavorite
            snapshotsOpen && snapshotsError -> "retry-snapshots"
            snapshotsOpen && !snapshotsLoaded -> return@LaunchedEffect
            snapshotsOpen -> selectedSnapshot?.let(::snapshotTag)
                ?: if (onCreateSnapshot != null) "create-snapshot" else ShellTags.ToggleFavorite
            !entry.game.canLaunch && onConfigureFolders != null -> manageTag
            slotsError -> "retry-slots"
            !slotsLoaded -> return@LaunchedEffect
            else -> slotTag(selectedSlot ?: 1)
        }
        if (ready && snapshotsOpen && snapshotsLoaded && !selectingTarget) {
            val index = snapshots.indexOfFirst { it.id == selectedSnapshot }
            if (index >= 0 && snapshotListState.layoutInfo.visibleItemsInfo.none { it.key == selectedSnapshot }) {
                snapshotListState.scrollToItem(index)
            }
        }
        repeat(4) {
            withFrameNanos { }
            for (tag in listOfNotNull(restoreKey, focused, default).distinct()) {
                if (runCatching { requesters[tag]?.requestFocus() == true }.getOrDefault(false)) {
                    restoreKey = null
                    return@LaunchedEffect
                }
            }
        }
    }

    fun operate(focusOnFailure: String?, operation: suspend () -> Unit) {
        if (busy || working) return
        working = true
        localMessage = ""
        localError = false
        scope.launch {
            try {
                operation()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e("FBNG", "Progress operation failed", error)
                localError = true
                localMessage = resources.getString(R.string.progress_error, error.message.orEmpty())
                restore(focusOnFailure)
                onOperationError(error)
            } finally {
                working = false
            }
        }
    }
    fun switchTab(snapshotTab: Boolean) {
        if (snapshotsOpen == snapshotTab) return
        snapshotsOpen = snapshotTab
        focused = null
        restore(if (snapshotTab) selectedSnapshot?.let(::snapshotTag) else selectedSlot?.let(::slotTag))
    }
    fun dismiss() {
        if (blocked) return
        val confirmation = pending
        if (confirmation != null) {
            pending = null
            val row = when (confirmation.action) {
                ProgressAction.DeleteSlot -> slotTag(confirmation.id)
                ProgressAction.DeleteSnapshot -> snapshotTag(confirmation.id)
                ProgressAction.Overwrite -> targetTag(confirmation.id)
            }
            restore(row)
        } else if (selectingTarget) {
            targetSnapshot = null
            restore(selectedSnapshot?.let(::snapshotTag))
        } else {
            onBack()
        }
    }
    BackHandler(onBack = ::dismiss)

    fun chooseSnapshot(id: Int) {
        if (!canApply) return
        val items = slots.orEmpty()
        targetSlot = savedSlot ?: items.firstOrNull { !it.available }?.id ?: items.first().id
        selectedSnapshot = id
        targetSnapshot = id
        restore(targetTag(targetSlot))
    }
    fun applySnapshot(target: Int) {
        val id = targetSnapshot ?: return
        operate(targetTag(target)) {
            if (paused) requireNotNull(onApplySnapshot)(id, target)
            else withContext(NonCancellable + Dispatchers.IO) { store.applySnapshot(id, target) }
            targetSnapshot = null
            snapshotsOpen = false
            selectedSlot = target
            slotsRevision++
            restore(slotTag(target))
            localMessage = if (paused) resources.getString(R.string.progress_applied_running)
                else resources.getString(R.string.progress_written, target)
        }
    }
    fun chooseTarget(slot: SaveSlotInfo) {
        if (!canApply) return
        targetSlot = slot.id
        if (slot.available) {
            val id = targetSnapshot ?: return
            operate(targetTag(slot.id)) {
                val commit = withContext(Dispatchers.IO) { store.readSnapshot(id).coreCommit }
                pending = ProgressConfirmation(ProgressAction.Overwrite, slot.id, commit)
            }
        } else {
            applySnapshot(slot.id)
        }
    }
    fun createSnapshot() {
        val create = onCreateSnapshot ?: return
        if (!canOperate || selectingTarget) return
        operate(focused) {
            selectedSnapshot = create()
            snapshotsOpen = true
            snapshotsRevision++
            restore(selectedSnapshot?.let(::snapshotTag))
            localMessage = resources.getString(R.string.progress_created)
        }
    }
    fun canDeleteSlot(slot: SaveSlotInfo) =
        slot.available && (if (slot.id == activeSlot) onDeleteCurrent != null else entry.game.canLaunch || paused)

    fun requestDelete(item: SaveSlotInfo) {
        if (!canOperate || selectingTarget) return
        if (snapshotsOpen && snapshotsLoaded) {
            selectedSnapshot = item.id
            pending = ProgressConfirmation(ProgressAction.DeleteSnapshot, item.id)
        } else if (!snapshotsOpen && slotsLoaded && canDeleteSlot(item)) {
            selectedSlot = item.id
            pending = ProgressConfirmation(ProgressAction.DeleteSlot, item.id)
        }
    }
    fun confirm(confirmation: ProgressConfirmation) {
        pending = null
        when (confirmation.action) {
            ProgressAction.Overwrite -> applySnapshot(confirmation.id)
            ProgressAction.DeleteSlot -> operate(slotTag(confirmation.id)) {
                if (confirmation.id == activeSlot) requireNotNull(onDeleteCurrent)()
                else withContext(NonCancellable + Dispatchers.IO) { store.deleteSlot(confirmation.id) }
                slotsRevision++
                restore(slotTag(confirmation.id))
                localMessage = resources.getString(R.string.progress_deleted)
            }
            ProgressAction.DeleteSnapshot -> operate(snapshotTag(confirmation.id)) {
                val index = snapshots.indexOfFirst { it.id == confirmation.id }
                withContext(NonCancellable + Dispatchers.IO) { store.deleteSnapshot(confirmation.id) }
                selectedSnapshot = (snapshots.getOrNull(index + 1) ?: snapshots.getOrNull(index - 1))?.id
                restore(selectedSnapshot?.let(::snapshotTag))
                snapshotsRevision++
                localMessage = resources.getString(R.string.progress_snapshot_deleted)
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val selectedSaveInfo = slots.orEmpty().firstOrNull { slotTag(it.id) == focused }
    val selectedSnapshotInfo = snapshots.firstOrNull { snapshotTag(it.id) == focused }
    val deleteItem = if (snapshotsOpen && snapshotsLoaded) selectedSnapshotInfo
        else selectedSaveInfo?.takeIf { !snapshotsOpen && slotsLoaded && canDeleteSlot(it) }
    val applyReady = canApply && !selectingTarget
    val createReady = canOperate && !selectingTarget

    Surface(
        color = scheme.background, contentColor = scheme.onBackground,
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            val key = event.key
            val release = event.type == KeyEventType.KeyUp
            when {
                key == Key.ButtonB -> { if (release) dismiss(); true }
                pending != null || selectingTarget -> false
                key == Key.ButtonL1 || key == Key.ButtonR1 -> {
                    if (release && !blocked) switchTab(key == Key.ButtonR1)
                    true
                }
                key == Key.ButtonX && deleteItem != null -> { if (release) requestDelete(deleteItem); true }
                key == Key.ButtonY && onCreateSnapshot != null -> { if (release) createSnapshot(); true }
                else -> false
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            var origin by remember { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current
            val hinge = fold?.takeIf { it.separating }
            val compact = maxHeight < FbngDimens.shortShellHeight || hinge?.orientationVertical == false
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
                val feedback = localMessage.ifEmpty { message }
                val verticalMargin = when {
                    compact -> FbngDimens.space8
                    paused -> FbngDimens.space16 + FbngDimens.rp6BarInset
                    else -> FbngDimens.space16
                }
                val favorites: @Composable () -> Unit = {
                    FbngButton(onToggleFavorite, target(ShellTags.ToggleFavorite).height(FbngDimens.shellHeaderHeight),
                        enabled = !blocked && pending == null && !selectingTarget, kind = FbngButtonKind.Outline) {
                        Text(stringResource(if (favorite) R.string.progress_favorited else R.string.progress_favorite), maxLines = 1)
                    }
                }
                ShellPage(
                    title = entry.title,
                    margin = FbngDimens.compactMargin,
                    contentGap = if (compact) FbngDimens.space8 else FbngDimens.space12,
                    modalOpen = pending != null,
                    verticalMargin = verticalMargin,
                    leadingAction = {
                        val enabled = !blocked && pending == null && (!paused || ready || selectingTarget)
                        if (paused && !selectingTarget) {
                            ShellHeaderButton(::dismiss, Modifier.testTag(backTag).focusProperties { canFocus = false }, enabled) {
                                Text("‹  " + stringResource(R.string.progress_continue), maxLines = 1)
                            }
                        } else {
                            ShellHeaderBack(stringResource(if (selectingTarget) R.string.cancel else R.string.back),
                                ::dismiss, Modifier.testTag(backTag), enabled)
                        }
                    },
                    headerAction = {
                        if (!paused) favorites()
                        else if (onQuit != null && !selectingTarget) {
                            FbngButton(onQuit, target(ShellTags.Quit).height(FbngDimens.shellHeaderHeight),
                                enabled = !blocked && pending == null, kind = FbngButtonKind.Outline) {
                                Text(stringResource(R.string.progress_quit), maxLines = 1)
                            }
                        }
                    },
                    footer = {
                        val action = when {
                            selectingTarget -> R.string.progress_choose_target
                            focused == ShellTags.Quit -> R.string.progress_quit
                            focused == ShellTags.ToggleFavorite -> if (favorite) R.string.shell_hint_unfavorite else R.string.shell_hint_favorite
                            focused == manageTag -> R.string.progress_manage_folders
                            focused == "create-snapshot" -> R.string.progress_create_snapshot
                            focused?.startsWith("retry-") == true -> R.string.progress_retry
                            selectedSaveInfo != null && !paused && !entry.game.canLaunch -> null
                            selectedSaveInfo != null && !paused -> R.string.progress_start
                            selectedSaveInfo?.id == activeSlot && activeSlot != null -> R.string.progress_resume
                            selectedSaveInfo?.available == true -> R.string.progress_load_save
                            selectedSaveInfo != null -> R.string.progress_start_empty
                            selectedSnapshotInfo != null -> R.string.progress_apply_snapshot
                            else -> R.string.shell_hint_select
                        }
                        val hints = buildList {
                            if (action != null) add("A" to action)
                            add("B" to if (selectingTarget) R.string.cancel else if (paused) R.string.progress_continue else R.string.back)
                            if (!selectingTarget && deleteItem != null) add("X" to R.string.progress_delete_short)
                            if (!selectingTarget && snapshotsOpen && onCreateSnapshot != null && focused != "create-snapshot") {
                                add("Y" to R.string.progress_create_snapshot)
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val feedbackWidth = maxWidth / 3
                            ShellFooter(hints) {
                                if (feedback.isNotEmpty()) Text(
                                    feedback, Modifier.widthIn(max = feedbackWidth),
                                    color = if (localError || (localMessage.isEmpty() && messageIsError)) scheme.error else scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                        Column(
                            Modifier.weight(7f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        ) {
                            val previewId = if (snapshotsOpen) targetSnapshot ?: selectedSnapshot.takeIf { snapshotsLoaded }
                                else selectedSlot.takeIf { slotsLoaded }
                            val screenshot = rememberSaveScreenshot(store, snapshotsOpen, previewId,
                                if (snapshotsOpen) snapshotsRevision else slotsRevision) {
                                previewId?.let { if (snapshotsOpen) store.snapshotCover(it) else store.slotCover(it) }
                            }
                            GamePreview(entry.title, preview.copy(image = screenshot),
                                Modifier.fillMaxWidth().weight(1f).clip(FbngShapes.PanelItem))
                            if (paused) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { favorites() }
                            } else {
                                Text(entry.game.fileNames.joinToString(" · "), Modifier.padding(horizontal = FbngDimens.space8),
                                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Column(
                            Modifier.weight(12f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        ) {
                            val shortButton = Modifier.height(32.dp)
                            val padding = PaddingValues(horizontal = FbngDimens.space4)
                            if (selectingTarget) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.progress_target_title), Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    FbngButton(::dismiss, shortButton.focusProperties { canFocus = false },
                                        enabled = !blocked && pending == null,
                                        kind = FbngButtonKind.Outline, contentPadding = padding) {
                                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                }
                            } else {
                                FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4, Alignment.End),
                                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                                ) {
                                    Row(
                                        Modifier.align(Alignment.CenterVertically),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ProgressShoulderHint("L1")
                                        Spacer(Modifier.width(15.dp / Rp6DesignDensity))
                                        ProgressTab(R.string.progress_slots, Modifier.testTag("slots-tab"), !snapshotsOpen, !blocked) {
                                            switchTab(false)
                                        }
                                        Spacer(Modifier.width(10.dp / Rp6DesignDensity))
                                        ProgressTab(R.string.progress_snapshots, Modifier.testTag("snapshots-tab"), snapshotsOpen, !blocked) {
                                            switchTab(true)
                                        }
                                        Spacer(Modifier.width(10.dp / Rp6DesignDensity))
                                        ProgressShoulderHint("R1")
                                    }
                                    if (!entry.game.canLaunch && !paused && !snapshotsOpen) {
                                        Text(entry.game.missing.joinToString(" · ") {
                                            resources.getString(if (it.isBios) R.string.library_missing_bios else R.string.library_missing_parent, it.name)
                                        }, Modifier.weight(1f).align(Alignment.CenterVertically),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = Color(0xFFF1C484), style = MaterialTheme.typography.labelMedium)
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    if (snapshotsOpen && onCreateSnapshot != null) {
                                        FbngButton(::createSnapshot, target("create-snapshot").then(shortButton),
                                            enabled = createReady, kind = FbngButtonKind.Outline, contentPadding = padding) {
                                            Text(stringResource(R.string.progress_create_snapshot),
                                                style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        }
                                    } else if (!snapshotsOpen && !entry.game.canLaunch && onConfigureFolders != null) {
                                        FbngButton(onConfigureFolders, target(manageTag).then(shortButton), enabled = !blocked,
                                            contentPadding = padding) {
                                            Text(stringResource(R.string.progress_manage_folders), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            val showSnapshotRows = snapshotsOpen && !selectingTarget
                            val listLoaded = if (showSnapshotRows) snapshotsLoaded else slotsLoaded
                            val listError = if (showSnapshotRows) snapshotsError else slotsError
                            val progressItems = if (showSnapshotRows) snapshots else slots.orEmpty()
                            val progressRow: @Composable (SaveSlotInfo, Modifier, () -> Unit) -> Unit =
                                { item, rowModifier, revealFocusedRow ->
                                    val tag = when {
                                        selectingTarget -> targetTag(item.id)
                                        showSnapshotRows -> snapshotTag(item.id)
                                        else -> slotTag(item.id)
                                    }
                                    val selected = when {
                                        selectingTarget -> item.id == targetSlot
                                        showSnapshotRows -> item.id == selectedSnapshot
                                        else -> item.id == selectedSlot
                                    }
                                    val caption = when {
                                        showSnapshotRows -> item.sourceSlot?.let { stringResource(R.string.progress_snapshot_source, it) }
                                            ?: stringResource(R.string.progress_legacy)
                                        item.id == savedSlot -> stringResource(R.string.progress_active)
                                        item.available -> stringResource(R.string.progress_saved)
                                        else -> stringResource(R.string.progress_empty_short)
                                    }
                                    val enabled = when {
                                        selectingTarget -> canApply
                                        showSnapshotRows -> applyReady
                                        else -> canOperate && (paused || entry.game.canLaunch)
                                    }
                                    ProgressCard(
                                        item, showSnapshotRows, selectingTarget, selected,
                                        active = !showSnapshotRows && item.id == savedSlot, caption,
                                        rowModifier.fillMaxWidth().then(target(tag) {
                                            revealFocusedRow()
                                            when {
                                                selectingTarget -> targetSlot = item.id
                                                showSnapshotRows -> selectedSnapshot = item.id
                                                else -> selectedSlot = item.id
                                            }
                                        }),
                                        enabled = enabled,
                                        previewable = !enabled && canOperate && !selectingTarget,
                                        onClick = {
                                            when {
                                                selectingTarget -> chooseTarget(item)
                                                showSnapshotRows -> chooseSnapshot(item.id)
                                                else -> {
                                                    selectedSlot = item.id
                                                    onPrimary(item.id)
                                                }
                                            }
                                        },
                                    )
                                }
                            when {
                                !listLoaded -> FbngPanel {
                                    val status = when {
                                        !listError -> R.string.progress_loading
                                        showSnapshotRows -> R.string.progress_snapshots_read_failed
                                        else -> R.string.progress_slots_read_failed
                                    }
                                    Text(stringResource(status), style = MaterialTheme.typography.bodyLarge,
                                        color = if (listError) scheme.error else scheme.onSurfaceVariant)
                                    if (listError) {
                                        val retry = if (showSnapshotRows) "retry-snapshots" else "retry-slots"
                                        FbngButton({
                                            if (showSnapshotRows) {
                                                snapshotsError = false
                                                snapshotsRevision++
                                            } else {
                                                slotsError = false
                                                slotsRevision++
                                            }
                                        }, target(retry), enabled = !blocked) { Text(stringResource(R.string.progress_retry)) }
                                    }
                                }
                                progressItems.isEmpty() -> Text(stringResource(R.string.progress_no_snapshots),
                                    Modifier.padding(FbngDimens.space8), style = MaterialTheme.typography.bodyLarge)
                                showSnapshotRows -> BoxWithConstraints(Modifier.fillMaxSize()) {
                                    val focusInset = FbngDimens.focusGap + FbngDimens.focusStroke
                                    val rowHeight = (maxHeight / VisibleSnapshotRows - focusInset * 2)
                                        .coerceAtLeast(FbngDimens.space32)
                                    LazyColumn(Modifier.fillMaxSize().testTag("snapshot-list"), state = snapshotListState) {
                                        items(progressItems, key = SaveSlotInfo::id) { item ->
                                            val bringIntoView = remember { BringIntoViewRequester() }
                                            Box(Modifier.bringIntoViewRequester(bringIntoView).padding(focusInset)) {
                                                progressRow(item, Modifier.height(rowHeight)) {
                                                    scope.launch { bringIntoView.bringIntoView() }
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> Column(Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                    for (item in progressItems) key(item.id, selectingTarget) {
                                        progressRow(item, Modifier.weight(1f)) {}
                                    }
                                }
                            }
                        }
                    }
                }
                pending?.let { confirmation ->
                    Box(Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = FbngDimens.compactMargin, vertical = verticalMargin)) {
                        val backLabel = if (confirmation.action == ProgressAction.Overwrite) R.string.progress_back_to_target
                            else R.string.cancel
                        ShellFooter(listOf("A" to R.string.pad_confirm, "B" to backLabel))
                    }
                }
            }
        }
    }

    pending?.let { confirmation ->
        val deletingCurrent = confirmation.action == ProgressAction.DeleteSlot && confirmation.id == activeSlot
        val title = when (confirmation.action) {
            ProgressAction.DeleteSlot -> stringResource(if (deletingCurrent) R.string.progress_delete_running_title else R.string.progress_delete_title, confirmation.id)
            ProgressAction.DeleteSnapshot -> stringResource(R.string.progress_delete_snapshot_title)
            ProgressAction.Overwrite -> stringResource(R.string.progress_overwrite_title, confirmation.id)
        }
        val body = when (confirmation.action) {
            ProgressAction.DeleteSlot -> stringResource(if (deletingCurrent) R.string.progress_delete_running_body else R.string.progress_delete_body)
            ProgressAction.DeleteSnapshot -> stringResource(R.string.progress_delete_snapshot_body)
            ProgressAction.Overwrite -> stringResource(if (paused) R.string.progress_apply_running_body else R.string.progress_apply_body, confirmation.id)
        } + confirmation.coreCommit.takeIf { it.isNotEmpty() && it != BuildConfig.FBNEO_COMMIT }?.let {
            "\n" + stringResource(R.string.load_version_warn, it.take(12), BuildConfig.FBNEO_COMMIT_SHORT)
        }.orEmpty()
        val action = when (confirmation.action) {
            ProgressAction.DeleteSlot -> if (deletingCurrent) R.string.progress_delete_running else R.string.progress_delete
            ProgressAction.DeleteSnapshot -> R.string.progress_delete_snapshot
            ProgressAction.Overwrite -> R.string.progress_overwrite
        }
        GameConfirmationDialog(title, body, stringResource(action),
            onConfirm = { confirm(confirmation) }, onDismiss = ::dismiss, immersive = paused)
    }
}

@Composable
private fun ProgressTab(label: Int, modifier: Modifier, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.size(122.dp / Rp6DesignDensity, 70.dp / Rp6DesignDensity)
            .focusProperties { canFocus = false }
            .semantics { selected = active }
            .fbngFocusTarget(
                onClick = onClick,
                container = if (active) Color(0x26B4DBEC) else Color.Transparent,
                enabled = enabled,
                role = Role.Tab,
                shape = RoundedCornerShape(24.dp / Rp6DesignDensity),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (active) Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp / Rp6DesignDensity)
                .background(Color(0xFFB6F5DF)),
        )
        Text(
            stringResource(label),
            color = if (active) Color(0xFFE8F5F9) else Color(0xFFBDC9D2),
            fontSize = 30.sp / Rp6DesignDensity,
            lineHeight = 43.sp / Rp6DesignDensity,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressShoulderHint(label: String) {
    Box(
        Modifier.size(49.dp / Rp6DesignDensity, 40.dp / Rp6DesignDensity)
            .background(Color(0xFF2B3F4B), RoundedCornerShape(9.dp / Rp6DesignDensity)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color(0xFFC6D6E0),
            fontSize = 23.sp / Rp6DesignDensity,
            lineHeight = 28.sp / Rp6DesignDensity,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProgressCard(
    slot: SaveSlotInfo,
    snapshot: Boolean,
    target: Boolean,
    selected: Boolean,
    active: Boolean,
    caption: String,
    modifier: Modifier,
    enabled: Boolean,
    previewable: Boolean,
    onClick: () -> Unit,
) {
    val time = remember(slot.savedAtEpochMs) {
        if (slot.available && slot.savedAtEpochMs > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(slot.savedAtEpochMs)) else ""
    }
    val scheme = MaterialTheme.colorScheme
    val highlighted = selected || active
    val title = when {
        snapshot -> time.ifEmpty { stringResource(R.string.progress_snapshot, slot.id) }
        target -> stringResource(R.string.progress_slot, slot.id)
        else -> caption
    }
    Row(
        modifier.semantics { this.selected = selected }.fbngFocusTarget(
            onClick, if (highlighted) scheme.primary.copy(alpha = .22f) else scheme.surface,
            enabled = enabled,
            focusableWhenDisabled = previewable,
            restingStroke = if (highlighted) scheme.primary.copy(alpha = .65f) else Color.Transparent,
        ).padding(horizontal = FbngDimens.space8),
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!snapshot && !target) Box(
            Modifier.size(FbngDimens.space24).background(scheme.surfaceContainerHighest, FbngShapes.ActionKey),
            contentAlignment = Alignment.Center,
        ) {
            Text(slot.id.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (snapshot || target) Text(caption, style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!snapshot && time.isNotEmpty()) Text(time, style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (enabled) Text("›", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
    }
}
