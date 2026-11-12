package com.xiaojiwei.fbng.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.os.SystemClock
import android.util.Log
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.importing.ZipArchives
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
    private val index = LibraryIndex(File(storage.root, "library-index"))
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

    suspend fun refresh(
        catalog: DatCatalog,
        onUpdate: suspend (LibraryRefresh) -> Unit = {},
    ): LibraryRefresh {
        storage.ensure()
        // Older builds kept a disposable copy of the scanned folder archives here.
        File(storage.root, "folder-library").deleteRecursively()
        val scanner = LibraryScanner(catalog)
        val folders = preferences.all.mapNotNull { (uri, name) ->
            (name as? String)?.let { LibraryFolder(Uri.parse(uri), it) }
        }.sortedWith(compareBy({ it.name }, { it.uri.toString() }))
        val sourceKeys = listOf(INSTALLED_SOURCE) + folders.map { it.uri.toString() }
        val cached = index.read(catalog)
        val current = cached.filterKeys(sourceKeys.toHashSet()::contains).toMutableMap()

        fun snapshot(): LibraryRefresh {
            val archives = sourceKeys.flatMap { source ->
                current[source]?.entries?.values?.mapNotNull { it.archive }.orEmpty()
            }
            val scans = folders.map { folder ->
                val source = current[folder.uri.toString()] ?: return@map FolderScan(folder)
                var roms = 0
                var bios = 0
                var skipped = 0
                for (entry in source.entries.values) {
                    when (entry.archive?.set?.isBios) {
                        true -> bios++
                        false -> roms++
                        null -> skipped++
                    }
                }
                FolderScan(folder, roms, bios, skipped, source.unreadable, source.unavailable, source.incomplete)
            }
            return LibraryRefresh(scanner.snapshot(archives), scans)
        }

        if (current.isNotEmpty()) onUpdate(snapshot())
        current[INSTALLED_SOURCE] = scanner.installed(storage, cached[INSTALLED_SOURCE]?.entries.orEmpty())
        var lastPublished: Long? = null
        for (folder in folders) {
            val key = folder.uri.toString()
            val previous = cached[key]?.entries.orEmpty()
            current[key] = scan(folder, scanner, previous) { entries ->
                val now = SystemClock.elapsedRealtime()
                if (lastPublished?.let { now - it < PUBLISH_INTERVAL_MS } != true) {
                    current[key] = IndexedSource(previous + entries)
                    onUpdate(snapshot())
                    lastPublished = now
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (current != cached) index.write(catalog, current)
        return snapshot()
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
        cached: Map<String, IndexedArchive>,
        onMatch: suspend (Map<String, IndexedArchive>) -> Unit,
    ): IndexedSource {
        val rootId = DocumentsContract.getTreeDocumentId(folder.uri)
        val pending = ArrayDeque(listOf(rootId))
        val visited = mutableSetOf<String>()
        val entries = linkedMapOf<String, IndexedArchive>()
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
                if (parent == rootId) return IndexedSource(unavailable = true)
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
                val path = uri.toString()
                val stamp = ArchiveStamp(child.name, child.size, child.modified)
                val previous = cached[path]
                val entry = try {
                    if (previous != null && stamp.matches(previous.stamp)) previous else {
                        val members = ZipArchives.directory(resolver, uri, storage.cacheDir)
                        IndexedArchive(stamp, scanner.inspect(members, child.name, ArchiveLocation.Folder(uri)))
                    }
                } catch (error: Exception) {
                    logReadFailure("Could not read ZIP directory for ${child.name}", error)
                    unreadable++
                    continue
                }
                entries[path] = entry
                if (entry.archive != null && entry !== previous) onMatch(entries)
            }
        }
        return IndexedSource(entries, unreadable, incomplete = incomplete)
    }

    private fun children(tree: Uri, parent: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = resolver.query(uri, columns, null, null, null) ?: throw IOException("Cannot list $uri")
        return cursor.use {
            if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING) ||
                it.extras.getString(DocumentsContract.EXTRA_ERROR) != null
            ) throw IOException("Folder listing is incomplete: $uri")
            val sizeColumn = it.getColumnIndex(Document.COLUMN_SIZE)
            val modifiedColumn = it.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            buildList {
                while (it.moveToNext()) {
                    add(Child(
                        id = it.getString(0),
                        name = it.getString(1),
                        mimeType = it.getString(2),
                        size = if (sizeColumn < 0 || it.isNull(sizeColumn)) -1 else it.getLong(sizeColumn),
                        modified = if (modifiedColumn < 0 || it.isNull(modifiedColumn)) 0 else it.getLong(modifiedColumn),
                    ))
                }
            }.sortedWith(compareBy({ child -> child.name }, { child -> child.id }))
        }
    }

    private data class Child(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val modified: Long,
    ) {
        val directory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    }

    private fun logReadFailure(message: String, error: Exception) {
        if (error is CancellationException) throw error
        Log.w(TAG, message, error)
    }

    private companion object {
        const val TAG = "FBNG"
        const val PUBLISH_INTERVAL_MS = 500L
    }
}
