package com.xiaojiwei.fbng.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.game.FbneoCore
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.game.RunningGame
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.game.SavedSlot
import com.xiaojiwei.fbng.library.PreparedGame
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngPanel
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "FBNG"

/** 48dp touch target plus 4dp of padding on each side of the bar. */
internal val MinHudBar = 56.dp

@Composable
internal fun GameScreen(
    game: PreparedGame,
    bridge: GamepadBridge,
    onQuit: () -> Unit,
    onStarted: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources by rememberUpdatedState(LocalResources.current)
    val activity = context.findActivity()
    val corePath = remember {
        val info = activity.applicationInfo
        val apks = ArrayList<File>(1 + (info.splitSourceDirs?.size ?: 0))
        apks.add(File(info.sourceDir))
        info.splitSourceDirs?.forEach { apks.add(File(it)) }
        FbneoCore.resolveLoadPath(File(info.nativeLibraryDir), apks)
    }
    if (corePath == null) {
        LaunchedEffect(activity) {
            Log.e(
                TAG,
                "no ${FbneoCore.FILE_NAME} in ${activity.applicationInfo.nativeLibraryDir} and no stored ${FbneoCore.ZIP_ENTRY}",
            )
        }
        BackHandler(onBack = onQuit)
        MissingCore(onQuit)
        return
    }

    val store = remember(game.stateDir) { SaveStateStore(game.stateDir) }
    val session = remember(corePath, game.rom.absolutePath) {
        val data = GLRetroViewData(activity).apply {
            coreFilePath = corePath
            gameFilePath = game.rom.absolutePath
            systemDirectory = game.systemDir.absolutePath
            savesDirectory = game.savesDir.absolutePath
            variables = emptyArray<Variable>()
            shader = ShaderConfig.Default
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true
            skipDuplicateFrames = false
            enableMicrophone = false
        }
        val view = GLRetroView(activity, data)
        // Load the core before the GL surface exists so retro_load_game is not raced.
        val running = RunningGame(view, store)
        Log.i(
            TAG,
            "core $corePath game ${game.rom.absolutePath} system ${game.systemDir}",
        )
        running
    }
    val scope = rememberCoroutineScope()
    var message by remember(session) { mutableStateOf("") }
    var showReset by remember(session) { mutableStateOf(false) }
    var slotMode by remember(session) { mutableStateOf<SaveSlotMode?>(null) }
    var slotRevision by remember(session) { mutableStateOf(0) }
    var pausePageOpen by remember(session) { mutableStateOf(false) }
    var openingPausePage by remember(session) { mutableStateOf(false) }
    var pauseBeforeReset by remember(session) { mutableStateOf(false) }
    var coreFailed by remember(session) { mutableStateOf(false) }
    var foreground by remember(session) {
        mutableStateOf(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val started by rememberUpdatedState(onStarted)

    fun reportFailure(error: Exception, resource: Int) {
        if (error is CancellationException) throw error
        Log.e(TAG, "game operation failed", error)
        message = resources.getString(resource, error.message ?: error.javaClass.simpleName)
        pausePageOpen = true
        bridge.target = null
        if (!session.paused) session.togglePause()
    }

    fun loadMessage(slot: SavedSlot?): String {
        val warning = if (slot != null && slot.coreCommit.isNotEmpty() && slot.coreCommit != BuildConfig.FBNEO_COMMIT) {
            "\n" + resources.getString(R.string.load_version_warn, slot.coreCommit.take(12), BuildConfig.FBNEO_COMMIT_SHORT)
        } else ""
        return (if (slot == null) resources.getString(R.string.load_missing) else resources.getString(R.string.load_ok)) + warning
    }

    fun quit() {
        if (session.busy || showReset || slotMode != null) return
        scope.launch {
            message = resources.getString(R.string.save_working)
            try {
                session.saveBeforeQuit()
                onQuit()
            } catch (error: Exception) {
                reportFailure(error, R.string.save_fail)
            }
        }
    }

    fun dismissReset() {
        showReset = false
        if (!pauseBeforeReset) session.togglePause()
    }

    fun dismissSlots() {
        if (session.busy) return
        slotMode = null
        session.setDialogOpen(false)
    }

    fun openSlots(mode: SaveSlotMode) {
        if (!session.ready || session.busy || coreFailed || showReset || slotMode != null) return
        bridge.target = null
        session.setDialogOpen(true)
        message = ""
        slotMode = mode
    }

    fun openPausePage() {
        if (openingPausePage) return
        openingPausePage = true
        bridge.target = null
        scope.launch {
            try {
                session.pauseForMenu()
                pausePageOpen = true
            } finally {
                openingPausePage = false
            }
        }
    }

    fun resume() {
        if (!session.ready || session.busy || coreFailed || showReset || slotMode != null) return
        if (session.paused) session.togglePause()
        pausePageOpen = false
    }

    // A back gesture during loading still opens the page. Once initialization
    // finishes, pause before giving control back to the player.
    LaunchedEffect(session, pausePageOpen, session.ready, session.busy, showReset, slotMode) {
        if (pausePageOpen && session.ready && !session.busy && !session.paused && !showReset && slotMode == null) {
            session.togglePause()
        }
    }

    BackHandler {
        when {
            slotMode != null -> dismissSlots()
            showReset -> dismissReset()
            pausePageOpen || coreFailed -> quit()
            else -> openPausePage()
        }
    }

    DisposableEffect(session) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    foreground = true
                    session.start()
                }
                Lifecycle.Event.ON_PAUSE -> try {
                    foreground = false
                    bridge.target = null
                    session.stop()
                } catch (error: Exception) {
                    reportFailure(error, R.string.save_fail)
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            bridge.onPauseToggle = null
            bridge.target = null
            activity.lifecycle.removeObserver(observer)
            try {
                session.destroy()
            } catch (error: Exception) {
                Log.e(TAG, "Could not save on disposal", error)
            }
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(session, pausePageOpen, openingPausePage, session.paused, session.busy, showReset, slotMode, coreFailed, foreground) {
        bridge.target = if (!foreground || openingPausePage || pausePageOpen || session.paused || session.busy || showReset || slotMode != null || coreFailed) null else session.view
        bridge.onPauseToggle = {
            if (!showReset && slotMode == null && !coreFailed && !session.busy) {
                if (pausePageOpen) resume() else openPausePage()
            }
        }
        onDispose { bridge.target = null }
    }

    LaunchedEffect(session.view) {
        session.view.getGLRetroEvents().collect { event ->
            if (event is GLRetroView.GLRetroEvents.SurfaceCreated ||
                event is GLRetroView.GLRetroEvents.FrameRendered
            ) {
                if (!session.initializationStarted && !coreFailed) {
                    message = resources.getString(R.string.load_working)
                    try {
                        val slot = session.initialize()
                        message = if (slot == null) "" else loadMessage(slot)
                    } catch (error: Exception) {
                        reportFailure(error, R.string.auto_load_fail)
                    }
                    if (pausePageOpen && !session.paused) session.togglePause()
                    started()
                }
            }
        }
    }
    LaunchedEffect(session.view) {
        session.view.getGLRetroErrors().collect { code ->
            coreFailed = true
            session.fail()
            message = when (code) {
                GLRetroView.ERROR_LOAD_LIBRARY -> resources.getString(R.string.core_error_library)
                GLRetroView.ERROR_LOAD_GAME -> resources.getString(R.string.core_error_game)
                else -> resources.getString(R.string.core_error_generic, code)
            }
            Log.e(TAG, "core error $code")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).onKeyEvent { event ->
        if (pausePageOpen && !showReset && slotMode == null && event.key == Key.ButtonB && event.type == KeyEventType.KeyUp) {
            resume()
            true
        } else false
    }) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // One AndroidView for the session. It fills the play area; LibretroDroid
            // letterboxes inside that rect without reserving any space for controls.
            AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize())
            // Keep the same GL surface mounted underneath an opaque pause page.
            // Running games have no HUD, regardless of the available black bars.
            if (pausePageOpen || coreFailed) GamePausePage(
                ready = session.ready && !coreFailed,
                busy = session.busy,
                hasSlot = session.hasSlot,
                dialogOpen = showReset || slotMode != null,
                message = message,
                failed = coreFailed,
                onSave = { openSlots(SaveSlotMode.Save) },
                onLoad = { openSlots(SaveSlotMode.Load) },
                onResume = ::resume,
                onReset = {
                    pauseBeforeReset = session.paused
                    if (!session.paused) session.togglePause()
                    showReset = true
                },
                onQuit = ::quit,
            )
        }
    }
    slotMode?.let { mode ->
        fun chooseSlot(id: Int?) {
            scope.launch {
                if (session.busy || coreFailed || slotMode != mode) return@launch
                message = resources.getString(if (mode == SaveSlotMode.Save) R.string.save_working else R.string.load_working)
                try {
                    if (mode == SaveSlotMode.Save) {
                        if (id == null) session.createManualSave() else session.save(id)
                        message = resources.getString(R.string.save_ok)
                        slotRevision++
                    } else {
                        message = loadMessage(session.load(requireNotNull(id)))
                        dismissSlots()
                    }
                } catch (error: Exception) {
                    reportFailure(error, if (mode == SaveSlotMode.Save) R.string.save_fail else R.string.auto_load_fail)
                }
            }
        }
        SaveSlotsDialog(
            store = store,
            mode = mode,
            busy = session.busy,
            message = message,
            revision = slotRevision,
            onDismiss = ::dismissSlots,
            onChoose = ::chooseSlot,
            onCreate = { chooseSlot(null) },
        )
    }
    if (showReset) {
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        AlertDialog(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.onKeyEvent { event ->
                if (event.key == Key.ButtonB && event.type == KeyEventType.KeyUp) {
                    dismissReset()
                    true
                } else false
            },
            onDismissRequest = ::dismissReset,
            title = { Text(stringResource(R.string.reset_title)) },
            text = { Text(stringResource(R.string.reset_body)) },
            confirmButton = {
                FbngButton(onClick = {
                    showReset = false
                    scope.launch {
                        try {
                            session.reset()
                            message = resources.getString(R.string.reset_ok)
                        } catch (error: Exception) {
                            reportFailure(error, R.string.reset_fail)
                        } finally {
                            if (!pauseBeforeReset) session.togglePause()
                        }
                    }
                }) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = {
                FbngButton(
                    onClick = ::dismissReset,
                    kind = FbngButtonKind.Neutral,
                    modifier = Modifier.focusRequester(cancelFocus),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun GamePausePage(
    ready: Boolean,
    busy: Boolean,
    hasSlot: Boolean,
    dialogOpen: Boolean,
    message: String,
    failed: Boolean,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onReset: () -> Unit,
    onQuit: () -> Unit,
) {
    val focus = remember { HudFocus() }
    LaunchedEffect(ready, busy, dialogOpen) {
        if (busy || dialogOpen) return@LaunchedEffect
        val target = if (ready) focus.tag else ShellTags.Quit
        repeat(4) {
            withFrameNanos { }
            if (runCatching { focus.requesters.getValue(target).requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize().padding(FbngDimens.space16), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = FbngDimens.hudActionsWidth).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(FbngDimens.space4),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
            ) {
                FbngPanel(panel = true) {
                    Text(
                        stringResource(when {
                            failed -> R.string.game_stopped
                            !ready -> R.string.pause_loading
                            else -> R.string.game_paused
                        }),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(stringResource(R.string.pause_back_hint), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                    val button = Modifier.fillMaxWidth().heightIn(min = FbngDimens.buttonHeight)
                    FbngButton(onClick = onResume, enabled = ready && !busy,
                        modifier = button.then(focus.target(ShellTags.Pause)).testTag(ShellTags.Pause)) {
                        Text(stringResource(R.string.resume))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                        ActionButton(stringResource(R.string.save), ShellTags.Save, ready && !busy,
                            {
                                focus.tag = ShellTags.Save
                                onSave()
                            },
                            Modifier.weight(1f).then(focus.target(ShellTags.Save)))
                        ActionButton(stringResource(R.string.load), ShellTags.Load, ready && !busy,
                            {
                                focus.tag = ShellTags.Load
                                onLoad()
                            },
                            Modifier.weight(1f).then(focus.target(ShellTags.Load)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                        ActionButton(stringResource(R.string.reset), ShellTags.Reset, ready && !busy,
                            {
                                focus.tag = ShellTags.Reset
                                onReset()
                            },
                            Modifier.weight(1f).then(focus.target(ShellTags.Reset)))
                        ActionButton(stringResource(R.string.quit), ShellTags.Quit, !busy, onQuit,
                            Modifier.weight(1f).then(focus.target(ShellTags.Quit)))
                    }
                }
                FbngPanel {
                    Text(stringResource(if (hasSlot) R.string.slot_ready else R.string.slot_empty),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyLarge,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.GameHud(
    layout: GameHudLayout,
    ready: Boolean,
    busy: Boolean,
    hasSlot: Boolean,
    message: String,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onQuit: () -> Unit,
    paused: Boolean = false,
    dialogOpen: Boolean = false,
    onPause: () -> Unit = {},
    onReset: () -> Unit = {},
) {
    val focus = remember { HudFocus() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(paused, busy, ready, dialogOpen, layout.band, layout.axis) {
        if (paused && ready && !busy && !dialogOpen) focus.requesters.getValue(focus.tag).requestFocus()
        else if (!paused) focusManager.clearFocus(force = true)
    }
    when (layout.band) {
        HudBand.Sides -> {
            StatusDock(
                modifier = Modifier.align(Alignment.CenterStart).barSlot(widthPx = layout.barPx).clipToBounds(),
                hasSlot = hasSlot,
                message = message,
                roomy = roomyBar(layout.barPx),
            )
            ButtonDock(
                modifier = Modifier.align(Alignment.CenterEnd).barSlot(widthPx = layout.barPx).clipToBounds(),
                vertical = true,
                ready = ready,
                busy = busy,
                onSave = onSave,
                onLoad = onLoad,
                onQuit = onQuit,
                paused = paused,
                focus = focus,
                onPause = onPause,
                onReset = onReset,
            )
        }
        HudBand.Ends -> {
            StatusDock(
                modifier = Modifier.align(Alignment.TopCenter).barSlot(heightPx = layout.barPx).clipToBounds(),
                hasSlot = hasSlot,
                message = message,
                roomy = roomyBar(layout.barPx),
            )
            ButtonDock(
                modifier = Modifier.align(Alignment.BottomCenter).barSlot(heightPx = layout.barPx).clipToBounds(),
                vertical = false,
                ready = ready,
                busy = busy,
                onSave = onSave,
                onLoad = onLoad,
                onQuit = onQuit,
                paused = paused,
                focus = focus,
                onPause = onPause,
                onReset = onReset,
            )
        }
        HudBand.Overlay -> OverlayDock(
            axis = layout.axis,
            ready = ready,
            busy = busy,
            hasSlot = hasSlot,
            message = message,
            onSave = onSave,
            onLoad = onLoad,
            onQuit = onQuit,
            paused = paused,
            focus = focus,
            onPause = onPause,
            onReset = onReset,
        )
    }
}

@Composable
private fun roomyBar(barPx: Int): Boolean {
    val density = LocalDensity.current
    return with(density) { barPx.toDp() } >= 96.dp
}

@Composable
private fun StatusDock(
    modifier: Modifier,
    hasSlot: Boolean,
    message: String,
    roomy: Boolean,
) {
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight - 8.dp),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (roomy) {
                        Text(
                            stringResource(R.string.game_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        stringResource(if (hasSlot) R.string.slot_ready else R.string.slot_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (roomy) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (message.isNotEmpty()) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (roomy) 6 else 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (roomy) {
                        Text(
                            stringResource(R.string.in_game_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ButtonDock(
    modifier: Modifier,
    vertical: Boolean,
    ready: Boolean,
    busy: Boolean,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onQuit: () -> Unit,
    paused: Boolean,
    focus: HudFocus,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(FbngDimens.space4)
                    .then(if (vertical) Modifier.fillMaxWidth() else Modifier.widthIn(max = FbngDimens.hudActionsWidth).fillMaxWidth())
                    .heightIn(max = maxHeight - FbngDimens.space8),
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                if (vertical) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(vertical = FbngDimens.space4),
                        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                    ) {
                        HudActions(ready, busy, paused, focus, onSave, onLoad, onPause, onReset, onQuit,
                            Modifier.fillMaxWidth().heightIn(min = FbngDimens.buttonHeight))
                    }
                } else {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FbngDimens.space4),
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HudActions(ready, busy, paused, focus, onSave, onLoad, onPause, onReset, onQuit,
                            Modifier.widthIn(min = FbngDimens.hudActionWidth).heightIn(min = FbngDimens.buttonHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.OverlayDock(
    axis: BarAxis,
    ready: Boolean,
    busy: Boolean,
    hasSlot: Boolean,
    message: String,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onQuit: () -> Unit,
    paused: Boolean,
    focus: HudFocus,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    val align = when (axis) {
        BarAxis.Sides -> Alignment.CenterEnd
        BarAxis.Ends -> Alignment.BottomCenter
        BarAxis.None -> Alignment.TopCenter
    }
    Surface(
        modifier = Modifier.align(align).padding(FbngDimens.space8).widthIn(max = FbngDimens.hudActionsWidth),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(FbngDimens.space8),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            ) {
                HudActions(ready, busy, paused, focus, onSave, onLoad, onPause, onReset, onQuit,
                    Modifier.widthIn(min = FbngDimens.hudActionWidth).heightIn(min = FbngDimens.buttonHeight))
            }
            Text(
                stringResource(if (hasSlot) R.string.slot_ready else R.string.slot_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (message.isNotEmpty()) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private class HudFocus {
    val requesters = listOf(ShellTags.Pause, ShellTags.Save, ShellTags.Load, ShellTags.Reset, ShellTags.Quit)
        .associateWith { FocusRequester() }
    var tag = ShellTags.Pause

    fun target(id: String): Modifier = Modifier
        .focusRequester(requesters.getValue(id))
        .onFocusChanged { if (it.isFocused) tag = id }
}

@Composable
private fun HudActions(
    ready: Boolean,
    busy: Boolean,
    paused: Boolean,
    focus: HudFocus,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier,
) {
    ActionButton(
        stringResource(if (paused) R.string.resume else R.string.pause), ShellTags.Pause,
        enabled = ready && !busy, onClick = onPause,
        modifier = modifier.then(focus.target(ShellTags.Pause)),
    )
    ActionButton(stringResource(R.string.save), ShellTags.Save, ready && !busy, onSave, modifier.then(focus.target(ShellTags.Save)))
    ActionButton(stringResource(R.string.load), ShellTags.Load, ready && !busy, onLoad, modifier.then(focus.target(ShellTags.Load)))
    ActionButton(stringResource(R.string.reset), ShellTags.Reset, ready && !busy, onReset, modifier.then(focus.target(ShellTags.Reset)))
    ActionButton(stringResource(R.string.quit), ShellTags.Quit, !busy, onQuit, modifier.then(focus.target(ShellTags.Quit)))
}

@Composable
private fun ActionButton(
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
) {
    FbngButton(
        onClick = onClick,
        enabled = enabled,
        kind = FbngButtonKind.Outline,
        modifier = modifier.testTag(tag),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The slot is exactly [widthPx] or [heightPx] when set, so the dock cannot cross into the game. */
private fun Modifier.barSlot(widthPx: Int? = null, heightPx: Int? = null): Modifier = layout { measurable, constraints ->
    val width = (widthPx ?: constraints.maxWidth).coerceIn(0, constraints.maxWidth)
    val height = (heightPx ?: constraints.maxHeight).coerceIn(0, constraints.maxHeight)
    val placeable = measurable.measure(Constraints(width, width, height, height))
    layout(width, height) { placeable.place(0, 0) }
}

@Composable
internal fun MissingCore(onQuit: () -> Unit) {
    val quit = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(4) {
            val focused = try {
                quit.requestFocus(FocusDirection.Enter)
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
            if (focused) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(FbngDimens.space24)
                .fbngIsland().padding(FbngDimens.space24),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space16, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.core_missing),
                modifier = Modifier
                    .testTag(ShellTags.CoreMissing)
                    .focusable(),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            FbngButton(
                onClick = onQuit,
                modifier = Modifier
                    .testTag(ShellTags.Quit)
                    .heightIn(min = 48.dp)
                    .focusRequester(quit),
            ) { Text(stringResource(R.string.quit)) }
        }
    }
}
