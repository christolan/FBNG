package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
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
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngPanel

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
    FbngPanel {
        Text(stringResource(R.string.covers_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.covers_body), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        FbngButton(
            onClick = state.fetch,
            modifier = Modifier.fillMaxWidth().libraryFocus(ShellTags.FetchCovers, requesters.getValue(ShellTags.FetchCovers), onFocused),
            kind = FbngButtonKind.Neutral,
        ) { Text(stringResource(R.string.covers_fetch)) }
        FbngButton(
            onClick = state.refresh,
            modifier = Modifier.fillMaxWidth().libraryFocus(ShellTags.RefreshCovers, requesters.getValue(ShellTags.RefreshCovers), onFocused),
            kind = FbngButtonKind.Neutral,
        ) { Text(stringResource(R.string.covers_refresh)) }
        val work = state.manualWork
        if (work != null) {
            val status = when (work.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> R.string.covers_waiting
                WorkInfo.State.RUNNING -> R.string.covers_working
                WorkInfo.State.SUCCEEDED -> if (work.outputData.getInt(CoverWorker.FAILED, 0) > 0) {
                    R.string.covers_failed
                } else R.string.covers_done
                WorkInfo.State.FAILED -> R.string.covers_failed
                WorkInfo.State.CANCELLED -> R.string.covers_cancelled
            }
            Text(stringResource(status), style = MaterialTheme.typography.bodyLarge,
                color = if (work.state == WorkInfo.State.FAILED || work.outputData.getInt(CoverWorker.FAILED, 0) > 0) {
                    scheme.error
                } else scheme.onSurfaceVariant)
            if (work.state == WorkInfo.State.RUNNING) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.secondaryContainer)
            }
            val data = if (work.state.isFinished) work.outputData else work.progress
            if (data.keyValueMap.isNotEmpty()) {
                Text(stringResource(R.string.covers_result,
                    data.getInt(CoverWorker.DOWNLOADED, 0), data.getInt(CoverWorker.EXISTING, 0),
                    data.getInt(CoverWorker.MISSING, 0), data.getInt(CoverWorker.FAILED, 0)),
                    style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.covers_source), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
    }
}
