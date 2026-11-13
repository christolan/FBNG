package com.xiaojiwei.fbng.library

import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.ZipMember
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal data class ArchiveStamp(val name: String, val size: Long, val modified: Long) {
    fun matches(cached: ArchiveStamp?) = size >= 0 && modified > 0 && this == cached
}

internal data class IndexedArchive(val stamp: ArchiveStamp, val archive: LibraryArchive?)

internal data class IndexedSource(
    val entries: Map<String, IndexedArchive> = emptyMap(),
    val unreadable: Int = 0,
    val unavailable: Boolean = false,
    val incomplete: Boolean = false,
)

internal const val INSTALLED_SOURCE = ""

internal class LibraryIndex(file: File) {
    private val file = AtomicFile(file)

    fun read(catalog: DatCatalog): Map<String, IndexedSource> = try {
        DataInputStream(file.openRead().buffered()).use { input ->
            val format = input.readInt()
            if (format !in 1..FORMAT || input.readUTF() != version(catalog)) return@use emptyMap()
            buildMap {
                repeat(input.readCount()) {
                    val source = input.readUTF()
                    val unreadable = input.readInt()
                    val unavailable = input.readBoolean()
                    val incomplete = input.readBoolean()
                    val entries = buildMap {
                        repeat(input.readCount()) {
                            val path = input.readUTF()
                            val stamp = ArchiveStamp(input.readUTF(), input.readLong(), input.readLong())
                            val archive = if (input.readBoolean()) {
                                val set = catalog.byName[input.readUTF()] ?: throw IOException("Unknown set in index")
                                val members = List(input.readCount()) {
                                    ZipMember(input.readUTF(), input.readLong(), input.readLong())
                                }
                                val location = if (source == INSTALLED_SOURCE) ArchiveLocation.Installed(File(path))
                                else ArchiveLocation.Folder(Uri.parse(path))
                                val displayName = if (format >= 2) input.readUTF() else stamp.name
                                LibraryArchive(set, location, members, displayName)
                            } else null
                            put(path, IndexedArchive(stamp, archive))
                        }
                    }
                    put(source, IndexedSource(entries, unreadable, unavailable, incomplete))
                }
            }
        }
    } catch (_: FileNotFoundException) {
        emptyMap()
    } catch (error: IOException) {
        Log.w(TAG, "Could not read library index", error)
        emptyMap()
    }

    fun write(catalog: DatCatalog, sources: Map<String, IndexedSource>) {
        try {
            val stream = file.startWrite()
            try {
                val output = DataOutputStream(stream.buffered())
                output.writeInt(FORMAT)
                output.writeUTF(version(catalog))
                output.writeInt(sources.size)
                for ((source, index) in sources) {
                    output.writeUTF(source)
                    output.writeInt(index.unreadable)
                    output.writeBoolean(index.unavailable)
                    output.writeBoolean(index.incomplete)
                    output.writeInt(index.entries.size)
                    for ((path, entry) in index.entries) {
                        output.writeUTF(path)
                        output.writeUTF(entry.stamp.name)
                        output.writeLong(entry.stamp.size)
                        output.writeLong(entry.stamp.modified)
                        val archive = entry.archive
                        output.writeBoolean(archive != null)
                        if (archive == null) continue
                        output.writeUTF(archive.set.name)
                        output.writeInt(archive.members.size)
                        for (member in archive.members) {
                            output.writeUTF(member.name)
                            output.writeLong(member.size)
                            output.writeLong(member.crc)
                        }
                            output.writeUTF(archive.displayName)
                    }
                }
                output.flush()
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        } catch (error: IOException) {
            Log.w(TAG, "Could not save library index", error)
        }
    }

    private fun version(catalog: DatCatalog) = "${BuildConfig.FBNEO_COMMIT}/${catalog.version}"

    private fun DataInputStream.readCount(): Int = readInt().also {
        if (it !in 0..MAX_COUNT) throw IOException("Invalid library index count: $it")
    }

    private companion object {
        const val FORMAT = 2
        const val MAX_COUNT = 1_000_000
        const val TAG = "FBNG"
    }
}
