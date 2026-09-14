package com.shankaravam.festival.presentation.donation

import com.shankaravam.festival.core.tts.AnnouncementLanguage
import com.shankaravam.festival.core.tts.buildDonationAnnouncement
import com.shankaravam.festival.domain.model.Donation

/**
 * Announcement preview TEXT for the detail sheet (plan §14).
 * Signature frozen since G3 — delegates to the G4 template engine so text
 * and speech can never drift apart.
 */
fun buildAnnouncementPreview(
    donation: Donation,
    eventName: String,
    effectiveAmount: Double? = null
): String =
    buildDonationAnnouncement(donation, eventName, AnnouncementLanguage.TELUGU, effectiveAmount)
