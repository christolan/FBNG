package com.xiaojiwei.fbng.game

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import androidx.annotation.MainThread
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Copies the game surface itself; Compose dialogs and HUD controls are never included. */
internal object GameCover {
    data class Source(val surface: Surface, val bounds: Rect)

    private val handler by lazy { Handler(HandlerThread("game-cover").apply { start() }.looper) }

    @MainThread
    fun source(view: SurfaceView): Source? {
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

    fun capture(source: Source?): ByteArray? {
        if (source == null) return null
        val scale = minOf(1f, 640f / maxOf(source.bounds.width(), source.bounds.height()))
        val bitmap = createBitmap((source.bounds.width() * scale).roundToInt().coerceAtLeast(1),
            (source.bounds.height() * scale).roundToInt().coerceAtLeast(1))
        val done = CountDownLatch(1)
        var result: ByteArray? = null
        try {
            PixelCopy.request(source.surface, source.bounds, bitmap, { status ->
                try {
                    if (status == PixelCopy.SUCCESS) {
                        result = ByteArrayOutputStream().use { out ->
                            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) out.toByteArray() else null
                        }
                    }
                } finally {
                    // The callback owns the bitmap, even when the caller has already timed out.
                    bitmap.recycle()
                    done.countDown()
                }
            }, handler)
        } catch (_: IllegalArgumentException) {
            bitmap.recycle()
            return null
        }
        // The worker callback also lets Android's synchronous stop callback save its last frame.
        return if (done.await(1, TimeUnit.SECONDS)) result else null
    }
}
