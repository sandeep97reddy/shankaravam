package com.shankaravam.festival.core.util

import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.HONORIFIC_SRIMATI
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptFormatterTest {

    private fun donation(
        id: String = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
        name: String = "Ramesh",
        amount: Double = 1116.0,
        payment: String = "Cash",
        addedTime: Long = 1_786_000_000_000L
    ) = Donation(
        id = id,
        eventId = "e1",
        donorName = name,
        amount = amount,
        paymentMethod = payment,
        addedBy = "Counter 1 - Ramesh",
        addedTime = addedTime,
        createdAt = addedTime
    )

    @Test
    fun cash_receipt_carries_brand_donor_amount_and_counter() {
        val text = buildWhatsAppReceipt(donation(), "Sri Vinayaka Utsav Samiti - 2026", "Counter 1 - Ramesh")
        assertTrue(text.contains("Sri Vinayaka Utsav Samiti - 2026"))
        assertTrue(text.contains("శ్రీ Ramesh గారు"))
        assertTrue(text.contains("₹1,116"))
        assertTrue(text.contains("(Cash)"))
        assertTrue(text.contains("Counter 1 - Ramesh"))
        assertTrue(text.contains("#F47AC10B"))
        assertTrue(text.contains("🙏"))
    }

    @Test
    fun online_payment_labels_the_channel() {
        val text = buildWhatsAppReceipt(donation(amount = 5000.0, payment = "UPI"), "E", "C")
        assertTrue(text.contains("₹5,000"))
        assertTrue(text.contains("(UPI)"))
    }

    @Test
    fun non_cash_receipt_shows_item_instead_of_amount() {
        val text = buildWhatsAppReceipt(
            donation().copy(isNonCash = true, itemDescription = "Rice bag", quantity = 2.0, unit = "bags"),
            "E", "C"
        )
        assertTrue(text.contains("2 bags Rice bag"))
        assertFalse(text.contains("₹"))
    }

    @Test
    fun honorific_and_blank_fields_degrade_gracefully() {
        val text = buildWhatsAppReceipt(
            donation(name = "  Lakshmi  ").copy(honorific = HONORIFIC_SRIMATI),
            "", ""
        )
        assertTrue(text.contains("శ్రీమతి Lakshmi గారు"))
        assertTrue(text.contains("ఉత్సవ సమితి")) // blank event fallback
        assertFalse(text.contains("గోత్రం")) // never collected, never printed
    }

    @Test
    fun date_line_renders_in_a_fixed_locale() {
        val default = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
            val text = buildWhatsAppReceipt(donation(addedTime = 1_786_000_000_000L), "E", "C")
            assertTrue(text.contains("2026"), text)
        } finally {
            TimeZone.setDefault(default)
        }
    }
}
