package com.xiaojiwei.fbng.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
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
    pausedCover: ByteArray? = null,
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
    var dialogTrigger by remember { mutableStateOf<String?>(null) }
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
        localMessage = ""
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
        restore(dialogTrigger ?: focused)
        dialogTrigger = null
    }
    fun back() {
        if (blocked) return
        if (dialogOpen) dismiss() else onBack()
    }
    BackHandler(onBack = ::back)
    fun prepareApply(id: Int) = operate {
        val state = withContext(Dispatchers.IO) { store.readSnapshot(id) }
        dialogTrigger = "apply-snapshot"
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
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (dialogOpen || !snapshotsOpen || snapshotPage?.items.orEmpty().none { snapshotTag(it.id) == focused }) {
                return@onPreviewKeyEvent false
            }
            val step = when (event.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> return@onPreviewKeyEvent false
            }
            if (event.type == KeyEventType.KeyDown) changePage(step)
            true
        }.onKeyEvent { event ->
            when {
                event.key == Key.ButtonB && event.type == KeyEventType.KeyUp -> { back(); true }
                !dialogOpen && !blocked && event.type == KeyEventType.KeyUp &&
                    (event.key == Key.ButtonL1 || event.key == Key.ButtonR1) -> {
                    val next = event.key == Key.ButtonR1
                    if (snapshotsOpen != next) {
                        snapshotsOpen = next
                        restore(if (next) "snapshots-tab" else "slots-tab")
                    }
                    true
                }
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
                val feedback = if (messageIsError) message else localMessage.ifEmpty { message }
                val favorites: @Composable () -> Unit = {
                    FbngButton(onToggleFavorite, target(ShellTags.ToggleFavorite).height(FbngDimens.shellHeaderHeight),
                        enabled = !blocked && !dialogOpen, kind = FbngButtonKind.Outline) {
                        Text(stringResource(if (favorite) R.string.progress_favorited else R.string.progress_favorite))
                    }
                }
                ShellPage(
                    title = entry.title,
                    margin = FbngDimens.compactMargin,
                    modalOpen = dialogOpen,
                    verticalMargin = if (paused) FbngDimens.space16 + FbngDimens.rp6BarInset else FbngDimens.space16,
                    leadingAction = {
                        ShellHeaderBack(stringResource(if (paused) R.string.resume else R.string.back),
                            ::back, target(ShellTags.DetailBack), enabled = !blocked && !dialogOpen)
                    },
                    headerAction = {
                        if (paused) Box(Modifier.height(FbngDimens.shellHeaderHeight), contentAlignment = Alignment.CenterEnd) {
                            Text(stringResource(R.string.progress_paused), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        } else favorites()
                    },
                    footer = {
                        val hints = buildList {
                            val action = when (focused) {
                                primary -> when {
                                    paused -> R.string.progress_resume
                                    entry.game.canLaunch -> R.string.progress_start
                                    else -> R.string.progress_manage_folders
                                }
                                ShellTags.DetailBack -> if (paused) R.string.progress_resume else R.string.back
                                ShellTags.ToggleFavorite -> if (favorite) R.string.shell_hint_unfavorite else R.string.shell_hint_favorite
                                ShellTags.Quit -> if (ready) R.string.progress_save_quit else R.string.quit
                                "slots-tab" -> R.string.progress_slots
                                "snapshots-tab" -> R.string.progress_snapshots
                                "delete-progress" -> R.string.progress_delete
                                "create-snapshot" -> R.string.progress_create_snapshot
                                "apply-snapshot" -> R.string.progress_apply_short
                                "delete-snapshot" -> R.string.progress_delete_snapshot
                                "retry-snapshots", "retry-slots" -> R.string.progress_retry
                                "snapshot-prev", "snapshot-next" -> R.string.shell_hint_page
                                else -> R.string.shell_hint_select
                            }
                            add("A" to action)
                            add("L1 / R1" to R.string.shell_hint_switch_tab)
                            add("B" to if (paused) R.string.progress_resume else R.string.back)
                            if (snapshotsOpen && (snapshotPage?.pages ?: 0) > 1) add("← / →" to R.string.shell_hint_page)
                        }
                        ShellFooter(hints) {
                            Text(feedback, Modifier.widthIn(max = 350.dp),
                                color = if (localError || messageIsError) scheme.error else scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                        Column(
                            Modifier.weight(7f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        ) {
                            val frame by produceState<ImageBitmap?>(null, pausedCover) {
                                value = pausedCover?.let { bytes ->
                                    withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                                }
                            }
                            if (paused) {
                                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                                    val bitmap = frame
                                    val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: FbngDimens.coverAspectRatio
                                    val artwork = Modifier.fillMaxWidth().height(minOf(maxHeight, maxWidth / ratio))
                                        .clip(MaterialTheme.shapes.medium)
                                    if (bitmap == null) GameCover(cover, artwork, contentScale = ContentScale.Crop)
                                    else Image(bitmap, null, artwork.background(Color.Black), contentScale = ContentScale.Fit)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { favorites() }
                            } else {
                                GameCover(cover, Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop)
                                Text(entry.game.fileNames.joinToString(" · "),
                                    Modifier.padding(horizontal = FbngDimens.space8, vertical = FbngDimens.space4),
                                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        val displayedPage = snapshotPage
                        val listLoaded = if (snapshotsOpen) snapshotsLoaded else slotsLoaded
                        val listError = if (snapshotsOpen) snapshotsError else slotsError
                        val items = if (snapshotsOpen) displayedPage?.items.orEmpty() else slots.orEmpty()
                        val snapshot = items.firstOrNull { snapshotsOpen && it.id == selectedSnapshot }
                        Column(
                            Modifier.weight(12f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        ) {
                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4)) {
                                    FbngButton({ snapshotsOpen = false }, target("slots-tab").semantics { selected = !snapshotsOpen },
                                        enabled = !blocked, selected = !snapshotsOpen, kind = FbngButtonKind.Neutral, role = Role.Tab) {
                                        Text(stringResource(R.string.progress_slots))
                                    }
                                    FbngButton({ snapshotsOpen = true }, target("snapshots-tab").semantics { selected = snapshotsOpen },
                                        enabled = !blocked, selected = snapshotsOpen, kind = FbngButtonKind.Neutral, role = Role.Tab) {
                                        Text(stringResource(R.string.progress_snapshots))
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
                            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                                    else -> Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                                    ) {
                                        for (item in items) key(snapshotsOpen, item.id) {
                                            val tag = if (snapshotsOpen) snapshotTag(item.id) else "progress-slot-${item.id}"
                                            val selected = if (snapshotsOpen) item.id == selectedSnapshot else item.id == currentSlot
                                            val caption = when {
                                                snapshotsOpen -> item.sourceSlot?.let { stringResource(R.string.progress_snapshot_source, it) }
                                                    ?: stringResource(R.string.progress_legacy)
                                                paused && selected -> stringResource(R.string.progress_active)
                                                item.available -> stringResource(R.string.progress_saved)
                                                else -> stringResource(R.string.progress_empty_short)
                                            }
                                            ProgressCard(
                                                item, snapshot = snapshotsOpen, selected = selected,
                                                modifier = Modifier.weight(1f).fillMaxWidth().libraryFocus(tag, requester(tag)) {
                                                    focused = it
                                                    if (snapshotsOpen) selectedSnapshot = item.id
                                                },
                                                enabled = if (snapshotsOpen) canOperateSnapshots else canOperateSlots && (!paused || selected),
                                                caption = caption,
                                                onClick = { if (snapshotsOpen) selectedSnapshot = item.id else selectedSlot = item.id },
                                            )
                                        }
                                        repeat(GameProgressStore.SLOT_COUNT - items.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val stackActions = maxWidth < 385.dp
                                val actionHeight = FbngDimens.shellHeaderHeight
                                Box(Modifier.fillMaxWidth().height(if (stackActions) actionHeight * 2 + FbngDimens.space8 else actionHeight)) {
                                    Row(Modifier.align(Alignment.TopStart),
                                      horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8), verticalAlignment = Alignment.CenterVertically) {
                                      val padding = PaddingValues(horizontal = FbngDimens.space8, vertical = FbngDimens.space4)
                                      if (!entry.game.canLaunch && !paused && !snapshotsOpen) {
                                          Text(entry.game.missing.joinToString(" · ") {
                                              resources.getString(if (it.isBios) R.string.library_missing_bios else R.string.library_missing_parent, it.name)
                                          }, Modifier.widthIn(max = 225.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                              color = Color(0xFFF1C484), style = MaterialTheme.typography.labelMedium)
                                      } else if (!snapshotsOpen) {
                                          FbngButton(
                                              {
                                                  dialogTrigger = "delete-progress"
                                                  pending = ProgressConfirmation(ProgressAction.DeleteSlot, currentSlot)
                                              },
                                              target("delete-progress").height(actionHeight),
                                              enabled = canOperateSlots && (paused || items.any { it.id == currentSlot && it.available }),
                                              kind = FbngButtonKind.Outline, contentPadding = padding,
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
                                              }, target("create-snapshot").height(actionHeight), enabled = canOperateSnapshots, kind = FbngButtonKind.Outline, contentPadding = padding) {
                                                  Text(stringResource(R.string.progress_create_snapshot))
                                              }
                                          }
                                          if (snapshot != null) {
                                              FbngButton(
                                                  { prepareApply(snapshot.id) }, target("apply-snapshot").height(actionHeight),
                                                  enabled = canOperateSnapshots && (paused || slotsLoaded),
                                                  kind = FbngButtonKind.Outline, contentPadding = padding,
                                              ) { Text(stringResource(R.string.progress_apply_short)) }
                                              FbngButton(
                                                  {
                                                      dialogTrigger = "delete-snapshot"
                                                      pending = ProgressConfirmation(ProgressAction.DeleteSnapshot, snapshot.id)
                                                  },
                                                  target("delete-snapshot").height(actionHeight), enabled = canOperateSnapshots, kind = FbngButtonKind.Outline, contentPadding = padding,
                                              ) { Text(stringResource(R.string.progress_delete_short)) }
                                          }
                                      }
                                    }
                                    Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                                      if (onQuit != null) {
                                          FbngButton(onQuit, target(ShellTags.Quit).height(actionHeight), enabled = !blocked, kind = FbngButtonKind.Outline,
                                              contentPadding = PaddingValues(horizontal = FbngDimens.space8)) {
                                              Text(stringResource(if (ready) R.string.progress_save_quit else R.string.quit))
                                          }
                                      }
                                      FbngButton({ onPrimary(currentSlot) }, target(primary).height(actionHeight), enabled = primaryEnabled,
                                          contentPadding = PaddingValues(horizontal = FbngDimens.space12)) {
                                          Text(stringResource(when {
                                              paused -> R.string.progress_resume
                                              entry.game.canLaunch -> R.string.progress_start
                                              else -> R.string.progress_manage_folders
                                          }))
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
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.progress_slot, slot.id), maxLines = 1)
                                    Text(stringResource(if (slot.available) R.string.progress_saved else R.string.progress_empty_short),
                                        style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = { FbngButton(::dismiss, Modifier.then(Modifier.libraryFocus("target-cancel", cancel) {}), kind = FbngButtonKind.Neutral) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { FbngButton({ targetSnapshot = null; pending = ProgressConfirmation(ProgressAction.Apply, id, targetSlot, state) }) { Text(stringResource(R.string.progress_next)) } },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = FbngDimens.space24).widthIn(max = 460.dp).fillMaxWidth()
                .onKeyEvent { if (it.key == Key.ButtonB && it.type == KeyEventType.KeyUp) { dismiss(); true } else false },
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
                ProgressAction.Apply -> stringResource(R.string.progress_overwrite)
            }, onDismiss = ::dismiss, immersive = paused,
            onConfirm = {
                pending = null
                dialogTrigger = null
                val focusAfter = when (confirmation.action) {
                    ProgressAction.DeleteSlot -> primary
                    ProgressAction.DeleteSnapshot -> null
                    ProgressAction.Apply -> if (paused) primary else "progress-slot-${confirmation.target}"
                }
                operate(focusAfter) {
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
    slot: SaveSlotInfo,
    snapshot: Boolean,
    selected: Boolean,
    modifier: Modifier,
    enabled: Boolean,
    caption: String,
    onClick: () -> Unit,
) {
    val time = remember(slot.savedAtEpochMs) {
        if (slot.savedAtEpochMs > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(slot.savedAtEpochMs)) else ""
    }
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.semantics { this.selected = selected }.fbngFocusTarget(
            onClick,
            if (selected) scheme.primary.copy(alpha = .22f) else scheme.surface,
            enabled = enabled,
            restingStroke = if (selected) scheme.primary.copy(alpha = .65f) else Color.Transparent,
        )
            .padding(horizontal = FbngDimens.space12),
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!snapshot) Box(
            Modifier.size(FbngDimens.space24).background(
                if (selected) scheme.primary else scheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall,
            ),
            contentAlignment = Alignment.Center,
        ) { Text(slot.id.toString(), style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onSurface) }
        Text(if (snapshot) time.ifEmpty { stringResource(R.string.progress_snapshot, slot.id) } else caption,
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (snapshot) caption else time, style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
