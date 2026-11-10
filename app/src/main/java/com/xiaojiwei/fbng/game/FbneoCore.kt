package com.xiaojiwei.fbng.game

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Path that LibretroDroid can pass to dlopen.
 *
 * The core is packaged uncompressed and page-aligned, and the manifest sets
 * extractNativeLibs to false, so Android does not copy it into nativeLibraryDir.
 * A file check in that directory is false even when lib/arm64-v8a contains the
 * library. API 23+ dlopen accepts "<apk>!/<zip entry>" for an uncompressed,
 * page-aligned entry. minSdk is 33.
 *
 * If a build or a device did extract the library, the real file is preferred.
 */
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
