package com.xiaojiwei.fbng.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.DatParse
import com.xiaojiwei.fbng.dat.FbneoDat
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.importing.ImportReport
import com.xiaojiwei.fbng.importing.InstallOutcome
import com.xiaojiwei.fbng.importing.RomInstaller
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.LibraryScanner
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DAT_ASSET = "fbneo/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat"
private const val TAG = "FBNG"

private sealed interface Screen {
    data object Library : Screen
    data object Game : Screen
}

sealed interface CatalogLoad {
    data object Loading : CatalogLoad
    data class Ready(val catalog: DatCatalog) : CatalogLoad
    data class Failed(val message: String) : CatalogLoad
}

@Composable
fun FbngApp(bridge: GamepadBridge) {
    val context = LocalContext.current
    val storage = remember { AppStorage(context.filesDir).also(AppStorage::ensure) }
    val load by produceState<CatalogLoad>(CatalogLoad.Loading, context) {
        value = withContext(Dispatchers.IO) { loadBundledCatalog(context) }
    }
    var snapshot by remember { mutableStateOf<LibrarySnapshot?>(null) }
    var reports by remember { mutableStateOf<List<ImportReport>>(emptyList()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }

    LaunchedEffect(load) {
        val ready = load as? CatalogLoad.Ready ?: return@LaunchedEffect
        if (snapshot == null) {
            snapshot = withContext(Dispatchers.IO) { LibraryScanner(storage, ready.catalog).scan() }
        }
    }

    FbngTheme {
        when (screen) {
            Screen.Library -> LibraryScreen(
                load = load,
                snapshot = snapshot,
                reports = reports,
                onImported = { imported, refreshed ->
                    reports = imported
                    snapshot = refreshed
                },
                storage = storage,
                onPlay = { screen = Screen.Game },
            )
            Screen.Game -> GameScreen(
                storage = storage,
                bridge = bridge,
                onQuit = { screen = Screen.Library },
            )
        }
    }
}

internal suspend fun importUris(
    context: Context,
    uris: List<Uri>,
    storage: AppStorage,
    catalog: DatCatalog,
): List<ImportReport> = withContext(Dispatchers.IO) {
    val installer = RomInstaller(storage, catalog)
    uris.map { uri ->
        val name = displayName(context, uri)
        val temp = File.createTempFile("import-", ".zip", storage.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                ImportReport(name, InstallOutcome.UNREADABLE, null, null)
            } else {
                input.use { stream -> temp.outputStream().use { stream.copyTo(it) } }
                installer.importFile(temp, name).also { report ->
                    Log.i(TAG, "import $name -> ${report.outcome} ${report.match?.setName}")
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "import failed for $name", error)
            ImportReport(name, InstallOutcome.UNREADABLE, null, null)
        } finally {
            temp.delete()
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

private fun displayName(context: Context, uri: Uri): String {
    val queried = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    return queried?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "zip")
}
