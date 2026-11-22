package com.xiaojiwei.fbng.game

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class SnapshotPage(val items: List<SaveSlotInfo>, val total: Int, val page: Int) {
    val pages: Int get() = ((total + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    companion object { const val PAGE_SIZE = 3 }
}

internal class GameProgressStore(private val directory: File) {
    companion object {
        const val SLOT_COUNT = 3
        private val snapshotName = Regex("slot([0-9]+)\\.save")
    }

    private val slotDirectory = File(directory, "progress")
    private val snapshotDirectory = File(directory, "snapshots")
    private val slots = SaveStateStore(slotDirectory)
    private val snapshots = SaveStateStore(snapshotDirectory)
    private val migration = File(directory, "progress-v1.migrated")
    private val selection = File(directory, "selected-slot")

    @Synchronized
    fun initialize() {
        if (migration.isFile) return
        check(directory.isDirectory || directory.mkdirs()) { "无法创建存档目录，请检查剩余空间。" }
        val legacy = SaveStateStore(directory)
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
        }
        writeMarker(migration, "1")
    }

    fun listSlots(): List<SaveSlotInfo> {
        initialize()
        return (1..SLOT_COUNT).map(slots::slotInfo)
    }

    fun selectedSlot(): Int {
        initialize()
        return readSelection()
    }

    private fun readSelection(): Int =
        selection.takeIf { it.isFile }?.readText()?.toIntOrNull()?.takeIf { it in 1..SLOT_COUNT } ?: 1

    fun slotWithProgress(): Int? {
        val selected = readSelection()
        if (slots.hasSlot(selected)) return selected
        return (1..SLOT_COUNT).map(slots::slotInfo).filter { it.available }
            .maxByOrNull { it.savedAtEpochMs }?.id
            ?: 1.takeIf { !migration.isFile && SaveStateStore(directory).hasSlot(SaveStateStore.AUTO_SLOT) }
    }

    @Synchronized
    fun selectSlot(id: Int) {
        requireSlot(id)
        initialize()
        writeMarker(selection, id.toString())
    }

    fun hasSlot(id: Int): Boolean { requireSlot(id); return slots.hasSlot(id) }
    fun readSlot(id: Int): SavedSlot? { requireSlot(id); initialize(); return slots.read(id) }
    fun slotCover(id: Int): ByteArray? { requireSlot(id); return slots.readCover(id) }

    @Synchronized
    fun saveSlot(id: Int, state: SavedSlot, cover: ByteArray?) {
        requireSlot(id)
        initialize()
        slots.write(state.bytes, state.coreCommit, state.savedAtEpochMs, id, cover)
    }

    @Synchronized
    fun deleteSlot(id: Int) { requireSlot(id); initialize(); slots.delete(id) }

    private fun snapshotFiles(): List<Pair<Int, File>> =
        (snapshotDirectory.listFiles() ?: if (snapshotDirectory.exists()) {
            error("无法读取快照目录，请检查存储空间。")
        } else emptyArray()).mapNotNull { file ->
            snapshotName.matchEntire(file.name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to file }
        }

    @Synchronized
    fun snapshotPage(requestedPage: Int): SnapshotPage {
        initialize()
        val files = snapshotFiles().sortedWith(compareByDescending<Pair<Int, File>> { it.second.lastModified() }.thenByDescending { it.first })
        val page = requestedPage.coerceIn(0, ((files.size - 1).coerceAtLeast(0)) / SnapshotPage.PAGE_SIZE)
        return SnapshotPage(files.drop(page * SnapshotPage.PAGE_SIZE).take(SnapshotPage.PAGE_SIZE).map { snapshots.slotInfo(it.first) }, files.size, page)
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

    private fun requireSlot(id: Int) = require(id in 1..SLOT_COUNT) { "请选择槽位 1、2 或 3。" }

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
