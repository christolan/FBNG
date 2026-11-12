package com.xiaojiwei.fbng.importing

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream

internal inline fun <T> ContentResolver.withLocalZip(uri: Uri, cache: File, read: (File) -> T): T {
    val input = openInputStream(uri) ?: throw IOException("Cannot open $uri")
    return input.use { it.withLocalZip(cache, read) }
}

internal inline fun <T> InputStream.withLocalZip(cache: File, read: (File) -> T): T {
    val temporary = File.createTempFile("import-", ".zip", cache)
    try {
        temporary.outputStream().use { copyTo(it) }
        return read(temporary)
    } finally {
        temporary.delete()
    }
}
