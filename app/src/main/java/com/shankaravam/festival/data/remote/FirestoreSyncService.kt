package com.shankaravam.festival.data.remote

import com.shankaravam.festival.core.util.CODE_STATUS_ACTIVE
import com.shankaravam.festival.core.util.CODE_STATUS_CLOSED
import com.shankaravam.festival.core.util.CODE_TTL_MILLIS
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.isCodeLive
import com.shankaravam.festival.data.local.AppDatabase
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.domain.model.AdminConfig
import com.shankaravam.festival.domain.model.SyncStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class SyncResult(val uploaded: Int, val downloaded: Int, val conflicts: Int)

/**
 * Team-directory row (ADMIN_HEAD_PLAN S2.2). Identity/presence fields are
 * display-only — the security boundary is firestore.rules, never these.
 * `deviceTag` is the last-4 install tag; the full UUID never leaves Room.
 */
data class CloudMember(
    val userId: String,
    val role: String,
    val status: String,
    val approvedBy: String = "",
    val joinedAt: Long = 0L,
    val email: String? = null,
    val displayName: String? = null,
    val counterName: String? = null,
    val deviceTag: String? = null,
    val lastActiveAt: Long = 0L
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

            // Event header first: joiners resolve codes to an id, but the doc
            // itself was never uploaded — Device B saw an empty dashboard.
            runCatching {
                val local = database.eventDao().observeEvent(eventId).first()
                if (local != null) {
                    eventRef.set(FirestoreMappers.eventToMap(local)).await()
                }
            }

            database.donationDao().pendingSync().forEach { row ->
                eventRef.collection("donations").document(row.id)
                    .set(FirestoreMappers.donationToMap(row, prefs.deviceId)).await()
                database.donationDao().updateSyncState(row.id, SyncStatus.SYNCED.name)
                uploaded++
            }
            database.expenseDao().pendingSync().forEach { row ->
                eventRef.collection("expenses").document(row.id)
                    .set(FirestoreMappers.expenseToMap(row, prefs.deviceId)).await()
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

            // S3.2/S3.5 presence + own-status refresh: best-effort, NEVER fails
            // the ledger sync. One merge-write per sync per device (Spark-safe);
            // signed-out / never-joined devices skip silently. Role/status are
            // echoed back verbatim so the S1 self-touch rule (equality-pinned)
            // always holds; the read also enforces revoke/approve locally.
            if (prefs.isCloudEvent(eventId)) {
                runCatching {
                    val me = Firebase.auth.currentUser ?: return@runCatching
                    val seatRef = eventRef.collection("members").document(me.uid)
                    val snap = seatRef.get().await()
                    val curRole = snap.getString("role") ?: return@runCatching
                    val curStatus = snap.getString("status") ?: return@runCatching
                    prefs.setMyRole(eventId, curRole)
                    prefs.setMyStatus(eventId, curStatus)
                    seatRef.set(
                        FirestoreMappers.memberToMap(
                            role = curRole,
                            status = curStatus,
                            approvedBy = snap.getString("approvedBy") ?: "",
                            joinedAt = null,
                            email = me.email,
                            displayName = me.displayName,
                            counterName = prefs.rawCounterName(),
                            deviceTag = prefs.deviceId.takeLast(4).uppercase(),
                            lastActiveAt = System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }

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

    /**
     * Publish the invite code (plan §7): code doc + event header + own head seat.
     * Each (re)publish stamps a fresh 10-day window ([CODE_TTL_MILLIS]) and
     * re-opens a previously closed code — republishing IS the re-open path.
     * No rules change needed: create/update already pass for the creator.
     */
    suspend fun publishShareCode(eventId: String, code: String, eventName: String, uid: String): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fs: FirebaseFirestore = Firebase.firestore
                val now = System.currentTimeMillis()
                fs.collection("codes").document(code.uppercase())
                    .set(
                        mapOf(
                            "eventId" to eventId,
                            "eventName" to eventName,
                            "createdBy" to uid,
                            "createdAt" to now,
                            "expiresAt" to now + CODE_TTL_MILLIS,
                            "status" to CODE_STATUS_ACTIVE
                        )
                    ).await()
                // Header so joiners can pull the event into their Room.
                runCatching {
                    val local = database.eventDao().observeEvent(eventId).first()
                    if (local != null) {
                        fs.collection("events").document(eventId)
                            .set(FirestoreMappers.eventToMap(local)).await()
                    }
                }
                // Own head seat: rules only let the creator (globalHeadId)
                // self-register as active/global_head — this unblocks isCollector.
                // Merge-write preserves identity/presence keys (S2.5); the
                // original joinedAt survives republishes so roster order holds.
                runCatching {
                    val seatRef = fs.collection("events").document(eventId)
                        .collection("members").document(uid)
                    val existingJoinedAt =
                        runCatching { seatRef.get().await().getLong("joinedAt") }.getOrNull()
                    seatRef.set(
                        FirestoreMappers.memberToMap(
                            role = "global_head", status = "active",
                            approvedBy = uid,
                            joinedAt = existingJoinedAt ?: System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
                }
                prefs.setMyRole(eventId, SessionPrefs.ROLE_GLOBAL_HEAD)
                prefs.setMyStatus(eventId, SessionPrefs.STATUS_ACTIVE)
                prefs.markCloudEvent(eventId)
                prefs.putShareCode(eventId, code.uppercase())
                prefs.putShareCodeReverse(code.uppercase(), eventId)
            }.fold(
                onSuccess = { Outcome.Ok(Unit) },
                onFailure = { Outcome.Err(it.message ?: "Could not publish invite.") }
            )
        }

    /**
     * Close an invite code (feature #2): flips status to closed so
     * [requestToJoin] rejects it. Rules already allow this for the creator
     * or the master admin — the VM gates the button to the head. Merge-write
     * preserves eventId/createdBy/expiry for audit.
     */
    suspend fun closeShareCode(code: String): Outcome<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Firebase.firestore.collection("codes").document(code.trim().uppercase())
                    .set(mapOf("status" to CODE_STATUS_CLOSED), SetOptions.merge()).await()
            }.fold(
                onSuccess = { Outcome.Ok(Unit) },
                onFailure = { Outcome.Err(it.message ?: "Could not close invite.") }
            )
        }

    /**
     * Join by typed code (S3.1): file membership FIRST, then pull the header.
     * Identity is stamped for the team directory (tag-only device id); the
     * whitelisted admin auto-activates as head via the S1 rules clause.
     * Header pull is best-effort — a second Google account cannot read it
     * until approved — so it must never fail the join; current-event
     * selection only moves when Room actually holds the event.
     */
    suspend fun requestToJoin(
        code: String,
        uid: String,
        email: String? = null,
        displayName: String? = null
    ): Outcome<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = code.trim().uppercase()
                val codeDoc = Firebase.firestore.collection("codes").document(normalized).get().await()
                val eventId = codeDoc.getString("eventId")
                    ?: throw IllegalStateException("Invite code not found.")
                // Expiry/closure gate (feature #2): enforced here, client-side,
                // for cooperative volunteers — the codes rules intentionally stay
                // untouched (no redeploy). Pre-expiry codes lack both fields and
                // grandfather as live (see isCodeLive).
                if (!isCodeLive(codeDoc.getString("status"), codeDoc.getLong("expiresAt"), System.currentTimeMillis())) {
                    throw IllegalStateException("Invite code has expired or was closed by the head.")
                }
                prefs.markCloudEvent(eventId)

                // Identity: explicit params win, else the live auth user.
                // Full deviceIds never leave the device — last-4 tag only.
                val authUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                val effectiveEmail = email ?: authUser?.email
                val effectiveName = displayName ?: authUser?.displayName
                val now = System.currentTimeMillis()
                val isAdmin = AdminConfig.isGlobalHeadEmail(effectiveEmail)

                // Membership first: self-create is allowed for pending/member
                // and (S1 clause) for the whitelisted admin as active/head.
                // Merge-write + preserved joinedAt: rejoins never rewind order
                // nor wipe presence written by another device.
                val seatRef = Firebase.firestore.collection("events").document(eventId)
                    .collection("members").document(uid)
                val existingJoinedAt =
                    runCatching { seatRef.get().await().getLong("joinedAt") }.getOrNull()
                seatRef.set(
                    FirestoreMappers.memberToMap(
                        role = if (isAdmin) SessionPrefs.ROLE_GLOBAL_HEAD else SessionPrefs.ROLE_MEMBER,
                        status = if (isAdmin) SessionPrefs.STATUS_ACTIVE else SessionPrefs.STATUS_PENDING,
                        approvedBy = if (isAdmin) uid else "",
                        joinedAt = existingJoinedAt ?: now,
                        email = effectiveEmail,
                        displayName = effectiveName,
                        counterName = prefs.rawCounterName(),
                        deviceTag = prefs.deviceId.takeLast(4).uppercase(),
                        lastActiveAt = now
                    ),
                    SetOptions.merge()
                ).await()

                if (isAdmin) {
                    prefs.setMyRole(eventId, SessionPrefs.ROLE_GLOBAL_HEAD)
                    prefs.setMyStatus(eventId, SessionPrefs.STATUS_ACTIVE)
                } else {
                    prefs.setMyRole(eventId, SessionPrefs.ROLE_MEMBER)
                    prefs.setMyStatus(eventId, SessionPrefs.STATUS_PENDING)
                }

                // Header into Room so the dashboard leaves its empty state.
                runCatching {
                    val header = Firebase.firestore.collection("events")
                        .document(eventId).get().await()
                    header.data?.let { FirestoreMappers.eventFromMap(eventId, it) }?.let {
                        database.eventDao().upsert(it)
                    }
                }
                if (runCatching { database.eventDao().observeEvent(eventId).first() }.getOrNull() != null) {
                    prefs.setCurrentEventId(eventId)
                }
                prefs.putShareCodeReverse(normalized, eventId)
                eventId
            }.fold(
                onSuccess = { Outcome.Ok(it) },
                onFailure = { Outcome.Err(it.message ?: "Could not join.") }
            )
        }

    /**
     * Full roster, join-ordered (S3.3). Single orderBy, no where-clause — so
     * no composite index is needed (the old pending-only query required one
     * and is deleted). Pending views partition client-side. Head-gated by
     * callers; volunteers never invoke it (Rule #1).
     */
    suspend fun fetchAllMembers(eventId: String): Outcome<List<CloudMember>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Firebase.firestore.collection("events").document(eventId)
                    .collection("members").orderBy("joinedAt", Query.Direction.ASCENDING)
                    .get().await().documents.map { doc ->
                        FirestoreMappers.memberFromMap(doc.id, doc.data ?: emptyMap())
                    }
            }.fold(
                onSuccess = { Outcome.Ok(it) },
                onFailure = { Outcome.Err(it.message ?: "Could not load team.") }
            )
        }

    /**
     * Unified role/status writer (S3.4). Contract (ADMIN_HEAD_PLAN App.A):
     * Collector=(organizer,active), Viewer=(member,active),
     * Revoke=(unchanged role,revoked). Merge-write, joinedAt never touched.
     * Collectors may only approve pending→active non-head; anything beyond
     * is denied server-side (S1 rules) and surfaces here as Err.
     */
    suspend fun setMemberRole(
        eventId: String,
        userId: String,
        role: String,
        status: String,
        approvedBy: String
    ): Outcome<Unit> = withContext(Dispatchers.IO) {
        val cleanRole = role.trim().lowercase()
        val cleanStatus = status.trim().lowercase()
        if (cleanRole != SessionPrefs.ROLE_GLOBAL_HEAD
            && cleanRole != SessionPrefs.ROLE_ORGANIZER
            && cleanRole != SessionPrefs.ROLE_MEMBER
        ) {
            return@withContext Outcome.Err("Unknown role: $role")
        }
        if (cleanStatus != SessionPrefs.STATUS_ACTIVE
            && cleanStatus != SessionPrefs.STATUS_PENDING
            && cleanStatus != SessionPrefs.STATUS_REVOKED
        ) {
            return@withContext Outcome.Err("Unknown status: $status")
        }
        runCatching {
            Firebase.firestore.collection("events").document(eventId)
                .collection("members").document(userId)
                .set(
                    FirestoreMappers.memberToMap(
                        role = cleanRole, status = cleanStatus,
                        approvedBy = approvedBy, joinedAt = null
                    ),
                    SetOptions.merge()
                ).await()
            // Best-effort role + status refresh for our own row.
            runCatching {
                val me = Firebase.firestore.collection("events").document(eventId)
                    .collection("members").document(userId).get().await()
                prefs.setMyRole(eventId, me.getString("role") ?: cleanRole)
                prefs.setMyStatus(eventId, me.getString("status") ?: cleanStatus)
            }
        }.fold(
            onSuccess = { Outcome.Ok(Unit) },
            onFailure = { Outcome.Err(it.message ?: "Could not update member.") }
        )
    }

    /**
     * P4 re-fetch guard: which exact rendering (hash of text+language+speaker
     * +roster) was cloud-generated, when, and by whom. Firestore-only —
     * never Room (no migration). Merge-write touches only these keys so
     * updatedAt/version (and sync comparisons) never see it as a ledger edit.
     */
    data class AudioMeta(val hash: String, val generatedAt: Long, val generatedBy: String)

    suspend fun readAudioMeta(eventId: String, donationId: String): AudioMeta? =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = events().document(eventId)
                    .collection("donations").document(donationId).get().await()
                val hash = doc.getString("audioHash") ?: return@runCatching null
                AudioMeta(
                    hash = hash,
                    generatedAt = doc.getLong("audioGeneratedAt") ?: 0L,
                    generatedBy = doc.getString("audioGeneratedBy") ?: ""
                )
            }.getOrNull()
        }

    /** Best-effort stamp after a successful generation. Never throws. */
    suspend fun stampAudioMeta(eventId: String, donationId: String, meta: AudioMeta) {
        runCatching {
            events().document(eventId).collection("donations").document(donationId)
                .set(
                    mapOf(
                        "audioHash" to meta.hash,
                        "audioGeneratedAt" to meta.generatedAt,
                        "audioGeneratedBy" to meta.generatedBy
                    ),
                    SetOptions.merge()
                ).await()
        }
    }

    /** Global-head-only TTS key sync (plan §13): read for all, write for head. */
    suspend fun readTtsKey(): Outcome<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = Firebase.firestore.collection("config").document("tts_settings").get().await()
            val key = doc.getString("sarvamApiKey") ?: ""
            val speaker = doc.getString("defaultSpeaker") ?: "priya"
            key to com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(speaker)
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
