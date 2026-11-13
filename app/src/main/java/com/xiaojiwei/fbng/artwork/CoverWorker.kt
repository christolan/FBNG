package com.xiaojiwei.fbng.artwork

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** WorkManager keeps offline requests alive across app exits and reconnects. */
class CoverWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = CoverRepository.get(applicationContext)
        repository.downloadLock.withLock {
            val manual = inputData.getBoolean(MANUAL, false)
            val refresh = inputData.getBoolean(REFRESH, false)
            var downloaded = 0
            var existing = 0
            var missing = 0
            var failed = 0
            fun counts() = workDataOf(DOWNLOADED to downloaded, EXISTING to existing, MISSING to missing, FAILED to failed)
            try {
                repository.reloadImages()
                for ((path, names) in repository.candidates()) {
                    currentCoroutineContext().ensureActive()
                    // Favorites may change while a queued batch is running.
                    val eligible = repository.eligibleNames()
                    val count = names.count { it in eligible }
                    if (count == 0) continue
                    when {
                        path == null -> missing += count
                        !refresh && repository.hasImage(path) -> existing += count
                        !manual && repository.recentlyMissing(path) -> missing += count
                        else -> try {
                            if (repository.download(path)) downloaded += count else missing += count
                        } catch (error: IOException) {
                            Log.w("FBNG", "Cover download failed", error)
                            failed += count
                        }
                    }
                    setProgress(counts())
                    // Do not hammer every remaining image during a network/server outage.
                    if (failed > 0) break
                }
            } catch (error: IOException) {
                Log.w("FBNG", "Could not read cover storage", error)
                failed++
                return@withLock Result.success(counts())
            }
            if (failed > 0 && !manual && runAttemptCount < 3) Result.retry()
            // A terminal error is data, so it cannot cancel later requests in the unique queue.
            else Result.success(counts())
        }
    }

    companion object {
        const val MANUAL = "manual"
        const val REFRESH = "refresh"
        const val DOWNLOADED = "downloaded"
        const val EXISTING = "existing"
        const val MISSING = "missing"
        const val FAILED = "failed"
        private const val WORK_NAME = "fbng-game-covers"

        fun enqueue(context: Context, manual: Boolean = false, refresh: Boolean = false): UUID {
            val request = OneTimeWorkRequestBuilder<CoverWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(MANUAL to manual, REFRESH to refresh))
                .addTag(if (refresh) REFRESH else "missing")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            return request.id
        }
    }
}
