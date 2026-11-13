package com.xiaojiwei.fbng.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val REPOSITORY = "christolan/FBNG"
private const val RELEASES = "https://api.github.com/repos/$REPOSITORY/releases"
private const val DOWNLOADS = "https://github.com/$REPOSITORY/releases/download"
private const val PAGE_SIZE = 100
private val versionLine = Regex("""(?m)^版本 ([0-9]+(?:\.[0-9]+){1,3})（versionCode ([0-9]+)）\r?$""")
private val sha256Digest = Regex("""sha256:([a-fA-F0-9]{64})""")

internal data class AppRelease(
    val versionName: String,
    val versionCode: Long,
    val size: Long,
    val sha256: String,
)

internal object GitHubUpdates {
    suspend fun check(installedVersion: Long): AppRelease? = withContext(Dispatchers.IO) {
        var latest: AppRelease? = null
        var page = 1
        do {
            ensureActive()
            val releases = request("$RELEASES?per_page=$PAGE_SIZE&page=$page", "application/vnd.github+json") { http ->
                JSONArray(http.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            }
            val candidate = findAppUpdate(releases, latest?.versionCode ?: installedVersion)
            if (candidate != null) latest = candidate
            page++
        } while (releases.length() == PAGE_SIZE)
        latest
    }

    suspend fun download(release: AppRelease, file: File, onProgress: suspend (Int) -> Unit) =
        withContext(Dispatchers.IO) {
            request("$DOWNLOADS/v${release.versionName}/FBNG-${release.versionName}.apk", "application/octet-stream") { http ->
                http.inputStream.buffered().use { input ->
                    file.outputStream().buffered().use { output ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var percent = 0
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > release.size) throw InvalidUpdate("APK exceeds its advertised size")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val progress = (100.0 * total / release.size).toInt()
                            if (progress != percent) {
                                percent = progress
                                onProgress(percent)
                            }
                        }
                        if (total != release.size || !digest.digest().toHexString().equals(release.sha256, ignoreCase = true)) {
                            throw InvalidUpdate("APK size or SHA-256 does not match the release")
                        }
                    }
                }
            }
        }

    private inline fun <T> request(url: String, accept: String, read: (HttpURLConnection) -> T): T {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "FBNG-Updater")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("GitHub HTTP $status")
            return read(connection)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun findAppUpdate(releases: JSONArray, installedVersion: Long): AppRelease? =
    (0 until releases.length())
        .asSequence()
        .mapNotNull(releases::optJSONObject)
        .filterNot { it.optBoolean("draft") }
        .mapNotNull { release ->
            val version = versionLine.find(release.optString("body")) ?: return@mapNotNull null
            val (name, code) = version.destructured
            val versionCode = code.toLongOrNull() ?: return@mapNotNull null
            if (versionCode <= installedVersion || release.optString("tag_name") != "v$name") return@mapNotNull null
            release.optJSONArray("assets")?.let { findApk(it, name, versionCode) }
        }
        .maxByOrNull(AppRelease::versionCode)

private fun findApk(assets: JSONArray, name: String, versionCode: Long): AppRelease? {
    for (index in 0 until assets.length()) {
        val asset: JSONObject = assets.optJSONObject(index) ?: continue
        if (asset.optString("name") != "FBNG-$name.apk" || asset.optString("state") != "uploaded") continue
        val size = asset.optLong("size")
        val digest = sha256Digest.matchEntire(asset.optString("digest"))
        if (size > 0 && digest != null) return AppRelease(name, versionCode, size, digest.groupValues[1])
    }
    return null
}

internal class InvalidUpdate(message: String) : IOException(message)
