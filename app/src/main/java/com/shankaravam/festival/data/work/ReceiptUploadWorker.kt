package com.shankaravam.festival.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shankaravam.festival.ShankaRavamApp
import com.shankaravam.festival.data.remote.AudioCloudClient
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Phase-3 receipt upload (R2 plan §5.1). PUTs the compressor's WebP bytes to
 * the temple gateway, records the returned gateway path for delta sync.
 *
 * Never blocks saving: saving commits to Room in <10 ms; this runs in the
 * background on CONNECTED with exponential backoff. Result discipline
 * mirrors SyncWorker — auth/config dead-ends succeed quietly (Settings
 * surfaces them) instead of hot-looping; only transient faults retry.
 */
class ReceiptUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ShankaRavamApp).container
        val expenseId = inputData.getString(KEY_EXPENSE_ID) ?: return Result.failure()
        val eventId = inputData.getString(KEY_EVENT_ID)
            ?: container.sessionPrefs.currentEventId.value
            ?: return Result.failure()
        // Sharing unconfigured → pure offline app, nothing to do.
        if (container.sessionPrefs.gatewayBaseUrl.isBlank()) return Result.success()
        val expense = runCatching {
            container.expenseRepository.observeById(expenseId).first()
        }.getOrNull() ?: return Result.success() // row scrubbed — quiet
        if (!expense.receiptUrl.isNullOrBlank()) return Result.success() // already uploaded
        val path = expense.receiptPath ?: return Result.success() // nothing attached
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return Result.success()
        if (file.length() > RECEIPT_UPLOAD_MAX_BYTES) return Result.failure() // compressor caps; never retry
        val token = container.authRepository.idToken()
        if (token.isNullOrBlank()) return Result.retry() // sign-in may follow; backoff bounds the cost
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return Result.retry()
        return when (val up = container.audioCloud.uploadReceipt(token, eventId, expenseId, bytes)) {
            is AudioCloudClient.ReceiptUpload.Done -> {
                container.expenseRepository.attachReceiptUrl(expenseId, up.path)
                // Hand off to delta sync so peers learn the path promptly.
                runCatching { SyncWorker.syncNow(applicationContext, eventId) }
                Result.success()
            }
            is AudioCloudClient.ReceiptUpload.Retry -> Result.retry()
            is AudioCloudClient.ReceiptUpload.GiveUp -> Result.success()
        }
    }

    companion object {
        const val KEY_EXPENSE_ID = "expense_id"
        const val KEY_EVENT_ID = "event_id"
        private const val UNIQUE_PREFIX = "shankaravam-receipt-"

        /** Matches the Worker-side 150 KB ceiling (compressor targets ~100 KB). */
        const val RECEIPT_UPLOAD_MAX_BYTES = 150L * 1024L

        private fun constraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Unique per expense: re-attach replaces the pending upload. */
        fun schedule(context: Context, expenseId: String, eventId: String) {
            val request = OneTimeWorkRequestBuilder<ReceiptUploadWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_EXPENSE_ID to expenseId, KEY_EVENT_ID to eventId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PREFIX + expenseId, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
