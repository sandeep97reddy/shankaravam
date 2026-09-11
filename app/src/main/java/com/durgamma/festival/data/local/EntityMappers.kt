package com.durgamma.festival.data.local

import com.durgamma.festival.domain.model.ActivityRecord
import com.durgamma.festival.domain.model.AudioStatus
import com.durgamma.festival.domain.model.Correction
import com.durgamma.festival.domain.model.CorrectionTargetType
import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Event
import com.durgamma.festival.domain.model.EventStatus
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.model.ExpenseStatus
import com.durgamma.festival.domain.model.SyncStatus
import com.durgamma.festival.domain.model.decodeEnum

/** Entity <-> domain mapping. Unknown stored values fall back, never crash. */

fun EventEntity.toDomain() = Event(
    id = id, name = name, templeName = templeName, location = location,
    startDateMillis = startDateMillis, endDateMillis = endDateMillis,
    defaultLanguage = defaultLanguage,
    status = decodeEnum(status, EventStatus.ACTIVE),
    globalHeadId = globalHeadId, creatorId = creatorId, deviceId = deviceId,
    createdAt = createdAt, updatedAt = updatedAt, version = version,
    syncStatus = decodeEnum(syncStatus, SyncStatus.LOCAL_ONLY)
)

fun Event.toEntity() = EventEntity(
    id = id, name = name, templeName = templeName, location = location,
    startDateMillis = startDateMillis, endDateMillis = endDateMillis,
    defaultLanguage = defaultLanguage, status = status.name,
    globalHeadId = globalHeadId, creatorId = creatorId, deviceId = deviceId,
    createdAt = createdAt, updatedAt = updatedAt, version = version,
    syncStatus = syncStatus.name
)

fun DonationEntity.toDomain() = Donation(
    id = id, eventId = eventId, donorName = donorName,
    pronunciationText = pronunciationText, amount = amount, currency = currency,
    isNonCash = isNonCash, itemDescription = itemDescription,
    quantity = quantity, unit = unit, paymentMethod = paymentMethod, tags = tags,
    status = decodeEnum(status, DonationStatus.RECEIVED),
    announcementEnabled = announcementEnabled,
    audioStatus = decodeEnum(audioStatus, AudioStatus.NOT_GENERATED),
    notes = notes, addedBy = addedBy, addedTime = addedTime,
    createdAt = createdAt, updatedAt = updatedAt, version = version,
    syncStatus = decodeEnum(syncStatus, SyncStatus.PENDING_UPLOAD)
)

fun Donation.toEntity() = DonationEntity(
    id = id, eventId = eventId, donorName = donorName,
    pronunciationText = pronunciationText, amount = amount, currency = currency,
    isNonCash = isNonCash, itemDescription = itemDescription,
    quantity = quantity, unit = unit, paymentMethod = paymentMethod, tags = tags,
    status = status.name, announcementEnabled = announcementEnabled,
    audioStatus = audioStatus.name, notes = notes, addedBy = addedBy,
    addedTime = addedTime, createdAt = createdAt, updatedAt = updatedAt,
    version = version, syncStatus = syncStatus.name
)

fun ExpenseEntity.toDomain() = Expense(
    id = id, eventId = eventId, amount = amount, description = description,
    category = category, dateMillis = dateMillis, paidBy = paidBy,
    paymentMethod = paymentMethod, vendor = vendor, notes = notes,
    receiptPath = receiptPath, addedBy = addedBy, addedTime = addedTime,
    createdAt = createdAt, updatedAt = updatedAt,
    status = decodeEnum(status, ExpenseStatus.ACTIVE), version = version,
    syncStatus = decodeEnum(syncStatus, SyncStatus.PENDING_UPLOAD)
)

fun Expense.toEntity() = ExpenseEntity(
    id = id, eventId = eventId, amount = amount, description = description,
    category = category, dateMillis = dateMillis, paidBy = paidBy,
    paymentMethod = paymentMethod, vendor = vendor, notes = notes,
    receiptPath = receiptPath, addedBy = addedBy, addedTime = addedTime,
    createdAt = createdAt, updatedAt = updatedAt, status = status.name,
    version = version, syncStatus = syncStatus.name
)

fun CorrectionEntity.toDomain() = Correction(
    id = id, eventId = eventId, targetRecordId = targetRecordId,
    targetType = decodeEnum(targetType, CorrectionTargetType.DONATION),
    originalAmount = originalAmount, deltaAmount = deltaAmount, reason = reason,
    correctedBy = correctedBy, createdAt = createdAt,
    syncStatus = decodeEnum(syncStatus, SyncStatus.PENDING_UPLOAD)
)

fun Correction.toEntity() = CorrectionEntity(
    id = id, eventId = eventId, targetRecordId = targetRecordId,
    targetType = targetType.name, originalAmount = originalAmount,
    deltaAmount = deltaAmount, reason = reason, correctedBy = correctedBy,
    createdAt = createdAt, syncStatus = syncStatus.name
)

fun ActivityEntity.toDomain() = ActivityRecord(
    id = id, eventId = eventId, actionType = actionType, details = details,
    actorId = actorId, timestamp = timestamp
)

fun ActivityRecord.toEntity() = ActivityEntity(
    id = id, eventId = eventId, actionType = actionType, details = details,
    actorId = actorId, timestamp = timestamp
)
