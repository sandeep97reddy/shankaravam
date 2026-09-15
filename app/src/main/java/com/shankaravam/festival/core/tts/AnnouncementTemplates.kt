package com.shankaravam.festival.core.tts

import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.HONORIFIC_KUMARI
import com.shankaravam.festival.domain.model.HONORIFIC_SRI
import com.shankaravam.festival.domain.model.HONORIFIC_SRIMATI

enum class AnnouncementLanguage { TELUGU, ENGLISH, BILINGUAL }

/**
 * Spoken-template version marker for disk-cached audio clips.
 *
 * Dual caching architecture:
 * 1. Row-level donation announcements: Content-Addressed Storage (CAS).
 *    CAS hash covers the exact spoken text bytes ([audioHashFor] in AudioImport.kt and
 *    `canonicalHashInput` in tools/worker/src/lib.ts). Any text change automatically
 *    yields a new hash. Do NOT wire [TTS_TEMPLATE_VERSION] into [audioHashFor], as that
 *    would invalidate all donation clips across devices and exhaust daily cloud synthesis quota.
 * 2. Intro phrase clips: Keyed on preset, eventId tail, language, location, and explicitly
 *    namespaced by [TTS_TEMPLATE_VERSION] in [introPhraseKey]. Bumping this version safely
 *    regenerates intro clips while preserving existing donation audio cache.
 */
const val TTS_TEMPLATE_VERSION = "v2"

/** Pandal mic title as stored on the donation (never blank — defaults to శ్రీ). */
fun honorificTe(donation: Donation): String =
    donation.honorific.ifBlank { HONORIFIC_SRI }

/** English gloss for the stored honorific (Sri / Smt. / Kumari). */
fun honorificEn(honorific: String): String = when (honorific.trim()) {
    HONORIFIC_SRIMATI -> "Smt."
    HONORIFIC_KUMARI -> "Kumari"
    else -> "Sri"
}

/**
 * Auto-links a queue intro preset from the event name ("హనుమాన్ జయంతి" →
 * Hanuman Jayanthi instead of the Vinayaka default). Returns null when
 * nothing matches so the caller keeps its default.
 */
fun presetForEventName(eventName: String): FestivalPreset? {
    val name = eventName.lowercase()
    return when {
        // 1. Grama Devatha Jatara (MUST precede Rama Navami to prevent "grama" -> "rama" substring collision!)
        "jatara" in name || "జాతర" in name || "tirunalla" in name || "తిరునాళ్ళ" in name ||
            "తిరునాళ్ల" in name || "gangamma" in name || "గంగమ్మ" in name ||
            "poleramma" in name || "పోలేరమ్మ" in name || "ankalamma" in name || "అంకమ్మ" in name ->
            FestivalPreset.GRAMA_DEVATHA_JATARA

        // 2. Bonalu & Bathukamma
        "bonalu" in name || "బోనాలు" in name || "bathukamma" in name || "బతుకమ్మ" in name ||
            "mahankali" in name || "మహంకాళి" in name || "ellamma" in name || "ఎల్లమ్మ" in name ->
            FestivalPreset.BONALU_BATHUKAMMA

        // 3. Ayyappa Swami Mandala Pooja (MUST precede generic Annadanam)
        "ayyappa" in name || "అయ్యప్ప" in name || "padipooja" in name || "పడిపూజ" in name ||
            "shabarimalai" in name || "శబరిమల" in name ||
            "mandala pooja" in name || "మండల పూజ" in name ||
            "mandala deeksha" in name || "మండల దీక్ష" in name ->
            FestivalPreset.AYYAPPA_MANDALAM

        // 4. Sri Krishna Janmashtami
        "krishna" in name || "కృష్ణ" in name || "janmashtami" in name || "జన్మాష్టమి" in name ||
            "gokulashtami" in name || "గోకులాష్టమి" in name || "utlotsav" in name || "ఉట్లోత్సవ" in name ->
            FestivalPreset.KRISHNA_JANMASHTAMI

        // 5. Hanuman Jayanthi
        "hanuman" in name || "హనుమాన్" in name || "anjaney" in name || "ఆంజనేయ" in name ->
            FestivalPreset.HANUMAN_JAYANTHI

        // 6. Maha Shivaratri
        "shivaratr" in name || "shivratr" in name || "శివరాత్రి" in name || "shiva" in name || "శివ" in name ->
            FestivalPreset.MAHA_SHIVARATRI

        // 7. Sri Rama Navami
        "rama" in name || "రామ" in name || "sita" in name || "సీత" in name || "kalyana" in name || "కళ్యాణ" in name ->
            FestivalPreset.SRI_RAMA_NAVAMI

        // 8. Kanaka Durgamma / Dasara Navaratri
        "durga" in name || "దుర్గ" in name || "navaratri" in name || "navratri" in name ||
            "నవరాత్ర" in name || "devi" in name || "దేవి" in name || "dussehra" in name || "దసరా" in name ->
            FestivalPreset.KANAKA_DURGAMMA

        // 9. Temple Annadanam / Development
        "annadan" in name || "అన్నదాన" in name || "temple" in name || "ఆలయ" in name || "devel" in name || "అభివృద్ధి" in name ->
            FestivalPreset.TEMPLE_ANNADANAM

        // 10. Vinayaka Chavithi
        "vinayak" in name || "వినాయక" in name || "ganesh" in name || "గణేష్" in name ||
            "chavithi" in name || "చవితి" in name ->
            FestivalPreset.VINAYAKA_CHAVITHI

        else -> null
    }
}

enum class FestivalPreset(val id: String, val titleTe: String, val titleEn: String, val introTe: String, val introEn: String) {
    VINAYAKA_CHAVITHI(
        id = "vinayaka_chavithi",
        titleTe = "వినాయక చవితి",
        titleEn = "Vinayaka Chavithi",
        introTe = "{location} వినాయక చవితి ఉత్సవాల సందర్భంగా విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of donors who contributed to Vinayaka Chavithi celebrations in {location}:"
    ),
    KANAKA_DURGAMMA(
        id = "kanaka_durgamma",
        titleTe = "కనకదుర్గమ్మ ఉత్సవాలు",
        titleEn = "Kanaka Durgamma Navaratri",
        introTe = "{location} కనకదుర్గమ్మ దేవి శరన్నవరాత్రి ఉత్సవాలకు విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Kanaka Durgamma Navaratri in {location}:"
    ),
    SRI_RAMA_NAVAMI(
        id = "sri_rama_navami",
        titleTe = "శ్రీరామనవమి",
        titleEn = "Sri Rama Navami",
        introTe = "{location} శ్రీ సీతారాముల కళ్యాణ మహోత్సవానికి విరాళాలు అందించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to Sri Sita Rama Kalyana Mahotsavam in {location}:"
    ),
    HANUMAN_JAYANTHI(
        id = "hanuman_jayanthi",
        titleTe = "శ్రీ హనుమాన్ జయంతి",
        titleEn = "Hanuman Jayanthi",
        introTe = "{location} శ్రీ హనుమాన్ జయంతి మహోత్సవానికి విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Hanuman Jayanthi celebrations in {location}:"
    ),
    MAHA_SHIVARATRI(
        id = "maha_shivaratri",
        titleTe = "మహా శివరాత్రి",
        titleEn = "Maha Shivaratri",
        introTe = "{location} మహా శివరాత్రి జాగరణ మరియు ఉత్సవాల సందర్భంగా విరాళాలు అందించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Maha Shivaratri celebrations in {location}:"
    ),
    BONALU_BATHUKAMMA(
        id = "bonalu_bathukamma",
        titleTe = "శ్రీ మహంకాళి బోనాలు మరియు బతుకమ్మ",
        titleEn = "Bonalu & Bathukamma",
        introTe = "{location} శ్రీ మహంకాళి బోనాలు మరియు బతుకమ్మ ఉత్సవాల సందర్భంగా విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Sri Mahankali Bonalu and Bathukamma celebrations in {location}:"
    ),
    AYYAPPA_MANDALAM(
        id = "ayyappa_mandalam",
        titleTe = "శ్రీ అయ్యప్ప స్వామి మండల పూజ",
        titleEn = "Ayyappa Swami Mandala Pooja",
        introTe = "స్వామియే శరణం అయ్యప్ప! {location} శ్రీ అయ్యప్ప స్వామి మండల పూజ మరియు నిత్య అన్నదాన మహోత్సవానికి విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Swamiye Saranam Ayyappa! Details of devotees who contributed to Sri Ayyappa Swami Mandala Pooja and Annadanam in {location}:"
    ),
    KRISHNA_JANMASHTAMI(
        id = "krishna_janmashtami",
        titleTe = "శ్రీ కృష్ణ జన్మాష్టమి",
        titleEn = "Sri Krishna Janmashtami",
        introTe = "{location} శ్రీ కృష్ణ జన్మాష్టమి మరియు ఉట్లోత్సవ వేడుకల సందర్భంగా విరాళాలు అందించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to Sri Krishna Janmashtami celebrations in {location}:"
    ),
    GRAMA_DEVATHA_JATARA(
        id = "grama_devatha_jatara",
        titleTe = "గ్రామ దేవత తిరునాళ్ళ / జాతర",
        titleEn = "Grama Devatha Jatara",
        introTe = "{location} గ్రామ దేవత తిరునాళ్ళ మరియు జాతర మహోత్సవాల సందర్భంగా విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Grama Devatha Tirunalla and Jatara celebrations in {location}:"
    ),
    TEMPLE_ANNADANAM(
        id = "temple_annadanam",
        titleTe = "ఆలయ అన్నదానం / అభివృద్ధి",
        titleEn = "Temple Annadanam / Development",
        introTe = "మన ఆలయ అభివృద్ధి మరియు నిత్య అన్నదాన కార్యక్రమానికి విరాళాలు సమర్పించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to temple development and Annadanam:"
    ),
    CUSTOM(
        id = "custom",
        titleTe = "ఇతర / కస్టమ్",
        titleEn = "Other / Custom",
        introTe = "{location} {event} సందర్భంగా విరాళాలు సమర్పించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to {event} in {location}:"
    );

    fun formatIntro(location: String, eventName: String, lang: AnnouncementLanguage): String {
        val trimmedLoc = location.trim()
        val locTe = if (trimmedLoc.isBlank()) "మన ప్రాంతంలో" else "మన $trimmedLoc లో"
        val locEn = if (trimmedLoc.isBlank()) "our locality" else trimmedLoc
        val ev = eventName.trim().ifBlank { titleTe }
        val evEn = eventName.trim().ifBlank { titleEn }

        return when (lang) {
            AnnouncementLanguage.TELUGU -> introTe.replace("{location}", locTe).replace("{event}", ev)
            AnnouncementLanguage.ENGLISH -> introEn.replace("{location}", locEn).replace("{event}", evEn)
            AnnouncementLanguage.BILINGUAL -> {
                val te = introTe.replace("{location}", locTe).replace("{event}", ev)
                val en = introEn.replace("{location}", locEn).replace("{event}", evEn)
                "$te $en"
            }
        }
    }
}

/**
 * Single donation announcement (for real-time pop-up when entered at counter).
 * T0.2: [effectiveAmount] overrides the spoken cash figure (post-correction);
 * defaults to the raw row so existing callers keep working. Non-cash rows
 * ignore it (qty/item corrections are out of scope).
 */
fun buildDonationAnnouncement(
    donation: Donation,
    eventName: String,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU,
    effectiveAmount: Double? = null
): String {
    val teluguName = donation.pronunciationText?.ifBlank { null } ?: donation.donorName
    val event = eventName.ifBlank { "ఉత్సవం" }
    val amount = effectiveAmount ?: donation.amount
    val telugu = teluguSingleAnnouncement(donation, teluguName, event, amount)
    return when (language) {
        AnnouncementLanguage.TELUGU -> telugu
        AnnouncementLanguage.ENGLISH -> englishSingleAnnouncement(donation, event, amount)
        AnnouncementLanguage.BILINGUAL -> "$telugu ${englishSingleAnnouncement(donation, event, amount)}"
    }
}

/**
 * Formats non-cash offering text cleanly, preventing duplicate quantity or unit
 * stutter if the donor/volunteer already embedded them inside [itemDescription]
 * (e.g. "50 kg rice bag" with quantity=50 and unit="kg").
 */
fun formatOfferingText(
    itemDescription: String?,
    quantity: Double?,
    unit: String?,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU
): String {
    val rawItem = itemDescription?.trim()?.ifBlank { null }
    val cleanUnit = unit?.trim()?.ifBlank { null }

    if (rawItem == null && quantity == null && cleanUnit == null) {
        return if (language == AnnouncementLanguage.ENGLISH) "service" else "సేవ"
    }

    val item = rawItem ?: if (language == AnnouncementLanguage.ENGLISH) "items" else "వస్తువులు"

    val itemAlreadyHasQty = quantity?.let { q ->
        if (!q.isFinite() || q <= 0) return@let true
        val numStr = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
        val numRegex = Regex("""(?<!\d)${Regex.escape(numStr)}(?!\d)""")
        if (numRegex.containsMatchIn(item)) return@let true

        if (language == AnnouncementLanguage.TELUGU && q % 1.0 == 0.0 && q < 100_000) {
            val teWords = safeWordsForNumber(q.toLong()).trim()
            if (teWords.isNotEmpty() && item.contains(teWords, ignoreCase = true)) return@let true
        }
        false
    } ?: true

    val itemAlreadyHasUnit = cleanUnit?.let { u ->
        if (item.contains(u, ignoreCase = true)) return@let true
        val uLower = u.lowercase()
        when {
            uLower.startsWith("kg") || uLower.startsWith("కిలో") || uLower.startsWith("కేజీ") ->
                item.contains("kg", ignoreCase = true) || item.contains("కేజీ") || item.contains("కిలో")
            uLower.startsWith("bag") || uLower.startsWith("బస్తా") || uLower.startsWith("సంచి") ->
                item.contains("bag", ignoreCase = true) || item.contains("బస్తా") || item.contains("సంచి")
            uLower.startsWith("tin") || uLower.startsWith("టిన్") || uLower.startsWith("డబ్బా") ->
                item.contains("tin", ignoreCase = true) || item.contains("టిన్") || item.contains("డబ్బా")
            uLower.startsWith("litre") || uLower.startsWith("liter") || uLower.startsWith("లీటర్") ->
                item.contains("litre", ignoreCase = true) || item.contains("liter", ignoreCase = true) || item.contains("లీటర్")
            else -> false
        }
    } ?: true

    val qtyPrefix = if (!itemAlreadyHasQty && quantity.isFinite() && quantity > 0) {
        if (language == AnnouncementLanguage.TELUGU) {
            if (quantity % 1.0 == 0.0 && quantity < 100_000) "${safeWordsForNumber(quantity.toLong())} " else "$quantity "
        } else {
            if (quantity % 1.0 == 0.0) "${quantity.toLong()} " else "$quantity "
        }
    } else ""

    val unitPrefix = if (!itemAlreadyHasUnit) {
        "$cleanUnit "
    } else ""

    return "$qtyPrefix$unitPrefix$item".trim()
}

/**
 * Pandal Roster Mode item:
 * Crisp, natural mic announcement without repeating "సమర్పించారు... ధన్యవాదాలు" each time.
 * e.g.: "శ్రీ రెడబోతు సందీప్ రెడ్డి గారు — వెయ్యి నూట పదహారు రూపాయలు"
 */
fun buildRosterItemAnnouncement(
    donation: Donation,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU,
    effectiveAmount: Double? = null
): String {
    val teluguName = donation.pronunciationText?.ifBlank { null } ?: donation.donorName
    val honorific = honorificTe(donation)
    val amount = effectiveAmount ?: donation.amount
    val telugu = if (!donation.isNonCash) {
        "$honorific $teluguName గారు, ${safeWordsForAmount(amount)}."
    } else {
        val offering = formatOfferingText(
            itemDescription = donation.itemDescription,
            quantity = donation.quantity,
            unit = donation.unit,
            language = AnnouncementLanguage.TELUGU
        )
        "$honorific $teluguName గారు, $offering."
    }

    val english = if (!donation.isNonCash) {
        "${honorificEn(donation.honorific)} ${donation.donorName}, ${formatInr(amount)}."
    } else {
        val offering = formatOfferingText(
            itemDescription = donation.itemDescription,
            quantity = donation.quantity,
            unit = donation.unit,
            language = AnnouncementLanguage.ENGLISH
        )
        "${honorificEn(donation.honorific)} ${donation.donorName}, $offering."
    }

    return when (language) {
        AnnouncementLanguage.TELUGU -> telugu
        AnnouncementLanguage.ENGLISH -> english
        AnnouncementLanguage.BILINGUAL -> "$telugu $english"
    }
}

/**
 * M3: cache key for the roster intro phrase. Keyed on everything the intro
 * TEXT varies with — preset, event, queue language, and location — so a
 * language switch or location edit regenerates instead of replaying a stale
 * clip in the wrong language. Pure — pin with unit tests.
 */
fun introPhraseKey(
    preset: FestivalPreset,
    eventId: String?,
    language: AnnouncementLanguage,
    location: String
): String {
    val tail = eventId?.takeLast(6)?.ifBlank { "loc" } ?: "loc"
    val locHash = "%08x".format(location.trim().lowercase().hashCode())
    return "intro_${TTS_TEMPLATE_VERSION}_${preset.name.lowercase()}_${tail}_${language.name.lowercase()}_$locHash"
}

/** Opening header for continuous queue playback. */
fun buildOpeningAnnouncement(
    location: String,
    eventName: String,
    preset: FestivalPreset = FestivalPreset.VINAYAKA_CHAVITHI,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU
): String = preset.formatIntro(location, eventName, language)

/** Closing outro for continuous queue playback. */
fun buildClosingAnnouncement(
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU
): String = when (language) {
    AnnouncementLanguage.TELUGU ->
        "విరాళాలు సమర్పించిన దాతలందరికీ ఉత్సవ కమిటీ తరపున హృదయపూర్వక ధన్యవాదాలు. దేవుని కృపాకటాక్షాలు మీ కుటుంబాలపై ఎల్లప్పుడూ ఉండాలని కోరుకుంటున్నాము."
    AnnouncementLanguage.ENGLISH ->
        "Heartfelt thanks to all the donors from the festival committee. May divine blessings always be upon you and your family."
    AnnouncementLanguage.BILINGUAL ->
        "విరాళాలు సమర్పించిన దాతలందరికీ ఉత్సవ కమిటీ తరపున హృదయపూర్వక ధన్యవాదాలు. Heartfelt thanks to all the donors from the committee."
}

private fun teluguSingleAnnouncement(donation: Donation, name: String, event: String, amount: Double = donation.amount): String {
    val thanks = "వారికి ఉత్సవ కమిటీ తరపున హృదయపూర్వక ధన్యవాదాలు. వారి కుటుంబం చల్లగా ఉండాలని కోరుకుంటున్నాము."
    val honorific = honorificTe(donation)
    return if (!donation.isNonCash) {
        "$honorific $name గారు $event సందర్భంగా, " +
            "${safeWordsForAmount(amount)} విరాళంగా సమర్పించారు. $thanks"
    } else {
        val offering = formatOfferingText(
            itemDescription = donation.itemDescription,
            quantity = donation.quantity,
            unit = donation.unit,
            language = AnnouncementLanguage.TELUGU
        )
        "$honorific $name గారు $event సందర్భంగా, $offering విరాళంగా సమర్పించారు. $thanks"
    }
}

private fun englishSingleAnnouncement(donation: Donation, event: String, amount: Double = donation.amount): String {
    val name = donation.donorName
    val honorific = honorificEn(donation.honorific)
    val eventEn = event.ifBlank { "the festival" }
    return if (!donation.isNonCash) {
        "$honorific $name donated ${formatInr(amount)} towards $eventEn. Thank you!"
    } else {
        val offering = formatOfferingText(
            itemDescription = donation.itemDescription,
            quantity = donation.quantity,
            unit = donation.unit,
            language = AnnouncementLanguage.ENGLISH
        )
        "$honorific $name donated $offering towards $eventEn. Thank you!"
    }
}

/** Short line spoken before any public test so the organizer can check levels. */
const val AUDIO_TEST_LINE = "పరీక్ష. ఆడియో సరిగ్గా పనిచేస్తోంది."

/**
 * L6: the sentence the CLOUD test path synthesizes (donation-shaped, so the
 * levels check exercises a realistic clip). Shared with the quota pre-check's
 * hash in AnnouncementQueueViewModel.testAudio — keep the two in sync via
 * this constant. Native fallback speaks [AUDIO_TEST_LINE] instead.
 */
const val AUDIO_TEST_SYNTH_LINE = "శ్రీ మహేష్ బాబు గారు, వెయ్యి నూట పదహారు రూపాయలు."

/** Crash-proof wrappers: formatters never throw now, this guards any future regression. */
private fun safeWordsForAmount(amount: Double): String =
    runCatching { TeluguNumberFormatter.wordsForAmount(amount) }.getOrDefault("సున్నా రూపాయలు")

private fun safeWordsForNumber(n: Long): String =
    runCatching { TeluguNumberFormatter.wordsForNumber(n) }.getOrDefault("సున్నా")
