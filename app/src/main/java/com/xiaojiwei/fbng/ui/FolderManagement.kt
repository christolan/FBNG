package com.xiaojiwei.fbng.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngPanel
import com.xiaojiwei.fbng.ui.theme.fbngIsland

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
    fun control(tag: String) = Modifier
        .fillMaxWidth()
        .testTag(tag)
        .onFocusChanged { if (it.isFocused) onFocused(tag) }
        .focusRequester(requesters.getValue(tag))

    Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
        FbngPanel {
            Text(stringResource(R.string.folder_heading), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.folder_body),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
            FbngButton(onClick = actions.add, enabled = !busy, modifier = control(ShellTags.AddFolder)) {
                Text(stringResource(R.string.folder_add))
            }
            if (folders.isNotEmpty()) {
                FbngButton(
                    onClick = actions.rescan,
                    enabled = !busy,
                    modifier = control(ShellTags.ScanFolders),
                    kind = FbngButtonKind.Outline,
                ) {
                    Text(stringResource(R.string.folder_rescan))
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.primary, trackColor = scheme.secondaryContainer)
                Text(stringResource(workMessage), style = MaterialTheme.typography.bodyLarge)
            }
            if (error != null) {
                Text(stringResource(error), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
            }
        }
        for (scan in folders) {
            val folder = scan.folder
            key(folder.uri) {
                val removeLabel = stringResource(R.string.folder_remove_label, folder.name)
                FolderCard(
                    scan = scan,
                    busy = busy,
                    removeModifier = control(ShellTags.removeFolder(folder.uri))
                        .semantics { contentDescription = removeLabel },
                    onRemove = { actions.remove(folder.uri) },
                )
            }
        }
    }
}

@Composable
private fun FolderCard(scan: FolderScan, busy: Boolean, removeModifier: Modifier, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .fbngIsland(panel = false)
            .padding(FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        Text(scan.folder.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        if (scan.unavailable) {
            Text(stringResource(R.string.folder_unavailable), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
        } else {
            val hasMatches = scan.roms > 0 || scan.bios > 0
            Text(
                if (hasMatches) stringResource(R.string.folder_matches, scan.roms, scan.bios)
                else stringResource(R.string.folder_no_matches),
                style = MaterialTheme.typography.bodyLarge,
                color = if (hasMatches) scheme.secondary else scheme.onSurfaceVariant,
            )
            if (scan.skipped > 0) {
                Text(
                    stringResource(R.string.folder_skipped, scan.skipped),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (scan.unreadable > 0) {
                Text(
                    stringResource(R.string.folder_unreadable_zip, scan.unreadable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.error,
                )
            }
            if (scan.incomplete) {
                Text(stringResource(R.string.folder_incomplete), style = MaterialTheme.typography.bodyLarge, color = scheme.error)
            }
        }
        FbngButton(onClick = onRemove, enabled = !busy, modifier = removeModifier, kind = FbngButtonKind.Outline) {
            Text(stringResource(R.string.folder_remove))
        }
    }
}
