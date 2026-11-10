package com.xiaojiwei.fbng.importing

import com.xiaojiwei.fbng.dat.ZipMember
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile

object ZipArchives {
    fun read(file: File): List<ZipMember> {
        ZipFile(file).use { zip ->
            val members = ArrayList<ZipMember>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || ignored(entry.name)) continue
                val crc = CRC32()
                var size = 0L
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        crc.update(buffer, 0, read)
                        size += read
                    }
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
