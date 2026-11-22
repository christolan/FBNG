package com.xiaojiwei.fbng.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.xiaojiwei.fbng.game.GameProgressStore
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.library.PreparedGame
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "FBNG"

@Composable
internal fun GameScreen(
    game: PreparedGame,
    entry: LibraryEntry,
    initialSlot: Int?,
    preview: GamePreviewArtwork,
    favorite: Boolean,
    bridge: GamepadBridge,
    onToggleFavorite: () -> Unit,
    onQuit: () -> Unit,
    onStarted: () -> Unit = {},
) {
    var deletedSlot by rememberSaveable(game.rom.absolutePath) { mutableStateOf<Int?>(null) }
    var selected by rememberSaveable(game.rom.absolutePath) { mutableStateOf(initialSlot ?: 1) }
    var generation by rememberSaveable(game.rom.absolutePath) { mutableStateOf(0) }
    if (deletedSlot != null) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            GameLaunchPage(entry, preview, rememberFoldSnapshot(), favorite, busy = false, launchError = false,
                onPlay = { selected = it ?: 1; generation++; deletedSlot = null },
                onToggleFavorite = onToggleFavorite, onConfigureFolders = onQuit, onBack = onQuit,
                initialSelectedSlot = deletedSlot)
        }
    } else key(generation) {
        GameSessionScreen(game, entry, selected, preview, favorite, bridge, onToggleFavorite, onQuit,
            onStarted = onStarted, onDeleted = { deletedSlot = it },
            onStartEmptySave = { selected = it; generation++ })
    }
}

@Composable
private fun GameSessionScreen(
    game: PreparedGame,
    entry: LibraryEntry,
    initialSlot: Int,
    preview: GamePreviewArtwork,
    favorite: Boolean,
    bridge: GamepadBridge,
    onToggleFavorite: () -> Unit,
    onQuit: () -> Unit,
    onStarted: () -> Unit,
    onDeleted: (Int) -> Unit,
    onStartEmptySave: (Int) -> Unit,
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
        BackHandler(onBack = onQuit)
        MissingCore(onQuit)
        return
    }
    val store = remember(game.stateDir) { GameProgressStore(game.stateDir) }
    val session = remember(corePath, game.rom.absolutePath, initialSlot) {
        val nativeSaveDirectory = File(context.cacheDir, "fbneo-${UUID.randomUUID()}").apply { mkdir() }
        val data = GLRetroViewData(activity).apply {
            coreFilePath = corePath
            gameFilePath = game.rom.absolutePath
            systemDirectory = game.systemDir.absolutePath
            savesDirectory = nativeSaveDirectory.absolutePath
            variables = emptyArray<Variable>()
            shader = ShaderConfig.Default
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true
            skipDuplicateFrames = false
            enableMicrophone = false
        }
        RunningGame(GLRetroView(activity, data), store, initialSlot, nativeSaveDirectory)
    }
    val scope = rememberCoroutineScope()
    var message by remember(session) { mutableStateOf("") }
    var messageError by remember(session) { mutableStateOf(false) }
    var pausePageOpen by remember(session) { mutableStateOf(false) }
    var pauseSaved by remember(session) { mutableStateOf(false) }
    var panelInputBlocked by remember(session) { mutableStateOf(false) }
    var coreFailed by remember(session) { mutableStateOf(false) }
    var foreground by remember(session) { mutableStateOf(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val started by rememberUpdatedState(onStarted)
    val fold = rememberFoldSnapshot()
    fun reportFailure(error: Exception, resource: Int) {
        if (error is CancellationException) throw error
        Log.e(TAG, "Game operation failed", error)
        message = resources.getString(resource, error.message ?: error.javaClass.simpleName)
        messageError = true
        pausePageOpen = true
        bridge.target = null
        if (!session.ready) coreFailed = true
    }
    fun quit() {
        if (session.busy || panelInputBlocked) return
        scope.launch {
            try { session.saveBeforeQuit(); onQuit() }
            catch (error: Exception) { reportFailure(error, R.string.save_fail) }
        }
    }
    fun openPausePage() {
        bridge.target = null
        pausePageOpen = true
    }
    fun resume() {
        if (!session.ready || session.busy || !session.paused || coreFailed || panelInputBlocked) return
        session.resume()
        pausePageOpen = false
        pauseSaved = false
        if (foreground) bridge.target = session.view
    }
    fun loadSave(slot: Int) {
        if (slot == session.slotId) {
            resume()
            return
        }
        if (!session.ready || session.busy || !session.paused || coreFailed || panelInputBlocked) return
        scope.launch {
            try {
                if (session.switchSave(slot)) resume()
                else {
                    session.destroy()
                    onStartEmptySave(slot)
                }
            } catch (error: Exception) {
                reportFailure(error, R.string.auto_load_fail)
            }
        }
    }
    fun back() { if (pausePageOpen) resume() else openPausePage() }
    BackHandler(onBack = ::back)
    LaunchedEffect(session, pausePageOpen, session.ready) {
        if (pausePageOpen && session.ready && !session.paused) {
            try { session.pauseAndSave() }
            catch (error: Exception) { reportFailure(error, R.string.save_fail) }
            finally { pauseSaved = true }
        }
    }
    DisposableEffect(session) {
        val insets = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { foreground = true; session.start() }
                Lifecycle.Event.ON_PAUSE -> try { foreground = false; bridge.target = null; session.stop() }
                    catch (error: Exception) { reportFailure(error, R.string.save_fail) }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            bridge.onPauseToggle = null
            bridge.onPrimaryDisconnected = null
            bridge.target = null
            activity.lifecycle.removeObserver(observer)
            try { session.destroy() } catch (error: Exception) { Log.e(TAG, "Could not save on disposal", error) }
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    val receivesInput = foreground && session.ready && !pausePageOpen && !session.paused && !session.busy && !panelInputBlocked && !coreFailed
    SideEffect {
        bridge.target = session.view.takeIf { receivesInput }
        bridge.onPauseToggle = { if (foreground && !panelInputBlocked && !session.busy) back() }
        bridge.onPrimaryDisconnected = { if (!coreFailed) { openPausePage(); message = resources.getString(R.string.gamepad_disconnected) } }
    }
    LaunchedEffect(session.view) {
        session.view.getGLRetroEvents().collect { event ->
            if (event is GLRetroView.GLRetroEvents.SurfaceCreated || event is GLRetroView.GLRetroEvents.FrameRendered) {
                if (!session.initializationStarted && !coreFailed) {
                    try {
                        val saved = session.initialize()
                        val warning = saved?.takeIf { it.coreCommit.isNotEmpty() && it.coreCommit != BuildConfig.FBNEO_COMMIT }
                        message = warning?.let { resources.getString(R.string.load_version_warn, it.coreCommit.take(12), BuildConfig.FBNEO_COMMIT_SHORT) } ?: ""
                        started()
                    } catch (error: Exception) { reportFailure(error, R.string.auto_load_fail) }
                }
            }
        }
    }
    LaunchedEffect(session.view) {
        session.view.getGLRetroErrors().collect { code ->
            coreFailed = true
            session.fail()
            messageError = true
            message = resources.getString(when (code) {
                GLRetroView.ERROR_LOAD_LIBRARY -> R.string.core_error_library
                GLRetroView.ERROR_LOAD_GAME -> R.string.core_error_game
                else -> R.string.core_error_generic
            }, code)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize())
            if (pauseSaved || coreFailed) GameProgressPage(
                entry, preview, fold, favorite, store,
                busy = session.busy || (session.ready && !session.paused), ready = session.ready && !coreFailed, message = message,
                messageIsError = messageError, activeSlot = session.slotId,
                onPrimary = ::loadSave, onToggleFavorite = onToggleFavorite, onBack = ::resume,
                onQuit = ::quit, onDialogOpen = { panelInputBlocked = it; session.setDialogOpen(it) },
                onCreateSnapshot = { session.createSnapshot() },
                onApplySnapshot = { id, slot -> session.applySnapshot(id, slot) },
                onDeleteCurrent = { session.deleteCurrentProgress(); onDeleted(session.slotId) },
                onOperationError = { if (!session.ready) reportFailure(it, R.string.auto_load_fail) },
            )
        }
    }
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
