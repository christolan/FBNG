package com.xiaojiwei.fbng.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.FbngApplication
import com.xiaojiwei.fbng.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface UpdateState {
    data object Unchecked : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data object CheckFailed : UpdateState
    data class Available(val release: AppRelease, val error: Int? = null) : UpdateState
    data class Downloading(val release: AppRelease, val percent: Int) : UpdateState
    data class Ready(val release: AppRelease, val autoInstall: Boolean = false, val message: Int? = null) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Confirm(val release: AppRelease, val intent: Intent) : UpdateState
    data object Installed : UpdateState
}

internal class AppUpdate(private val context: FbngApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val apk = File(context.cacheDir, "fbng-update.apk")
    private val packageManager = context.packageManager
    private val installer = packageManager.packageInstaller
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Unchecked)
    val state = mutableState.asStateFlow()
    val installing get() = state.value is UpdateState.Installing || state.value == UpdateState.Installed
    private var downloadJob: Job? = null
    private var sessionId: Int? = null

    fun checkOnStartup() {
        if (state.value == UpdateState.Unchecked) check(clearCache = true)
    }

    fun check(clearCache: Boolean = false) {
        when (state.value) {
            UpdateState.Unchecked, UpdateState.Current, UpdateState.CheckFailed -> Unit
            else -> return
        }
        mutableState.value = UpdateState.Checking
        scope.launch {
            try {
                if (clearCache) withContext(Dispatchers.IO) { apk.delete() }
                val release = GitHubUpdates.check(BuildConfig.VERSION_CODE.toLong())
                mutableState.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Current
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not check GitHub releases", error)
                mutableState.value = UpdateState.CheckFailed
            }
        }
    }

    fun download() {
        val available = state.value as? UpdateState.Available ?: return
        val release = available.release
        mutableState.value = UpdateState.Downloading(release, 0)
        downloadJob = scope.launch {
            try {
                GitHubUpdates.download(release, apk) { percent ->
                    mutableState.value = UpdateState.Downloading(release, percent)
                }
                withContext(Dispatchers.IO) { verifyApk(release) }
                mutableState.value = UpdateState.Ready(release, autoInstall = true)
            } catch (error: CancellationException) {
                apk.delete()
                mutableState.value = UpdateState.Available(release)
                throw error
            } catch (error: Exception) {
                apk.delete()
                Log.w(TAG, "Could not download or verify update ${release.versionName}", error)
                val message = if (error is InvalidUpdate) R.string.update_invalid else R.string.update_download_failed
                mutableState.value = UpdateState.Available(release, message)
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun install(requestPermission: () -> Unit) {
        val ready = state.value as? UpdateState.Ready ?: return
        val release = ready.release
        if (!packageManager.canRequestPackageInstalls()) {
            mutableState.value = UpdateState.Ready(release, message = R.string.update_allow_install)
            try {
                requestPermission()
            } catch (error: Exception) {
                Log.w(TAG, "Could not open the system settings for installing APKs", error)
                mutableState.value = UpdateState.Ready(release, message = R.string.update_install_failed)
            }
            return
        }
        mutableState.value = UpdateState.Installing(release)
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { stage(release) }
                sessionId = id
                withContext(Dispatchers.IO) {
                    val callback = PendingIntent.getBroadcast(
                        context,
                        id,
                        Intent(context, UpdateInstallReceiver::class.java),
                        PendingIntent.FLAG_MUTABLE,
                    )
                    installer.openSession(id).use { it.commit(callback.intentSender) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not submit update to the package installer", error)
                sessionId?.let(::abandon)
                sessionId = null
                mutableState.value = if (error is InvalidUpdate) {
                    apk.delete()
                    UpdateState.Available(release, R.string.update_download_again)
                } else {
                    UpdateState.Ready(release, message = R.string.update_install_failed)
                }
            }
        }
    }

    fun onPermissionResult() {
        val ready = state.value as? UpdateState.Ready ?: return
        if (packageManager.canRequestPackageInstalls()) {
            mutableState.value = ready.copy(autoInstall = true, message = null)
        }
    }

    fun confirm(launch: (Intent) -> Unit) {
        val confirm = state.value as? UpdateState.Confirm ?: return
        mutableState.value = UpdateState.Installing(confirm.release)
        try {
            launch(confirm.intent)
        } catch (error: Exception) {
            Log.w(TAG, "Could not open the system install confirmation", error)
            sessionId?.let(::abandon)
            sessionId = null
            mutableState.value = UpdateState.Ready(confirm.release, message = R.string.update_install_failed)
        }
    }

    fun onInstallResult(result: Intent) {
        val id = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (sessionId != id) {
            // A pending confirmation from a process that was stopped can be retried from Settings.
            if (id != -1 && status == PackageInstaller.STATUS_PENDING_USER_ACTION) abandon(id)
            return
        }
        val release = when (val current = state.value) {
            is UpdateState.Installing -> current.release
            is UpdateState.Confirm -> current.release
            else -> return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = result.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            if (confirmation != null) {
                mutableState.value = UpdateState.Confirm(release, confirmation)
                return
            }
            abandon(id)
        }
        sessionId = null
        mutableState.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                apk.delete()
                UpdateState.Installed
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Ready(release, message = R.string.update_install_cancelled)
            else -> {
                Log.w(TAG, "PackageInstaller status $status: ${result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                UpdateState.Ready(release, message = R.string.update_install_failed)
            }
        }
    }

    private fun verifyApk(release: AppRelease) {
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val downloaded = packageManager.getPackageArchiveInfo(apk.path, flags)
            ?: throw InvalidUpdate("APK cannot be read")
        val installed = packageManager.getPackageInfo(context.packageName, flags)
        if (downloaded.packageName != installed.packageName ||
            downloaded.versionName != release.versionName ||
            downloaded.longVersionCode != release.versionCode ||
            downloaded.longVersionCode <= installed.longVersionCode
        ) {
            throw InvalidUpdate("APK package or version does not match the update")
        }
        val currentSigners = installed.signingInfo?.apkContentsSigners.orEmpty().toSet()
        val updateSigners = downloaded.signingInfo?.apkContentsSigners.orEmpty().toSet()
        if (currentSigners.isEmpty() || currentSigners != updateSigners) {
            throw InvalidUpdate("APK signing certificate differs from the installed app")
        }
    }

    private suspend fun stage(release: AppRelease): Int {
        if (apk.length() != release.size) throw InvalidUpdate("Verified APK is no longer cached")
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(release.size)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().buffered().use { input ->
                    session.openWrite("base.apk", 0, release.size).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        session.fsync(output)
                    }
                }
            }
        } catch (error: Exception) {
            abandon(id)
            throw error
        }
        return id
    }

    private fun abandon(id: Int) {
        runCatching { installer.abandonSession(id) }
            .onFailure { Log.w(TAG, "Could not abandon install session $id", it) }
    }

    private companion object {
        const val TAG = "FBNG.Update"
    }
}

internal class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as FbngApplication).appUpdate.onInstallResult(intent)
    }
}
