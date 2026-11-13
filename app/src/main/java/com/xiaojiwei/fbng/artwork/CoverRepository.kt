package com.xiaojiwei.fbng.artwork

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.xiaojiwei.fbng.library.RecentGames
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class CoverImage(val file: File, val version: Long)

/** Persistent images are separate from the disposable decoder cache. */
internal class CoverRepository private constructor(private val context: Context) {
    private val directory = File(context.filesDir, "artwork/downloaded")
    private val attempts = context.getSharedPreferences("cover-attempts", Context.MODE_PRIVATE)
    private val mutableImages = MutableStateFlow<Map<String, CoverImage>>(emptyMap())
    val images = mutableImages.asStateFlow()
    val downloadLock = Mutex()

    private val index: Map<String, String> by lazy {
        val json = context.assets.open("fbneo/cover-index.json").bufferedReader().use { JSONObject(it.readText()) }
        val assets = json.getJSONArray("assets")
        val sets = json.getJSONObject("sets")
        sets.keys().asSequence().associateWith { assets.getString(sets.getInt(it)) }
    }
    private val fileNames by lazy { index.values.distinct().associateWith(::hashFileName) }

    fun eligibleNames(): Set<String> =
        context.getSharedPreferences("library-favorites", Context.MODE_PRIVATE).getStringSet("sets", null).orEmpty().toSet() +
            RecentGames(context).read().toSet()

    fun candidates(): Map<String?, List<String>> = eligibleNames().sorted().groupBy { index[it] }

    suspend fun reloadImages() = withContext(Dispatchers.IO) {
        val existing = directory.listFiles().orEmpty().filter { it.extension == "png" }.associateBy { it.name }
        mutableImages.value = index.mapNotNull { (name, path) ->
            existing[fileName(path)]?.takeIf { it.length() > 0 }?.let { name to CoverImage(it, it.lastModified()) }
        }.toMap()
    }

    fun hasImage(path: String): Boolean = File(directory, fileName(path)).let { it.isFile && it.length() > 0 }

    fun recentlyMissing(path: String): Boolean = attempts.getLong(fileName(path), 0) > System.currentTimeMillis()

    /** False means a confirmed 404; transport and decoding failures preserve the existing image. */
    suspend fun download(path: String): Boolean = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val url = "https://raw.githubusercontent.com/libretro-thumbnails/FBNeo_-_Arcade_Games/master/" +
            path.split('/').joinToString("/") { Uri.encode(it) }
        val connection = URL(url).openConnection() as HttpURLConnection
        val temporary = File.createTempFile("cover-", ".tmp", directory)
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "FBNG-cover-downloader")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                attempts.edit().putLong(fileName(path), System.currentTimeMillis() + MISSING_RETRY_MS).apply()
                return@withContext false
            }
            if (code != HttpURLConnection.HTTP_OK) throw IOException("Cover HTTP $code")
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("Cover exceeds download limit")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("Cover exceeds download limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0 ||
                options.outWidth.toLong() * options.outHeight > MAX_PIXELS
            ) throw IOException("Invalid cover dimensions")
            // Decode a small sample too: a valid header alone does not prove the image is readable.
            val sample = BitmapFactory.Options().apply { inSampleSize = maxOf(options.outWidth, options.outHeight) / 512 + 1 }
            val bitmap = BitmapFactory.decodeFile(temporary.absolutePath, sample) ?: throw IOException("Unreadable cover")
            bitmap.recycle()
            currentCoroutineContext().ensureActive()
            Files.move(temporary.toPath(), File(directory, fileName(path)).toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            attempts.edit().remove(fileName(path)).apply()
            reloadImages()
            true
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private fun fileName(path: String): String = fileNames.getValue(path)

    private fun hashFileName(path: String): String =
        MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') } + ".png"

    companion object {
        private const val MAX_BYTES = 8L * 1024 * 1024
        private const val MAX_PIXELS = 16L * 1024 * 1024
        private const val MISSING_RETRY_MS = 24L * 60 * 60 * 1000
        @Volatile private var instance: CoverRepository? = null
        fun get(context: Context): CoverRepository = instance ?: synchronized(this) {
            instance ?: CoverRepository(context.applicationContext).also { instance = it }
        }
    }
}
