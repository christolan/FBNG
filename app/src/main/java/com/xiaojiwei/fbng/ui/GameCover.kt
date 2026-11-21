package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.work.WorkInfo
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.artwork.CoverImage
import com.xiaojiwei.fbng.artwork.CoverWorker
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import androidx.compose.foundation.layout.height
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget

internal data class CoverUiState(
    val images: Map<String, CoverImage> = emptyMap(),
    val manualWork: WorkInfo? = null,
    val fetch: () -> Unit = {},
    val refresh: () -> Unit = {},
)

/** A fixed slot keeps focus and scroll positions stable when a cover arrives. */
@Composable
internal fun GameCover(
    title: String,
    image: CoverImage?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var loaded by remember(image) { mutableStateOf(false) }
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(
            title,
            modifier = Modifier.padding(FbngDimens.space16),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (image != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(image.file)
                    .memoryCacheKey("${image.file.name}:${image.version}").build(),
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
internal fun CoverSettings(
    state: CoverUiState,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
        Text(stringResource(R.string.covers_title), style = MaterialTheme.typography.headlineSmall)
        for ((tag, label, action) in listOf(
            Triple(ShellTags.FetchCovers, R.string.covers_fetch, state.fetch),
            Triple(ShellTags.RefreshCovers, R.string.covers_refresh, state.refresh),
        )) {
            Row(
                Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
                    .fbngFocusTarget(action, scheme.surface)
                    .height(FbngDimens.buttonHeight).padding(horizontal = FbngDimens.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("›", color = scheme.secondary)
            }
        }
        Spacer(Modifier.weight(1f))
        val work = state.manualWork
        if (work != null) {
            val failed = work.state == WorkInfo.State.FAILED || work.outputData.getInt(CoverWorker.FAILED, 0) > 0
            val status = when {
                failed -> stringResource(R.string.covers_failed)
                work.state == WorkInfo.State.SUCCEEDED ->
                    stringResource(R.string.covers_updated_count, work.outputData.getInt(CoverWorker.DOWNLOADED, 0))
                work.state == WorkInfo.State.RUNNING -> stringResource(R.string.covers_working)
                work.state == WorkInfo.State.CANCELLED -> stringResource(R.string.covers_cancelled)
                else -> stringResource(R.string.covers_waiting)
            }
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = if (failed) scheme.error else scheme.onSurfaceVariant)
        }
    }
}
