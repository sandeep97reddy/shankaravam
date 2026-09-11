package com.durgamma.festival.core.tts

import com.durgamma.festival.core.util.formatInr
import com.durgamma.festival.domain.model.Donation

enum class AnnouncementLanguage { TELUGU, ENGLISH, BILINGUAL }

/**
 * Template engine (plan §12). G4 owns the amount rendering (Telugu words);
 * G3's preview delegates here so text and speech can never drift apart.
 */
fun buildDonationAnnouncement(
    donation: Donation,
    eventName: String,
    language: AnnouncementLanguage = AnnouncementLanguage.TELUGU
): String {
    val teluguName = donation.pronunciationText?.ifBlank { null } ?: donation.donorName
    val event = eventName.ifBlank { "ఉత్సవం" }
    val telugu = teluguAnnouncement(donation, teluguName, event)
    return when (language) {
        AnnouncementLanguage.TELUGU -> telugu
        AnnouncementLanguage.ENGLISH -> englishAnnouncement(donation, event)
        AnnouncementLanguage.BILINGUAL -> "$telugu ${englishAnnouncement(donation, event)}"
    }
}

private fun teluguAnnouncement(donation: Donation, name: String, event: String): String {
    val thanks = "ధన్యవాదాలు!"
    return if (!donation.isNonCash) {
        "శ్రీ $name గారు $event కోసం " +
            "${TeluguNumberFormatter.wordsForAmount(donation.amount)} విరాళంగా అందించారు. $thanks"
    } else {
        val item = donation.itemDescription?.ifBlank { null } ?: "సేవ"
        val qty = donation.quantity?.let { q ->
            if (q % 1.0 == 0.0 && q < 100_000) "${TeluguNumberFormatter.wordsForNumber(q.toLong())} " else "$q "
        } ?: ""
        val unit = donation.unit?.ifBlank { null }?.let { "$it " } ?: ""
        "శ్రీ $name గారు $event కోసం $qty$unit$item విరాళంగా అందించారు. $thanks"
    }
}

private fun englishAnnouncement(donation: Donation, event: String): String {
    val name = donation.donorName
    val eventEn = event.ifBlank { "the festival" }
    return if (!donation.isNonCash) {
        "Sri $name donated ${formatInr(donation.amount)} towards $eventEn. Thank you!"
    } else {
        val qty = buildString {
            donation.quantity?.let { append("$it ") }
            donation.unit?.ifBlank { null }?.let { append("$it ") }
        }
        val item = donation.itemDescription?.ifBlank { null } ?: "service"
        "Sri $name donated $qty$item towards $eventEn. Thank you!"
    }
}

/** Short line spoken before any public test so the organizer can check levels. */
const val AUDIO_TEST_LINE = "పరీక్ష. ఆడియో సరిగ్గా పనిచేస్తోంది."
