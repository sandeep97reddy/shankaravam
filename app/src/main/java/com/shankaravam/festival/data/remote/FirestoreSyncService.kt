package com.shankaravam.festival.data.remote

import com.shankaravam.festival.core.util.CODE_STATUS_ACTIVE
import com.shankaravam.festival.core.util.CODE_STATUS_CLOSED
import com.shankaravam.festival.core.util.CODE_TTL_MILLIS
import com.shankaravam.festival.core.util.Outcome
import com.shankaravam.festival.core.util.isCodeLive
import com.shankaravam.festival.data.local.AppDatabase
import com.shankaravam.festival.data.local.EventEntity
import com.shankaravam.festival.data.local.SecureKeyStore
import com.shankaravam.festival.data.local.SessionPrefs
import com.shankaravam.festival.domain.model.AdminConfig
import com.shankaravam.festival.domain.model.SyncStatus
import com.shankaravam.festival.domain.model.VoiceEngineMode
import com.shankaravam.festival.domain.model.shouldApplySharedKey
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

/** F3 read fudge: covers inter-device clock skew on push stamps (see [stampForPush]). */
const val SYNC_FUDGE_MILLIS: Long = 120_000L

/** F5 voice auto-pull throttle (owner ruling: 15 min ≈ <1% of Spark quota). */
const val VOICE_PULL_THROTTLE_MILLIS: Long = 15L * 60L * 1000L

/**
 * Gap-2 batch ceiling: Firestore WriteBatch caps at 500 writes. 400 leaves
 * headroom while collapsing dozens of pandal-cellular round trips into one
 * commit per chunk. Pure chunking via Kotlin `chunked`, unit-testable.
 */
const val UPLOAD_BATCH_CHUNK: Int = 400

/**
 * F3 push stamp (pure + unit-tested): offline rows carry stale `updatedAt`
 * that peers' watermarks would skip forever ("worked yesterday, dead today").
 * Stamping `max(local, now)` at cloud entry guarantees every peer syncing
 * after the push downloads them. `createdAt` (audit) is never touched; the
 * stamp is mirrored into Room atomically via `markSynced`.
 */
fun stampForPush(localUpdatedAt: Long, now: Long = System.currentTimeMillis()): Long =
    maxOf(localUpdatedAt, now)

/**
 * F3 sync result: Done (synced), Blocked (pending/revoked/signed-out — show
 * the message, NEVER retry: retrying PERMISSION_DENIED hot-loops the worker),
 * Failed (network/unknown — retry).
 */
sealed interface SyncOutcome {
    data class Done(val result: SyncResult) : SyncOutcome
    data class Blocked(val message: String) : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

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
    private val prefs: SessionPrefs,
    /** F5: auto-pull target. Set post-construction-safe via AppContainer (lazy). */
    private val secureKeys: SecureKeyStore? = null
) {
    private fun events() = Firebase.firestore.collection("events")

    suspend fun syncEvent(eventId: String): SyncOutcome = withContext(Dispatchers.IO) {
        try {
            // Seat-first (F3): learn approval/revoke BEFORE touching ledger —
            // a pending joiner's ledger read throws PERMISSION_DENIED and used
            // to abort the whole sync before the seat refresh below ever ran
            // (approval deadlock: stuck pending forever). Seat MISSING is
            // fine: the head's first sync has no member doc yet and passes via
            // the isEventCreator rules path (creator bootstrap).
            val me = runCatching { Firebase.auth.currentUser }.getOrNull()
                ?: return@withContext SyncOutcome.Blocked("Sign in to sync — entries stay on this device.")
            // Gap-1 gate: unpublished local events never touch cloud, even
            // with global sync ON. Only publishShareCode/requestToJoin set
            // the cloud flag — this preserves explicit-publish and stops
            // both silent auto-publish (creator path) and PERMISSION_DENIED
            // retry churn (offline-created path). Blocked, never Failed.
            if (!prefs.isCloudEvent(eventId)) {
                return@withContext SyncOutcome.Blocked("Publish the invite to sync this festival — entries stay on this device.")
            }
            var seatRole: String? = null
            var seatStatus: String? = null
            var seatApprovedBy = ""
            if (prefs.isCloudEvent(eventId)) {
                val seat = runCatching {
                    events().document(eventId).collection("members").document(me.uid).get().await()
                }.getOrNull()
                val role = seat?.getString("role")
                val status = seat?.getString("status")
                if (role != null && status != null) {
                    seatRole = role
                    seatStatus = status
                    seatApprovedBy = seat.getString("approvedBy") ?: ""
                    prefs.setMyRole(eventId, role)
                    prefs.setMyStatus(eventId, status)
                    if (status == SessionPrefs.STATUS_PENDING) {
                        return@withContext SyncOutcome.Blocked("Waiting for head approval — entries stay on this device until approved.")
                    }
                    if (status == SessionPrefs.STATUS_REVOKED) {
                        return@withContext SyncOutcome.Blocked("Access revoked by the head — changes stay on this device.")
                    }
                }
            }

            var uploaded = 0
            var downloaded = 0
            var conflicts = 0
            val eventRef = events().document(eventId)
            val deviceTag = prefs.deviceId.takeLast(4).uppercase()

            // Gap-2 batched uploads: snapshot pending rows once, precompute
            // push stamps (F3 max(local,now) so peers' watermarks can't skip
            // offline rows), then commit in ≤400-op WriteBatches. One chunk =
            // one round trip instead of one per row on pandal cellular.
            // Room rows are marked SYNCED only AFTER their chunk commits, so
            // a mid-sync failure resumes cleanly (committed chunks stay
            // SYNCED, the rest stay PENDING). Ledger docs use merge (not
            // overwrite) so P4 audioMeta keys stamped via merge-write survive
            // a ledger re-upload. Failure throws to the outer catch → Failed
            // → existing exponential-backoff retry.
            val donationOps = database.donationDao().pendingSyncForEvent(eventId).map { row ->
                val stamp = stampForPush(row.updatedAt)
                Triple(
                    eventRef.collection("donations").document(row.id),
                    FirestoreMappers.donationToMap(row.copy(updatedAt = stamp), deviceTag),
                    Pair(row.id, stamp)
                )
            }
            val expenseOps = database.expenseDao().pendingSyncForEvent(eventId).map { row ->
                val stamp = stampForPush(row.updatedAt)
                Triple(
                    eventRef.collection("expenses").document(row.id),
                    FirestoreMappers.expenseToMap(row.copy(updatedAt = stamp), deviceTag),
                    Pair(row.id, stamp)
                )
            }
            val correctionOps = database.correctionDao().pendingSyncForEvent(eventId).map { row ->
                eventRef.collection("corrections").document(row.id) to
                    FirestoreMappers.correctionToMap(row)
            }
            val headerMap = runCatching {
                database.eventDao().observeEvent(eventId).first()?.let { FirestoreMappers.eventToMap(it) }
            }.getOrNull()
            // Event header commits ALONE and best-effort (pre-batch
            // semantics): a header denial (e.g. viewer role) must never fail
            // the ledger chunks below — ledger failure still throws → Failed
            // → backoff retry, exactly as before.
            if (headerMap != null) {
                runCatching {
                    Firebase.firestore.batch()
                        .set(eventRef, headerMap, SetOptions.merge())
                        .commit().await()
                }
            }
            data class BatchOp(
                val ref: com.google.firebase.firestore.DocumentReference,
                val data: Map<String, Any?>,
                val merge: Boolean,
                val mark: (suspend () -> Unit)? = null
            )
            val ledgerOps = donationOps.map { (ref, map, idStamp) ->
                BatchOp(ref, map, merge = true) {
                    database.donationDao().markSynced(idStamp.first, SyncStatus.SYNCED.name, idStamp.second)
                }
            } + expenseOps.map { (ref, map, idStamp) ->
                BatchOp(ref, map, merge = true) {
                    database.expenseDao().markSynced(idStamp.first, SyncStatus.SYNCED.name, idStamp.second)
                }
            } + correctionOps.map { (ref, map) ->
                val id = ref.id
                BatchOp(ref, map, merge = true) {
                    database.correctionDao().updateSyncState(id, SyncStatus.SYNCED.name)
                }
            }
            ledgerOps.chunked(UPLOAD_BATCH_CHUNK).forEach { chunk ->
                val batch = Firebase.firestore.batch()
                for (op in chunk) {
                    if (op.merge) batch.set(op.ref, op.data, SetOptions.merge())
                    else batch.set(op.ref, op.data)
                }
                batch.commit().await()
                for (op in chunk) {
                    op.mark?.invoke()
                    if (op.mark != null) uploaded++
                }
            }

            // Downloads share one ingest path with the foreground listener.
            val since = (prefs.lastSyncMillis(eventId) - SYNC_FUDGE_MILLIS).coerceAtLeast(0L)
            // Header first (Gap-3): renames/closures must land before ledger
            // rows render. Best-effort — never fails the ledger sync.
            downloaded += runCatching { pullEventHeader(eventRef, eventId) }.getOrDefault(0)
            downloaded += pullDonations(eventRef, eventId, since).also { conflicts += it.second }.first
            downloaded += pullExpenses(eventRef, eventId, since).also { conflicts += it.second }.first
            // Corrections (F3: previously upload-only — peers never got them).
            downloaded += pullCorrections(eventRef, eventId, prefs.lastCorrectionMillis(eventId))

            // Presence: best-effort merge for the known-active seat only.
            // Never fails the ledger sync; never runs from snapshot callbacks.
            if (prefs.isCloudEvent(eventId) && seatRole != null && seatStatus != null) {
                runCatching {
                    eventRef.collection("members").document(me.uid).set(
                        FirestoreMappers.memberToMap(
                            role = seatRole,
                            status = seatStatus,
                            approvedBy = seatApprovedBy,
                            joinedAt = null,
                            email = me.email,
                            displayName = me.displayName,
                            counterName = prefs.syncCounterName(),
                            deviceTag = prefs.deviceId.takeLast(4).uppercase(),
                            lastActiveAt = System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
                }
            }

            prefs.setLastSyncMillis(eventId, System.currentTimeMillis())
            // F5: shared voice follows every successful sync (throttled, silent,
            // never fails the ledger result).
            runCatching { maybeAutoPullVoice() }
            SyncOutcome.Done(SyncResult(uploaded, downloaded, conflicts))
        } catch (e: Exception) {
            SyncOutcome.Failed(e.message ?: "Sync failed. Will retry.")
        }
    }

    /**
     * F5 shared-voice auto-pull (pure decision in [shouldApplySharedKey]).
     * Throttled to [VOICE_PULL_THROTTLE_MILLIS], except when the device holds
     * no key at all (first acquisition is always attempted). Returns whether
     * a new key was applied and whether the cloud publishes any key, so VMs
     * can refresh UI / phrase notices. Never throws.
     */
    data class VoicePullResult(val applied: Boolean, val remotePresent: Boolean)

    suspend fun maybeAutoPullVoice(force: Boolean = false, respectLock: Boolean = true): VoicePullResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val store = secureKeys ?: return@runCatching VoicePullResult(false, false)
                val now = System.currentTimeMillis()
                val localKey = store.getSarvamKey()
                if (!force && localKey.isNotBlank() &&
                    now - prefs.lastVoicePullAt() < VOICE_PULL_THROTTLE_MILLIS
                ) {
                    return@runCatching VoicePullResult(false, true)
                }
                prefs.setLastVoicePullAt(now)
                val remote = when (val r = readTtsKey()) {
                    is Outcome.Ok -> r.value
                    is Outcome.Err -> return@runCatching VoicePullResult(false, false)
                }
                val decision = shouldApplySharedKey(
                    localKey, remote.first, prefs.voiceOfflineLocked, respectLock
                )
                if (!decision.applyKey) return@runCatching VoicePullResult(false, true)
                store.setSarvamKey(remote.first)
                prefs.sarvamSpeaker = remote.second
                if (decision.flipToCloud) prefs.voiceEngineMode = VoiceEngineMode.SARVAM_CLOUD
                prefs.setLastVoiceSyncAt(now)
                VoicePullResult(true, true)
            }.getOrDefault(VoicePullResult(false, false))
        }

    private suspend fun pullDonations(
        eventRef: com.google.firebase.firestore.DocumentReference,
        eventId: String,
        since: Long
    ): Pair<Int, Int> = ingestDonationDocs(
        eventId,
        eventRef.collection("donations").whereGreaterThan("updatedAt", since).get().await().documents
    )

    private suspend fun pullExpenses(
        eventRef: com.google.firebase.firestore.DocumentReference,
        eventId: String,
        since: Long
    ): Pair<Int, Int> = ingestExpenseDocs(
        eventId,
        eventRef.collection("expenses").whereGreaterThan("updatedAt", since).get().await().documents
    )

    private suspend fun pullCorrections(
        eventRef: com.google.firebase.firestore.DocumentReference,
        eventId: String,
        since: Long
    ): Int = ingestCorrectionDocs(
        eventId,
        eventRef.collection("corrections").whereGreaterThan("createdAt", since).get().await().documents
    )

    /**
     * Gap-3 header pull (periodic/immediate path): fetches the single event
     * doc and funnels through [ingestEventHeader]. Returns 1 when applied,
     * 0 otherwise. Never throws — callers wrap in runCatching anyway.
     */
    private suspend fun pullEventHeader(
        eventRef: com.google.firebase.firestore.DocumentReference,
        eventId: String
    ): Int {
        val data = eventRef.get().await().data ?: return 0
        return if (ingestEventHeader(eventId, data)) 1 else 0
    }

    /**
     * Gap-3 shared header ingest: identical newer-wins rule for the periodic
     * pull and the foreground listener. Header conflicts resolve by
     * `updatedAt` (last-writer-wins is acceptable for a name/status rename —
     * money rows keep their CONFLICT flow, untouched). Local-only columns
     * (`creatorId`, `deviceId`, `defaultLanguage`) that [FirestoreMappers]
     * zeroes on download are preserved; `version` never rewinds. Returns
     * true when Room was updated. Never throws.
     */
    suspend fun ingestEventHeader(eventId: String, data: Map<String, Any?>): Boolean {
        val remote = FirestoreMappers.eventFromMap(eventId, data) ?: return false
        val local = database.eventDao().observeEvent(eventId).first()
        if (local == null) {
            database.eventDao().upsert(remote)
            return true
        }
        if (remote.updatedAt <= local.updatedAt) return false
        database.eventDao().upsert(
            remote.copy(
                creatorId = local.creatorId,
                deviceId = local.deviceId,
                defaultLanguage = local.defaultLanguage,
                version = maxOf(local.version, remote.version)
            )
        )
        return true
    }

    /**
     * F3 shared ingest: identical upsert/conflict rules for the periodic pull
     * and the foreground listener. Advances the ledger watermark past
     * everything seen (monotonic — re-deliveries are idempotent no-ops).
     * Returns (downloaded, conflicts).
     */
    suspend fun ingestDonationDocs(
        eventId: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): Pair<Int, Int> {
        var downloaded = 0
        var conflicts = 0
        var maxSeen = prefs.lastSyncMillis(eventId)
        for (doc in docs) {
            val data = doc.data ?: continue
            val remote = FirestoreMappers.donationFromMap(doc.id, eventId, data) ?: continue
            if (remote.updatedAt > maxSeen) maxSeen = remote.updatedAt
            val local = database.donationDao().observeById(doc.id).first()
            if (local == null) {
                database.donationDao().upsert(remote)
                downloaded++
            } else if (FirestoreMappers.isRemoteNewer(local.updatedAt, local.version, remote.updatedAt, remote.version)) {
                database.donationDao().updateSyncState(local.id, SyncStatus.CONFLICT.name)
                conflicts++
            }
        }
        if (maxSeen > prefs.lastSyncMillis(eventId)) prefs.setLastSyncMillis(eventId, maxSeen)
        return downloaded to conflicts
    }

    /** F3 shared ingest for expenses (see [ingestDonationDocs]). */
    suspend fun ingestExpenseDocs(
        eventId: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): Pair<Int, Int> {
        var downloaded = 0
        var conflicts = 0
        var maxSeen = prefs.lastSyncMillis(eventId)
        for (doc in docs) {
            val data = doc.data ?: continue
            val remote = FirestoreMappers.expenseFromMap(doc.id, eventId, data) ?: continue
            if (remote.updatedAt > maxSeen) maxSeen = remote.updatedAt
            val local = database.expenseDao().observeById(doc.id).first()
            if (local == null) {
                database.expenseDao().upsert(remote)
                downloaded++
            } else {
                // Phase-3 receipts: attachments merge independently of ledger
                // versioning. A newer receiptUrl is adopted even when the
                // money row is identical (versions equal → isRemoteNewer is
                // false and would drop it), and a concurrent money edit still
                // flows through the CONFLICT path below untouched.
                if (remote.receiptUrl != null && remote.receiptUrl != local.receiptUrl) {
                    database.expenseDao().applyRemoteReceiptUrl(local.id, remote.receiptUrl)
                    downloaded++
                }
                if (FirestoreMappers.isRemoteNewer(local.updatedAt, local.version, remote.updatedAt, remote.version)) {
                    database.expenseDao().updateSyncState(local.id, SyncStatus.CONFLICT.name)
                    conflicts++
                }
            }
        }
        if (maxSeen > prefs.lastSyncMillis(eventId)) prefs.setLastSyncMillis(eventId, maxSeen)
        return downloaded to conflicts
    }

    /**
     * F3 corrections ingest (createdAt cursor — correction docs carry no
     * updatedAt). Set-by-id is idempotent; existing rows are never touched.
     * Returns the downloaded count.
     */
    suspend fun ingestCorrectionDocs(
        eventId: String,
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): Int {
        var downloaded = 0
        var maxSeen = prefs.lastCorrectionMillis(eventId)
        for (doc in docs) {
            val data = doc.data ?: continue
            val remote = FirestoreMappers.correctionFromMap(doc.id, eventId, data) ?: continue
            if (remote.createdAt > maxSeen) maxSeen = remote.createdAt
            if (database.correctionDao().countById(doc.id) == 0) {
                database.correctionDao().insert(remote)
                downloaded++
            }
        }
        prefs.setLastCorrectionMillis(eventId, maxSeen)
        return downloaded
    }

    // ---- sharing / membership ----

    /**
     * Publish the invite code (plan §7): code doc + event header + own head seat.
     * Each (re)publish stamps a fresh 10-day window ([CODE_TTL_MILLIS]) and
     * re-opens a previously closed code — republishing IS the re-open path.
     * No rules change needed: create/update already pass for the creator.
     */
    suspend fun publishShareCode(
        eventId: String,
        code: String,
        eventName: String,
        uid: String,
        email: String? = null,
        displayName: String? = null
    ): Outcome<Unit> =
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
                            // Timestamp (not millis-Long) so firestore.rules
                            // can compare expiry against request.time
                            // server-side; readers accept legacy Longs too.
                            "expiresAt" to com.google.firebase.Timestamp(
                                java.util.Date(now + CODE_TTL_MILLIS)
                            ),
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
                            joinedAt = existingJoinedAt ?: System.currentTimeMillis(),
                            email = email,
                            displayName = displayName,
                            counterName = prefs.syncCounterName(),
                            deviceTag = prefs.deviceId.takeLast(4).uppercase(),
                            lastActiveAt = now
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
                // for cooperative volunteers — AND server-side via the viaCode
                // stamp below (rules re-verify liveness; direct member writes
                // with a dead/forged code are denied). Pre-expiry codes lack
                // both fields and grandfather as live (see isCodeLive).
                // expiresAt travels as Timestamp now, Long on legacy codes.
                val expiresAt = runCatching {
                    codeDoc.getTimestamp("expiresAt")?.toDate()?.time
                }.getOrNull() ?: codeDoc.getLong("expiresAt")
                if (!isCodeLive(codeDoc.getString("status"), expiresAt, System.currentTimeMillis())) {
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
                // nor wipe presence written by another device. viaCode binds
                // the seat to this invite for the server-side liveness check.
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
                        counterName = prefs.syncCounterName(),
                        deviceTag = prefs.deviceId.takeLast(4).uppercase(),
                        lastActiveAt = now,
                        viaCode = normalized
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
                val inRoom = runCatching { database.eventDao().observeEvent(eventId).first() }.getOrNull()
                if (inRoom == null) {
                    val fallbackName = codeDoc.getString("eventName")?.takeIf { it.isNotBlank() } ?: "Festival"
                    database.eventDao().upsert(
                        EventEntity(
                            id = eventId,
                            name = fallbackName,
                            templeName = "",
                            location = "",
                            startDateMillis = now,
                            endDateMillis = null,
                            defaultLanguage = "te",
                            status = "ACTIVE",
                            globalHeadId = "",
                            creatorId = "",
                            deviceId = "",
                            createdAt = now,
                            updatedAt = now,
                            version = 1L,
                            syncStatus = "PENDING"
                        )
                    )
                }
                prefs.setCurrentEventId(eventId)
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
            val speaker = doc.getString("defaultSpeaker") ?: "shubh"
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
