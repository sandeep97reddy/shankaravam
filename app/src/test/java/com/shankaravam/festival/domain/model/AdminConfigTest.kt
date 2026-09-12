package com.shankaravam.festival.domain.model

import com.shankaravam.festival.data.remote.FirestoreMappers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S5 regression locks (ADMIN_HEAD_PLAN S5.1). All pure JVM — SessionPrefs
 * (needs Context) is deliberately NOT covered here; no Robolectric is added
 * for this track (plan §5.2).
 */
class AdminConfigTest {

    // ---- whitelist email matching ----

    @Test
    fun whitelist_matches_exact_case_padded_variants() {
        assertTrue(AdminConfig.isGlobalHeadEmail("sandeepreddyr97@gmail.com"))
        assertTrue(AdminConfig.isGlobalHeadEmail("SandeepReddyR97@Gmail.com"))
        assertTrue(AdminConfig.isGlobalHeadEmail("  sandeepreddyr97@gmail.com  "))
    }

    @Test
    fun whitelist_rejects_null_blank_and_other_addresses() {
        assertFalse(AdminConfig.isGlobalHeadEmail(null))
        assertFalse(AdminConfig.isGlobalHeadEmail(""))
        assertFalse(AdminConfig.isGlobalHeadEmail("   "))
        assertFalse(AdminConfig.isGlobalHeadEmail("someone.else@gmail.com"))
        assertFalse(AdminConfig.isGlobalHeadEmail("sandeepreddyr97@yahoo.com"))
        assertFalse(AdminConfig.isGlobalHeadEmail("sandeepreddyr97@gmail.com.evil.com"))
    }

    // ---- head-only gates ----

    @Test
    fun team_roster_and_member_management_are_head_only() {
        assertTrue(AccessPolicy.canViewTeamRoster(UserRole.GLOBAL_HEAD))
        assertTrue(AccessPolicy.canManageMembers(UserRole.GLOBAL_HEAD))
        assertFalse(AccessPolicy.canViewTeamRoster(UserRole.ORGANIZER))
        assertFalse(AccessPolicy.canManageMembers(UserRole.ORGANIZER))
        assertFalse(AccessPolicy.canViewTeamRoster(UserRole.MEMBER))
        assertFalse(AccessPolicy.canManageMembers(UserRole.MEMBER))
    }

    // ---- presence boundaries (injected clock) ----

    private val now = 1_000_000_000_000L

    @Test
    fun presence_zero_and_negative_are_offline() {
        assertEquals(MemberPresence.OFFLINE, presenceOf(0L, now))
        assertEquals(MemberPresence.OFFLINE, presenceOf(-5L, now))
    }

    @Test
    fun presence_active_window_is_exclusive_at_15m() {
        assertEquals(MemberPresence.ACTIVE_NOW, presenceOf(now, now))
        assertEquals(MemberPresence.ACTIVE_NOW, presenceOf(now - 899_000L, now)) // 14:59
        assertEquals(MemberPresence.IDLE, presenceOf(now - 900_000L, now)) // 15:00 exactly
    }

    @Test
    fun presence_idle_window_is_exclusive_at_2h() {
        assertEquals(MemberPresence.IDLE, presenceOf(now - 7_199_000L, now)) // 1:59:59
        assertEquals(MemberPresence.OFFLINE, presenceOf(now - 7_200_000L, now)) // 2:00 exactly
        assertEquals(MemberPresence.OFFLINE, presenceOf(now - 86_400_000L, now)) // a day
    }

    @Test
    fun presence_future_timestamps_heal_to_active() {
        assertEquals(MemberPresence.ACTIVE_NOW, presenceOf(now + 60_000L, now))
    }

    // ---- member mapper: legacy tolerance + round-trip + privacy ----

    @Test
    fun legacy_four_key_doc_parses_with_safe_defaults() {
        val member = FirestoreMappers.memberFromMap(
            "uid-old",
            mapOf("role" to "member", "status" to "pending", "approvedBy" to "", "joinedAt" to 123L)
        )
        assertEquals("uid-old", member.userId)
        assertEquals("member", member.role)
        assertEquals("pending", member.status)
        assertEquals(123L, member.joinedAt)
        assertNull(member.email)
        assertNull(member.displayName)
        assertNull(member.counterName)
        assertNull(member.deviceTag)
        assertEquals(0L, member.lastActiveAt)
        assertEquals(MemberPresence.OFFLINE, presenceOf(member.lastActiveAt, now))
    }

    @Test
    fun member_round_trip_preserves_identity_and_presence() {
        val map = FirestoreMappers.memberToMap(
            role = "organizer", status = "active", approvedBy = "uid-head",
            joinedAt = 456L, email = "ramesh@gmail.com", displayName = "Ramesh",
            counterName = "Counter 1 - Ramesh", deviceTag = "4F2A", lastActiveAt = 789L
        )
        val back = FirestoreMappers.memberFromMap("uid-r", map)
        assertEquals("organizer", back.role)
        assertEquals("active", back.status)
        assertEquals(456L, back.joinedAt)
        assertEquals("ramesh@gmail.com", back.email)
        assertEquals("Counter 1 - Ramesh", back.counterName)
        assertEquals("4F2A", back.deviceTag)
        assertEquals(789L, back.lastActiveAt)
    }

    @Test
    fun member_map_never_emits_full_device_id_and_omits_nulls() {
        val map = FirestoreMappers.memberToMap(
            role = "organizer", status = "active", approvedBy = "uid-head", joinedAt = null
        )
        assertFalse(map.containsKey("deviceId"))
        assertFalse(map.containsKey("joinedAt"))
        assertFalse(map.containsKey("email"))
        assertFalse(map.containsKey("lastActiveAt"))
    }

    @Test
    fun legacy_device_id_degrades_to_last4_tag() {
        val member = FirestoreMappers.memberFromMap(
            "uid-legacy",
            mapOf(
                "role" to "organizer", "status" to "active",
                "approvedBy" to "uid-head", "joinedAt" to 1L,
                "deviceId" to "f47ac10b-58cc-4372-a567-0e02b2c3d479"
            )
        )
        assertEquals("D479", member.deviceTag)
    }

    // ---- least-privilege fallback lock (S1 seal) ----

    @Test
    fun unknown_and_revoked_role_strings_fall_back_to_member() {
        assertEquals(UserRole.MEMBER, roleOf("revoked"))
        assertEquals(UserRole.MEMBER, roleOf("collector"))
        assertEquals(UserRole.MEMBER, roleOf("viewer"))
        assertEquals(UserRole.MEMBER, roleOf("super_admin"))
        assertEquals(UserRole.MEMBER, roleOf(null))
    }
}
