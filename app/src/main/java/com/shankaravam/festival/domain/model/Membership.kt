package com.shankaravam.festival.domain.model

/** Plan §6 roles. Offline default (SessionPrefs.myRole) is ORGANIZER so offline
 * use never degrades — but unknown/corrupt strings fall back to MEMBER
 * (least privilege, S1 seal): "revoked"/"viewer"/typos must never grant
 * counter powers. */
enum class UserRole { GLOBAL_HEAD, ORGANIZER, MEMBER }

fun roleOf(name: String?): UserRole =
    runCatching { UserRole.valueOf(name?.uppercase() ?: "") }.getOrDefault(UserRole.MEMBER)

enum class MemberStatus { ACTIVE, PENDING, REVOKED }

fun memberStatusOf(name: String?): MemberStatus =
    runCatching { MemberStatus.valueOf(name?.uppercase() ?: "") }.getOrDefault(MemberStatus.PENDING)

/**
 * Pure gating rules (plan §6). Enforced in ViewModels (donation correct,
 * expense cancel/correct, approvals, close-event, key management).
 */
object AccessPolicy {
    fun canAddDonation(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canAnnounce(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canAddExpense(role: UserRole): Boolean = true
    fun canExport(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canCorrect(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canCancelExpense(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canApproveMembers(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canCloseEvent(role: UserRole): Boolean = role != UserRole.MEMBER
    fun canManageKeys(role: UserRole): Boolean = role == UserRole.GLOBAL_HEAD
    fun canViewTeamRoster(role: UserRole): Boolean = role == UserRole.GLOBAL_HEAD
    fun canManageMembers(role: UserRole): Boolean = role == UserRole.GLOBAL_HEAD
}

/**
 * Master-admin whitelist (ADMIN_HEAD_PLAN S2.1). Single source for the
 * whitelisted email — firestore.rules carries the same literal as the
 * server-side boundary. Client use is UI gating ONLY, never authorization.
 */
object AdminConfig {
    const val GLOBAL_HEAD_EMAIL = "sandeepreddyr97@gmail.com"

    /** Presence windows: <15m active, 15m–2h idle, beyond offline. */
    const val ACTIVE_WINDOW_MILLIS = 15L * 60L * 1000L
    const val IDLE_WINDOW_MILLIS = 2L * 60L * 60L * 1000L

    fun isGlobalHeadEmail(email: String?): Boolean =
        !email.isNullOrBlank() &&
            email.trim().lowercase(java.util.Locale.ROOT) == GLOBAL_HEAD_EMAIL
}

enum class MemberPresence { ACTIVE_NOW, IDLE, OFFLINE }
/** Pure + unit-testable (inject `now` in tests). Future timestamps heal to ACTIVE_NOW. */
fun presenceOf(lastActiveAt: Long, now: Long = System.currentTimeMillis()): MemberPresence = when {
    lastActiveAt <= 0L -> MemberPresence.OFFLINE
    now - lastActiveAt < AdminConfig.ACTIVE_WINDOW_MILLIS -> MemberPresence.ACTIVE_NOW
    now - lastActiveAt < AdminConfig.IDLE_WINDOW_MILLIS -> MemberPresence.IDLE
    else -> MemberPresence.OFFLINE
}

/**
 * Human name for any team identity (roster rows, approvals, history lines).
 * Preference: counter name (what volunteers recognize at the pandal) →
 * Google display name → email → short-ID fallback. Raw UIDs never reach
 * the UI through this helper. Pure + unit-testable.
 */
fun resolveMemberName(
    counterName: String?,
    displayName: String?,
    email: String?,
    userId: String
): String {
    if (!counterName.isNullOrBlank()) return counterName.trim()
    if (!displayName.isNullOrBlank()) return displayName.trim()
    if (!email.isNullOrBlank()) return email.trim()
    val tail = userId.takeLast(6)
    return if (tail.isNotBlank()) "ID: …$tail" else "Unknown counter"
}
