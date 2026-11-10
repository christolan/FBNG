package com.xiaojiwei.fbng.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.game.SessionLifecycleOwner
import com.xiaojiwei.fbng.library.AppStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "FBNG"
private val ControlBand = 168.dp

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
    val notReadyText = stringResource(R.string.core_not_ready)
    val saveWorking = stringResource(R.string.save_working)
    val saveOk = stringResource(R.string.save_ok)
    val saveEmpty = stringResource(R.string.save_empty)
    val saveFail = stringResource(R.string.save_fail)
    val loadWorking = stringResource(R.string.load_working)
    val loadOk = stringResource(R.string.load_ok)
    val loadMissing = stringResource(R.string.load_missing)
    val loadFail = stringResource(R.string.load_fail)

    LaunchedEffect(session.view) {
        session.view.getGLRetroEvents().collect { event ->
            if (event is GLRetroView.GLRetroEvents.SurfaceCreated ||
                event is GLRetroView.GLRetroEvents.FrameRendered
            ) {
                ready = true
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

    val fold = rememberFoldSnapshot()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val chrome = chooseGameChrome(
            widthDp = maxWidth.value.toInt(),
            heightDp = maxHeight.value.toInt(),
            widthPx = constraints.maxWidth,
            heightPx = constraints.maxHeight,
            fold = fold,
        )
        val density = LocalDensity.current
        val gameModifier = when (chrome) {
            GameChrome.BottomBar -> Modifier.fillMaxSize().padding(bottom = ControlBand)
            GameChrome.SideBar -> Modifier.fillMaxSize().padding(end = ControlBand)
            GameChrome.SplitVertical -> Modifier
                .align(Alignment.TopStart)
                .width(with(density) { (fold?.hingeStartPx ?: 0).toDp() })
                .fillMaxHeight()
            GameChrome.SplitHorizontal -> Modifier
                .align(Alignment.TopStart)
                .height(with(density) { (fold?.hingeStartPx ?: 0).toDp() })
                .fillMaxWidth()
        }
        val controlModifier = when (chrome) {
            GameChrome.BottomBar -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(ControlBand)
            GameChrome.SideBar -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(ControlBand)
            GameChrome.SplitVertical -> Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(with(density) { (constraints.maxWidth - (fold?.hingeEndPx ?: constraints.maxWidth)).toDp() })
            GameChrome.SplitHorizontal -> Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { (constraints.maxHeight - (fold?.hingeEndPx ?: constraints.maxHeight)).toDp() })
        }
        // One AndroidView for the session. A new modifier only resizes GLSurfaceView,
        // so folding or rotating does not construct another core.
        AndroidView(factory = { session.view }, modifier = gameModifier)
        ControlPanel(
            modifier = controlModifier,
            ready = ready,
            busy = busy,
            hasSlot = hasSlot,
            message = message,
            onSave = {
                if (!ready || busy) {
                    message = notReadyText
                    return@ControlPanel
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
                    return@ControlPanel
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

@Composable
private fun ControlPanel(
    modifier: Modifier,
    ready: Boolean,
    busy: Boolean,
    hasSlot: Boolean,
    message: String,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onQuit: () -> Unit,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.game_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(if (hasSlot) R.string.slot_ready else R.string.slot_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ActionButton(stringResource(R.string.save), enabled = ready && !busy, onClick = onSave)
        ActionButton(stringResource(R.string.load), enabled = ready && !busy, onClick = onLoad)
        ActionButton(stringResource(R.string.quit), enabled = !busy, onClick = onQuit)
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            stringResource(R.string.in_game_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun MissingCore(onQuit: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.core_missing))
        Button(onClick = onQuit) { Text(stringResource(R.string.quit)) }
    }
}
