package com.xiaojiwei.fbng.ui

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val placeholderColors = listOf(
    Color(0xFF263D5B), Color(0xFF244C48), Color(0xFF4A3B60),
    Color(0xFF633D46), Color(0xFF61472F), Color(0xFF364A35),
)

internal data class GamePreviewArtwork(
    val image: ImageBitmap? = null,
    val placeholderColor: Color = placeholderColors.first(),
)

internal fun gamePreviewArtwork(setNames: List<String>): Map<String, GamePreviewArtwork> =
    setNames.sorted().mapIndexed { index, name ->
        name to GamePreviewArtwork(placeholderColor = placeholderColors[index % placeholderColors.size])
    }.toMap()

@Composable
internal fun rememberSaveScreenshot(vararg selection: Any?, read: () -> ByteArray?): ImageBitmap? =
    key(selection.asList()) {
        val image by produceState<ImageBitmap?>(null) {
            value = withContext(Dispatchers.IO) {
                try {
                    read()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.w("FBNG", "Could not read save screenshot", error)
                    null
                }
            }
        }
        image
    }

@Composable
internal fun GamePreview(
    title: String,
    artwork: GamePreviewArtwork,
    modifier: Modifier = Modifier,
    announceTitle: Boolean = false,
) {
    val image = artwork.image
    val semantics = if (announceTitle) Modifier else Modifier.clearAndSetSemantics { }
    Box(
        modifier.then(semantics).background(if (image == null) artwork.placeholderColor else Color(0xFF0B121C)),
        contentAlignment = Alignment.Center,
    ) {
        if (image == null) {
            Text(
                title, Modifier.padding(horizontal = FbngDimens.space8),
                color = Color(0xFFF8FAFC), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Image(image, null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        }
    }
}
