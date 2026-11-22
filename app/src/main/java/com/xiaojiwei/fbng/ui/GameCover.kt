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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    val clear: () -> Unit = {},
    val clearing: Boolean = false,
    val clearResult: Boolean? = null,
)

@Composable
internal fun GameCover(
    image: CoverImage?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(image.file)
                    .memoryCacheKey("${image.file.name}:${image.version}").build(),
                contentDescription = null,
                contentScale = contentScale,
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
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
        for ((tag, label, action) in listOf(
            Triple(ShellTags.FetchCovers, R.string.covers_fetch, state.fetch),
            Triple(ShellTags.RefreshCovers, R.string.covers_refresh, state.refresh),
            Triple(ShellTags.ClearCovers, R.string.covers_clear, onClear),
        )) {
            val enabled = !state.clearing && (tag != ShellTags.ClearCovers || state.images.isNotEmpty())
            Row(
                Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
                    .fbngFocusTarget(
                        action, scheme.surface, enabled = enabled,
                        focusableWhenDisabled = state.clearing && tag == ShellTags.FetchCovers,
                    )
                    .height(FbngDimens.buttonHeight).padding(horizontal = FbngDimens.space12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
                if (enabled) Text("›", color = scheme.secondary)
            }
        }
        Spacer(Modifier.weight(1f))
        val work = state.manualWork
        if (state.clearing || state.clearResult != null) {
            val label = when {
                state.clearing -> R.string.covers_clearing
                state.clearResult == true -> R.string.covers_cleared
                else -> R.string.covers_clear_failed
            }
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                color = if (state.clearResult == false && !state.clearing) scheme.error else scheme.onSurfaceVariant)
        } else if (work != null) {
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
