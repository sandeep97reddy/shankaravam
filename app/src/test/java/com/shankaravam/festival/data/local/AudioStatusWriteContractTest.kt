package com.shankaravam.festival.data.local

import com.shankaravam.festival.domain.model.AudioStatus
import com.shankaravam.festival.domain.repository.DonationRepository
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * T0.3 regression pin: audio bookkeeping is local-only derived state and must
 * NEVER accept a timestamp — the old `updatedAt = :now` defeated
 * `isRemoteNewer` (a peer's genuine money edit could be silently ignored) and
 * smeared audio timing into ledger timestamps via `stampForPush`.
 *
 * No Room runtime exists in `testDebugUnitTest` (pure JVM), so instead of a
 * DB round-trip this pins the write signatures: with no `Long` parameter, no
 * caller — present or future — can smuggle time into the write. The SQL
 * itself (`UPDATE donations SET audioStatus = :status WHERE id = :id`) is
 * reviewed alongside `DonationDao.kt:49-50`.
 */
class AudioStatusWriteContractTest {

    @Test
    fun repository_updateAudioStatus_takes_no_timestamp() {
        // Suspend fun → JVM (String, AudioStatus, Continuation); NoSuchMethod
        // fails the test if anyone re-adds a timestamp parameter.
        val m = DonationRepository::class.java.getMethod(
            "updateAudioStatus",
            String::class.java,
            AudioStatus::class.java,
            Continuation::class.java
        )
        assertTrue(
            m.parameterTypes.none { it == Long::class.javaPrimitiveType || it == Long::class.javaObjectType },
            "updateAudioStatus must not take a timestamp"
        )
    }

    @Test
    fun dao_updateAudioStatus_takes_no_timestamp() {
        val m = DonationDao::class.java.getMethod(
            "updateAudioStatus",
            String::class.java,
            String::class.java,
            Continuation::class.java
        )
        assertTrue(
            m.parameterTypes.none { it == Long::class.javaPrimitiveType || it == Long::class.javaObjectType },
            "DonationDao.updateAudioStatus must not take a timestamp"
        )
    }
}
