package com.xiaojiwei.fbng.importing

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException

internal inline fun <T> ContentResolver.withLocalZip(uri: Uri, cache: File, read: (File) -> T): T {
    val temporary = File.createTempFile("import-", ".zip", cache)
    try {
        val input = openInputStream(uri) ?: throw IOException("Cannot open $uri")
        input.use { stream -> temporary.outputStream().use { stream.copyTo(it) } }
        return read(temporary)
    } finally {
        temporary.delete()
    }
}
