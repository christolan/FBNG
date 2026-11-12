package com.xiaojiwei.fbng.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.DatParse
import com.xiaojiwei.fbng.dat.FbneoDat
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.importing.ImportReport
import com.xiaojiwei.fbng.importing.InstallOutcome
import com.xiaojiwei.fbng.importing.RomInstaller
import com.xiaojiwei.fbng.importing.withLocalZip
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.FolderLibrary
import com.xiaojiwei.fbng.library.LibraryRefresh
import com.xiaojiwei.fbng.library.PreparedGame
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DAT_ASSET = "fbneo/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat"
private const val TAG = "FBNG"
private val libraryLock = Mutex()

sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data class Ready(val catalog: DatCatalog) : CatalogLoad
    data class Failed(val message: String) : CatalogLoad
}

@Composable
fun FbngApp(bridge: GamepadBridge) {
    val context = LocalContext.current.applicationContext
    val storage = remember { AppStorage(context.filesDir).also(AppStorage::ensure) }
    val folders = remember { FolderLibrary(context, storage) }
    val scope = rememberCoroutineScope()
    val saveableState = rememberSaveableStateHolder()
    val load by produceState<CatalogLoad>(CatalogLoad.Loading, context) {
        value = withContext(Dispatchers.IO) { loadBundledCatalog(context) }
    }
    var library by remember { mutableStateOf<LibraryRefresh?>(null) }
    var reports by remember { mutableStateOf<List<ImportReport>>(emptyList()) }
    var playing by remember { mutableStateOf<PreparedGame?>(null) }
    var workMessage by remember { mutableStateOf<Int?>(null) }
    var folderError by remember { mutableStateOf<Int?>(null) }
    var launchError by remember { mutableStateOf(false) }

    LaunchedEffect(load) {
        val ready = load as? CatalogLoad.Ready ?: return@LaunchedEffect
        libraryLock.withLock {
            if (library == null) library = withContext(Dispatchers.IO) { folders.refresh(ready.catalog) }
        }
    }

    fun updateLibrary(message: Int, action: suspend (DatCatalog) -> Unit = {}) {
        if (workMessage != null || playing != null) return
        workMessage = message
        folderError = null
        launchError = false
        scope.launch {
            try {
                val ready = snapshotFlow { load }.first { it != CatalogLoad.Loading } as? CatalogLoad.Ready
                if (ready != null) libraryLock.withLock {
                    action(ready.catalog)
                    library = withContext(Dispatchers.IO) { folders.refresh(ready.catalog) }
                }
            } finally {
                workMessage = null
            }
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) updateLibrary(R.string.importing) { catalog ->
            reports = importUris(context, uris, storage, catalog)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(LocalFolderPicker) { uri ->
        if (uri != null) updateLibrary(R.string.folder_scanning) {
            try {
                withContext(Dispatchers.IO) { folders.add(uri) }
            } catch (error: SecurityException) {
                Log.w(TAG, "Could not retain library folder permission", error)
                folderError = R.string.folder_add_failed
            } catch (error: IllegalArgumentException) {
                Log.w(TAG, "Invalid library folder URI", error)
                folderError = R.string.folder_add_failed
            }
        }
    }

    fun play(setName: String) {
        if (workMessage != null || playing != null) return
        val game = library?.snapshot?.games?.firstOrNull { it.name == setName && it.canLaunch } ?: return
        workMessage = R.string.preparing_game
        launchError = false
        scope.launch {
            try {
                playing = libraryLock.withLock { withContext(Dispatchers.IO) { folders.prepare(game) } }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Could not prepare ${game.name}", error)
                launchError = true
            } finally {
                workMessage = null
            }
        }
    }

    FbngTheme {
        val game = playing
        if (game == null) {
            saveableState.SaveableStateProvider("Library") {
                LibraryContent(
                    load = load,
                    snapshot = library?.snapshot,
                    reports = reports,
                    busy = workMessage != null,
                    workMessage = workMessage ?: R.string.importing,
                    folders = library?.folders.orEmpty(),
                    folderError = folderError,
                    launchError = launchError,
                    folderActions = FolderActions(
                        add = { folderPicker.launch(null) },
                        rescan = { updateLibrary(R.string.folder_scanning) },
                        remove = { uri ->
                            updateLibrary(R.string.folder_scanning) {
                                withContext(Dispatchers.IO) { folders.remove(uri) }
                            }
                        },
                    ),
                    readFold = { rememberFoldSnapshot() },
                    onImport = { zipPicker.launch(ZIP_MIME) },
                    onPlay = ::play,
                )
            }
        } else {
            GameScreen(game = game, bridge = bridge, onQuit = { playing = null })
        }
    }
}

private suspend fun importUris(
    context: Context,
    uris: List<Uri>,
    storage: AppStorage,
    catalog: DatCatalog,
): List<ImportReport> = withContext(Dispatchers.IO) {
    val installer = RomInstaller(storage, catalog)
    uris.map { uri ->
        var name = uri.lastPathSegment ?: "zip"
        try {
            name = displayName(context, uri) ?: name
            context.contentResolver.withLocalZip(uri, storage.cacheDir) { temp ->
                installer.importFile(temp, name).also { report ->
                    Log.i(TAG, "import $name -> ${report.outcome} ${report.match?.setName}")
                }
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Log.w(TAG, "import failed for $name", error)
            ImportReport(name, InstallOutcome.UNREADABLE, null, null)
        }
    }
}

private fun loadBundledCatalog(context: Context): CatalogLoad {
    return try {
        val text = context.assets.open(DAT_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        when (val parsed = FbneoDat.parse(text)) {
            is DatParse.Ok -> {
                Log.i(TAG, "DAT ${parsed.catalog.version}: ${parsed.catalog.games.size} games")
                CatalogLoad.Ready(parsed.catalog)
            }
            is DatParse.Error -> {
                Log.e(TAG, "DAT parse failed: ${parsed.message}")
                CatalogLoad.Failed(parsed.message)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.e(TAG, "DAT load failed", error)
        CatalogLoad.Failed("找不到内置的 FBNeo DAT，游戏库无法打开。")
    }
}

private fun displayName(context: Context, uri: Uri): String? {
    val queried = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    return queried?.takeIf { it.isNotBlank() }
}

private object LocalFolderPicker : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}

private val ZIP_MIME = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
