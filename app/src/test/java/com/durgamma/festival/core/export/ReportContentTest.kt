package com.durgamma.festival.core.export

import com.durgamma.festival.domain.model.Donation
import com.durgamma.festival.domain.model.DonationStatus
import com.durgamma.festival.domain.model.Expense
import com.durgamma.festival.domain.usecase.BalanceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportContentTest {

    @Test
    fun donations_csv_quotes_commas_and_telugu() {
        val csv = ReportContent.donationsCsv(
            listOf(
                Donation(
                    id = "d1", eventId = "e1",
                    donorName = "Ramesh, Jr.",
                    pronunciationText = "రమేష్",
                    amount = 5000.0, tags = listOf("Cash", "Sponsor"),
                    status = DonationStatus.CONFIRMED, addedTime = 0L
                )
            )
        )
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("donor_name,"))
        assertTrue("\"Ramesh, Jr.\"" in lines[1])
        assertTrue("రమేష్" in lines[1])
        assertTrue("Cash;Sponsor" in lines[1])
    }

    @Test
    fun expenses_csv_has_header_and_row() {
        val csv = ReportContent.expensesCsv(
            listOf(
                Expense(
                    id = "x1", eventId = "e1", amount = 1500.0,
                    description = "Flowers", category = "Decorations",
                    dateMillis = 0L, addedTime = 0L
                )
            )
        )
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("amount,description,"))
        assertTrue("Flowers" in lines[1])
    }

    @Test
    fun whatsapp_summary_carries_bilingual_totals() {
        val text = ReportContent.whatsAppSummary(
            eventName = "Vinayaka Chavithi 2026",
            templeName = "Sri Durgamma Temple",
            totals = BalanceSnapshot(cashCollected = 7000.0, balance = 5500.0, donorCount = 5, nonCashCount = 1),
            corrections = emptyList(),
            cashCollectedText = "₹7,000",
            expenseTotalText = "₹1,500",
            balanceText = "₹5,500",
            pledgedText = "₹13,000"
        )
        assertTrue("Vinayaka Chavithi 2026" in text)
        assertTrue("₹5,500" in text)
        assertTrue("Donors: 5" in text)
        assertTrue("మిగులు" in text)
    }

    @Test
    fun donation_line_marks_cancelled() {
        val line = ReportContent.donationLine(
            Donation(
                id = "d", eventId = "e", donorName = "X",
                amount = 10.0, status = DonationStatus.CANCELLED, addedTime = 0L
            ),
            "₹10"
        )
        assertTrue("[cancelled]" in line)
    }
}
