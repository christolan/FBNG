package com.xiaojiwei.fbng.game

import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import com.swordfish.libretrodroid.GLRetroView
import com.xiaojiwei.fbng.BuildConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class RunningGame(
    val view: GLRetroView,
    private val store: GameProgressStore,
    initialSlot: Int,
    private val nativeSaveDirectory: File,
) {
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
    var slotId by mutableStateOf(initialSlot)
        private set
    private var savedSinceResume = false
    private var capturedFrame: ByteArray? = null

    init {
        require(slotId in 1..GameProgressStore.SLOT_COUNT)
        life.lifecycle.addObserver(view)
        life.create()
    }

    fun start() { foreground = true; updatePlayback() }

    fun resume() {
        if (!ready || busy || dialogOpen) return
        paused = false
        updatePlayback()
    }

    suspend fun pauseAndSave() = withCoreLocked {
        paused = true
        if (foreground && !savedSinceResume) pauseWithFrame { GameCover.captureLastFrame(view) }
        else life.pause()
        saveProgress()
    }

    fun setDialogOpen(open: Boolean) { dialogOpen = open; updatePlayback() }

    suspend fun initialize(): SavedSlot? {
        check(!initializationStarted)
        initializationStarted = true
        return withPausedCore {
            try {
                val slot = withContext(Dispatchers.IO) { store.readSlot(slotId) }
                if (slot != null) check(view.unserializeState(slot.bytes, useEmulationThread = false)) {
                    "无法恢复这个存档，请尝试应用兼容快照或删除进度后重新开始。"
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
        val cover = if (savedSinceResume) withContext(Dispatchers.IO) { store.slotCover(slotId) } else capturedFrame
        withContext(NonCancellable + Dispatchers.IO) { store.createSnapshot(state, cover, slotId) }
    }

    suspend fun switchSave(target: Int): Boolean = withPausedCore {
        saveProgress()
        val state = withContext(Dispatchers.IO) { store.readSlot(target) }
        if (state == null) {
            quitSaved = true
            return@withPausedCore false
        }
        restoreProgress(state, target) { store.selectSlot(target) }
        true
    }

    suspend fun applySnapshot(id: Int, target: Int) = withPausedCore {
        saveProgress()
        val (state, cover) = withContext(Dispatchers.IO) { store.readSnapshot(id) to store.snapshotCover(id) }
        restoreProgress(state, target) { store.activateSnapshot(target, state, cover) }
    }

    private suspend fun restoreProgress(state: SavedSlot, target: Int, persist: () -> Unit) {
        withContext(NonCancellable) {
            val original = captureState()
            val wasAllowed = canAutoSave
            canAutoSave = false
            try {
                check(view.unserializeState(state.bytes, useEmulationThread = false)) {
                    "无法读取进度，请检查核心版本。"
                }
                withContext(Dispatchers.IO) { persist() }
                slotId = target
                savedSinceResume = true
                canAutoSave = true
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
            saveProgress()
            quitSaved = true
        }
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
        pauseBeforeStop()
        saveBeforeStop()
    }

    fun destroy() {
        if (closed) return
        if (busy) {
            destroyRequested = true
            life.pause()
            return
        }
        try {
            pauseBeforeStop()
            saveBeforeStop()
        } finally {
            closed = true
            ready = false
            try {
                life.destroy()
            } finally {
                if (!nativeSaveDirectory.deleteRecursively()) Log.w("FBNG", "Could not clean up the FBNeo session directory")
            }
        }
    }

    private fun pauseBeforeStop() {
        if (life.lifecycle.currentState == Lifecycle.State.RESUMED && needsAutoSave()) {
            pauseWithFrame { GameCover.captureLastFrameBlocking(view) }
        } else {
            life.pause()
        }
    }

    private inline fun pauseWithFrame(capture: () -> ByteArray?) {
        val previousMode = view.renderMode
        view.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        try {
            capturedFrame = capture()
        } finally {
            life.pause()
            view.renderMode = previousMode
        }
    }

    private fun needsAutoSave() = ready && !busy && canAutoSave && !quitSaved && !closed && !savedSinceResume

    private fun saveBeforeStop() {
        if (!needsAutoSave()) return
        store.saveSlot(slotId, captureState(), capturedFrame)
        savedSinceResume = true
        capturedFrame = null
    }

    private suspend fun saveProgress() {
        if (savedSinceResume) return
        val state = captureState()
        val cover = capturedFrame
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { store.saveSlot(slotId, state, cover) }
            savedSinceResume = true
            capturedFrame = null
        }
    }

    private fun captureState() = SavedSlot(
        view.serializeState(useEmulationThread = false).also { check(it.isNotEmpty()) { "核心没有返回存档数据。" } },
        BuildConfig.FBNEO_COMMIT, System.currentTimeMillis(),
    )

    private suspend fun <T> withPausedCore(action: suspend () -> T): T = withCoreLocked {
        life.pause()
        action()
    }

    private suspend fun <T> withCoreLocked(action: suspend () -> T): T {
        check(!closed && !destroyRequested && !busy && !failed)
        busy = true
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
        if (foreground && !paused && !dialogOpen && !busy && !failed && !quitSaved) {
            savedSinceResume = false
            capturedFrame = null
            life.resume()
        } else life.pause()
    }
}
