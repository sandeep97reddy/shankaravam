package com.shankaravam.festival.core.tts

import java.security.MessageDigest

/**
 * P4 shared-clip support (WhatsApp roster audio, human-recorded or Sarvam —
 * the cache plays any valid mp3 bytes). All pure + JVM unit-tested.
 */

/** Fingerprint of an announcement rendering; equal across devices for equal input. */
fun audioHashFor(text: String, language: String, speaker: String, roster: Boolean): String =
    runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$language|$speaker|$roster|$text".toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { "%02x".format(it) }
    }.getOrDefault("unhashable")

/** Donor identity offered to the batch matcher. */
data class ClipDonor(
    val id: String,
    val donorName: String,
    val pronunciation: String? = null
)

data class ClipMatchResult(
    /** fileIndex → donationId. Each file used once, each donor matched once. */
    val matched: Map<Int, String>,
    val unmatched: List<Int>
)

/** "01 Ramesh.mp3" → "01ramesh" (extension stripped, letters+digits kept, incl. Telugu). */
fun normalizeClipName(fileName: String): String {
    val base = fileName.substringBeforeLast('.', fileName)
    // Telugu vowel signs / virama are Unicode marks, not letters — keep them
    // or "రమేష్" would lose its shape and never match.
    return base.lowercase().filter { c ->
        c.isLetterOrDigit() ||
            c.category == CharCategory.NON_SPACING_MARK ||
            c.category == CharCategory.COMBINING_SPACING_MARK ||
            c.category == CharCategory.ENCLOSING_MARK
    }
}

/**
 * Matches shared clip filenames to donors by name containment. Filenames from
 * WhatsApp carry no donation ids, so matching is best-effort: exact hits
 * first, then containment; leftovers are reported, never force-attached.
 */
fun matchRosterClips(fileNames: List<String>, donors: List<ClipDonor>): ClipMatchResult {
    val normalizedFiles = fileNames.map(::normalizeClipName)
    val normalizedDonors = donors.map { d ->
        d.id to listOfNotNull(
            d.donorName.takeIf { it.isNotBlank() },
            d.pronunciation?.takeIf { it.isNotBlank() }
        ).map { normalizeClipName(it) }
            .filter { it.length >= 2 }
    }
    val matched = mutableMapOf<Int, String>()
    val usedDonors = mutableSetOf<String>()
    // Pass 1: exact equality (after track-number stripping).
    normalizedFiles.forEachIndexed { index, file ->
        val bare = file.replace(Regex("^[0-9]+"), "")
        normalizedDonors.forEach { (id, names) ->
            if (id !in usedDonors && index !in matched && names.any { it == file || it == bare }) {
                matched[index] = id
                usedDonors += id
            }
        }
    }
    // Pass 2: containment either way.
    normalizedFiles.forEachIndexed { index, file ->
        if (index in matched) return@forEachIndexed
        normalizedDonors.forEach { (id, names) ->
            if (id !in usedDonors && index !in matched &&
                names.any { it in file || file in it }
            ) {
                matched[index] = id
                usedDonors += id
            }
        }
    }
    val unmatched = normalizedFiles.indices.filter { it !in matched }
    return ClipMatchResult(matched, unmatched)
}
