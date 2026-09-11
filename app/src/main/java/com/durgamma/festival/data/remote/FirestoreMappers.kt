package com.durgamma.festival.data.remote

import com.durgamma.festival.data.local.CorrectionEntity
import com.durgamma.festival.data.local.DonationEntity
import com.durgamma.festival.data.local.EventEntity
import com.durgamma.festival.data.local.ExpenseEntity

/**
 * Entity ↔ Firestore document mapping (plan §22). All timestamps travel as
 * epoch-millis Longs (not Timestamp) so mapping stays unit-testable without
 * the Firebase SDK, and delta queries compare plain numbers.
 *
 * Audio cache paths and Sarvam keys NEVER enter these maps (Rule #2).
 */
object FirestoreMappers {

    // ---- donations ----

    fun donationToMap(e: DonationEntity): Map<String, Any?> = mapOf(
        "donorName" to e.donorName,
        "pronunciationText" to e.pronunciationText,
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
        "deviceId" to e.addedBy,
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

    fun expenseToMap(e: ExpenseEntity): Map<String, Any?> = mapOf(
        "amount" to e.amount,
        "description" to e.description,
        "category" to e.category,
        "date" to e.dateMillis,
        "paidBy" to e.paidBy,
        "paymentMethod" to e.paymentMethod,
        "vendor" to e.vendor,
        "addedBy" to e.addedBy,
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

    // ---- members / codes / config ----

    fun memberToMap(role: String, status: String, approvedBy: String, joinedAt: Long): Map<String, Any?> =
        mapOf("role" to role, "status" to status, "approvedBy" to approvedBy, "joinedAt" to joinedAt)

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
