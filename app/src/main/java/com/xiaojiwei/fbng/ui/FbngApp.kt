package com.xiaojiwei.fbng.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.FbngApplication
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.artwork.CoverRepository
import com.xiaojiwei.fbng.artwork.CoverWorker
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.DatParse
import com.xiaojiwei.fbng.dat.FbneoDat
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.FolderLibrary
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.library.LibraryRefresh
import com.xiaojiwei.fbng.library.PreparedGame
import com.xiaojiwei.fbng.library.RecentGames
import com.xiaojiwei.fbng.library.readGameTitles
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import com.xiaojiwei.fbng.update.UpdateState
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DAT_ASSET = "fbneo/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat"
private const val GAME_TITLES_ASSET = "fbneo/game-titles-zh-Hans.tsv"
private const val TAG = "FBNG"
private val libraryLock = Mutex()
private val prepareLock = Mutex()

sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data class Ready(
        val catalog: DatCatalog,
        val localizedTitles: Map<String, String> = emptyMap(),
    ) : CatalogLoad
    data class Failed(val message: String) : CatalogLoad
}

private data class GameLaunch(val game: PreparedGame, val entry: LibraryEntry, val initialSlot: Int?)

@Composable
fun FbngApp(bridge: GamepadBridge) {
    val context = LocalContext.current.applicationContext
    val appearance = remember(context) { AppearancePreferences(context) }
    var cornerRadius by remember(appearance) { mutableStateOf(appearance.readCornerRadius()) }
    val updater = if (BuildConfig.DEBUG) null else (context as FbngApplication).appUpdate
    val updateState = updater?.state?.collectAsStateWithLifecycle()?.value
    val installingUpdate = updateState == UpdateState.Installed || updateState is UpdateState.Installing
    LaunchedEffect(updater) { updater?.checkOnStartup() }
    val storage = remember { AppStorage(context.filesDir).also(AppStorage::ensure) }
    val folders = remember { FolderLibrary(context, storage) }
    val recentGames = remember { RecentGames(context) }
    var recentNames by remember { mutableStateOf(recentGames.read()) }
    val favorites = remember(context) { context.getSharedPreferences("library-favorites", Context.MODE_PRIVATE) }
    val coverPreferences = remember(context) { context.getSharedPreferences("cover-requests", Context.MODE_PRIVATE) }
    val coverRepository = remember(context) { CoverRepository.get(context) }
    val coverImages by coverRepository.images.collectAsStateWithLifecycle()
    var manualCoverId by remember { mutableStateOf(coverPreferences.getString("manual-id", null)) }
    val manualCoverWork by produceState<WorkInfo?>(null, manualCoverId) {
        val id = manualCoverId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (id != null) WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collect { value = it }
    }
    LaunchedEffect(coverRepository) {
        coverRepository.reloadImages()
        if (coverRepository.eligibleNames().isNotEmpty()) CoverWorker.enqueue(context)
    }
    fun requestCovers(refresh: Boolean) {
        if (manualCoverWork?.state?.isFinished == false &&
            (if (refresh) CoverWorker.REFRESH else "missing") in manualCoverWork?.tags.orEmpty()
        ) return
        val id = CoverWorker.enqueue(context, manual = true, refresh = refresh).toString()
        coverPreferences.edit().putString("manual-id", id).apply()
        manualCoverId = id
    }
    var favoriteNames by remember(favorites) {
        mutableStateOf(favorites.getStringSet("sets", null).orEmpty().toSet())
    }
    fun toggleFavorite(name: String) {
        val updated = if (name in favoriteNames) favoriteNames - name else favoriteNames + name
        favorites.edit().putStringSet("sets", updated).apply()
        favoriteNames = updated
        if (name in updated) CoverWorker.enqueue(context)
    }
    val scope = rememberCoroutineScope()
    val saveableState = rememberSaveableStateHolder()
    val load by produceState<CatalogLoad>(CatalogLoad.Loading, context) {
        value = withContext(Dispatchers.IO) { loadBundledCatalog(context) }
    }
    var library by remember { mutableStateOf<LibraryRefresh?>(null) }
    var playing by remember { mutableStateOf<GameLaunch?>(null) }
    var workMessage by remember { mutableStateOf<Int?>(null) }
    var initialScan by remember { mutableStateOf(true) }
    var preparing by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<Int?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshLibrary(catalog: DatCatalog) {
        library = withContext(Dispatchers.IO) {
            folders.refresh(catalog) { partial ->
                withContext(Dispatchers.Main) {
                    // Keep the visible pages stable until the background scan completes.
                    if (library == null) library = partial
                }
            }
        }
    }

    LaunchedEffect(load) {
        val ready = load as? CatalogLoad.Ready ?: return@LaunchedEffect
        try {
            libraryLock.withLock {
                if (library == null) refreshLibrary(ready.catalog)
            }
        } finally {
            initialScan = false
        }
    }

    fun updateLibrary(message: Int, action: suspend (DatCatalog) -> Unit = {}) {
        if (workMessage != null || preparing || playing != null || updater?.installing == true) return
        workMessage = message
        folderError = null
        launchError = null
        scope.launch {
            try {
                val ready = snapshotFlow { load }.first { it != CatalogLoad.Loading } as? CatalogLoad.Ready
                if (ready != null) libraryLock.withLock {
                    action(ready.catalog)
                    refreshLibrary(ready.catalog)
                }
            } finally {
                workMessage = null
            }
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

    fun play(entry: LibraryEntry, initialSlot: Int?) {
        if (preparing || playing != null || updater?.installing == true) return
        val setName = entry.game.name
        val game = library?.snapshot?.games?.firstOrNull { it.name == setName && it.canLaunch } ?: return
        preparing = true
        launchError = null
        scope.launch {
            try {
                val prepared = prepareLock.withLock { withContext(Dispatchers.IO) { folders.prepare(game) } }
                playing = GameLaunch(prepared, entry.copy(game = game), initialSlot)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Could not prepare ${game.name}", error)
                launchError = setName
            } finally {
                preparing = false
            }
        }
    }

    var returnFromGame by remember { mutableStateOf<String?>(null) }
    FbngTheme(cornerRadius) {
        val game = playing
        if (game == null) {
            saveableState.SaveableStateProvider("Library") {
                LibraryContent(
                    load = load,
                    snapshot = library?.snapshot,
                    busy = initialScan || workMessage != null || preparing || installingUpdate,
                    playBusy = preparing || installingUpdate,
                    workMessage = workMessage ?: when {
                        initialScan -> R.string.folder_scanning
                        installingUpdate -> R.string.update_installing
                        else -> R.string.preparing_game
                    },
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
                    onPlay = ::play,
                    initialSection = ShellSection.Favorites,
                    favoriteNames = favoriteNames,
                    recentNames = recentNames,
                    recentTimes = recentNames.associateWith(recentGames::playedAt),
                    cornerRadius = cornerRadius,
                    onCornerRadiusChange = { option ->
                        appearance.writeCornerRadius(option)
                        cornerRadius = option
                    },
                    returnFromGame = returnFromGame,
                    onReturnRestored = { returnFromGame = null },
                    covers = CoverUiState(coverImages, manualCoverWork, { requestCovers(false) }, { requestCovers(true) }),
                    appUpdate = if (updater != null && updateState != null) {
                        UpdateUiState(updater, updateState, canInstall = !initialScan && workMessage == null && !preparing)
                    } else null,
                    onToggleFavorite = ::toggleFavorite,
                )
            }
        } else {
            val setName = game.entry.game.name
            GameScreen(
                game = game.game,
                entry = game.entry,
                initialSlot = game.initialSlot,
                cover = coverImages[setName],
                favorite = setName in favoriteNames,
                bridge = bridge,
                onToggleFavorite = { toggleFavorite(setName) },
                onStarted = {
                    recentNames = recentGames.record(setName)
                    CoverWorker.enqueue(context)
                },
                onQuit = {
                    returnFromGame = setName
                    playing = null
                },
            )
        }
    }
}

private fun loadBundledCatalog(context: Context): CatalogLoad {
    return try {
        val text = context.assets.open(DAT_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        when (val parsed = FbneoDat.parse(text)) {
            is DatParse.Ok -> {
                Log.i(TAG, "DAT ${parsed.catalog.version}: ${parsed.catalog.games.size} games")
                val localizedTitles = try {
                    context.assets.open(GAME_TITLES_ASSET).bufferedReader(Charsets.UTF_8).use(::readGameTitles)
                } catch (error: IOException) {
                    Log.w(TAG, "Could not load the Chinese game titles", error)
                    emptyMap()
                }
                CatalogLoad.Ready(parsed.catalog, localizedTitles)
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

private object LocalFolderPicker : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
