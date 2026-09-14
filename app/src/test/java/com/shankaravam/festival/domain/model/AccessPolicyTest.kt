package com.shankaravam.festival.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessPolicyTest {

    @Test
    fun organizer_keeps_full_counter_powers() {
        val role = UserRole.ORGANIZER
        assertTrue(AccessPolicy.canAddDonation(role))
        assertTrue(AccessPolicy.canAnnounce(role))
        assertTrue(AccessPolicy.canAddExpense(role))
        assertTrue(AccessPolicy.canExport(role))
        assertTrue(AccessPolicy.canCorrect(role))
        assertTrue(AccessPolicy.canCancelExpense(role))
        assertTrue(AccessPolicy.canApproveMembers(role))
        assertTrue(AccessPolicy.canCloseEvent(role))
        assertFalse(AccessPolicy.canManageKeys(role))
    }

    @Test
    fun member_can_only_view() {
        val role = UserRole.MEMBER
        assertFalse(AccessPolicy.canAddDonation(role))
        assertFalse(AccessPolicy.canAnnounce(role))
        // Viewers never write money — matches firestore.rules canWriteLedger.
        assertFalse(AccessPolicy.canAddExpense(role))
        assertFalse(AccessPolicy.canExport(role))
        assertFalse(AccessPolicy.canCorrect(role))
        assertFalse(AccessPolicy.canApproveMembers(role))
        assertFalse(AccessPolicy.canCloseEvent(role))
        assertFalse(AccessPolicy.canManageKeys(role))
    }

    @Test
    fun global_head_alone_manages_keys() {
        assertTrue(AccessPolicy.canManageKeys(UserRole.GLOBAL_HEAD))
        assertTrue(AccessPolicy.canCloseEvent(UserRole.GLOBAL_HEAD))
    }

    @Test
    fun unknown_role_names_fall_back_to_member_least_privilege() {
        assertTrue(roleOf(null) == UserRole.MEMBER)
        assertTrue(roleOf("super_admin") == UserRole.MEMBER)
        assertTrue(roleOf("revoked") == UserRole.MEMBER)
        assertTrue(roleOf("collector") == UserRole.MEMBER)
        assertTrue(roleOf("member") == UserRole.MEMBER)
        assertTrue(roleOf("organizer") == UserRole.ORGANIZER)
        assertTrue(roleOf("global_head") == UserRole.GLOBAL_HEAD)
    }

    @Test
    fun write_money_needs_active_collector() {
        assertTrue(AccessPolicy.canWriteMoney(UserRole.ORGANIZER, MemberStatus.ACTIVE))
        assertTrue(AccessPolicy.canWriteMoney(UserRole.GLOBAL_HEAD, MemberStatus.ACTIVE))
        // Status axis: pending/revoked collectors cannot write (rules deny).
        assertFalse(AccessPolicy.canWriteMoney(UserRole.ORGANIZER, MemberStatus.PENDING))
        assertFalse(AccessPolicy.canWriteMoney(UserRole.GLOBAL_HEAD, MemberStatus.REVOKED))
        assertFalse(AccessPolicy.canWriteMoney(UserRole.ORGANIZER, MemberStatus.REVOKED))
        // Role axis: active viewers cannot write.
        assertFalse(AccessPolicy.canWriteMoney(UserRole.MEMBER, MemberStatus.ACTIVE))
    }

    @Test
    fun write_blocked_reason_matches_sync_copy() {
        assertTrue(AccessPolicy.writeBlockedReason(UserRole.ORGANIZER, MemberStatus.ACTIVE) == null)
        assertTrue(
            AccessPolicy.writeBlockedReason(UserRole.ORGANIZER, MemberStatus.PENDING)
                ?.contains("Waiting for head approval") == true
        )
        assertTrue(
            AccessPolicy.writeBlockedReason(UserRole.ORGANIZER, MemberStatus.REVOKED)
                ?.contains("revoked") == true
        )
        assertTrue(
            AccessPolicy.writeBlockedReason(UserRole.MEMBER, MemberStatus.ACTIVE)
                ?.contains("collectors") == true
        )
    }

    @Test
    fun unknown_status_names_default_to_pending_blocked() {
        assertTrue(memberStatusOf(null) == MemberStatus.PENDING)
        assertTrue(memberStatusOf("bogus") == MemberStatus.PENDING)
        assertFalse(AccessPolicy.canWriteMoney(UserRole.ORGANIZER, memberStatusOf("bogus")))
    }
}
