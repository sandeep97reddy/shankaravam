package com.shankaravam.festival.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** F5: shared-key apply matrix — auto-pull reaches collectors, guards hold. */
class VoiceKeyApplyTest {

    @Test
    fun blank_remote_never_touches_local() {
        assertEquals(
            SharedKeyDecision(false, false),
            shouldApplySharedKey(localKey = "abc", remoteKey = "", offlineLocked = false)
        )
        assertEquals(
            SharedKeyDecision(false, false),
            shouldApplySharedKey(localKey = "", remoteKey = "   ", offlineLocked = false)
        )
    }

    @Test
    fun identical_key_is_noop() {
        assertEquals(
            SharedKeyDecision(false, false),
            shouldApplySharedKey(localKey = "abc", remoteKey = "abc", offlineLocked = false)
        )
    }

    @Test
    fun new_key_flips_unlocked_device_to_cloud() {
        assertEquals(
            SharedKeyDecision(true, true),
            shouldApplySharedKey(localKey = "", remoteKey = "xyz", offlineLocked = false)
        )
        assertEquals(
            SharedKeyDecision(true, true),
            shouldApplySharedKey(localKey = "old", remoteKey = "xyz", offlineLocked = false)
        )
    }

    @Test
    fun explicit_offline_lock_survives_pull() {
        // Key still applies (waits unused); mode stays offline.
        assertEquals(
            SharedKeyDecision(true, false),
            shouldApplySharedKey(localKey = "", remoteKey = "xyz", offlineLocked = true)
        )
    }

    @Test
    fun manual_pull_bypasses_lock() {
        assertEquals(
            SharedKeyDecision(true, true),
            shouldApplySharedKey(
                localKey = "", remoteKey = "xyz",
                offlineLocked = true, respectLock = false
            )
        )
    }
}
