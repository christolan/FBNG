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

    init {
        life.lifecycle.addObserver(view)
        life.create()
    }

    fun start() {
        foreground = true
        updatePlayback()
    }

    fun togglePause() {
        if (!ready || busy) return
        paused = !paused
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

    suspend fun save() = withPausedCore {
        val bytes = snapshot()
        // Once captured, finish the disk write even if the screen is disposed.
        withContext(NonCancellable + Dispatchers.IO) { store.write(bytes, BuildConfig.FBNEO_COMMIT) }
        hasSlot = store.hasSlot()
        canAutoSave = true
    }

    suspend fun load(): SavedSlot? = withPausedCore {
        // A failed read must not let a later automatic save overwrite the old slot.
        canAutoSave = false
        val slot = withContext(Dispatchers.IO) { store.read() }
        check(!closed && !failed) { "游戏已关闭或核心出错" }
        if (slot != null) {
            check(view.unserializeState(slot.bytes, useEmulationThread = false)) { "无法读取存档，请检查核心版本或重置游戏。" }
        }
        canAutoSave = true
        hasSlot = store.hasSlot()
        slot
    }

    suspend fun reset() = withPausedCore {
        canAutoSave = false
        store.clear()
        hasSlot = store.hasSlot()
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
            life.destroy()
        }
    }

    private fun saveBeforeStop() {
        if (!ready || busy || !canAutoSave || quitSaved || closed) return
        // Android can kill the process immediately after onStop. Pause the GL thread,
        // then capture directly and fsync before returning from the lifecycle callback.
        store.write(snapshot(), BuildConfig.FBNEO_COMMIT)
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
        if (foreground && !paused && !busy && !failed) life.resume() else life.pause()
    }
}
