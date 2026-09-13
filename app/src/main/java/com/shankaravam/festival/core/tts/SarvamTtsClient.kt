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
 * Defaults to "priya" (official recommended female speaker for Telugu).
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
    else -> "priya"
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
    fun cachedFile(donationId: String, roster: Boolean = false, speaker: String = "priya"): File? {
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

    suspend fun getOrGenerateAudio(
        donationId: String,
        text: String,
        apiKey: String,
        speaker: String = "priya",
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

    /** Deletes every cached clip for one donation (all speakers, legacy, imports). */
    fun deleteDonationCache(donationId: String): Int =
        runCatching { deleteDonationFiles(audioDir(), donationId) }.getOrDefault(0)

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
     * beyond [maxFiles]. Only our `donation_*.mp3` files; never throws.
     * Returns the deleted count.
     */
    fun pruneCache(excludeIds: Set<String>, maxFiles: Int, maxAgeDays: Int): Int = runCatching {
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 60L * 60L * 1000L
        val ours = audioDir().listFiles()
            ?.filter { it.isFile && it.name.startsWith("donation_") && it.name.endsWith(".mp3") }
            .orEmpty()
        // Prefix exclusion: every speaker variant, legacy slot and human import
        // for a live queue id survives; only truly stray clips are victims.
        val excluded = ours
            .filter { f -> excludeIds.any { id -> f.name.startsWith("donation_${id}") } }
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
        fun cacheFileName(donationId: String, roster: Boolean = false, speaker: String = "priya"): String {
            val norm = normalizeSarvamSpeaker(speaker)
            return if (roster) "donation_${donationId}_${norm}_roster.mp3"
            else "donation_${donationId}_${norm}.mp3"
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
         * (all speaker variants, legacy slots, human imports, roster). Only
         * touches `donation_{id}*` names; chime/test files survive.
         * Returns the deleted count; never throws.
         */
        fun deleteDonationFiles(audioDir: File, donationId: String): Int {
            val prefix = "donation_${donationId}"
            return runCatching {
                audioDir.listFiles()
                    ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".mp3") }
                    .orEmpty()
                    .count { runCatching { it.delete() }.getOrDefault(false) }
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
