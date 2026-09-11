package com.shankaravam.festival.core.tts

import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.HONORIFIC_KUMARI
import com.shankaravam.festival.domain.model.HONORIFIC_SRI
import com.shankaravam.festival.domain.model.HONORIFIC_SRIMATI

enum class AnnouncementLanguage { TELUGU, ENGLISH, BILINGUAL }

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
        "hanuman" in name || "హనుమాన్" in name || "anjaney" in name || "ఆంజనేయ" in name ->
            FestivalPreset.HANUMAN_JAYANTHI
        "shivaratr" in name || "shivratr" in name || "శివరాత్రి" in name || "shiva" in name || "శివ" in name ->
            FestivalPreset.MAHA_SHIVARATRI
        "rama" in name || "రామ" in name || "sita" in name || "సీత" in name || "kalyana" in name || "కళ్యాణ" in name ->
            FestivalPreset.SRI_RAMA_NAVAMI
        "durga" in name || "దుర్గ" in name || "navaratri" in name || "navratri" in name ||
            "నవరాత్ర" in name || "devi" in name || "దేవి" in name || "dussehra" in name || "దసరా" in name ->
            FestivalPreset.KANAKA_DURGAMMA
        "annadan" in name || "అన్నదాన" in name || "temple" in name || "ఆలయ" in name || "devel" in name || "అభివృద్ధి" in name ->
            FestivalPreset.TEMPLE_ANNADANAM
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
        introTe = "మన {location} లో వినాయక చవితి ఉత్సవాల సందర్భంగా విరాళాలు అందించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to Vinayaka Chavithi celebrations in {location}:"
    ),
    KANAKA_DURGAMMA(
        id = "kanaka_durgamma",
        titleTe = "కనకదుర్గమ్మ ఉత్సవాలు",
        titleEn = "Kanaka Durgamma Navaratri",
        introTe = "మన {location} లో కనకదుర్గమ్మ దేవి శరన్నవరాత్రి ఉత్సవాలకు విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Kanaka Durgamma Navaratri in {location}:"
    ),
    SRI_RAMA_NAVAMI(
        id = "sri_rama_navami",
        titleTe = "శ్రీరామనవమి",
        titleEn = "Sri Rama Navami",
        introTe = "మన {location} లో శ్రీ సీతారాముల కళ్యాణ మహోత్సవానికి విరాళాలు అందించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to Sri Sita Rama Kalyana Mahotsavam in {location}:"
    ),
    HANUMAN_JAYANTHI(
        id = "hanuman_jayanthi",
        titleTe = "శ్రీ హనుమాన్ జయంతి",
        titleEn = "Hanuman Jayanthi",
        introTe = "మన {location} లో శ్రీ హనుమాన్ జయంతి మహోత్సవానికి విరాళాలు సమర్పించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Hanuman Jayanthi celebrations in {location}:"
    ),
    MAHA_SHIVARATRI(
        id = "maha_shivaratri",
        titleTe = "మహా శివరాత్రి",
        titleEn = "Maha Shivaratri",
        introTe = "మన {location} లో మహా శివరాత్రి జాగరణ మరియు ఉత్సవాల సందర్భంగా విరాళాలు అందించిన భక్తుల వివరాలు:",
        introEn = "Details of devotees who contributed to Maha Shivaratri celebrations in {location}:"
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
        introTe = "మన {location} లో {event} సందర్భంగా విరాళాలు సమర్పించిన దాతల వివరాలు:",
        introEn = "Details of donors who contributed to {event} in {location}:"
    );

    fun formatIntro(location: String, eventName: String, lang: AnnouncementLanguage): String {
        val loc = location.ifBlank { "మన ప్రాంతం" }
        val locEn = location.ifBlank { "our locality" }
        val ev = eventName.ifBlank { titleTe }
        val evEn = eventName.ifBlank { titleEn }

        return when (lang) {
            AnnouncementLanguage.TELUGU -> introTe.replace("{location}", loc).replace("{event}", ev)
            AnnouncementLanguage.ENGLISH -> introEn.replace("{location}", locEn).replace("{event}", evEn)
            AnnouncementLanguage.BILINGUAL -> {
                val te = introTe.replace("{location}", loc).replace("{event}", ev)
                val en = introEn.replace("{location}", locEn).replace("{event}", evEn)
                "$te $en"
            }
        }
    }
}

/**
 * Single donation announcement (for real-time pop-up when entered at counter).
 */
fun buildDonationAnnouncement(
    donation: Donation,
    eventName: String,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU
): String {
    val teluguName = donation.pronunciationText?.ifBlank { null } ?: donation.donorName
    val event = eventName.ifBlank { "ఉత్సవం" }
    val telugu = teluguSingleAnnouncement(donation, teluguName, event)
    return when (language) {
        AnnouncementLanguage.TELUGU -> telugu
        AnnouncementLanguage.ENGLISH -> englishSingleAnnouncement(donation, event)
        AnnouncementLanguage.BILINGUAL -> "$telugu ${englishSingleAnnouncement(donation, event)}"
    }
}

/**
 * Pandal Roster Mode item:
 * Crisp, natural mic announcement without repeating "సమర్పించారు... ధన్యవాదాలు" each time.
 * e.g.: "శ్రీ రెడబోతు సందీప్ రెడ్డి గారు — వెయ్యి నూట పదహారు రూపాయలు"
 */
fun buildRosterItemAnnouncement(
    donation: Donation,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU
): String {
    val teluguName = donation.pronunciationText?.ifBlank { null } ?: donation.donorName
    val honorific = honorificTe(donation)
    val telugu = if (!donation.isNonCash) {
        "$honorific $teluguName గారు, ${TeluguNumberFormatter.wordsForAmount(donation.amount)}."
    } else {
        val item = donation.itemDescription?.ifBlank { null } ?: "సేవ"
        val qty = donation.quantity?.let { q ->
            if (q % 1.0 == 0.0 && q < 100_000) "${TeluguNumberFormatter.wordsForNumber(q.toLong())} " else "$q "
        } ?: ""
        val unit = donation.unit?.ifBlank { null }?.let { "$it " } ?: ""
        "$honorific $teluguName గారు, $qty$unit$item."
    }

    val english = if (!donation.isNonCash) {
        "${honorificEn(donation.honorific)} ${donation.donorName}, ${formatInr(donation.amount)}."
    } else {
        val qty = buildString {
            donation.quantity?.let { append("$it ") }
            donation.unit?.ifBlank { null }?.let { append("$it ") }
        }
        val item = donation.itemDescription?.ifBlank { null } ?: "service"
        "${honorificEn(donation.honorific)} ${donation.donorName}, $qty$item."
    }

    return when (language) {
        AnnouncementLanguage.TELUGU -> telugu
        AnnouncementLanguage.ENGLISH -> english
        AnnouncementLanguage.BILINGUAL -> "$telugu $english"
    }
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

private fun teluguSingleAnnouncement(donation: Donation, name: String, event: String): String {
    val thanks = "ధన్యవాదాలు!"
    val honorific = honorificTe(donation)
    return if (!donation.isNonCash) {
        "$honorific $name గారు $event కోసం " +
            "${TeluguNumberFormatter.wordsForAmount(donation.amount)} విరాళంగా అందించారు. $thanks"
    } else {
        val item = donation.itemDescription?.ifBlank { null } ?: "సేవ"
        val qty = donation.quantity?.let { q ->
            if (q % 1.0 == 0.0 && q < 100_000) "${TeluguNumberFormatter.wordsForNumber(q.toLong())} " else "$q "
        } ?: ""
        val unit = donation.unit?.ifBlank { null }?.let { "$it " } ?: ""
        "$honorific $name గారు $event కోసం $qty$unit$item విరాళంగా అందించారు. $thanks"
    }
}

private fun englishSingleAnnouncement(donation: Donation, event: String): String {
    val name = donation.donorName
    val honorific = honorificEn(donation.honorific)
    val eventEn = event.ifBlank { "the festival" }
    return if (!donation.isNonCash) {
        "$honorific $name donated ${formatInr(donation.amount)} towards $eventEn. Thank you!"
    } else {
        val qty = buildString {
            donation.quantity?.let { append("$it ") }
            donation.unit?.ifBlank { null }?.let { append("$it ") }
        }
        val item = donation.itemDescription?.ifBlank { null } ?: "service"
        "$honorific $name donated $qty$item towards $eventEn. Thank you!"
    }
}

/** Short line spoken before any public test so the organizer can check levels. */
const val AUDIO_TEST_LINE = "పరీక్ష. ఆడియో సరిగ్గా పనిచేస్తోంది."
