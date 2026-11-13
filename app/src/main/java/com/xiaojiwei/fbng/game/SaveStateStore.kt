package com.xiaojiwei.fbng.game

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SavedSlot(
    val bytes: ByteArray,
    val coreCommit: String,
    val savedAtEpochMs: Long,
)

data class SaveSlotInfo(
    val id: Int,
    val savedAtEpochMs: Long,
    val available: Boolean = true,
) {
    val automatic: Boolean get() = id == SaveStateStore.AUTO_SLOT
}

/** Each new slot atomically bundles the state, metadata and any available screenshot. */
class SaveStateStore(private val directory: File) {
    companion object {
        const val AUTO_SLOT = 0
        private val SLOT_NAME = Regex("slot([0-9]+)\\.save")
    }

    private val legacyState = File(directory, "slot0.state")
    private val legacyMeta = File(directory, "slot0.properties")

    private fun bundle(id: Int): File {
        require(id >= 0)
        return File(directory, "slot$id.save")
    }

    fun hasSlot(id: Int = AUTO_SLOT): Boolean = bundle(id).isFile ||
        (id == AUTO_SLOT && legacyState.isFile && legacyState.length() > 0L)

    fun listSlots(): List<SaveSlotInfo> {
        val ids = directory.listFiles().orEmpty().mapNotNull {
            SLOT_NAME.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull()
        }.filter { it > AUTO_SLOT }.distinct().sorted()
        return (listOf(AUTO_SLOT) + ids).map { id ->
            val file = bundle(id)
            val available = hasSlot(id)
            // A damaged slot stays visible and cannot be silently overwritten as a new slot.
            val timestamp = runCatching {
                metadata(id).getProperty("savedAtEpochMs")?.toLongOrNull()
            }.getOrNull() ?: if (file.isFile) file.lastModified() else legacyState.lastModified()
            SaveSlotInfo(id, timestamp, available)
        }
    }

    fun read(id: Int = AUTO_SLOT): SavedSlot? {
        if (!hasSlot(id)) return null
        val file = bundle(id)
        val props: Properties
        val bytes: ByteArray
        if (file.isFile) {
            ZipFile(file).use { zip ->
                props = zip.getInputStream(requireNotNull(zip.getEntry("metadata.properties"))).use {
                    Properties().apply { load(it) }
                }
                bytes = zip.getInputStream(requireNotNull(zip.getEntry("state"))).use { it.readBytes() }
            }
        } else {
            props = metadata(id)
            bytes = legacyState.readBytes()
        }
        check(bytes.isNotEmpty()) { "存档数据为空，请选择其他槽位。" }
        return SavedSlot(bytes, props.getProperty("coreCommit").orEmpty(),
            props.getProperty("savedAtEpochMs")?.toLongOrNull() ?: 0L)
    }

    fun readCover(id: Int): ByteArray? {
        val file = bundle(id)
        if (!file.isFile) return null
        return ZipFile(file).use { zip ->
            zip.getEntry("cover.jpg")?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
        }
    }

    fun write(
        bytes: ByteArray,
        coreCommit: String,
        savedAtEpochMs: Long = System.currentTimeMillis(),
        id: Int = AUTO_SLOT,
        cover: ByteArray? = null,
    ) {
        require(bytes.isNotEmpty())
        check(directory.isDirectory || directory.mkdirs()) { "无法创建存档目录，请检查剩余空间。" }
        val file = bundle(id)
        val tmp = File(directory, file.name + ".tmp")
        val props = Properties().apply {
            setProperty("coreCommit", coreCommit)
            setProperty("savedAtEpochMs", savedAtEpochMs.toString())
            setProperty("size", bytes.size.toString())
        }
        val metaBytes = ByteArrayOutputStream().also { props.store(it, "FBNG slot $id") }.toByteArray()
        try {
            FileOutputStream(tmp).use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.setLevel(1)
                    fun entry(name: String, content: ByteArray) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(content)
                        zip.closeEntry()
                    }
                    entry("state", bytes)
                    entry("metadata.properties", metaBytes)
                    if (cover != null) entry("cover.jpg", cover)
                    zip.finish()
                    zip.flush()
                    out.fd.sync()
                }
            }
            // All files live on the same internal filesystem. Never copy over a live slot.
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
    }

    fun clear() {
        directory.listFiles().orEmpty().filter {
            SLOT_NAME.matches(it.name.removeSuffix(".tmp")) ||
                it.name in setOf("slot0.state", "slot0.properties", "slot0.state.tmp", "slot0.properties.tmp")
        }.forEach { Files.deleteIfExists(it.toPath()) }
    }

    private fun metadata(id: Int): Properties {
        val file = bundle(id)
        if (file.isFile) return ZipFile(file).use { zip ->
            zip.getInputStream(requireNotNull(zip.getEntry("metadata.properties"))).use {
                Properties().apply { load(it) }
            }
        }
        return Properties().apply {
            if (id == AUTO_SLOT && legacyMeta.isFile) legacyMeta.inputStream().use { load(it) }
        }
    }
}
