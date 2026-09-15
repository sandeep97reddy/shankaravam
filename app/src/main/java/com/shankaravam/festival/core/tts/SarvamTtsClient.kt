package com.shankaravam.festival.core.tts

import android.content.Context
import android.util.Base64
import com.shankaravam.festival.data.remote.SarvamApiService
import com.shankaravam.festival.data.remote.SarvamErrorParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Normalizes speaker names to valid Sarvam bulbul:v3 speakers.
 * Maps legacy "meera" -> "kavitha" and "arvind" -> "aditya".
 * T0.5: unset/corrupt values default to "shubh" (temple default voice).
 * Stored user choices pass through untouched — never force-migrated.
 */
fun normalizeSarvamSpeaker(raw: String?): String = when (raw?.lowercase()?.trim()) {
    "priya" -> "priya"
    "shubh" -> "shubh"
    "kavitha", "meera" -> "kavitha"
    "aditya", "arvind" -> "aditya"
    "ratan" -> "ratan"
    "neha" -> "neha"
    "ishita" -> "ishita"
    "mani" -> "mani"
    "vijay" -> "vijay"
    "ritu" -> "ritu"
    "roopa" -> "roopa"
    "suhani" -> "suhani"
    "pooja" -> "pooja"
    "ashutosh" -> "ashutosh"
    "rehan" -> "rehan"
    "rohan" -> "rohan"
    "varun" -> "varun"
    else -> "shubh"
}

/**
 * Cloud voice (plan §13). Audio lands ONLY in local disk cache —
 * never Firebase Storage (AGENTS.md Rule #2). Throws on any failure so the
 * DualTtsEngine can fall back to native TTS silently.
 */
class SarvamTtsClient(
    private val context: Context,
    private val api: SarvamApiService = SarvamApiService.create()
) {
    /**
     * Full-sentence and roster recordings are cached separately, and each
     * Sarvam speaker gets its own file (`donation_{id}_{speaker}.mp3` vs
     * `donation_{id}_{speaker}_roster.mp3`) so switching voices or
     * announcement style never plays the wrong recording (Phase 1 ghost-voice
     * fix). Human-imported clips (WhatsApp share) live in the speaker-agnostic
     * legacy slot — see [importedFile].
     */
    fun cachedFile(donationId: String, roster: Boolean = false, speaker: String = "shubh"): File? {
        val file = File(audioDir(), cacheFileName(donationId, roster, speaker))
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Speaker-agnostic human-import slot (`donation_{id}[_roster].mp3`).
     * Written only by manual import (WhatsApp/file picker) — never by
     * [getOrGenerateAudio]. The engine plays human clips before Sarvam
     * speaker files (a deliberate import is an intentional override), so a
     * human recording is never shadowed by older auto-generated cloud audio.
     */
    fun importedFile(donationId: String, roster: Boolean = false): File? {
        val file = File(audioDir(), legacyCacheFileName(donationId, roster))
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Phase-3 content-addressed read (`audio_{hash}.mp3`, shared across
     * phones). The hash is `audioHashFor` over the client-built
     * effective-amount text — any spoken-byte change is a different file, so
     * corrections auto-invalidate. Null for malformed hashes. Never throws.
     */
    fun cachedCasFile(hash: String): File? = runCatching {
        if (!isCasHash(hash)) return@runCatching null
        File(audioDir(), casFileName(hash)).takeIf { it.exists() && it.length() > 0 }
    }.getOrNull()

    /**
     * Phase-3 content-addressed write. Overwrites only its own hash slot
     * (immutable content ⇒ same bytes). Never throws (null on failure).
     */
    fun putCasFile(hash: String, bytes: ByteArray): File? = runCatching {
        require(isCasHash(hash)) { "bad CAS hash" }
        require(bytes.isNotEmpty()) { "empty audio" }
        File(audioDir(), casFileName(hash)).apply { writeBytes(bytes) }
    }.getOrNull()

    /**
     * Phase-3 direct synthesis into the CAS slot (used when the gateway is
     * unreachable but a local Sarvam key exists — kept until Phase 4). Same
     * bytes as the gateway JIT for the same text, so sources never diverge.
     */
    suspend fun getOrGenerateCasAudio(
        hash: String,
        text: String,
        apiKey: String,
        speaker: String = "shubh"
    ): File = withContext(Dispatchers.IO) {
        require(isCasHash(hash)) { "bad CAS hash" }
        cachedCasFile(hash)?.let { return@withContext it }

        val normSpeaker = normalizeSarvamSpeaker(speaker)
        val payload = JSONObject()
            .put("text", text)
            .put("language_code", "te-IN")
            .put("speaker", normSpeaker)
            .put("model", "bulbul:v3")
            .put("output_audio_codec", "mp3")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val raw = try {
            api.synthesize(apiKey.trim(), payload).string()
        } catch (e: Throwable) {
            throw IOException(SarvamErrorParser.parse(e), e)
        }

        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IOException("Unexpected Sarvam response (not JSON): ${raw.take(100)}")

        val audioBase64 = runCatching {
            if (json.has("audios")) {
                json.getJSONArray("audios").getString(0)
            } else if (json.has("audio")) {
                json.getString("audio")
            } else null
        }.getOrNull() ?: throw IOException("Missing audio in Sarvam response: ${raw.take(100)}")

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        if (bytes.isEmpty()) throw IOException("Empty audio from Sarvam")

        putCasFile(hash, bytes) ?: throw IOException("Could not cache audio")
    }

    /**
     * Pre-Phase-3 donation-keyed synthesis (legacy `donation_{id}_*.mp3`
     * names). No production callers since the CAS cutover — kept for the
     * one-release dual-read transition, removal in the follow-up.
     */
    @Deprecated("CAS-only since Phase 3 (getOrGenerateCasAudio); removal after transition.")
    suspend fun getOrGenerateAudio(
        donationId: String,
        text: String,
        apiKey: String,
        speaker: String = "shubh",
        roster: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        // Speaker-aware hit: a Priya file must never satisfy a Shubh request.
        cachedFile(donationId, roster, speaker)?.let { return@withContext it }

        val normSpeaker = normalizeSarvamSpeaker(speaker)
        val payload = JSONObject()
            .put("text", text)
            .put("language_code", "te-IN")
            .put("speaker", normSpeaker)
            .put("model", "bulbul:v3")
            .put("output_audio_codec", "mp3")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val raw = try {
            api.synthesize(apiKey.trim(), payload).string()
        } catch (e: Throwable) {
            throw IOException(SarvamErrorParser.parse(e), e)
        }

        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IOException("Unexpected Sarvam response (not JSON): ${raw.take(100)}")

        val audioBase64 = runCatching {
            if (json.has("audios")) {
                json.getJSONArray("audios").getString(0)
            } else if (json.has("audio")) {
                json.getString("audio")
            } else null
        }.getOrNull() ?: throw IOException("Missing audio in Sarvam response: ${raw.take(100)}")

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        if (bytes.isEmpty()) throw IOException("Empty audio from Sarvam")

        val file = File(audioDir(), cacheFileName(donationId, roster, normSpeaker))
        file.writeBytes(bytes)
        file
    }

    /**
     * F6 phrase cache (intro/outro): checks if a pre-generated phrase is cached on disk.
     */
    fun cachedPhraseFile(phraseKey: String, speaker: String = "shubh"): File? {
        val file = File(audioDir(), phraseCacheFileName(phraseKey, speaker))
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * F6 phrase synthesis (intro/outro): generates and caches shared phrases so the
     * whole announcement queue speaks in a single, consistent Sarvam voice.
     */
    suspend fun getOrGeneratePhraseAudio(
        phraseKey: String,
        text: String,
        apiKey: String,
        speaker: String = "shubh"
    ): File = withContext(Dispatchers.IO) {
        val normSpeaker = normalizeSarvamSpeaker(speaker)
        cachedPhraseFile(phraseKey, normSpeaker)?.let { return@withContext it }

        val payload = JSONObject()
            .put("text", text)
            .put("language_code", "te-IN")
            .put("speaker", normSpeaker)
            .put("model", "bulbul:v3")
            .put("output_audio_codec", "mp3")
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val raw = try {
            api.synthesize(apiKey.trim(), payload).string()
        } catch (e: Throwable) {
            throw IOException(SarvamErrorParser.parse(e), e)
        }

        val audioBase64 = runCatching {
            val json = JSONObject(raw)
            json.getJSONArray("audios").getString(0)
        }.getOrNull() ?: throw IOException("Missing audio in Sarvam response: ${raw.take(100)}")

        val bytes = Base64.decode(audioBase64, Base64.DEFAULT)
        if (bytes.isEmpty()) throw IOException("Empty audio from Sarvam")

        val file = File(audioDir(), phraseCacheFileName(phraseKey, normSpeaker))
        file.writeBytes(bytes)
        file
    }

    /** Deletes every cached clip for one donation (all speakers, legacy, imports). */
    fun deleteDonationCache(donationId: String): Int =
        runCatching { deleteDonationFiles(audioDir(), donationId) }.getOrDefault(0)

    /**
     * T0.1 grace-edit invalidation: deletes regenerable Sarvam speaker clips
     * only, then quarantines irreplaceable human imports to `.bak-<timestamp>`
     * so playback falls through to the corrected figure while the original
     * stays recoverable. Never throws; best-effort.
     */
    fun invalidateDonationAudio(
        donationId: String,
        now: Long = System.currentTimeMillis()
    ): AudioInvalidation = runCatching {
        val dir = audioDir()
        val sarvam = deleteSarvamClipFiles(dir, donationId)
        val human = quarantineHumanImportFiles(dir, donationId, now)
        AudioInvalidation(sarvamDeleted = sarvam, humanQuarantined = human)
    }.getOrDefault(AudioInvalidation(0, 0))

    /** Deletes only regenerable Sarvam speaker clips; human slot untouched. Never throws. */
    fun deleteSarvamClips(donationId: String): Int =
        runCatching { deleteSarvamClipFiles(audioDir(), donationId) }.getOrDefault(0)

    /**
     * Quarantines human-import slots (`donation_{id}[_roster].mp3`) to
     * `*.bak-<timestamp>`. Never throws.
     */
    fun quarantineHumanImports(donationId: String, now: Long = System.currentTimeMillis()): Int =
        runCatching { quarantineHumanImportFiles(audioDir(), donationId, now) }.getOrDefault(0)

    /** True if a quarantined `.bak-` backup exists for [donationId]. Never throws. */
    fun hasAudioBackup(donationId: String): Boolean = runCatching {
        val prefix = "donation_${donationId}"
        audioDir().listFiles()?.any { it.isFile && it.name.startsWith(prefix) && isBackupFile(it.name) } == true
    }.getOrDefault(false)

    /**
     * One-time rename migration (Phase 1): pre-speaker `donation_{id}[_roster].mp3`
     * files are attributed to [speaker] (the active voice at upgrade time) by
     * renaming them to the speaker-suffixed name. Human imports ride along and
     * stay playable under the active speaker. Idempotent; never throws.
     * Returns the number of files renamed-or-removed.
     */
    fun migrateLegacyCache(speaker: String): Int =
        runCatching { migrateLegacyToSpeaker(audioDir(), speaker) }.getOrDefault(0)

    private fun audioDir(): File =
        File(context.cacheDir, "audio").apply { if (!exists()) mkdirs() }

    /**
     * P4 cache ceiling: drops clips older than [maxAgeDays], then the oldest
     * beyond [maxFiles]. Covers Sarvam `donation_*.mp3`, F6 `phrase_*.mp3`
     * AND Phase-3 CAS `audio_{hash}.mp3` files (H1 — CAS used to be exempt
     * forever, leaking one file per correction/voice/language change);
     * quarantined `.bak-<timestamp>` backups are always exempt (recoverable
     * human audio), as are chime/test files. Never throws.
     * Returns the deleted count.
     *
     * @param excludeHashes live-queue CAS hashes (bare hex, no prefix) that
     * must survive even under count pressure — a pruned live clip simply
     * regenerates on next prefetch, but excluding avoids the churn.
     */
    fun pruneCache(
        excludeIds: Set<String>,
        maxFiles: Int,
        maxAgeDays: Int,
        excludeHashes: Set<String> = emptySet()
    ): Int = runCatching {
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 60L * 60L * 1000L
        val ours = audioDir().listFiles()
            ?.filter { it.isFile && isPrunableCacheFile(it.name) }
            .orEmpty()
        // Prefix exclusion: every speaker variant, legacy slot and human import
        // for a live queue id survives; only truly stray clips are victims.
        // CAS exclusion: exact-name match on live hashes (prefix scan can't
        // map a hash back to a donation id).
        val excluded = ours
            .filter { f ->
                excludeIds.any { id -> f.name.startsWith("donation_${id}") } ||
                    (f.name.startsWith("audio_") && excludeHashes.any { h -> f.name == casFileName(h) })
            }
            .map { it.name }
            .toSet()
        var deleted = 0
        selectPruneVictims(ours, maxFiles, cutoff, excluded).forEach {
            if (runCatching { it.delete() }.getOrDefault(false)) deleted++
        }
        deleted
    }.getOrDefault(0)

    companion object {
        /**
         * Pure filename mapping — unit-tested (no Android needed).
         * Sarvam cloud clips are namespaced per normalized speaker so voices
         * never collide on disk.
         */
        fun cacheFileName(donationId: String, roster: Boolean = false, speaker: String = "shubh"): String {
            val norm = normalizeSarvamSpeaker(speaker)
            return if (roster) "donation_${donationId}_${norm}_roster.mp3"
            else "donation_${donationId}_${norm}.mp3"
        }

        /**
         * F6 pure phrase filename mapping — unit-tested.
         * Intro/outro clips are namespaced per normalized speaker and phraseKey.
         */
        fun phraseCacheFileName(phraseKey: String, speaker: String = "shubh"): String {
            val norm = normalizeSarvamSpeaker(speaker)
            val safeKey = phraseKey.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(50)
            return "phrase_${safeKey}_${norm}.mp3"
        }

        /**
         * Pre-speaker legacy name, now the human-import slot
         * (`donation_{id}[_roster].mp3`). Kept for import/migration only —
         * never generate Sarvam audio under this name.
         */
        fun legacyCacheFileName(donationId: String, roster: Boolean = false): String =
            if (roster) "donation_${donationId}_roster.mp3" else "donation_${donationId}.mp3"

        /** Pure victim selection — unit-tested (no Android needed). */
        fun selectPruneVictims(
            files: List<File>,
            maxFiles: Int,
            cutoffMillis: Long,
            excludeNames: Set<String>
        ): List<File> {
            val eligible = files.filter { it.name !in excludeNames }
            val aged = eligible.filter { it.lastModified() < cutoffMillis }
            val rest = (eligible - aged.toSet()).sortedBy { it.lastModified() }
            val overCount = (rest.size - maxFiles).coerceAtLeast(0)
            return aged + rest.take(overCount)
        }

        /**
         * Pure deletion helper — removes every clip belonging to [donationId]
         * (all speaker variants, legacy slots, human imports, roster, plus
         * quarantined `.bak-` backups). Only touches `donation_{id}*` names;
         * chime/test files survive. Used for whole-event scrub.
         * Returns the deleted count; never throws.
         */
        fun deleteDonationFiles(audioDir: File, donationId: String): Int {
            val prefix = "donation_${donationId}"
            return runCatching {
                audioDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(prefix) && (it.name.endsWith(".mp3") || isBackupFile(it.name)) }
                    .orEmpty()
                    .count { runCatching { it.delete() }.getOrDefault(false) }
            }.getOrDefault(0)
        }

        /** T0.1 result: regenerable deletes vs quarantined human backups. */
        data class AudioInvalidation(val sarvamDeleted: Int, val humanQuarantined: Int)

        /** Quarantined backups are `*.bak-<millis>`; exempt from playback/prune. */
        fun isBackupFile(name: String): Boolean = ".bak-" in name

        /**
         * Phase-3 content-addressed name (`audio_{hash}.mp3`, shared across
         * phones). The hash is full-hex SHA-256 — see `audioHashFor`.
         */
        fun casFileName(hash: String): String = "audio_${hash}.mp3"

        /** CAS slots only ever hold full-hex hashes (never donation ids). */
        fun isCasHash(hash: String): Boolean = hash.matches(Regex("[0-9a-f]{64}"))

        /**
         * T0.4 pure ceiling filter — unit-tested (no Android needed). Evictable:
         * Sarvam `donation_*.mp3` clips, F6 `phrase_*.mp3` clips, and Phase-3
         * CAS `audio_{hash}.mp3` clips (H1). Never evictable: quarantined
         * `.bak-` human backups, the baked `temple_chime.wav`, the
         * `audio_test_sample.mp3` levels check, or any other name. Phrase
         * clips are shared across rows (not per-donation), so the caller's
         * `excludeIds` never cover them — same age/count ceiling applies.
         * CAS clips for live rows are covered by `excludeHashes` instead.
         */
        fun isPrunableCacheFile(name: String): Boolean {
            if (isBackupFile(name)) return false
            if (!name.endsWith(".mp3")) return false
            if (name.startsWith("donation_") || name.startsWith("phrase_")) return true
            // CAS slot: exactly `audio_<64 hex>.mp3`, nothing else.
            return name.startsWith("audio_") && name.length == "audio_".length + 64 + ".mp3".length &&
                isCasHash(name.removePrefix("audio_").removeSuffix(".mp3"))
        }

        /**
         * Pure Sarvam-only deletion — removes `donation_{id}_{speaker}[_roster].mp3`
         * variants but preserves the speaker-agnostic human slots
         * (`donation_{id}.mp3` / `donation_{id}_roster.mp3`) and any `.bak-`
         * backups. Donation ids are UUIDs (no underscores), so the legacy shape
         * is exactly the id optionally followed by `_roster`.
         * Returns the deleted count; never throws.
         */
        fun deleteSarvamClipFiles(audioDir: File, donationId: String): Int {
            val prefix = "donation_${donationId}"
            val legacyFull = legacyCacheFileName(donationId, false)
            val legacyRoster = legacyCacheFileName(donationId, true)
            return runCatching {
                audioDir.listFiles()
                    ?.filter { f ->
                        f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".mp3") &&
                            f.name != legacyFull && f.name != legacyRoster && !isBackupFile(f.name)
                    }
                    .orEmpty()
                    .count { runCatching { it.delete() }.getOrDefault(false) }
            }.getOrDefault(0)
        }

        /**
         * Pure human quarantine — renames existing human slots to
         * `donation_{id}[_roster].mp3.bak-<now>` so playback (exact-name lookup)
         * and prune (`*.mp3` filter) both skip them while the bytes stay
         * recoverable. Empty/missing slots are left alone.
         * Returns the quarantined count; never throws.
         */
        fun quarantineHumanImportFiles(audioDir: File, donationId: String, now: Long): Int {
            return runCatching {
                var done = 0
                listOf(legacyCacheFileName(donationId, false), legacyCacheFileName(donationId, true)).forEach { legacyName ->
                    val src = File(audioDir, legacyName)
                    if (src.isFile && src.exists() && src.length() > 0) {
                        var dest = File(audioDir, "$legacyName.bak-$now")
                        if (dest.exists()) dest = File(audioDir, "$legacyName.bak-$now-${System.nanoTime()}")
                        if (runCatching { src.renameTo(dest) }.getOrDefault(false)) done++
                    }
                }
                done
            }.getOrDefault(0)
        }

        /**
         * Pure rename migration — attributes pre-speaker legacy files to
         * [speaker] (`donation_{id}.mp3` → `donation_{id}_{speaker}.mp3`,
         * same for `_roster`). Donation ids are UUIDs (no underscores), so the
         * legacy shape is unambiguous: remainder is exactly the id, optionally
         * followed by `_roster`. Already-migrated speaker files are untouched;
         * a legacy file whose target exists is deleted as a duplicate.
         * Returns renamed-or-removed count; never throws.
         */
        fun migrateLegacyToSpeaker(audioDir: File, speaker: String): Int {
            val norm = normalizeSarvamSpeaker(speaker)
            return runCatching {
                val files = audioDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith("donation_") && it.name.endsWith(".mp3") }
                    .orEmpty()
                var done = 0
                files.forEach { file ->
                    val core = file.name.removePrefix("donation_").removeSuffix(".mp3")
                    // Legacy full: "<id>" — legacy roster: "<id>_roster".
                    // UUIDs contain hyphens, never underscores, so any extra
                    // underscore segment means already-migrated/imported shape.
                    val (id, roster) = when {
                        !core.contains('_') -> core to false
                        core.endsWith("_roster") && !core.removeSuffix("_roster").contains('_') ->
                            core.removeSuffix("_roster") to true
                        else -> return@forEach
                    }
                    if (id.isBlank()) return@forEach
                    val target = File(audioDir, cacheFileName(id, roster, norm))
                    if (target.exists()) {
                        if (runCatching { file.delete() }.getOrDefault(false)) done++
                    } else {
                        if (runCatching { file.renameTo(target) }.getOrDefault(false)) done++
                    }
                }
                done
            }.getOrDefault(0)
        }
    }
}
