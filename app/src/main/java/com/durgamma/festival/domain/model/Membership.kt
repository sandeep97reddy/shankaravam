package com.durgamma.festival.domain.model

/** Plan §6 roles. Local default is ORGANIZER so offline use never degrades. */
enum class UserRole { GLOBAL_HEAD, ORGANIZER, MEMBER }

fun roleOf(name: String?): UserRole =
    runCatching { UserRole.valueOf(name?.uppercase() ?: "") }.getOrDefault(UserRole.ORGANIZER)

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
}
