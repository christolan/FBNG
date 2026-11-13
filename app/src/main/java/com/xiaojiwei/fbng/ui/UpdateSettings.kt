package com.xiaojiwei.fbng.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.update.AppUpdate
import com.xiaojiwei.fbng.update.UpdateState

internal data class UpdateUiState(
    val updater: AppUpdate,
    val state: UpdateState,
    val canInstall: Boolean,
)

@Composable
internal fun UpdateSettings(
    update: UpdateUiState,
    requester: FocusRequester,
    onFocused: (String) -> Unit,
) {
    val context = LocalContext.current
    val updater = update.updater
    val state = update.state
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updater.onPermissionResult()
    }
    val confirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    fun install() {
        updater.install {
            permission.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    LaunchedEffect(state, update.canInstall, lifecycleState) {
        if (!update.canInstall || lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        when (state) {
            is UpdateState.Ready -> {
                if (state.autoInstall) install() else requester.requestFocus()
            }
            is UpdateState.Confirm -> updater.confirm(confirmation::launch)
            else -> Unit
        }
    }

    val action = when (state) {
        is UpdateState.Available -> R.string.update_download
        is UpdateState.Downloading -> R.string.update_cancel_download
        is UpdateState.Ready -> if (state.message == R.string.update_allow_install) {
            R.string.update_open_permissions
        } else R.string.update_install
        is UpdateState.Confirm -> R.string.update_confirm
        is UpdateState.Installing, UpdateState.Installed -> R.string.update_install
        else -> R.string.update_check
    }
    val status = when (state) {
        UpdateState.Unchecked -> null
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.Current -> stringResource(R.string.update_current)
        UpdateState.CheckFailed -> stringResource(R.string.update_check_failed)
        is UpdateState.Available -> stringResource(state.error ?: R.string.update_available, state.release.versionName)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.percent)
        is UpdateState.Ready -> stringResource(state.message ?: R.string.update_ready, state.release.versionName)
        is UpdateState.Confirm -> stringResource(R.string.update_confirmation)
        is UpdateState.Installing -> stringResource(R.string.update_installing)
        UpdateState.Installed -> stringResource(R.string.update_installed)
    }
    val error = state == UpdateState.CheckFailed ||
        state is UpdateState.Available && state.error != null ||
        state is UpdateState.Ready && state.message == R.string.update_install_failed
    val actionEnabled = when (state) {
        is UpdateState.Ready, is UpdateState.Confirm -> update.canInstall
        UpdateState.Checking, is UpdateState.Installing, UpdateState.Installed -> false
        else -> true
    }
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
        Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.update_installed_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        if (status != null) {
            Text(
                status,
                modifier = Modifier.semantics { if (state !is UpdateState.Downloading) liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    error -> scheme.error
                    state is UpdateState.Available -> scheme.secondary
                    else -> scheme.onSurfaceVariant
                },
            )
        }
        if (state is UpdateState.Downloading) {
            LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        FbngButton(
            onClick = {
                when (state) {
                    is UpdateState.Available -> updater.download()
                    is UpdateState.Downloading -> updater.cancelDownload()
                    is UpdateState.Ready -> install()
                    is UpdateState.Confirm -> updater.confirm(confirmation::launch)
                    else -> updater.check()
                }
            },
            modifier = Modifier.fillMaxWidth().libraryFocus(ShellTags.AppUpdate, requester, onFocused),
            enabled = actionEnabled,
            kind = if (state is UpdateState.Available || state is UpdateState.Ready || state is UpdateState.Confirm) {
                FbngButtonKind.Filled
            } else FbngButtonKind.Neutral,
        ) {
            Text(stringResource(action))
        }
    }
}
