package com.xiaojiwei.fbng.game

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FbneoCoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun extractedLibraryIsUsedWhenTheInstallerCopiedIt() {
        val nativeDir = folder.newFolder("nativelib")
        val extracted = File(nativeDir, FbneoCore.FILE_NAME)
        extracted.writeBytes(byteArrayOf(1, 2, 3, 4))
        val apk = folder.newFile("base.apk")
        writeStored(apk, FbneoCore.ZIP_ENTRY, byteArrayOf(9, 9, 9))

        assertEquals(extracted.absolutePath, FbneoCore.resolveLoadPath(nativeDir, listOf(apk)))
    }

    @Test
    fun storedApkEntryIsOpenedFromTheZipWhenNothingWasExtracted() {
        val nativeDir = folder.newFolder("empty-nativelib")
        val apk = folder.newFile("base.apk")
        writeStored(apk, FbneoCore.ZIP_ENTRY, byteArrayOf(7, 7, 7, 7))

        assertEquals(
            "${apk.absolutePath}!/${FbneoCore.ZIP_ENTRY}",
            FbneoCore.resolveLoadPath(nativeDir, listOf(apk)),
        )
    }

    @Test
    fun emptyExtractedFileDoesNotHideTheCopyInsideTheApk() {
        val nativeDir = folder.newFolder("nativelib")
        File(nativeDir, FbneoCore.FILE_NAME).writeBytes(ByteArray(0))
        val apk = folder.newFile("base.apk")
        writeStored(apk, FbneoCore.ZIP_ENTRY, byteArrayOf(1))

        assertEquals(
            "${apk.absolutePath}!/${FbneoCore.ZIP_ENTRY}",
            FbneoCore.resolveLoadPath(nativeDir, listOf(apk)),
        )
    }

    @Test
    fun laterSplitIsUsedWhenTheBaseApkHasNoCore() {
        val nativeDir = folder.newFolder("nativelib")
        val base = folder.newFile("base.apk")
        writeStored(base, "lib/arm64-v8a/libother.so", byteArrayOf(1, 2))
        val split = folder.newFile("split.apk")
        writeStored(split, FbneoCore.ZIP_ENTRY, byteArrayOf(3, 4))

        assertEquals(
            "${split.absolutePath}!/${FbneoCore.ZIP_ENTRY}",
            FbneoCore.resolveLoadPath(nativeDir, listOf(base, split)),
        )
    }

    @Test
    fun compressedEntryIsNotALoadablePath() {
        val nativeDir = folder.newFolder("nativelib")
        val apk = folder.newFile("base.apk")
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(FbneoCore.ZIP_ENTRY))
            zip.write(byteArrayOf(1, 2, 3, 4, 5))
            zip.closeEntry()
        }

        assertNull(FbneoCore.resolveLoadPath(nativeDir, listOf(apk)))
    }

    @Test
    fun missingFromBothPlaces() {
        val nativeDir = folder.newFolder("nativelib")
        val apk = folder.newFile("base.apk")
        writeStored(apk, "lib/arm64-v8a/libother.so", byteArrayOf(1))
        val absent = folder.newFile("not-created.apk")
        absent.delete()

        assertNull(FbneoCore.resolveLoadPath(nativeDir, listOf(apk, absent)))
    }

    private fun writeStored(dest: File, entryName: String, payload: ByteArray) {
        ZipOutputStream(dest.outputStream()).use { zip ->
            val entry = ZipEntry(entryName)
            entry.method = ZipEntry.STORED
            entry.size = payload.size.toLong()
            entry.compressedSize = payload.size.toLong()
            entry.crc = CRC32().apply { update(payload) }.value
            zip.putNextEntry(entry)
            zip.write(payload)
            zip.closeEntry()
        }
    }
}
