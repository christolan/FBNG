package com.xiaojiwei.fbng.library

import android.net.Uri
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.DatGame
import com.xiaojiwei.fbng.dat.RomSetMatcher
import com.xiaojiwei.fbng.dat.ZipMember
import com.xiaojiwei.fbng.importing.ZipArchives
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal sealed interface ArchiveLocation {
    data class Installed(val file: File) : ArchiveLocation
    data class Folder(val uri: Uri) : ArchiveLocation
}

internal data class LibraryArchive(
    val set: DatGame,
    val location: ArchiveLocation,
    val members: List<ZipMember>,
) {
    val signatures = members.mapTo(HashSet()) { it.signature }
}

internal data class MissingDependency(val name: String, val isBios: Boolean)

internal data class LibraryGame(
    val name: String,
    val description: String,
    val missing: List<MissingDependency> = emptyList(),
    val archives: List<LibraryArchive> = emptyList(),
) {
    val canLaunch: Boolean get() = missing.isEmpty()
}

internal data class LibrarySnapshot(val games: List<LibraryGame> = emptyList())

internal class LibraryScanner(private val catalog: DatCatalog) {
    suspend fun installed(storage: AppStorage, cached: Map<String, IndexedArchive>): IndexedSource {
        val archives = storage.romsDir.listFiles().orEmpty().asSequence() +
            storage.systemFbneoDir.listFiles().orEmpty().asSequence()
        val entries = linkedMapOf<String, IndexedArchive>()
        for (file in archives) {
            currentCoroutineContext().ensureActive()
            if (!file.isFile || file.extension != "zip") continue
            val expected = catalog.byName[file.nameWithoutExtension] ?: continue
            // The old PGM install also made a copy in roms; the canonical BIOS is in system/fbneo.
            if (file != storage.archive(expected.name, expected.isBios)) continue
            val path = file.absolutePath
            val stamp = ArchiveStamp(file.name, file.length(), file.lastModified())
            val previous = cached[path]
            try {
                entries[path] = if (previous != null && stamp.matches(previous.stamp)) previous else {
                    val archive = inspect(ZipArchives.directory(file), file.name, ArchiveLocation.Installed(file))
                        ?.takeIf { it.set.name == expected.name }
                    IndexedArchive(stamp, archive)
                }
            } catch (_: IOException) {
                // A missing or damaged archive is never offered to the core.
            }
        }
        return IndexedSource(entries)
    }

    fun inspect(members: List<ZipMember>, displayName: String, location: ArchiveLocation): LibraryArchive? {
        val match = RomSetMatcher.identify(members, catalog, displayName).best ?: return null
        if (!match.isClean) return null
        return LibraryArchive(catalog.byName.getValue(match.setName), location, members)
    }

    fun snapshot(archives: List<LibraryArchive>): LibrarySnapshot {
        val bySet = archives.groupBy { it.set.name }
        val games = bySet.values.mapNotNull { copies ->
            val game = copies.first().set
            if (game.isBios) return@mapNotNull null
            copies.map { plan(it, bySet) }.minBy { it.missing.size }
        }.sortedWith(compareBy({ it.description.lowercase() }, { it.name }))
        return LibrarySnapshot(games)
    }

    private fun plan(game: LibraryArchive, bySet: Map<String, List<LibraryArchive>>): LibraryGame {
        val inherited = catalog.inheritedRoms(game.set).mapTo(HashSet()) { it.signature }
        inherited.removeAll(game.signatures)
        val ancestors = catalog.ancestors(game.set)
        val selected = mutableListOf(game)
        for (ancestor in ancestors) {
            if (inherited.isEmpty()) break
            val archive = bySet[ancestor.name]?.maxByOrNull { copy -> inherited.count { it in copy.signatures } }
            if (archive != null && inherited.removeAll(archive.signatures)) selected += archive
        }

        val missing = buildList {
            // The chain starts with the split parent. Attribute remaining ROMs to their original set.
            for (ancestor in ancestors.asReversed()) {
                if (inherited.removeAll(ancestor.roms.filter { it.required }.map { it.signature }.toSet())) {
                    add(MissingDependency(ancestor.name, ancestor.isBios))
                }
            }
        }.asReversed()
        return LibraryGame(game.set.name, game.set.description, missing, selected)
    }
}
