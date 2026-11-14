package com.xiaojiwei.fbng.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swordfish.libretrodroid.GLRetroView
import com.xiaojiwei.fbng.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Owns one core. All entry points run on the main thread, including lifecycle callbacks. */
internal class RunningGame(val view: GLRetroView, private val store: SaveStateStore) {
    val life = SessionLifecycleOwner()
    var ready by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var hasSlot by mutableStateOf(store.hasSlot())
        private set
    var initializationStarted = false
        private set
    private var foreground = false
    private var closed = false
    private var failed = false
    private var canAutoSave = false
    private var quitSaved = false
    private var dialogOpen = false
    private var pauseCover: ByteArray? = null

    init {
        life.lifecycle.addObserver(view)
        life.create()
    }

    fun start() {
        foreground = true
        updatePlayback()
    }

    fun togglePause() {
        if (!ready || busy || dialogOpen) return
        paused = !paused
        if (!paused) pauseCover = null
        updatePlayback()
    }

    suspend fun pauseForMenu() {
        if (!ready || busy || paused) return
        val cover = if (foreground) GameCover.source(view)?.let { GameCover.captureBeforePause(it) } else null
        if (!ready || busy || paused) return
        pauseCover = cover
        togglePause()
    }

    fun setDialogOpen(open: Boolean) {
        dialogOpen = open
        updatePlayback()
    }

    suspend fun initialize(): SavedSlot? {
        check(!initializationStarted)
        initializationStarted = true
        return try {
            load()
        } finally {
            if (!closed && !failed) ready = true
        }
    }

    suspend fun save(id: Int = SaveStateStore.AUTO_SLOT) = saveToSlot(id)

    suspend fun createManualSave() = saveToSlot(null)

    private suspend fun saveToSlot(id: Int?) = withPausedCore {
        val bytes = snapshot()
        val cachedCover = pauseCover
        val coverSource = if (!paused && id == SaveStateStore.AUTO_SLOT) GameCover.source(view) else null
        // Once captured, finish the disk write even if the screen is disposed.
        withContext(NonCancellable + Dispatchers.IO) {
            val cover = cachedCover ?: GameCover.capture(coverSource)
            if (id == null) {
                store.createManualSlot(bytes, BuildConfig.FBNEO_COMMIT, cover)
            } else {
                check(id == SaveStateStore.AUTO_SLOT || store.hasSlot(id)) { "这个槽位没有存档，请选择其他槽位。" }
                store.write(bytes, BuildConfig.FBNEO_COMMIT, id = id, cover = cover)
            }
        }
        hasSlot = store.hasSlot()
        canAutoSave = true
    }

    suspend fun load(id: Int = SaveStateStore.AUTO_SLOT): SavedSlot? = withPausedCore {
        // A failed read must not let a later automatic save overwrite the old slot.
        canAutoSave = false
        val slot = withContext(Dispatchers.IO) { store.read(id) }
        check(id == SaveStateStore.AUTO_SLOT || slot != null) { "这个槽位没有存档，请选择其他槽位。" }
        check(!closed && !failed) { "游戏已关闭或核心出错" }
        if (slot != null) {
            pauseCover = null
            check(view.unserializeState(slot.bytes, useEmulationThread = false)) { "无法读取存档，请检查核心版本或重置游戏。" }
        }
        canAutoSave = true
        hasSlot = store.hasSlot()
        slot
    }

    suspend fun reset() = withPausedCore {
        canAutoSave = false
        withContext(Dispatchers.IO) { store.clear() }
        hasSlot = store.hasSlot()
        pauseCover = null
        view.reset(useEmulationThread = false)
        canAutoSave = true
    }

    suspend fun saveBeforeQuit() {
        if (ready && canAutoSave) save()
        quitSaved = true
    }

    fun fail() {
        failed = true
        ready = false
        canAutoSave = false
        pauseCover = null
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
        try {
            saveBeforeStop()
        } finally {
            closed = true
            ready = false
            pauseCover = null
            life.destroy()
        }
    }

    private fun saveBeforeStop() {
        if (!ready || busy || !canAutoSave || quitSaved || closed) return
        // Android can kill the process immediately after onStop. Finish the write
        // before returning from the lifecycle callback.
        val bytes = snapshot()
        val cover = if (paused) pauseCover else GameCover.capture(GameCover.source(view))
        store.write(bytes, BuildConfig.FBNEO_COMMIT, cover = cover)
        hasSlot = store.hasSlot()
    }

    private fun snapshot(): ByteArray = view.serializeState(useEmulationThread = false).also {
        check(it.isNotEmpty()) { "核心没有返回存档数据" }
    }

    private suspend fun <T> withPausedCore(action: suspend () -> T): T {
        check(!closed && !busy && !failed)
        busy = true
        life.pause()
        return try {
            action()
        } finally {
            busy = false
            updatePlayback()
        }
    }

    private fun updatePlayback() {
        if (closed) return
        if (foreground && !paused && !dialogOpen && !busy && !failed) life.resume() else life.pause()
    }
}
