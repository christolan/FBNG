package com.xiaojiwei.fbng.game

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object FbneoCore {
    const val FILE_NAME = "libfbneo_libretro_android.so"
    const val ZIP_ENTRY = "lib/arm64-v8a/libfbneo_libretro_android.so"

    fun resolveLoadPath(nativeLibraryDir: File, apkFiles: Iterable<File>): String? {
        val extracted = File(nativeLibraryDir, FILE_NAME)
        if (extracted.isFile && extracted.length() > 0L) {
            return extracted.absolutePath
        }
        for (apk in apkFiles) {
            if (!apk.isFile) continue
            if (containsStoredCore(apk)) {
                return "${apk.absolutePath}!/$ZIP_ENTRY"
            }
        }
        return null
    }

    private fun containsStoredCore(apk: File): Boolean =
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(ZIP_ENTRY) ?: return@use false
            entry.method == ZipEntry.STORED && entry.size > 0L
        }
}
