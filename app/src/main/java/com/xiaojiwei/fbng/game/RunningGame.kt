package com.xiaojiwei.fbng.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swordfish.libretrodroid.GLRetroView
import com.xiaojiwei.fbng.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class RunningGame(val view: GLRetroView, private val store: GameProgressStore, val slotId: Int) {
    val life = SessionLifecycleOwner()
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var initializationStarted = false
        private set
    private var foreground = false
    private var closed = false
    private var destroyRequested = false
    private var failed = false
    private var canAutoSave = false
    private var quitSaved = false
    private var dialogOpen = false
    var pauseCover by mutableStateOf<ByteArray?>(null)
        private set
    private var coverGeneration = 0

    init {
        require(slotId in 1..GameProgressStore.SLOT_COUNT)
        life.lifecycle.addObserver(view)
        life.create()
    }

    fun start() { foreground = true; updatePlayback() }

    fun togglePause() {
        if (!ready || busy || dialogOpen) return
        paused = !paused
        if (!paused) clearPauseCover()
        updatePlayback()
    }

    suspend fun capturePauseCover() {
        if (!foreground || !ready || busy) return
        val generation = coverGeneration
        val source = GameCover.source(view) ?: return
        val cover = GameCover.capturePreview(source)
        if (generation == coverGeneration) pauseCover = cover
    }

    private fun clearPauseCover() { coverGeneration++; pauseCover = null }
    fun setDialogOpen(open: Boolean) { dialogOpen = open; updatePlayback() }

    suspend fun initialize(): SavedSlot? {
        check(!initializationStarted)
        initializationStarted = true
        return withPausedCore {
            try {
                val slot = withContext(Dispatchers.IO) { store.readSlot(slotId) }
                if (slot != null) check(view.unserializeState(slot.bytes, useEmulationThread = false)) {
                    "无法恢复这个槽位，请尝试应用兼容快照或删除进度后重新开始。"
                }
                withContext(NonCancellable + Dispatchers.IO) { store.selectSlot(slotId) }
                canAutoSave = true
                ready = true
                slot
            } catch (error: Exception) {
                fail()
                throw error
            }
        }
    }

    suspend fun createSnapshot(): Int = withPausedCore {
        val state = captureState()
        val cover = pauseCover
        withContext(NonCancellable + Dispatchers.IO) { store.createSnapshot(state, cover, slotId) }
    }

    suspend fun applySnapshot(id: Int): SavedSlot = withPausedCore {
        withContext(NonCancellable) {
            val state = withContext(Dispatchers.IO) { store.readSnapshot(id) }
            val cover = withContext(Dispatchers.IO) { store.snapshotCover(id) }
            val original = captureState()
            val wasAllowed = canAutoSave
            canAutoSave = false
            try {
                check(view.unserializeState(state.bytes, useEmulationThread = false)) {
                    "无法应用快照，请检查核心版本。原槽位未修改。"
                }
                withContext(Dispatchers.IO) { store.saveSlot(slotId, state, cover) }
                clearPauseCover()
                pauseCover = cover
                canAutoSave = true
                state
            } catch (error: Exception) {
                val restored = runCatching { view.unserializeState(original.bytes, useEmulationThread = false) }.getOrDefault(false)
                if (restored) canAutoSave = wasAllowed else fail()
                throw error
            }
        }
    }

    suspend fun deleteCurrentProgress() = withPausedCore {
        withContext(NonCancellable) {
            val wasAllowed = canAutoSave
            canAutoSave = false
            try {
                withContext(Dispatchers.IO) { store.deleteSlot(slotId) }
                quitSaved = true
                ready = false
            } catch (error: Exception) {
                canAutoSave = wasAllowed
                throw error
            }
        }
    }

    suspend fun saveBeforeQuit() {
        if (ready && canAutoSave) withPausedCore {
            val state = captureState()
            val cover = pauseCover ?: GameCover.capture(GameCover.source(view))
            withContext(NonCancellable + Dispatchers.IO) { store.saveSlot(slotId, state, cover) }
            quitSaved = true
        }
        quitSaved = true
    }

    fun fail() {
        failed = true
        ready = false
        canAutoSave = false
        clearPauseCover()
        life.pause()
    }

    fun stop() {
        foreground = false
        life.pause()
        saveBeforeStop()
    }

    fun destroy() {
        if (closed) return
        life.pause()
        if (busy) { destroyRequested = true; return }
        try { saveBeforeStop() } finally {
            closed = true
            ready = false
            clearPauseCover()
            life.destroy()
        }
    }

    private fun saveBeforeStop() {
        if (!ready || busy || !canAutoSave || quitSaved || closed) return
        val state = captureState()
        val cover = if (paused) pauseCover else GameCover.capture(GameCover.source(view))
        store.saveSlot(slotId, state, cover)
    }

    private fun captureState() = SavedSlot(
        view.serializeState(useEmulationThread = false).also { check(it.isNotEmpty()) { "核心没有返回存档数据。" } },
        BuildConfig.FBNEO_COMMIT, System.currentTimeMillis(),
    )

    private suspend fun <T> withPausedCore(action: suspend () -> T): T {
        check(!closed && !destroyRequested && !busy && !failed)
        busy = true
        life.pause()
        return try { action() } finally {
            busy = false
            if (destroyRequested) destroy() else {
                if (!foreground) saveBeforeStop()
                updatePlayback()
            }
        }
    }

    private fun updatePlayback() {
        if (closed || destroyRequested) return
        if (foreground && !paused && !dialogOpen && !busy && !failed) life.resume() else life.pause()
    }
}
