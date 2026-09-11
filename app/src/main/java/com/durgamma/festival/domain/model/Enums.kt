package com.durgamma.festival.domain.model

/** Plan §10 — pledged money is never part of the balance. */
enum class DonationStatus {
    PLEDGED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CONFIRMED,
    CANCELLED
}

/** Plan §20 sync lifecycle. LOCAL_ONLY until the user enables Cloud Sync (G6). */
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    UPLOADING,
    SYNCED,
    SYNC_FAILED,
    CONFLICT,
    NEEDS_REVIEW
}

/** Plan §13 — consumed by the G4 announcement engine. Column exists now to avoid a migration. */
enum class AudioStatus {
    NOT_GENERATED,
    PREPARING,
    READY,
    FAILED
}

enum class EventStatus { ACTIVE, CLOSED }

enum class ExpenseStatus { ACTIVE, CANCELLED }

enum class CorrectionTargetType { DONATION, EXPENSE }

/** Lenient decode: unknown future values fall back instead of crashing old builds. */
inline fun <reified E : Enum<E>> decodeEnum(name: String?, fallback: E): E {
    if (name == null) return fallback
    return runCatching { java.lang.Enum.valueOf(E::class.java, name) }.getOrDefault(fallback)
}
