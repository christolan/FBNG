package com.xiaojiwei.fbng.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GitHubUpdatesTest {
    @Test
    fun picksHighestNewerVersionCodeIncludingPrereleases() {
        val releases = JSONArray()
            .put(release("1.0.0", 10, prerelease = false))
            .put(release("0.10.0", 11))
            .put(release("0.8.0", 12).put("draft", true))
            .put(release("0.9.0", 9))

        assertEquals(AppRelease("0.10.0", 11, 105208057, digest), findAppUpdate(releases, 9))
        assertNull(findAppUpdate(releases, 11))
    }

    @Test
    fun ignoresReleasesWithoutMatchingUploadedAndVerifiableApk() {
        val releases = JSONArray()
            .put(release("0.5.0", 8).put("tag_name", "v0.5.1"))
            .put(release("0.6.0", 9, asset("0.6.1")))
            .put(release("0.7.0", 10, asset("0.7.0").put("state", "starter")))
            .put(release("0.8.0", 11, asset("0.8.0").put("digest", JSONObject.NULL)))
            .put(release("0.9.0", 12).put("body", "Release notes"))
            .put(release("0.10.0", 13, asset("0.10.0").put("size", 0)))

        assertNull(findAppUpdate(releases, 7))
    }

    private val digest = "2d".repeat(32)

    private fun release(
        version: String,
        code: Int,
        apk: JSONObject = asset(version),
        prerelease: Boolean = true,
    ) = JSONObject()
        .put("tag_name", "v$version")
        .put("body", "发布说明\r\n版本 $version（versionCode $code）\r\n")
        .put("prerelease", prerelease)
        .put("assets", JSONArray().put(apk))

    private fun asset(version: String) = JSONObject()
        .put("name", "FBNG-$version.apk")
        .put("state", "uploaded")
        .put("size", 105208057)
        .put("digest", "sha256:$digest")
}
