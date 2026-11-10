import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val fbneoPin = Properties().apply {
    rootProject.file("core/fbneo.pin.properties").inputStream().use(::load)
}

fun pin(key: String): String =
    fbneoPin.getProperty(key) ?: error("core/fbneo.pin.properties is missing $key")

val fbngDebugKey = Properties().apply {
    rootProject.file("keystore/fbng-debug.properties").inputStream().use(::load)
}

fun debugKey(name: String): String =
    fbngDebugKey.getProperty(name) ?: error("keystore/fbng-debug.properties is missing $name")

// Release signing material stays in Actions secrets. CI decodes the PKCS12
// keystore to FBNG_RELEASE_STORE_FILE and passes the other three values.
// A blank path keeps local release builds unsigned; debug still uses the test key.
val releaseStoreFile = System.getenv("FBNG_RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("FBNG_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("FBNG_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("FBNG_RELEASE_KEY_PASSWORD")
val fbneoCoreZipEntry = "lib/arm64-v8a/libfbneo_libretro_android.so"

val fbneoZipUrl = pin("fbneo.zipUrl")
val fbneoZipSha256 = pin("fbneo.zipSha256")
val fbneoEmbeddedMarker = pin("fbneo.embeddedMarker")
val fbneoJniDir = layout.buildDirectory.dir("fbneo-jni")

val fetchFbneoCore = tasks.register("fetchFbneoCore") {
    inputs.property("url", fbneoZipUrl)
    inputs.property("sha256", fbneoZipSha256)
    inputs.property("marker", fbneoEmbeddedMarker)
    outputs.dir(fbneoJniDir)
    doLast {
        val abiDir = fbneoJniDir.get().asFile.resolve("arm64-v8a")
        abiDir.mkdirs()
        val so = abiDir.resolve("libfbneo_libretro_android.so")
        val zip = File.createTempFile("fbneo-core", ".zip")
        try {
            download(fbneoZipUrl, zip)
            val actual = sha256(zip)
            if (actual != fbneoZipSha256.lowercase()) {
                throw GradleException(
                    "FBNeo core SHA-256 mismatch.\n  url:      $fbneoZipUrl\n  expected: $fbneoZipSha256\n  actual:   $actual\n" +
                        "buildbot's latest file moved. Update core/fbneo.pin.properties with the new digest, build date, and commit.",
                )
            }
            ZipFile(zip).use { archive ->
                val entry = archive.getEntry("fbneo_libretro_android.so")
                    ?: throw GradleException("Zip has no fbneo_libretro_android.so")
                archive.getInputStream(entry).use { input ->
                    so.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val align = minLoadAlign(so)
            if (align < 16 * 1024) {
                so.delete()
                throw GradleException("FBNeo core PT_LOAD align is $align, expected >= 16384")
            }
            if (!fileContainsAscii(so, fbneoEmbeddedMarker)) {
                so.delete()
                throw GradleException("FBNeo core does not contain marker $fbneoEmbeddedMarker")
            }
            logger.lifecycle(
                "Fetched FBNeo core ${so.length()} bytes, PT_LOAD align $align, marker $fbneoEmbeddedMarker",
            )
        } finally {
            zip.delete()
        }
    }
}

val libretroAlignment = configurations.create("libretroAlignment") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    add("libretroAlignment", libs.libretrodroid)

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.window)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
    implementation(libs.libretrodroid)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}

android {
    namespace = "com.xiaojiwei.fbng"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.xiaojiwei.fbng"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField("String", "FBNEO_COMMIT", "\"${pin("fbneo.commit")}\"")
        buildConfigField("String", "FBNEO_COMMIT_SHORT", "\"${pin("fbneo.commitShort")}\"")
        buildConfigField("String", "FBNEO_BUILD_DATE", "\"${pin("fbneo.buildDate")}\"")
        buildConfigField("String", "FBNEO_ZIP_URL", "\"${pin("fbneo.zipUrl")}\"")
        buildConfigField("String", "FBNEO_ZIP_SHA256", "\"${pin("fbneo.zipSha256")}\"")
        buildConfigField("String", "LIBRETRODROID_VERSION", "\"${pin("libretrodroid.version")}\"")
        buildConfigField("String", "FBNEO_DAT_VERSION", "\"${pin("fbneo.datVersion")}\"")
    }

    signingConfigs {
        getByName("debug") {
            // One committed test key so every debug APK, local or CI, overwrites the last install.
            // v1 writes the META-INF signature files ColorOS looks for. v2 and v3 are required to sideload.
            storeFile = rootProject.file("keystore/${debugKey("storeFile")}")
            storePassword = debugKey("storePassword")
            keyAlias = debugKey("keyAlias")
            keyPassword = debugKey("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
        val storePath = releaseStoreFile
        val storePasswordValue = releaseStorePassword.orEmpty()
        val aliasValue = releaseKeyAlias.orEmpty()
        val keyPasswordValue = releaseKeyPassword.orEmpty()
        if (!storePath.isNullOrEmpty()) {
            if (storePasswordValue.isEmpty() || aliasValue.isEmpty() || keyPasswordValue.isEmpty()) {
                throw GradleException(
                    "FBNG_RELEASE_STORE_FILE is set, but FBNG_RELEASE_STORE_PASSWORD, " +
                        "FBNG_RELEASE_KEY_ALIAS, or FBNG_RELEASE_KEY_PASSWORD is empty.",
                )
            }
            create("release") {
                storeFile = file(storePath)
                storePassword = storePasswordValue
                keyAlias = aliasValue
                keyPassword = keyPasswordValue
                storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Stored uncompressed and 16 KB-aligned. AGP then sets extractNativeLibs=false,
            // so the installer does not copy the core into nativeLibraryDir.
            useLegacyPackaging = false
        }
    }

    val jniOut = layout.buildDirectory.dir("fbneo-jni").get().asFile
    sourceSets.getByName("main").jniLibs.directories.add(jniOut.absolutePath)
}

tasks.named("preBuild").configure {
    dependsOn(fetchFbneoCore)
    dependsOn("verifyLibretroDroidAlignment")
}

tasks.register("verifyLibretroDroidAlignment") {
    val aarFiles = libretroAlignment
    inputs.files(aarFiles)
    doLast {
        val aar = aarFiles.files.single { it.name.endsWith(".aar") }
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("jni/arm64-v8a/liblibretrodroid.so")
                ?: throw GradleException("LibretroDroid AAR has no arm64 library: ${aar.name}")
            val temp = File.createTempFile("libretrodroid", ".so")
            try {
                zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
                val align = minLoadAlign(temp)
                if (align < 16 * 1024) {
                    throw GradleException(
                        "LibretroDroid ${pin("libretrodroid.version")} arm64 PT_LOAD align is $align, expected >= 16384",
                    )
                }
                logger.lifecycle("LibretroDroid arm64 PT_LOAD align ${align} (16 KB ok)")
            } finally {
                temp.delete()
            }
        }
    }
}

val androidSdkDir: File = run {
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (!fromEnv.isNullOrBlank()) {
        File(fromEnv)
    } else {
        val props = Properties()
        val local = rootProject.file("local.properties")
        if (local.isFile) local.inputStream().use { props.load(it) }
        File(props.getProperty("sdk.dir") ?: error("Set ANDROID_HOME or sdk.dir in local.properties"))
    }
}

fun registerApkCheck(variant: String) =
    tasks.register("verify${variant.replaceFirstChar { it.uppercase() }}Apk") {
        val apkDir = layout.buildDirectory.dir("outputs/apk/$variant")
        val sdkDir = androidSdkDir
        doLast {
            val dir = apkDir.get().asFile
            val apks = dir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
            val signed = apks.filterNot { it.name.endsWith("-unsigned.apk") }
            val apk = when {
                signed.size == 1 -> signed.single()
                signed.isEmpty() && apks.size == 1 -> apks.single()
                else -> throw GradleException("Expected one APK under $dir, found ${apks.map { it.name }}")
            }
            ZipFile(apk).use { zip ->
                val libs = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("lib/arm64-v8a/") && it.name.endsWith(".so") }
                    .toList()
                if (libs.isEmpty()) {
                    throw GradleException("APK ${apk.name} contains no arm64 native libraries")
                }
                val core = libs.singleOrNull { it.name == fbneoCoreZipEntry }
                    ?: throw GradleException("APK ${apk.name} is missing $fbneoCoreZipEntry")
                if (core.method != ZipEntry.STORED) {
                    throw GradleException("$fbneoCoreZipEntry must be stored uncompressed so dlopen can map it from the APK")
                }
                if (core.size <= 0L) {
                    throw GradleException("$fbneoCoreZipEntry is empty")
                }
                libs.forEach { entry ->
                    val temp = File.createTempFile("apkso", ".so")
                    try {
                        zip.getInputStream(entry).use { input -> temp.outputStream().use { input.copyTo(it) } }
                        val align = minLoadAlign(temp)
                        if (align < 16 * 1024) {
                            throw GradleException("${entry.name} PT_LOAD align is $align, expected >= 16384")
                        }
                        if (entry.name == fbneoCoreZipEntry && !fileContainsAscii(temp, fbneoEmbeddedMarker)) {
                            throw GradleException("$fbneoCoreZipEntry does not contain marker $fbneoEmbeddedMarker")
                        }
                        logger.lifecycle("${entry.name} PT_LOAD align $align")
                    } finally {
                        temp.delete()
                    }
                }
                logger.lifecycle(
                    "FBNeo core packaged: $fbneoCoreZipEntry (${core.size} bytes, stored uncompressed)",
                )
            }
            val zipalign = sdkDir.resolve("build-tools").listFiles().orEmpty()
                .map { it.resolve("zipalign") }
                .filter { it.canExecute() }
                .maxByOrNull { it.parentFile.name }
                ?: throw GradleException("zipalign not found under $sdkDir/build-tools")
            val process = ProcessBuilder(zipalign.absolutePath, "-c", "-P", "16", "4", apk.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            if (code != 0) {
                throw GradleException("zipalign -P 16 failed for ${apk.name} (exit $code)\n$output")
            }
            logger.lifecycle("zipalign -P 16 passed for ${apk.name}")
        }
    }

val verifyDebugApk = registerApkCheck("debug")
val verifyReleaseApk = registerApkCheck("release")

tasks.configureEach {
    if (name == "assembleDebug") {
        finalizedBy(verifyDebugApk)
    }
    if (name == "assembleRelease") {
        finalizedBy(verifyReleaseApk)
    }
}

private fun download(url: String, dest: File) {
    val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 30_000
        readTimeout = 180_000
        setRequestProperty("User-Agent", "FBNG-gradle")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) {
            throw GradleException("Download failed HTTP $code for $url")
        }
        connection.inputStream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
    } finally {
        connection.disconnect()
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun minLoadAlign(file: File): Long {
    file.inputStream().use { input ->
        val header = ByteArray(64)
        if (input.read(header) != header.size) {
            throw GradleException("${file.name} is too small to be an ELF")
        }
        if (header[0] != 0x7f.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) {
            throw GradleException("${file.name} is not an ELF")
        }
        if (header[4].toInt() != 2 || header[5].toInt() != 1) {
            throw GradleException("${file.name} is not a little-endian ELF64")
        }
        val phoff = le64(header, 32)
        val phentsize = le16(header, 54)
        val phnum = le16(header, 56)
        if (phentsize < 56) throw GradleException("${file.name} has a short program header")
        val skip = phoff - header.size
        if (skip < 0) throw GradleException("${file.name} program headers overlap the ELF header")
        skipFully(input, skip)
        var min = Long.MAX_VALUE
        val programHeader = ByteArray(phentsize)
        repeat(phnum) {
            requireRead(input, programHeader, phentsize)
            val type = le32(programHeader, 0)
            if (type == 1) {
                val align = le64(programHeader, 48)
                if (align in 1 until min) min = align
            }
        }
        if (min == Long.MAX_VALUE) throw GradleException("${file.name} has no PT_LOAD segment")
        return min
    }
}

private fun requireRead(input: java.io.InputStream, buffer: ByteArray, count: Int) {
    var offset = 0
    while (offset < count) {
        val read = input.read(buffer, offset, count - offset)
        if (read < 0) throw GradleException("Unexpected EOF while reading ELF")
        offset += read
    }
}

private fun skipFully(input: java.io.InputStream, count: Long) {
    var left = count
    while (left > 0) {
        val skipped = input.skip(left)
        if (skipped <= 0) {
            if (input.read() < 0) throw GradleException("Unexpected EOF while skipping ELF bytes")
            left -= 1
        } else {
            left -= skipped
        }
    }
}

private fun le16(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

private fun le32(bytes: ByteArray, offset: Int): Int =
    le16(bytes, offset) or (le16(bytes, offset + 2) shl 16)

private fun le64(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) {
        value = value or ((bytes[offset + i].toLong() and 0xffL) shl (8 * i))
    }
    return value
}

private fun fileContainsAscii(file: File, needle: String): Boolean {
    val pattern = needle.toByteArray(Charsets.US_ASCII)
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        var carry = ByteArray(0)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return false
            val window = ByteArray(carry.size + read)
            carry.copyInto(window)
            buffer.copyInto(window, carry.size, 0, read)
            if (indexOf(window, pattern) >= 0) return true
            carry = window.copyOfRange(maxOf(0, window.size - (pattern.size - 1)), window.size)
        }
    }
}

private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
    if (pattern.isEmpty() || data.size < pattern.size) return -1
    outer@ for (i in 0..data.size - pattern.size) {
        for (j in pattern.indices) {
            if (data[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return -1
}
