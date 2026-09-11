package com.durgamma.festival.presentation.donation

import com.durgamma.festival.core.tts.AnnouncementLanguage
import com.durgamma.festival.core.tts.buildDonationAnnouncement
import com.durgamma.festival.domain.model.Donation

/**
 * Announcement preview TEXT for the detail sheet (plan §14).
 * Signature frozen since G3 — delegates to the G4 template engine so text
 * and speech can never drift apart.
 */
fun buildAnnouncementPreview(donation: Donation, eventName: String): String =
    buildDonationAnnouncement(donation, eventName, AnnouncementLanguage.TELUGU)
