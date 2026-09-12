package com.shankaravam.festival.data.remote

import com.shankaravam.festival.data.local.DonationEntity
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
        val map = FirestoreMappers.donationToMap(donation(), "device-123")
        // Local-only artifacts must never be uploaded.
        assertFalse(map.containsKey("audioStatus"))
        assertFalse(map.containsKey("notes"))
        assertFalse(map.containsKey("syncStatus"))
        // Attribution: addedBy is the counter name, deviceId is the real install id.
        assertEquals("collector", map["addedBy"])
        assertEquals("device-123", map["deviceId"])

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
    fun conflict_rule_needs_newer_timestamp_and_new_version() {
        assertTrue(FirestoreMappers.isRemoteNewer(1000L, 1L, 2000L, 2L))
        // Same version re-downloaded after our own upload: not a conflict.
        assertFalse(FirestoreMappers.isRemoteNewer(2000L, 3L, 2000L, 3L))
        // Older remote row: ignore.
        assertFalse(FirestoreMappers.isRemoteNewer(2000L, 3L, 1000L, 2L))
        // Newer timestamp but same version (echo of our row): not a conflict.
        assertFalse(FirestoreMappers.isRemoteNewer(1000L, 2L, 2000L, 2L))
    }
}
