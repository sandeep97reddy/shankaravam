package com.shankaravam.festival.data.remote

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.shankaravam.festival.data.local.SessionPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * F3 lifecycle-aware foreground sync (the freshness leg; the 15-min
 * WorkManager stays as the background backstop).
 *
 * While the app is foregrounded AND the current event is cloud-joined AND a
 * user is signed in AND sync is on, three ledger listeners
 * (donations/expenses/corrections, all watermark-filtered) push deltas into
 * Room in ~1s, plus an own-seat listener that learns approve/revoke
 * instantly (the seat-first deadlock fix, live). Everything detaches on
 * background — zero radio, zero reads while away.
 *
 * Cost control: listeners are billed per changed doc only; idle holds are
 * free. Presence writes stay on the periodic path — never from snapshot
 * callbacks (that would be a write loop).
 */
class ForegroundSyncManager(
    private val prefs: SessionPrefs,
    private val auth: AuthRepository,
    private val syncService: FirestoreSyncService
) {
    data class SeatView(val eventId: String, val role: String, val status: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _seat = MutableStateFlow<SeatView?>(null)
    val seat: StateFlow<SeatView?> = _seat.asStateFlow()

    @Volatile private var foreground = false
    private var attachedEvent: String? = null
    private var seatReg: ListenerRegistration? = null
    private var ledgerRegs = listOf<ListenerRegistration>()
    private var gateJob: Job? = null

    fun onAppForeground() {
        foreground = true
        ensureGate()
        reevaluate()
    }

    fun onAppBackground() {
        foreground = false
        detach()
    }

    private fun ensureGate() {
        if (gateJob?.isActive == true) return
        gateJob = scope.launch {
            combine(
                prefs.currentEventId,
                auth.user,
                prefs.cloudSyncEnabledFlow
            ) { eventId, user, enabled -> Triple(eventId, user?.uid, enabled) }
                .collect { reevaluate() }
        }
    }

    private fun reevaluate() {
        if (!foreground) {
            detach()
            return
        }
        val eventId = prefs.currentEventId.value
        val uid = auth.user.value?.uid
        if (eventId == null || uid == null || !prefs.cloudSyncEnabled || !prefs.isCloudEvent(eventId)) {
            detach()
            return
        }
        if (attachedEvent == eventId) return
        detach()
        attach(eventId, uid)
    }

    private fun attach(eventId: String, uid: String) {
        runCatching {
            val eventRef = Firebase.firestore.collection("events").document(eventId)
            // Own seat first: approve/revoke lands here instantly.
            seatReg = eventRef.collection("members").document(uid)
                .addSnapshotListener { snap, _ ->
                    val role = snap?.getString("role")
                    val status = snap?.getString("status")
                    if (role != null && status != null) {
                        prefs.setMyRole(eventId, role)
                        prefs.setMyStatus(eventId, status)
                        _seat.value = SeatView(eventId, role, status)
                        if (status != SessionPrefs.STATUS_ACTIVE) detachLedger()
                    }
                }
            val since = (prefs.lastSyncMillis(eventId) - SYNC_FUDGE_MILLIS).coerceAtLeast(0L)
            ledgerRegs += eventRef.collection("donations")
                .whereGreaterThan("updatedAt", since)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        onLedgerError(eventId, err)
                        return@addSnapshotListener
                    }
                    val docs = snap?.documents ?: return@addSnapshotListener
                    scope.launch { runCatching { syncService.ingestDonationDocs(eventId, docs) } }
                }
            ledgerRegs += eventRef.collection("expenses")
                .whereGreaterThan("updatedAt", since)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        onLedgerError(eventId, err)
                        return@addSnapshotListener
                    }
                    val docs = snap?.documents ?: return@addSnapshotListener
                    scope.launch { runCatching { syncService.ingestExpenseDocs(eventId, docs) } }
                }
            ledgerRegs += eventRef.collection("corrections")
                .whereGreaterThan("createdAt", prefs.lastCorrectionMillis(eventId))
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        onLedgerError(eventId, err)
                        return@addSnapshotListener
                    }
                    val docs = snap?.documents ?: return@addSnapshotListener
                    scope.launch { runCatching { syncService.ingestCorrectionDocs(eventId, docs) } }
                }
            attachedEvent = eventId
        }.onFailure {
            detach()
        }
    }

    /**
     * A denied ledger stream means our seat changed (revoke) or never
     * activated — drop the ledgers and let one seat-first syncEvent learn the
     * truth (its Blocked copy reaches the UI; no retry storm).
     */
    private fun onLedgerError(eventId: String, err: FirebaseFirestoreException) {
        if (err.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return
        detachLedger()
        scope.launch { runCatching { syncService.syncEvent(eventId) } }
    }

    private fun detachLedger() {
        ledgerRegs.forEach { runCatching { it.remove() } }
        ledgerRegs = emptyList()
    }

    private fun detach() {
        detachLedger()
        runCatching { seatReg?.remove() }
        seatReg = null
        attachedEvent = null
        _seat.value = null
    }
}
