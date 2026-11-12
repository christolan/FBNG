package com.xiaojiwei.fbng.importing

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.xiaojiwei.fbng.dat.ZipMember
import java.io.File
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.ZipException
import java.util.zip.ZipFile
import org.apache.commons.compress.archivers.zip.ZipFile as SeekableZipFile

object ZipArchives {
    internal fun directory(file: File): List<ZipMember> = ZipFile(file).use { zip ->
        zip.entries().asSequence().filterNot { it.isDirectory || ignored(it.name) }
            .map { ZipMember(baseName(it.name), it.size, it.crc) }.toList()
    }

    internal fun directory(resolver: ContentResolver, uri: Uri, cache: File): List<ZipMember> {
        val descriptor = try {
            resolver.openFileDescriptor(uri, "r")
        } catch (_: IOException) {
            // Some providers only expose a stream or a slice of a file.
            null
        }
        if (descriptor != null) {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val channel = input.channel
                val seekable = try {
                    channel.position(0)
                    true
                } catch (_: IOException) {
                    false
                }
                if (seekable) {
                    return SeekableZipFile.builder().setSeekableByteChannel(channel)
                        .setIgnoreLocalFileHeader(true).get().use { zip ->
                            zip.entries.asSequence().filterNot { it.isDirectory || ignored(it.name) }
                                .map { ZipMember(baseName(it.name), it.size, it.crc) }.toList()
                        }
                }
                return input.withLocalZip(cache, ::directory)
            }
        }
        return resolver.withLocalZip(uri, cache, ::directory)
    }

    fun read(file: File): List<ZipMember> {
        ZipFile(file).use { zip ->
            val members = ArrayList<ZipMember>()
            val entries = zip.entries()
            val buffer = ByteArray(64 * 1024)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || ignored(entry.name)) continue
                val crc = CRC32()
                var size = 0L
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        crc.update(buffer, 0, read)
                        size += read
                    }
                }
                if (size != entry.size || crc.value != entry.crc) {
                    throw ZipException("ZIP member failed its checksum: ${entry.name}")
                }
                members += ZipMember(
                    name = baseName(entry.name),
                    size = size,
                    crc = crc.value,
                )
            }
            return members
        }
    }

    private fun ignored(path: String): Boolean {
        if (path.contains("__MACOSX")) return true
        val base = baseName(path)
        return base.isEmpty() || base == ".DS_Store"
    }

    private fun baseName(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\')
}
