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
import androidx.work.await
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CoverWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = CoverRepository.get(applicationContext)
        repository.downloadLock.withLock {
            val manual = inputData.getBoolean(MANUAL, false)
            if (!repository.prepareBatch(inputData.getLong(GENERATION, 0L), manual)) {
                return@withLock Result.success()
            }
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
                    if (failed > 0) break
                }
            } catch (error: IOException) {
                Log.w("FBNG", "Could not read cover storage", error)
                failed++
                return@withLock Result.success(counts())
            }
            if (failed > 0 && !manual && runAttemptCount < 3) Result.retry()
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
        private const val GENERATION = "generation"
        private const val WORK_NAME = "fbng-game-covers"

        internal suspend fun clearDownloaded(context: Context): Boolean {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).await()
            return CoverRepository.get(context).clearImages()
        }

        fun enqueue(context: Context, manual: Boolean = false, refresh: Boolean = false): UUID {
            val request = OneTimeWorkRequestBuilder<CoverWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(
                    MANUAL to manual,
                    REFRESH to refresh,
                    GENERATION to CoverRepository.get(context).downloadGeneration(),
                ))
                .addTag(if (refresh) REFRESH else "missing")
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            return request.id
        }
    }
}
