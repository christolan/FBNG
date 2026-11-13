package com.xiaojiwei.fbng.game

import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.nio.file.Files

data class SavedSlot(
    val bytes: ByteArray,
    val coreCommit: String,
    val savedAtEpochMs: Long,
)

/**
 * One libretro savestate slot. The bytes are whatever FBNeo returns from retro_serialize,
 * written with an fsync so they survive process death.
 */
class SaveStateStore(private val directory: File) {
    private val stateFile = File(directory, "slot0.state")
    private val metaFile = File(directory, "slot0.properties")

    fun hasSlot(): Boolean = stateFile.isFile && stateFile.length() > 0L

    fun read(): SavedSlot? {
        if (!hasSlot()) return null
        val props = Properties()
        if (metaFile.isFile) metaFile.inputStream().use(props::load)
        return SavedSlot(
            bytes = stateFile.readBytes(),
            coreCommit = props.getProperty("coreCommit").orEmpty(),
            savedAtEpochMs = props.getProperty("savedAtEpochMs")?.toLongOrNull() ?: 0L,
        )
    }

    fun write(bytes: ByteArray, coreCommit: String, savedAtEpochMs: Long = System.currentTimeMillis()) {
        require(bytes.isNotEmpty())
        directory.mkdirs()
        writeAtomically(stateFile, bytes)
        val props = Properties().apply {
            setProperty("coreCommit", coreCommit)
            setProperty("savedAtEpochMs", savedAtEpochMs.toString())
            setProperty("size", bytes.size.toString())
        }
        val metaBytes = java.io.ByteArrayOutputStream().also { props.store(it, "FBNG slot 0") }.toByteArray()
        writeAtomically(metaFile, metaBytes)
    }

    fun clear() {
        // Remove the payload first so a reset can never load stale progress.
        listOf(stateFile, metaFile, File(directory, "slot0.state.tmp"), File(directory, "slot0.properties.tmp"))
            .forEach { Files.deleteIfExists(it.toPath()) }
    }

    private fun writeAtomically(dest: File, bytes: ByteArray) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }
}
