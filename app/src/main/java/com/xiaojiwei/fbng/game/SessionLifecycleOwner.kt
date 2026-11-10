package com.xiaojiwei.fbng.game

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * LibretroDroid registers itself on a Lifecycle. Driving that lifecycle ourselves lets a
 * configuration change keep the GLSurfaceView, and lets quitting the game destroy the core
 * without finishing the Activity.
 */
class SessionLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun create() = moveTo(Lifecycle.State.CREATED)

    fun resume() = moveTo(Lifecycle.State.RESUMED)

    fun pause() = moveTo(Lifecycle.State.STARTED)

    fun destroy() = moveTo(Lifecycle.State.DESTROYED)

    private fun moveTo(state: Lifecycle.State) {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        if (registry.currentState == state) return
        registry.currentState = state
    }
}
