package com.xiaojiwei.fbng.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.xiaojiwei.fbng.game.CoreGeometry
import com.xiaojiwei.fbng.game.FbneoCore
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.game.SessionLifecycleOwner
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.ui.theme.FbngButton
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FBNG"

/** 48dp touch target plus 4dp of padding on each side of the bar. */
internal val MinHudBar = 56.dp

private class RunningSession(
    val view: GLRetroView,
    val life: SessionLifecycleOwner,
)

@Composable
fun GameScreen(
    storage: AppStorage,
    bridge: GamepadBridge,
    onQuit: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val corePath = remember {
        val info = activity.applicationInfo
        val apks = ArrayList<File>(1 + (info.splitSourceDirs?.size ?: 0))
        apks.add(File(info.sourceDir))
        info.splitSourceDirs?.forEach { apks.add(File(it)) }
        FbneoCore.resolveLoadPath(File(info.nativeLibraryDir), apks)
    }
    BackHandler(onBack = onQuit)
    if (corePath == null) {
        LaunchedEffect(activity) {
            Log.e(
                TAG,
                "no ${FbneoCore.FILE_NAME} in ${activity.applicationInfo.nativeLibraryDir} and no stored ${FbneoCore.ZIP_ENTRY}",
            )
        }
        MissingCore(onQuit)
        return
    }

    val session = remember(corePath, storage.kovshZip.absolutePath) {
        val data = GLRetroViewData(activity).apply {
            coreFilePath = corePath
            gameFilePath = storage.kovshZip.absolutePath
            systemDirectory = storage.systemDir.absolutePath
            savesDirectory = storage.savesDir.absolutePath
            variables = emptyArray<Variable>()
            shader = ShaderConfig.Default
            rumbleEventsEnabled = true
            preferLowLatencyAudio = true
            skipDuplicateFrames = false
            enableMicrophone = false
        }
        val view = GLRetroView(activity, data)
        val life = SessionLifecycleOwner()
        // Load the core before the GL surface exists so retro_load_game is not raced.
        life.lifecycle.addObserver(view)
        life.create()
        Log.i(
            TAG,
            "core $corePath game ${storage.kovshZip.absolutePath} system ${storage.systemDir}",
        )
        RunningSession(view, life)
    }
    val store = remember { SaveStateStore(storage.stateDir) }
    val scope = rememberCoroutineScope()

    DisposableEffect(session) {
        bridge.target = session.view
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        session.life.resume()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> session.life.resume()
                Lifecycle.Event.ON_PAUSE -> session.life.pause()
                Lifecycle.Event.ON_DESTROY -> session.life.destroy()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose {
            bridge.target = null
            activity.lifecycle.removeObserver(observer)
            session.life.destroy()
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var ready by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hasSlot by remember { mutableStateOf(store.hasSlot()) }
    var reportedAspect by remember { mutableStateOf(Float.NaN) }
    val notReadyText = stringResource(R.string.core_not_ready)
    val saveWorking = stringResource(R.string.save_working)
    val saveOk = stringResource(R.string.save_ok)
    val saveEmpty = stringResource(R.string.save_empty)
    val saveFail = stringResource(R.string.save_fail)
    val loadWorking = stringResource(R.string.load_working)
    val loadOk = stringResource(R.string.load_ok)
    val loadMissing = stringResource(R.string.load_missing)
    val loadFail = stringResource(R.string.load_fail)

    val fold = rememberFoldSnapshot()
    LaunchedEffect(session.view, fold?.separating, fold?.hingeStartPx, fold?.orientationVertical) {
        var loggedMissing = false
        var last = Float.NaN
        session.view.getGLRetroEvents().collect { event ->
            if (event is GLRetroView.GLRetroEvents.SurfaceCreated ||
                event is GLRetroView.GLRetroEvents.FrameRendered
            ) {
                ready = true
                val next = CoreGeometry.reportedAspectRatio()
                if (next == null) {
                    if (!loggedMissing) {
                        Log.w(TAG, "core aspect unavailable, HUD uses 4:3 until the core reports one")
                        loggedMissing = true
                    }
                } else if (!last.isFinite() || abs(last - next) > 0.001f) {
                    last = next
                    reportedAspect = next
                    Log.i(TAG, "core aspect $next")
                }
            }
        }
    }
    LaunchedEffect(session.view) {
        session.view.getGLRetroErrors().collect { code ->
            message = when (code) {
                GLRetroView.ERROR_LOAD_LIBRARY -> context.getString(R.string.core_error_library)
                GLRetroView.ERROR_LOAD_GAME -> context.getString(R.string.core_error_game)
                else -> context.getString(R.string.core_error_generic, code)
            }
            Log.e(TAG, "core error $code")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val density = LocalDensity.current
            val widthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            val heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            val layout = layoutGameHud(
                screenWidthPx = widthPx,
                screenHeightPx = heightPx,
                gameAspect = reportedAspect,
                minBarPx = with(density) { MinHudBar.roundToPx() },
            )
            // One AndroidView for the session. It fills the play area; LibretroDroid
            // letterboxes inside that rect. Folding, rotating, or a new core aspect
            // only moves the HUD — the surface is not padded into a sidebar.
            AndroidView(factory = { session.view }, modifier = Modifier.fillMaxSize())
            GameHud(
                layout = layout,
                ready = ready,
                busy = busy,
                hasSlot = hasSlot,
                message = message,
                onSave = {
                    if (!ready || busy) {
                        message = notReadyText
                        return@GameHud
                    }
                    busy = true
                    message = saveWorking
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes = session.view.serializeState()
                                if (bytes.isEmpty()) {
                                    saveEmpty
                                } else {
                                    store.write(bytes, BuildConfig.FBNEO_COMMIT)
                                    saveOk
                                }
                            }.getOrElse { error ->
                                Log.e(TAG, "save failed", error)
                                saveFail.format(error.message ?: error.javaClass.simpleName)
                            }
                        }
                        hasSlot = store.hasSlot()
                        busy = false
                        message = result
                    }
                },
                onLoad = {
                    if (!ready || busy) {
                        message = notReadyText
                        return@GameHud
                    }
                    busy = true
                    message = loadWorking
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            val slot = store.read()
                            if (slot == null) {
                                loadMissing
                            } else {
                                val warn = if (slot.coreCommit.isNotEmpty() && slot.coreCommit != BuildConfig.FBNEO_COMMIT) {
                                    "\n" + context.getString(
                                        R.string.load_version_warn,
                                        slot.coreCommit.take(12),
                                        BuildConfig.FBNEO_COMMIT_SHORT,
                                    )
                                } else {
                                    ""
                                }
                                val loaded = runCatching { session.view.unserializeState(slot.bytes) }.getOrDefault(false)
                                if (loaded) loadOk + warn else loadFail + warn
                            }
                        }
                        busy = false
                        message = result
                    }
                },
                onQuit = onQuit,
            )
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
) {
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
                color = MaterialTheme.colorScheme.surface,
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
) {
    Box(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
                    .then(
                        if (vertical) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.widthIn(max = 520.dp).fillMaxWidth()
                        },
                    )
                    .heightIn(max = maxHeight - 8.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                if (vertical) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ActionButton(stringResource(R.string.save), enabled = ready && !busy, onClick = onSave)
                        ActionButton(stringResource(R.string.load), enabled = ready && !busy, onClick = onLoad)
                        ActionButton(stringResource(R.string.quit), enabled = !busy, onClick = onQuit)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionButton(
                            stringResource(R.string.save),
                            enabled = ready && !busy,
                            onClick = onSave,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                        ActionButton(
                            stringResource(R.string.load),
                            enabled = ready && !busy,
                            onClick = onLoad,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                        ActionButton(
                            stringResource(R.string.quit),
                            enabled = !busy,
                            onClick = onQuit,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
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
) {
    val align = when (axis) {
        BarAxis.Sides -> Alignment.CenterEnd
        BarAxis.Ends -> Alignment.BottomCenter
        BarAxis.None -> Alignment.TopCenter
    }
    Surface(
        modifier = Modifier.align(align).padding(8.dp).widthIn(max = 420.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    stringResource(R.string.save),
                    enabled = ready && !busy,
                    onClick = onSave,
                    modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp),
                )
                ActionButton(
                    stringResource(R.string.load),
                    enabled = ready && !busy,
                    onClick = onLoad,
                    modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp),
                )
                ActionButton(
                    stringResource(R.string.quit),
                    enabled = !busy,
                    onClick = onQuit,
                    modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp),
                )
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

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
) {
    FbngButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
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
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(R.string.core_missing), color = MaterialTheme.colorScheme.onBackground)
            FbngButton(onClick = onQuit) { Text(stringResource(R.string.quit)) }
        }
    }
}
