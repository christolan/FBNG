package com.xiaojiwei.fbng.game

import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import androidx.annotation.MainThread
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal object GameCover {
    private data class Source(val surface: Surface, val bounds: Rect)

    private const val TAG = "FBNG"
    private const val CAPTURE_TIMEOUT_MS = 400L
    private val handler by lazy { Handler(HandlerThread("game-cover").apply { start() }.looper) }

    @MainThread
    private fun source(view: GLSurfaceView): Source? {
        if (!view.holder.surface.isValid || view.width <= 0 || view.height <= 0) return null
        val width = view.width
        val height = view.height
        val aspect = CoreGeometry.reportedAspectRatio() ?: (4f / 3f)
        val gameWidth = minOf(width, (height * aspect).roundToInt()).coerceAtLeast(1)
        val gameHeight = minOf(height, (width / aspect).roundToInt()).coerceAtLeast(1)
        val left = (width - gameWidth) / 2
        val top = (height - gameHeight) / 2
        return Source(view.holder.surface, Rect(left, top, left + gameWidth, top + gameHeight))
    }

    @MainThread
    suspend fun captureLastFrame(view: GLSurfaceView): ByteArray? = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            requestAfterRender(view) { continuation.resume(it) }
        }
    }

    @MainThread
    fun captureLastFrameBlocking(view: GLSurfaceView): ByteArray? {
        val done = CountDownLatch(1)
        var result: ByteArray? = null
        requestAfterRender(view) {
            result = it
            done.countDown()
        }
        return if (done.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) result else null
    }

    private fun requestAfterRender(view: GLSurfaceView, onComplete: (ByteArray?) -> Unit) {
        val source = source(view)
        if (source == null) {
            onComplete(null)
            return
        }
        view.queueEvent { handler.post { request(source, onComplete) } }
    }

    private fun request(source: Source, onComplete: (ByteArray?) -> Unit) {
        val scale = minOf(1f, 640f / maxOf(source.bounds.width(), source.bounds.height()))
        val bitmap = createBitmap((source.bounds.width() * scale).roundToInt().coerceAtLeast(1),
            (source.bounds.height() * scale).roundToInt().coerceAtLeast(1))
        try {
            PixelCopy.request(source.surface, source.bounds, bitmap, { status ->
                val result = try {
                    if (status != PixelCopy.SUCCESS) {
                        Log.w(TAG, "Could not copy game frame: PixelCopy status $status")
                        null
                    } else {
                        ByteArrayOutputStream().use { out ->
                            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) out.toByteArray() else null
                        }
                    }
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Could not encode save preview", error)
                    null
                } finally {
                    bitmap.recycle()
                }
                onComplete(result)
            }, handler)
        } catch (error: RuntimeException) {
            bitmap.recycle()
            Log.w(TAG, "Could not capture save preview", error)
            onComplete(null)
        }
    }
}
