package com.shankaravam.festival.domain.model

import androidx.compose.runtime.Immutable

/**
 * Phase 2 explicit voice-engine mode (RC5 remedy). Replaces the old implicit
 * rule ("key present ⇒ always try cloud"):
 * - OFFLINE_NATIVE: Android on-device TTS (+ human-imported clips, which are
 *   local recordings). Zero cloud calls, zero quota burn.
 * - SARVAM_CLOUD: Sarvam speaker file when cached/generable, human import as
 *   override, native Telugu as per-utterance fallback.
 * Default is SARVAM_CLOUD, which preserves the pre-Phase-2 behavior exactly.
 */
enum class VoiceEngineMode { OFFLINE_NATIVE, SARVAM_CLOUD }

fun voiceModeOf(name: String?): VoiceEngineMode =
    runCatching { VoiceEngineMode.valueOf(name?.uppercase() ?: "") }
        .getOrDefault(VoiceEngineMode.SARVAM_CLOUD)

/**
 * Single source of truth for "which voice speaks next" (Phase 2). Every
 * voice picker — Settings card and Announcement queue card — reads AND writes
 * this shape, so the two can never disagree or show a stuck label (RC1/RC3).
 * Pure + unit-testable.
 */
@Immutable
data class VoiceConfig(
    val engineMode: VoiceEngineMode = VoiceEngineMode.SARVAM_CLOUD,
    val sarvamSpeaker: String = "priya",
    val nativeVoice: String? = null,
    val hasSarvamKey: Boolean = false
) {
    /**
     * The one shared active-voice label. Branches on [engineMode] FIRST —
     * the old bug checked `hasKey` first, which made the native branches
     * unreachable whenever a key existed.
     */
    fun displayLabel(): String = when (engineMode) {
        VoiceEngineMode.OFFLINE_NATIVE ->
            if (nativeVoice != null) "📱 Android Voice (${nativeVoice.substringAfterLast("-", "Offline")})"
            else "📱 Android System Voice (Offline)"
        VoiceEngineMode.SARVAM_CLOUD ->
            if (!hasSarvamKey) "⚠️ Sarvam selected — no API key (using offline voice)"
            else when (com.shankaravam.festival.core.tts.normalizeSarvamSpeaker(sarvamSpeaker)) {
                "priya" -> "🌸 Priya (Sarvam Cloud HD)"
                "shubh" -> "🎙️ Shubh (Sarvam Cloud HD)"
                "kavitha" -> "🌸 Kavitha (Sarvam Cloud HD)"
                "ratan" -> "🎙️ Ratan (Sarvam Cloud HD)"
                else -> "☁️ Sarvam Voice (${sarvamSpeaker.replaceFirstChar { it.uppercase() }})"
            }
    }

    /** Short status line under the picker; also mode-first. */
    fun statusLine(): String = when {
        engineMode == VoiceEngineMode.OFFLINE_NATIVE ->
            "Offline Android voice active. Switch to Sarvam Cloud for studio clarity."
        hasSarvamKey -> "✓ High-fidelity Sarvam cloud voice active."
        else -> "Offline Android voice active. Add a Sarvam API key in Settings ⚙️ for studio clarity."
    }
}

/**
 * F5 shared-key apply decision (pure + unit-tested). The head's published key
 * reaches collectors automatically:
 * - blank remote → never touch local (no clobber, no mode flip)
 * - identical → no-op (no keyVersion bump → no prefetch storm)
 * - new remote → apply key+speaker; flip to cloud unless the user explicitly
 *   locked offline (their Offline chip tap wins over the pull).
 */
@Immutable
data class SharedKeyDecision(
    val applyKey: Boolean,
    val flipToCloud: Boolean
)

fun shouldApplySharedKey(
    localKey: String,
    remoteKey: String,
    offlineLocked: Boolean,
    respectLock: Boolean = true
): SharedKeyDecision {
    if (remoteKey.isBlank()) return SharedKeyDecision(false, false)
    if (remoteKey.trim() == localKey.trim() && localKey.isNotBlank()) {
        return SharedKeyDecision(false, false)
    }
    val locked = respectLock && offlineLocked
    return SharedKeyDecision(applyKey = true, flipToCloud = !locked)
}

/**
 * Phase 3 transparency pill (RC4 remedy): shown in the Announcement queue
 * instead of silently switching voices mid-queue when the 20-calls/30-min
 * budget runs out. Pure + unit-testable.
 */
fun quotaPillText(
    used: Int,
    max: Int,
    resetAt: Long,
    now: Long = System.currentTimeMillis()
): String {
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date(resetAt))
    return "⚠️ Cloud quota reached ($used/$max). Speaking via offline voice until $time."
}

/**
 * F6 Native Android TTS voice metadata (pure + unit-tested).
 * Captures network requirement and quality so the UI can badge voices
 * and the engine can prioritize natural network voices over robotic embedded ones.
 */
@Immutable
data class NativeVoiceInfo(
    val name: String,
    val isNetwork: Boolean,
    val quality: Int = 300
) {
    val displayName: String
        get() = name.substringAfterLast("-", name)

    val badgeLabel: String
        get() = if (isNetwork) "🌐 Network" else "💾 Offline"
}

/**
 * F6 voice selection algorithm (pure + unit-tested):
 * Prioritizes high-quality network Telugu voice (e.g. Google TTS high quality),
 * then high-quality embedded Telugu voice, then system fallback.
 */
fun pickBestTeluguVoice(voices: List<NativeVoiceInfo>): NativeVoiceInfo? {
    if (voices.isEmpty()) return null
    // 1. High-quality network voice
    val network = voices.filter { it.isNetwork }.maxByOrNull { it.quality }
    if (network != null) return network

    // 2. High-quality embedded voice
    val embedded = voices.filter { !it.isNetwork }.maxByOrNull { it.quality }
    if (embedded != null) return embedded

    // 3. Fallback
    return voices.firstOrNull()
}

