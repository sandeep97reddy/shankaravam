package com.shankaravam.festival.core.util

import com.shankaravam.festival.domain.model.Donation
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val inrFormat: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
        maximumFractionDigits = 0
    }

/** ₹5,000 style, no decimals (paise are never entered at the counter). */
fun formatInr(amount: Double): String = inrFormat.format(amount)

/**
 * P1 hygiene: SimpleDateFormat is not thread-safe, so one instance per thread.
 * (Today the receipt builds on main, but sharing may move to IO later.)
 */
private val receiptDateFormat: ThreadLocal<SimpleDateFormat> =
    ThreadLocal.withInitial { SimpleDateFormat("dd-MMM-yyyy, hh:mm a", Locale.ENGLISH) }

/** "12-Sep-2026, 08:30 PM" style; device timezone (offline-safe). */
fun formatReceiptDateTime(millis: Long): String =
    runCatching { receiptDateFormat.get()?.format(Date(millis)) ?: "" }.getOrDefault("")

/**
 * 1-tap WhatsApp digital receipt (feature #5): temple-branded bilingual text
 * with WhatsApp *bold* markup. Pure string building — sharing itself is a
 * native ACTION_SEND intent (zero cloud). No gothram: it is never collected.
 * IDs are UUIDs, so the reference is the first 8 chars, honestly labeled.
 */
fun buildWhatsAppReceipt(
    donation: Donation,
    eventName: String,
    counterName: String
): String {
    val ref = donation.id.take(8).uppercase().ifBlank { "—" }
    val donorLine = "${donation.honorific} ${donation.donorName.trim()} గారు"
    val giftLine = if (donation.isNonCash) {
        val qty = donation.quantity?.let { q ->
            if (!q.isFinite()) null
            else (if (q % 1.0 == 0.0) q.toLong().toString() else q.toString())
        }
        val item = listOfNotNull(qty, donation.unit?.ifBlank { null }, donation.itemDescription?.ifBlank { null })
            .joinToString(" ").ifEmpty { "వస్తు కానుక" }
        "🎁 *కానుక / Offering:* $item"
    } else {
        "💰 *మొత్తం / Amount:* ${formatInr(donation.amount)} (${donation.paymentMethod})"
    }
    return buildString {
        appendLine("🚩 *${eventName.trim().ifBlank { "ఉత్సవ సమితి" }}* 🚩")
        appendLine("--------------------------------")
        appendLine("🧾 *రసీదు / Receipt:* #$ref")
        appendLine("👤 *దాత / Donor:* $donorLine")
        appendLine(giftLine)
        appendLine("📅 *తేదీ / Date:* ${formatReceiptDateTime(donation.addedTime)}")
        appendLine("📍 *కౌంటర్ / Counter:* ${counterName.trim().ifBlank { "—" }}")
        appendLine("--------------------------------")
        append("🙏 *మీ కుటుంబానికి ఆయురారోగ్యాలు కలగాలని కోరుకుంటున్నాము!*")
    }
}
