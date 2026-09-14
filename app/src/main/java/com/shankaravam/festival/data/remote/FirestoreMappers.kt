package com.shankaravam.festival.data.remote

import com.shankaravam.festival.data.local.CorrectionEntity
import com.shankaravam.festival.data.local.DonationEntity
import com.shankaravam.festival.data.local.EventEntity
import com.shankaravam.festival.data.local.ExpenseEntity

/**
 * Entity ↔ Firestore document mapping (plan §22). All timestamps travel as
 * epoch-millis Longs (not Timestamp) so mapping stays unit-testable without
 * the Firebase SDK, and delta queries compare plain numbers.
 *
 * Audio cache paths and Sarvam keys NEVER enter these maps (Rule #2).
 */
object FirestoreMappers {

    // ---- donations ----

    fun donationToMap(e: DonationEntity, deviceTag: String = ""): Map<String, Any?> = mapOf(
        "donorName" to e.donorName,
        "pronunciationText" to e.pronunciationText,
        "honorific" to e.honorific,
        "amount" to e.amount,
        "currency" to e.currency,
        "isNonCash" to e.isNonCash,
        "itemDescription" to e.itemDescription,
        "quantity" to e.quantity,
        "unit" to e.unit,
        "paymentMethod" to e.paymentMethod,
        "tags" to e.tags,
        "status" to e.status,
        "announcementEnabled" to e.announcementEnabled,
        "addedBy" to e.addedBy,
        // F4 privacy: last-4 install tag only. The full UUID never leaves Room
        // (legacy rows may still carry a full "deviceId" — readers ignore it).
        "deviceTag" to deviceTag,
        "createdAt" to e.createdAt,
        "updatedAt" to e.updatedAt,
        "version" to e.version
    )

    fun donationFromMap(id: String, eventId: String, map: Map<String, Any?>): DonationEntity? {
        val donorName = map["donorName"] as? String ?: return null
        return DonationEntity(
            id = id,
            eventId = eventId,
            donorName = donorName,
            pronunciationText = map["pronunciationText"] as? String,
            honorific = (map["honorific"] as? String)?.ifBlank { null } ?: "శ్రీ",
            amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
            currency = map["currency"] as? String ?: "INR",
            isNonCash = map["isNonCash"] as? Boolean ?: false,
            itemDescription = map["itemDescription"] as? String,
            quantity = (map["quantity"] as? Number)?.toDouble(),
            unit = map["unit"] as? String,
            paymentMethod = map["paymentMethod"] as? String ?: "Cash",
            tags = (map["tags"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            status = map["status"] as? String ?: "RECEIVED",
            announcementEnabled = map["announcementEnabled"] as? Boolean ?: true,
            audioStatus = "NOT_GENERATED",
            notes = null,
            addedBy = map["addedBy"] as? String ?: "",
            addedTime = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
            version = (map["version"] as? Number)?.toLong() ?: 1L,
            syncStatus = "SYNCED"
        )
    }

    // ---- expenses ----

    fun expenseToMap(e: ExpenseEntity, deviceTag: String = ""): Map<String, Any?> = mapOf(
        "amount" to e.amount,
        "description" to e.description,
        "category" to e.category,
        "date" to e.dateMillis,
        "paidBy" to e.paidBy,
        "paymentMethod" to e.paymentMethod,
        "vendor" to e.vendor,
        "addedBy" to e.addedBy,
        // F4 privacy: last-4 install tag only (see donationToMap).
        "deviceTag" to deviceTag,
        "createdAt" to e.createdAt,
        "updatedAt" to e.updatedAt,
        "status" to e.status,
        "version" to e.version
    )

    fun expenseFromMap(id: String, eventId: String, map: Map<String, Any?>): ExpenseEntity? {
        val description = map["description"] as? String ?: return null
        return ExpenseEntity(
            id = id,
            eventId = eventId,
            amount = (map["amount"] as? Number)?.toDouble() ?: return null,
            description = description,
            category = map["category"] as? String ?: "Miscellaneous",
            dateMillis = (map["date"] as? Number)?.toLong() ?: 0L,
            paidBy = map["paidBy"] as? String ?: "",
            paymentMethod = map["paymentMethod"] as? String ?: "Cash",
            vendor = map["vendor"] as? String,
            notes = null,
            receiptPath = null,
            addedBy = map["addedBy"] as? String ?: "",
            addedTime = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
            status = (map["status"] as? String ?: "ACTIVE").uppercase(),
            version = (map["version"] as? Number)?.toLong() ?: 1L,
            syncStatus = "SYNCED"
        )
    }

    // ---- corrections (append-only; upload, never update) ----

    fun correctionToMap(e: CorrectionEntity): Map<String, Any?> = mapOf(
        "targetRecordId" to e.targetRecordId,
        "targetType" to e.targetType,
        "originalAmount" to e.originalAmount,
        "deltaAmount" to e.deltaAmount,
        "reason" to e.reason,
        "correctedBy" to e.correctedBy,
        "createdAt" to e.createdAt
    )

    /**
     * F3 peer download (corrections were upload-only; peers never received
     * them). Append-only + set-by-id = idempotent. Null when the doc is not a
     * correction. Local syncStatus resets to SYNCED — the row came from cloud.
     */
    fun correctionFromMap(id: String, eventId: String, map: Map<String, Any?>): CorrectionEntity? {
        val targetId = map["targetRecordId"] as? String ?: return null
        return CorrectionEntity(
            id = id,
            eventId = eventId,
            targetRecordId = targetId,
            targetType = map["targetType"] as? String ?: "DONATION",
            originalAmount = (map["originalAmount"] as? Number)?.toDouble() ?: 0.0,
            deltaAmount = (map["deltaAmount"] as? Number)?.toDouble() ?: 0.0,
            reason = map["reason"] as? String ?: "",
            correctedBy = map["correctedBy"] as? String ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
            syncStatus = "SYNCED"
        )
    }

    // ---- events (header only; members/codes are subcollections) ----

    fun eventToMap(e: EventEntity): Map<String, Any?> = mapOf(
        "name" to e.name,
        "templeName" to e.templeName,
        "location" to e.location,
        "startDate" to e.startDateMillis,
        "endDate" to e.endDateMillis,
        "status" to e.status.lowercase(),
        "globalHeadId" to e.globalHeadId,
        "createdAt" to e.createdAt,
        "updatedAt" to e.updatedAt
    )

    /** Joiner-side pull: never null for a valid header doc; local-only columns reset. */
    fun eventFromMap(id: String, map: Map<String, Any?>): EventEntity? {
        val name = map["name"] as? String ?: return null
        val now = System.currentTimeMillis()
        return EventEntity(
            id = id,
            name = name,
            templeName = map["templeName"] as? String ?: "",
            location = map["location"] as? String ?: "",
            startDateMillis = (map["startDate"] as? Number)?.toLong(),
            endDateMillis = (map["endDate"] as? Number)?.toLong(),
            defaultLanguage = "te",
            status = ((map["status"] as? String)?.uppercase() ?: "ACTIVE"),
            globalHeadId = map["globalHeadId"] as? String ?: "",
            creatorId = "",
            deviceId = "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: now,
            updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: now,
            version = 1L,
            syncStatus = "SYNCED"
        )
    }

    // ---- members / codes / config ----

    /**
     * Merge-write safe (ADMIN_HEAD_PLAN S2.2/S2.5): null/blank identity keys
     * are OMITTED so a role/status update never null-stomps presence fields
     * written by another device, and `joinedAt` is omitted when null so
     * approvals preserve the original join order. Full deviceIds are never
     * accepted here — callers pass the last-4 `deviceTag`.
     */
    fun memberToMap(
        role: String,
        status: String,
        approvedBy: String,
        joinedAt: Long?,
        email: String? = null,
        displayName: String? = null,
        counterName: String? = null,
        deviceTag: String? = null,
        lastActiveAt: Long? = null
    ): Map<String, Any?> = buildMap {
        put("role", role)
        put("status", status)
        put("approvedBy", approvedBy)
        if (joinedAt != null) put("joinedAt", joinedAt)
        email?.takeIf { it.isNotBlank() }?.let { put("email", it) }
        displayName?.takeIf { it.isNotBlank() }?.let { put("displayName", it) }
        counterName?.takeIf { it.isNotBlank() }?.let { put("counterName", it) }
        deviceTag?.takeIf { it.isNotBlank() }?.let { put("deviceTag", it) }
        if (lastActiveAt != null && lastActiveAt > 0L) put("lastActiveAt", lastActiveAt)
    }

    /**
     * Legacy-tolerant read: pre-S2 docs carry only the 4 core keys — every
     * new field defaults. A legacy `deviceId` (full UUID, if any S3-beta doc
     * wrote one) degrades to its last-4 tag; the full value is never surfaced.
     */
    fun memberFromMap(userId: String, map: Map<String, Any?>): CloudMember {
        val legacyDeviceId = map["deviceId"] as? String
        return CloudMember(
            userId = userId,
            role = map["role"] as? String ?: "member",
            status = map["status"] as? String ?: "pending",
            approvedBy = map["approvedBy"] as? String ?: "",
            joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: 0L,
            email = map["email"] as? String,
            displayName = map["displayName"] as? String,
            counterName = map["counterName"] as? String,
            deviceTag = (map["deviceTag"] as? String)
                ?: legacyDeviceId?.takeLast(4)?.uppercase(),
            lastActiveAt = (map["lastActiveAt"] as? Number)?.toLong() ?: 0L
        )
    }

    /**
     * Money conflict rule (plan §20): a remote row wins attention only when it
     * is strictly newer AND a different version. Local values are kept; the
     * row is flagged CONFLICT for human review — never last-write-wins.
     */
    fun isRemoteNewer(
        localUpdatedAt: Long,
        localVersion: Long,
        remoteUpdatedAt: Long,
        remoteVersion: Long
    ): Boolean = remoteUpdatedAt > localUpdatedAt && remoteVersion != localVersion
}
