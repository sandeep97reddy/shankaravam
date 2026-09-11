package com.durgamma.festival.data.remote

import com.durgamma.festival.core.util.Outcome
import com.durgamma.festival.data.local.AppDatabase
import com.durgamma.festival.data.local.SessionPrefs
import com.durgamma.festival.domain.model.SyncStatus
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class SyncResult(val uploaded: Int, val downloaded: Int, val conflicts: Int)

data class CloudMember(
    val userId: String,
    val role: String,
    val status: String,
    val approvedBy: String = "",
    val joinedAt: Long = 0L
)

/**
 * Delta sync only (Rule #2): uploads rows still marked LOCAL_ONLY /
 * PENDING_UPLOAD / SYNC_FAILED, downloads rows newer than the last sync.
 * Corrections and activities are append-only (set-by-id = idempotent).
 * Audio, receipts-bytes and TTS keys never enter money collections.
 */
class FirestoreSyncService(
    private val database: AppDatabase,
    private val prefs: SessionPrefs
) {
    private fun events() = Firebase.firestore.collection("events")

    suspend fun syncEvent(eventId: String): Outcome<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            var uploaded = 0
            var downloaded = 0
            var conflicts = 0
            val eventRef = events().document(eventId)

            database.donationDao().pendingSync().forEach { row ->
                eventRef.collection("donations").document(row.id)
                    .set(FirestoreMappers.donationToMap(row)).await()
                database.donationDao().updateSyncState(row.id, SyncStatus.SYNCED.name)
                uploaded++
            }
            database.expenseDao().pendingSync().forEach { row ->
                eventRef.collection("expenses").document(row.id)
                    .set(FirestoreMappers.expenseToMap(row)).await()
                database.expenseDao().updateSyncState(row.id, SyncStatus.SYNCED.name)
                uploaded++
            }
            database.correctionDao().pendingSync().forEach { row ->
                eventRef.collection("corrections").document(row.id)
                    .set(FirestoreMappers.correctionToMap(row)).await()
                database.correctionDao().updateSyncState(row.id, SyncStatus.SYNCED.name)
                uploaded++
            }

            val since = prefs.lastSyncMillis(eventId)
            downloaded += pullDonations(eventRef, eventId, since).also { conflicts += it.second }.first
            downloaded += pullExpenses(eventRef, eventId, since).also { conflicts += it.second }.first

            prefs.setLastSyncMillis(eventId, System.currentTimeMillis())
            SyncResult(uploaded, downloaded, conflicts)
        }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { Outcome.Err(it.message ?: "Sync failed. Will retry.") }
        )
    }

    private suspend fun pullDonations(
        eventRef: com.google.firebase.firestore.DocumentReference,
        eventId: String,
        since: Long
    ): Pair<Int, Int> {
        var downloaded = 0
        var conflicts = 0
        val snapshot = eventRef.collection("donations")
            .whereGreaterThan("updatedAt", since)
            .get().await()
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val remote = FirestoreMappers.donationFromMap(doc.id, eventId, data) ?: continue
            val local = database.donationDao().observeById(doc.id).first()
            if (local == null) {
                database.donationDao().upsert(remote)
                downloaded++
            } else if (FirestoreMappers.isRemoteNewer(local.updatedAt, local.version, remote.updatedAt, remote.version)) {
                database.donationDao().updateSyncState(local.id, SyncStatus.CONFLICT.name)
                conflicts++
            }
        }
        return downloaded to conflicts
    }

    private suspend fun pullExpenses(
        eventRef: com.google.firebase.firestore.DocumentReference,
        eventId: String,
        since: Long
    ): Pair<Int, Int> {
        var downloaded = 0
        var conflicts = 0
        val snapshot = eventRef.collection("expenses")
            .whereGreaterThan("updatedAt", since)
            .get().await()
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val remote = FirestoreMappers.expenseFromMap(doc.id, eventId, data) ?: continue
            val local = database.expenseDao().observeById(doc.id).first()
            if (local == null) {
                database.expenseDao().upsert(remote)
                downloaded++
            } else if (FirestoreMappers.isRemoteNewer(local.updatedAt, local.version, remote.updatedAt, remote.version)) {
                database.expenseDao().updateSyncState(local.id, SyncStatus.CONFLICT.name)
                conflicts++
            }
        }
        return downloaded to conflicts
    }

    // ---- sharing / membership ----

    /** Publish the invite code (plan §7): code doc + event header for joiners. */
    suspend fun publishShareCode(eventId: String, code: String, eventName: String, uid: String): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fs: FirebaseFirestore = Firebase.firestore
                fs.collection("codes").document(code.uppercase())
                    .set(mapOf("eventId" to eventId, "eventName" to eventName, "createdBy" to uid)).await()
                prefs.putShareCode(eventId, code.uppercase())
                prefs.putShareCodeReverse(code.uppercase(), eventId)
            }.fold(
                onSuccess = { Outcome.Ok(Unit) },
                onFailure = { Outcome.Err(it.message ?: "Could not publish invite.") }
            )
        }

    /** Join by typed code: resolves the event, then files a pending membership. */
    suspend fun requestToJoin(code: String, uid: String): Outcome<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = code.trim().uppercase()
                val codeDoc = Firebase.firestore.collection("codes").document(normalized).get().await()
                val eventId = codeDoc.getString("eventId")
                    ?: throw IllegalStateException("Invite code not found.")
                Firebase.firestore.collection("events").document(eventId)
                    .collection("members").document(uid)
                    .set(
                        FirestoreMappers.memberToMap(
                            role = "member", status = "pending",
                            approvedBy = "", joinedAt = System.currentTimeMillis()
                        )
                    ).await()
                prefs.putShareCodeReverse(normalized, eventId)
                eventId
            }.fold(
                onSuccess = { Outcome.Ok(it) },
                onFailure = { Outcome.Err(it.message ?: "Could not join.") }
            )
        }

    suspend fun pendingMembers(eventId: String): Outcome<List<CloudMember>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Firebase.firestore.collection("events").document(eventId)
                    .collection("members").whereEqualTo("status", "pending")
                    .orderBy("joinedAt", Query.Direction.ASCENDING)
                    .get().await().documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        CloudMember(
                            userId = doc.id,
                            role = data["role"] as? String ?: "member",
                            status = "pending"
                        )
                    }
            }.fold(
                onSuccess = { Outcome.Ok(it) },
                onFailure = { Outcome.Err(it.message ?: "Could not load requests.") }
            )
        }

    suspend fun setMember(
        eventId: String,
        userId: String,
        role: String,
        status: String,
        approvedBy: String
    ): Outcome<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Firebase.firestore.collection("events").document(eventId)
                .collection("members").document(userId)
                .set(
                    FirestoreMappers.memberToMap(
                        role = role, status = status,
                        approvedBy = approvedBy, joinedAt = System.currentTimeMillis()
                    )
                ).await()
            if (status == "active") {
                // Best-effort role refresh for our own row.
                runCatching {
                    val me = Firebase.firestore.collection("events").document(eventId)
                        .collection("members").document(userId).get().await()
                    val myRole = me.getString("role") ?: role
                    prefs.setMyRole(eventId, myRole)
                }
            }
        }.fold(
            onSuccess = { Outcome.Ok(Unit) },
            onFailure = { Outcome.Err(it.message ?: "Could not update member.") }
        )
    }

    /** Global-head-only TTS key sync (plan §13): read for all, write for head. */
    suspend fun readTtsKey(): Outcome<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Firebase.firestore.collection("config").document("tts_settings").get().await()
            val key = doc.getString("sarvamApiKey") ?: ""
            val speaker = doc.getString("defaultSpeaker") ?: "meera"
            key to speaker
        }.fold(
            onSuccess = { Outcome.Ok(it) },
            onFailure = { Outcome.Err(it.message ?: "Could not read voice settings.") }
        )
    }

    suspend fun writeTtsKey(apiKey: String, speaker: String, updatedBy: String): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Firebase.firestore.collection("config").document("tts_settings")
                    .set(
                        mapOf(
                            "sarvamApiKey" to apiKey,
                            "defaultSpeaker" to speaker,
                            "updatedAt" to System.currentTimeMillis(),
                            "updatedBy" to updatedBy
                        )
                    ).await()
            }.fold(
                onSuccess = { Outcome.Ok(Unit) },
                onFailure = { Outcome.Err(it.message ?: "Could not save voice settings.") }
            )
        }
}
