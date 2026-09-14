package com.shankaravam.festival.data.remote

import com.shankaravam.festival.data.local.DonationEntity
import com.shankaravam.festival.data.local.ExpenseEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirestoreMappersTest {

    private fun donation() = DonationEntity(
        id = "d1", eventId = "e1", donorName = "Ramesh",
        pronunciationText = "రమేష్", amount = 5000.0, currency = "INR",
        isNonCash = false, itemDescription = null, quantity = null, unit = null,
        paymentMethod = "UPI", tags = listOf("Cash", "Sponsor"),
        status = "CONFIRMED", announcementEnabled = true,
        audioStatus = "READY", notes = "local only",
        addedBy = "collector", addedTime = 1000L,
        createdAt = 1000L, updatedAt = 2000L, version = 3L,
        syncStatus = "PENDING_UPLOAD"
    )

    @Test
    fun donation_round_trip_preserves_ledger_fields() {
        val map = FirestoreMappers.donationToMap(donation(), "A1B2")
        // Local-only artifacts must never be uploaded.
        assertFalse(map.containsKey("audioStatus"))
        assertFalse(map.containsKey("notes"))
        assertFalse(map.containsKey("syncStatus"))
        // F4 privacy: tag-only attribution, full install id never leaves Room.
        assertFalse(map.containsKey("deviceId"))
        assertEquals("A1B2", map["deviceTag"])
        // Attribution: addedBy is the counter name.
        assertEquals("collector", map["addedBy"])

        val back = FirestoreMappers.donationFromMap("d1", "e1", map)
        assertNotNull(back)
        assertEquals("Ramesh", back.donorName)
        assertEquals(5000.0, back.amount)
        assertEquals(listOf("Cash", "Sponsor"), back.tags)
        assertEquals("CONFIRMED", back.status)
        assertEquals(2000L, back.updatedAt)
        assertEquals(3L, back.version)
        assertEquals("SYNCED", back.syncStatus)
    }

    @Test
    fun donation_from_map_rejects_missing_donor() {
        assertNull(FirestoreMappers.donationFromMap("d", "e", mapOf("amount" to 5.0)))
    }

    @Test
    fun event_header_round_trip_for_joiners() {
        val map = mapOf(
            "name" to "Vinayaka Chavithi",
            "templeName" to "Siva Temple",
            "location" to "Main Road",
            "status" to "active",
            "globalHeadId" to "uid-head",
            "createdAt" to 1000L,
            "updatedAt" to 2000L
        )
        val back = FirestoreMappers.eventFromMap("e1", map)
        assertNotNull(back)
        assertEquals("Vinayaka Chavithi", back.name)
        assertEquals("uid-head", back.globalHeadId)
        assertEquals("SYNCED", back.syncStatus)
        assertNull(FirestoreMappers.eventFromMap("e", mapOf("templeName" to "x")))
    }

    @Test
    fun expense_receipt_url_round_trips_but_never_the_local_path() {
        val e = ExpenseEntity(
            id = "x1", eventId = "e1", amount = 250.0, description = "Flowers",
            category = "Decorations", dateMillis = 1000L, paidBy = "Ramesh",
            paymentMethod = "Cash", vendor = null, notes = "local only",
            receiptPath = "/data/local/receipt_x1.webp",
            receiptUrl = "v1/receipts/e1/x1",
            addedBy = "collector", addedTime = 1000L,
            createdAt = 1000L, updatedAt = 2000L, status = "ACTIVE",
            version = 1L, syncStatus = "PENDING_UPLOAD"
        )
        val map = FirestoreMappers.expenseToMap(e, "A1B2")
        // Gateway path travels; local bytes/path never leave the device.
        assertEquals("v1/receipts/e1/x1", map["receiptUrl"])
        assertFalse(map.containsKey("receiptPath"))
        assertFalse(map.containsKey("notes"))
        assertFalse(map.containsKey("syncStatus"))

        val back = FirestoreMappers.expenseFromMap("x1", "e1", map)
        assertNotNull(back)
        assertEquals("v1/receipts/e1/x1", back.receiptUrl)
        assertNull(back.receiptPath)

        // Legacy docs without the key still read (NULL until first upload).
        val legacy = FirestoreMappers.expenseFromMap(
            "x2", "e1", mapOf("description" to "Old", "amount" to 10.0)
        )
        assertNotNull(legacy)
        assertNull(legacy.receiptUrl)
    }

    @Test
    fun member_via_code_stamped_on_join_omitted_elsewhere() {
        val join = FirestoreMappers.memberToMap(
            role = "member", status = "pending", approvedBy = "",
            joinedAt = 1000L, viaCode = "abc123"
        )
        // Normalized to the uppercase 6-char contract for the rules check.
        assertEquals("ABC123", join["viaCode"])
        assertEquals("pending", join["status"])

        // Presence/approval writes omit it — merge preserves the join stamp.
        val touch = FirestoreMappers.memberToMap(
            role = "member", status = "pending", approvedBy = "",
            joinedAt = null
        )
        assertFalse(touch.containsKey("viaCode"))
        assertFalse(touch.containsKey("joinedAt"))
    }

    @Test
    fun conflict_rule_needs_newer_timestamp_and_new_version() {        assertTrue(FirestoreMappers.isRemoteNewer(1000L, 1L, 2000L, 2L))
        // Same version re-downloaded after our own upload: not a conflict.
        assertFalse(FirestoreMappers.isRemoteNewer(2000L, 3L, 2000L, 3L))
        // Older remote row: ignore.
        assertFalse(FirestoreMappers.isRemoteNewer(2000L, 3L, 1000L, 2L))
        // Newer timestamp but same version (echo of our row): not a conflict.
        assertFalse(FirestoreMappers.isRemoteNewer(1000L, 2L, 2000L, 2L))
    }
}
