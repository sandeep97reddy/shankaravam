package com.durgamma.festival.presentation.donation

import com.durgamma.festival.domain.model.Donation

/**
 * Announcement preview TEXT for the detail sheet (plan §14).
 * G4 replaces the amount rendering with TeluguNumberFormatter and routes this
 * exact string into the DualTtsEngine — keep this function signature stable.
 */
fun buildAnnouncementPreview(donation: Donation, eventName: String): String {
    val name = donation.pronunciationText?.ifBlank { null } ?: donation.donorName
    val thanks = "ధన్యవాదాలు!"
    return if (!donation.isNonCash) {
        val amountText = if (donation.amount % 1.0 == 0.0) {
            donation.amount.toLong().toString()
        } else {
            donation.amount.toString()
        }
        "శ్రీ $name గారు $eventName కోసం $amountText రూపాయలు విరాళంగా అందించారు. $thanks"
    } else {
        val qty = buildString {
            donation.quantity?.let {
                append(if (it % 1.0 == 0.0) it.toLong().toString() else it.toString())
                append(' ')
            }
            donation.unit?.ifBlank { null }?.let { append("$it ") }
        }
        val item = donation.itemDescription?.ifBlank { null } ?: "సేవ"
        "శ్రీ $name గారు $eventName కోసం $qty$item విరాళంగా అందించారు. $thanks"
    }
}
