package com.xiaojiwei.fbng.game

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class GameProgressStore(private val directory: File) {
    companion object {
        const val SLOT_COUNT = 4
        private val snapshotName = Regex("slot([0-9]+)\\.save")
    }

    private val slotDirectory = File(directory, "progress")
    private val snapshotDirectory = File(directory, "snapshots")
    private val slots = SaveStateStore(slotDirectory)
    private val snapshots = SaveStateStore(snapshotDirectory)
    private val legacy = SaveStateStore(directory)
    private val migration = File(directory, "progress-v1.migrated")
    private val selection = File(directory, "selected-slot")

    @Synchronized
    fun initialize() {
        if (migration.isFile) return
        check(directory.isDirectory || directory.mkdirs()) { "无法创建存档目录，请检查剩余空间。" }
        for (old in legacy.listSlots().filter { it.available }) {
            val source = File(directory, "slot${old.id}.save")
            val snapshot = File(snapshotDirectory, "slot${old.id + 1}.save")
            if (!snapshot.isFile) {
                if (source.isFile) copyAtomic(source, snapshot) else {
                    val state = requireNotNull(legacy.read(old.id))
                    snapshots.write(state.bytes, state.coreCommit, state.savedAtEpochMs, old.id + 1)
                }
                if (old.savedAtEpochMs > 0) snapshot.setLastModified(old.savedAtEpochMs)
            }
            if (old.id == SaveStateStore.AUTO_SLOT && !slots.hasSlot(1)) {
                copyAtomic(snapshot, File(slotDirectory, "slot1.save"))
            }
            if (old.id == SaveStateStore.AUTO_SLOT && !selection.isFile) writeMarker(selection, "1")
        }
        writeMarker(migration, "1")
    }

    fun listSlots(): List<SaveSlotInfo> {
        initialize()
        return (1..SLOT_COUNT).map(slots::slotInfo)
    }

    fun currentSlot(): Int? {
        if (selection.isFile) return selection.readText().toIntOrNull()?.takeIf { it in 1..SLOT_COUNT }
        return 1.takeIf { legacy.hasSlot() && (!migration.isFile || slots.hasSlot(1)) }
    }

    fun currentSave(): SaveSlotInfo? {
        val id = currentSlot() ?: return null
        if (usesLegacyProgress(id)) return legacy.slotInfo(SaveStateStore.AUTO_SLOT).copy(id = id)
        return slots.slotInfo(id).takeIf { it.available }
    }

    fun currentCover(): ByteArray? {
        val id = currentSlot() ?: return null
        return if (usesLegacyProgress(id)) legacy.readCover(SaveStateStore.AUTO_SLOT) else slotCover(id)
    }

    private fun usesLegacyProgress(id: Int) = id == 1 && !migration.isFile && !slots.hasSlot(id) && legacy.hasSlot()

    @Synchronized
    fun activateSnapshot(id: Int, state: SavedSlot, cover: ByteArray?) {
        val oldState = readSlot(id)
        val oldCover = oldState?.let { slotCover(id) }
        saveSlot(id, state, cover)
        try {
            selectSlot(id)
        } catch (error: Exception) {
            runCatching {
                if (oldState == null) slots.delete(id) else saveSlot(id, oldState, oldCover)
            }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    @Synchronized
    fun selectSlot(id: Int) {
        requireSlot(id)
        initialize()
        writeMarker(selection, id.toString())
    }

    fun readSlot(id: Int): SavedSlot? { requireSlot(id); initialize(); return slots.read(id) }
    fun slotCover(id: Int): ByteArray? { requireSlot(id); return slots.readCover(id) }

    @Synchronized
    fun saveSlot(id: Int, state: SavedSlot, cover: ByteArray?) {
        requireSlot(id)
        initialize()
        slots.write(state.bytes, state.coreCommit, state.savedAtEpochMs, id, cover)
    }

    @Synchronized
    fun deleteSlot(id: Int) {
        requireSlot(id)
        initialize()
        if (currentSlot() != id) {
            slots.delete(id)
            return
        }
        writeMarker(selection, "0")
        try {
            slots.delete(id)
        } catch (error: Exception) {
            runCatching { selectSlot(id) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun snapshotFiles(): List<Pair<Int, File>> =
        (snapshotDirectory.listFiles() ?: if (snapshotDirectory.exists()) {
            error("无法读取快照目录，请检查存储空间。")
        } else emptyArray()).mapNotNull { file ->
            snapshotName.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to file }
        }

    @Synchronized
    fun listSnapshots(): List<SaveSlotInfo> {
        initialize()
        return snapshotFiles()
            .sortedWith(compareByDescending<Pair<Int, File>> { it.second.lastModified() }.thenByDescending { it.first })
            .map { snapshots.slotInfo(it.first) }
    }

    fun readSnapshot(id: Int): SavedSlot = requireNotNull(snapshots.read(id)) { "快照已不存在，请选择其他快照。" }
    fun snapshotCover(id: Int): ByteArray? = snapshots.readCover(id)

    @Synchronized
    fun createSnapshot(state: SavedSlot, cover: ByteArray?, sourceSlot: Int): Int {
        requireSlot(sourceSlot)
        initialize()
        val id = (snapshotFiles().maxOfOrNull { it.first } ?: 0).toLong() + 1
        check(id <= Int.MAX_VALUE) { "无法分配快照编号，请整理历史快照。" }
        snapshots.write(state.bytes, state.coreCommit, state.savedAtEpochMs, id.toInt(), cover, sourceSlot)
        return id.toInt()
    }

    @Synchronized
    fun applySnapshot(snapshotId: Int, slotId: Int): SavedSlot {
        val state = readSnapshot(snapshotId)
        saveSlot(slotId, state, snapshotCover(snapshotId))
        return state
    }

    @Synchronized
    fun deleteSnapshot(id: Int) { snapshots.delete(id) }

    private fun requireSlot(id: Int) = require(id in 1..SLOT_COUNT) { "请选择存档 1 至 $SLOT_COUNT。" }

    private fun copyAtomic(source: File, target: File) {
        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建存档目录。" }
        val temporary = File(target.path + ".tmp")
        try {
            FileOutputStream(temporary).use { out -> source.inputStream().use { it.copyTo(out) }; out.fd.sync() }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun writeMarker(file: File, value: String) {
        val temporary = File(file.path + ".tmp")
        try {
            FileOutputStream(temporary).use { it.write(value.toByteArray()); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
