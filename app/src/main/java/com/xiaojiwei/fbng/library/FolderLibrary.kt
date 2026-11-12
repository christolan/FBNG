package com.xiaojiwei.fbng.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.importing.ZipArchives
import com.xiaojiwei.fbng.importing.withLocalZip
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class LibraryFolder(val uri: Uri, val name: String)

internal data class FolderScan(
    val folder: LibraryFolder,
    val roms: Int = 0,
    val bios: Int = 0,
    val skipped: Int = 0,
    val unreadable: Int = 0,
    val unavailable: Boolean = false,
    val incomplete: Boolean = false,
)

internal data class LibraryRefresh(
    val snapshot: LibrarySnapshot,
    val folders: List<FolderScan>,
)

internal data class PreparedGame(
    val rom: File,
    val systemDir: File,
    val savesDir: File,
    val stateDir: File,
)

internal class FolderLibrary(context: Context, private val storage: AppStorage) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("library-folders", Context.MODE_PRIVATE)

    fun add(uri: Uri) {
        val rootId = DocumentsContract.getTreeDocumentId(uri)
        val root = DocumentsContract.buildDocumentUriUsingTree(uri, rootId)
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = try {
            resolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (error: Exception) {
            logReadFailure("Could not read folder name", error)
            null
        }
        preferences.edit().putString(uri.toString(), name ?: rootId).apply()
    }

    fun remove(uri: Uri) {
        preferences.edit().remove(uri.toString()).apply()
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider may already have revoked the grant.
        }
    }

    suspend fun refresh(catalog: DatCatalog): LibraryRefresh {
        storage.ensure()
        // Older builds kept a disposable copy of the scanned folder archives here.
        File(storage.root, "folder-library").deleteRecursively()
        storage.playDir.deleteRecursively()
        val scanner = LibraryScanner(catalog)
        val archives = scanner.installed(storage).toMutableList()
        val folders = preferences.all.mapNotNull { (uri, name) ->
            (name as? String)?.let { LibraryFolder(Uri.parse(uri), it) }
        }.sortedWith(compareBy({ it.name }, { it.uri.toString() }))
        val scans = folders.map { scan(it, scanner, archives) }
        return LibraryRefresh(scanner.snapshot(archives), scans)
    }

    suspend fun prepare(game: LibraryGame): PreparedGame {
        val directory = storage.playDir
        if (!directory.deleteRecursively() || !directory.mkdirs()) {
            throw IOException("Cannot prepare the game directory")
        }
        try {
            for (archive in game.archives) {
                currentCoroutineContext().ensureActive()
                val destination = File(directory, storage.archive(archive.set.name, archive.set.isBios).name)
                when (val location = archive.location) {
                    is ArchiveLocation.Installed -> try {
                        Files.createLink(destination.toPath(), location.file.toPath())
                    } catch (_: IOException) {
                        location.file.copyTo(destination)
                    }
                    is ArchiveLocation.Folder -> {
                        val input = resolver.openInputStream(location.uri)
                            ?: throw IOException("Cannot open ${archive.set.name}")
                        input.use { source -> destination.outputStream().use { source.copyTo(it) } }
                    }
                }
                if (ZipArchives.read(destination) != archive.members) {
                    throw IOException("Archive changed: ${archive.set.name}")
                }
            }
            return PreparedGame(
                rom = File(directory, storage.archive(game.name, isBios = false).name),
                systemDir = storage.systemDir,
                savesDir = storage.savesDir,
                stateDir = storage.stateDir(game.name),
            )
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    private suspend fun scan(
        folder: LibraryFolder,
        scanner: LibraryScanner,
        archives: MutableList<LibraryArchive>,
    ): FolderScan {
        val rootId = DocumentsContract.getTreeDocumentId(folder.uri)
        val pending = ArrayDeque(listOf(rootId))
        val visited = mutableSetOf<String>()
        var roms = 0
        var bios = 0
        var skipped = 0
        var unreadable = 0
        var incomplete = false
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val parent = pending.removeFirst()
            if (!visited.add(parent)) continue
            val children = try {
                children(folder.uri, parent)
            } catch (error: Exception) {
                logReadFailure("Could not list library folder", error)
                if (parent == rootId) return FolderScan(folder, unavailable = true)
                incomplete = true
                continue
            }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.directory) {
                    pending.addLast(child.id)
                    continue
                }
                if (!child.name.endsWith(".zip", ignoreCase = true) && child.mimeType != "application/zip") continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(folder.uri, child.id)
                try {
                    resolver.withLocalZip(uri, storage.cacheDir) { file ->
                        val archive = scanner.inspect(file, child.name, ArchiveLocation.Folder(uri))
                        if (archive == null) {
                            skipped++
                        } else {
                            archives += archive
                            if (archive.set.isBios) bios++ else roms++
                        }
                    }
                } catch (error: Exception) {
                    logReadFailure("Could not read or prepare ${child.name}", error)
                    unreadable++
                }
            }
        }
        return FolderScan(folder, roms, bios, skipped, unreadable, incomplete = incomplete)
    }

    private fun children(tree: Uri, parent: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val cursor = resolver.query(uri, columns, null, null, null) ?: throw IOException("Cannot list $uri")
        return cursor.use {
            if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING) ||
                it.extras.getString(DocumentsContract.EXTRA_ERROR) != null
            ) throw IOException("Folder listing is incomplete: $uri")
            buildList {
                while (it.moveToNext()) add(Child(it.getString(0), it.getString(1), it.getString(2)))
            }.sortedWith(compareBy({ child -> child.name }, { child -> child.id }))
        }
    }

    private data class Child(val id: String, val name: String, val mimeType: String) {
        val directory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    }

    private fun logReadFailure(message: String, error: Exception) {
        if (error is CancellationException) throw error
        Log.w(TAG, message, error)
    }

    private companion object {
        const val TAG = "FBNG"
    }
}
