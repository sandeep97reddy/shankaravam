package com.shankaravam.festival.domain.usecase

import android.content.Context
import com.shankaravam.festival.core.tts.SarvamTtsClient
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.data.local.AppDatabase
import com.shankaravam.festival.data.local.SessionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 2 local scrub (Steps 1-5). Local-only test festivals only.
 *
 * Gate: prefs.isCloudEvent(eventId) MUST be false. There is deliberately NO
 * syncStatus==LOCAL_ONLY row gate — SaveDonation/SaveExpense stamp
 * PENDING_UPLOAD at creation, so that check would block every event with
 * transactions (the exact case this exists for). A never-published event's
 * PENDING rows never left the device, so deleting them is safe.
 *
 * Order: pre-fetch paths → atomic cascade → best-effort files → prefs purge
 * → currentEvent retarget. Files never fail the delete (DB already gone).
 */
class DeleteLocalEventUseCase(
    private val database: AppDatabase,
    private val prefs: SessionPrefs,
    appContext: Context
) {
    private val appContext: Context = appContext.applicationContext

    /** Display-only counts for the confirmation dialog. Never throws. */
    suspend fun getCounts(eventId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        runCatching {
            database.donationDao().countForEvent(eventId) to
                database.expenseDao().countForEvent(eventId)
        }.getOrDefault(0 to 0)
    }

    suspend operator fun invoke(eventId: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        if (eventId.isBlank()) return@withContext Outcome.Err("Event not found.")
        if (prefs.isCloudEvent(eventId)) {
            return@withContext Outcome.Err(
                "Synced festivals cannot be deleted on-device. Use Close Festival."
            )
        }
        runCatching {
            // Step 1: collect disk paths before the rows vanish.
            val donationIds = database.donationDao().getDonationIdsForEvent(eventId)
            val receiptPaths = database.expenseDao().getReceiptPathsForEvent(eventId)

            // Step 2: atomic — orphans can never reach global pendingSync().
            database.deleteEventCascade(eventId)

            // Step 3: best-effort files. Phase 1: deletes EVERY clip for the row
            // (all speaker variants, legacy slots, human imports, roster) via
            // prefix scan — only donation_*.mp3 names, so temple_chime.wav and
            // cacheDir-root audio_test_sample.mp3 survive.
            val audioDir = File(appContext.cacheDir, "audio")
            donationIds.forEach { id ->
                runCatching { SarvamTtsClient.deleteDonationFiles(audioDir, id) }
            }
            receiptPaths.forEach { path ->
                if (path.isNotBlank()) runCatching { File(path).delete() }
            }

            // Step 4: per-event prefs so role/code/sync never resurrect.
            prefs.clearEventPrefs(eventId)

            // Step 5: move off the deleted event.
            if (prefs.currentEventId.value == eventId) {
                val next = database.eventDao().observeEvents().first().firstOrNull()?.id
                prefs.setCurrentEventId(next)
            }
        }.fold(
            onSuccess = { Outcome.Ok(Unit) },
            onFailure = { Outcome.Err(it.message ?: "Could not delete festival.") }
        )
    }
}
