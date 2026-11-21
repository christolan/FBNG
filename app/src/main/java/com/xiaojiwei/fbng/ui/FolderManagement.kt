package com.xiaojiwei.fbng.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens

internal data class FolderActions(
    val add: () -> Unit = {},
    val rescan: () -> Unit = {},
    val remove: (Uri) -> Unit = {},
)

@Composable
internal fun FolderManagement(
    folders: List<FolderScan>,
    busy: Boolean,
    workMessage: Int,
    error: Int?,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    actions: FolderActions,
) {
    val scheme = MaterialTheme.colorScheme
    var pendingRemoval by remember { mutableStateOf<FolderScan?>(null) }
    fun control(tag: String) = Modifier.libraryFocus(tag, requesters.getValue(tag), onFocused)

    Column(
        Modifier.fillMaxSize().padding(FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        Text(stringResource(R.string.folder_heading), style = MaterialTheme.typography.headlineSmall)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(FbngDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        ) {
            for (scan in folders) key(scan.folder.uri) {
                val tag = ShellTags.removeFolder(scan.folder.uri)
                val details = when {
                    scan.unavailable -> R.string.folder_unavailable
                    scan.incomplete -> R.string.folder_incomplete
                    else -> null
                }
                Column(
                    Modifier.focusProperties {
                        if (scan == folders.last()) down = requesters.getValue(ShellTags.AddFolder)
                    }.then(control(tag)).fillMaxWidth().fbngFocusTarget(
                        { if (!busy) pendingRemoval = scan }, scheme.surface,
                    ).padding(FbngDimens.space12),
                ) {
                    Text(scan.folder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (details != null) Text(stringResource(details), style = MaterialTheme.typography.labelMedium, color = scheme.error)
                    if (scan.unreadable > 0) Text(stringResource(R.string.folder_unreadable_zip, scan.unreadable),
                        style = MaterialTheme.typography.labelMedium, color = scheme.error)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val lastFolder = folders.lastOrNull()?.let { requesters.getValue(ShellTags.removeFolder(it.folder.uri)) }
            FbngButton(
                { if (!busy) actions.add() },
                Modifier.focusProperties {
                    if (lastFolder != null) {
                        up = lastFolder
                        right = requesters.getValue(ShellTags.ScanFolders)
                    }
                }.then(control(ShellTags.AddFolder)),
                kind = FbngButtonKind.Outline,
            ) {
                Text(stringResource(R.string.folder_add))
            }
            if (folders.isNotEmpty()) {
                FbngButton({ if (!busy) actions.rescan() }, Modifier.focusProperties {
                    if (lastFolder != null) up = lastFolder
                    left = requesters.getValue(ShellTags.AddFolder)
                }.then(control(ShellTags.ScanFolders)), kind = FbngButtonKind.Outline) {
                    Text(stringResource(R.string.folder_rescan))
                }
            }
            Spacer(Modifier.weight(1f))
            val status = when {
                error != null -> stringResource(error)
                busy -> stringResource(workMessage)
                folders.isNotEmpty() -> stringResource(R.string.folder_total, folders.sumOf { it.roms }, folders.sumOf { it.bios })
                else -> ""
            }
            Text(status, Modifier.weight(7f, fill = false), style = MaterialTheme.typography.labelMedium,
                color = if (error != null) scheme.error else scheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    pendingRemoval?.let { scan ->
        GameConfirmationDialog(
            title = stringResource(R.string.folder_remove_label, scan.folder.name),
            body = stringResource(R.string.folder_remove_confirmation),
            actionLabel = stringResource(R.string.folder_remove),
            onConfirm = { pendingRemoval = null; actions.remove(scan.folder.uri) },
            onDismiss = { pendingRemoval = null },
        )
    }
}
