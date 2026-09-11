package com.durgamma.festival.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.durgamma.festival.DurgammaApp
import com.durgamma.festival.core.util.Outcome
import java.util.concurrent.TimeUnit

/**
 * G6 background delta sync (plan §20). No-ops when Cloud Sync is off or no
 * one is signed in — offline use never pays for this worker. Network failures
 * retry with exponential backoff; auth/config failures succeed quietly (the
 * Settings screen surfaces them instead of the worker loop).
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DurgammaApp).container
        if (!container.sessionPrefs.cloudSyncEnabled) return Result.success()
        if (container.authRepository.user.value == null) return Result.success()
        val eventId = inputData.getString(KEY_EVENT_ID)
            ?: container.sessionPrefs.currentEventId.value
            ?: return Result.success()
        return when (container.syncService.syncEvent(eventId)) {
            is Outcome.Ok -> Result.success()
            is Outcome.Err -> Result.retry()
        }
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
        private const val UNIQUE_PERIODIC = "durgamma-delta-sync"
        private const val UNIQUE_NOW = "durgamma-delta-sync-now"

        private fun networkConstraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun syncNow(context: Context, eventId: String) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_EVENT_ID to eventId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NOW)
        }
    }
}
