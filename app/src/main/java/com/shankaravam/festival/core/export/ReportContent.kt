package com.shankaravam.festival.core.export

import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.model.ExpenseStatus
import com.shankaravam.festival.domain.usecase.BalanceSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure report-content builders (plan §20 exports). JVM-testable: no Android,
 * no PdfDocument here — [ReportExporter] renders these onto pages/files.
 */
object ReportContent {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun formatTime(millis: Long): String = dateFormat.format(Date(millis))

    private fun csvCell(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun donationsCsv(donations: List<Donation>): String = buildString {
        appendLine("donor_name,pronunciation,amount,currency,is_non_cash,item_description,quantity,unit,payment_method,tags,status,announce,added_by,added_time")
        donations.forEach { d ->
            appendLine(
                listOf(
                    csvCell(d.donorName),
                    csvCell(d.pronunciationText ?: ""),
                    d.amount.toString(),
                    d.currency,
                    d.isNonCash.toString(),
                    csvCell(d.itemDescription ?: ""),
                    d.quantity?.toString() ?: "",
                    csvCell(d.unit ?: ""),
                    csvCell(d.paymentMethod),
                    csvCell(d.tags.joinToString(";")),
                    d.status.name,
                    d.announcementEnabled.toString(),
                    csvCell(d.addedBy),
                    formatTime(d.addedTime)
                ).joinToString(",")
            )
        }
    }

    fun expensesCsv(expenses: List<Expense>): String = buildString {
        appendLine("amount,description,category,date,paid_by,payment_method,vendor,status,added_by,added_time")
        expenses.forEach { e ->
            appendLine(
                listOf(
                    e.amount.toString(),
                    csvCell(e.description),
                    csvCell(e.category),
                    formatTime(e.dateMillis),
                    csvCell(e.paidBy),
                    csvCell(e.paymentMethod),
                    csvCell(e.vendor ?: ""),
                    e.status.name,
                    csvCell(e.addedBy),
                    formatTime(e.addedTime)
                ).joinToString(",")
            )
        }
    }

    /**
     * WhatsApp share text: Telugu-first totals with English gloss (plan §20).
     * Kept short — full detail travels in the PDF.
     */
    fun whatsAppSummary(
        eventName: String,
        templeName: String,
        totals: BalanceSnapshot,
        corrections: List<Correction>,
        cashCollectedText: String,
        expenseTotalText: String,
        balanceText: String,
        pledgedText: String
    ): String = buildString {
        appendLine("🛕 $templeName — $eventName")
        appendLine("నివేదిక • Report (${formatTime(System.currentTimeMillis())})")
        appendLine()
        appendLine("💰 వసూళ్లు Collected: $cashCollectedText")
        appendLine("➖ ఖర్చులు Expenses: $expenseTotalText")
        appendLine("✅ మిగులు Balance: $balanceText")
        appendLine("🤝 వాగ్దానాలు Pledged: $pledgedText")
        appendLine("🙏 దాతలు Donors: ${totals.donorCount} • 📦 వస్తువులు Non-cash: ${totals.nonCashCount}")
        if (corrections.isNotEmpty()) {
            appendLine("✏️ సవరణలు Corrections: ${corrections.size}")
        }
        appendLine()
        append("వివరాలకు PDF చూడండి • See PDF for details.")
    }

    /** Rows for the PDF body, already ordered and capped by the caller. */
    fun donationLine(d: Donation, amountText: String): String {
        val what = if (d.isNonCash) d.itemDescription ?: "Item" else amountText
        val status = when (d.status) {
            DonationStatus.CONFIRMED -> "confirmed"
            DonationStatus.RECEIVED -> "received"
            DonationStatus.PLEDGED -> "pledged"
            DonationStatus.PARTIALLY_RECEIVED -> "partial"
            DonationStatus.CANCELLED -> "cancelled"
        }
        return "${d.donorName} — $what [$status]"
    }

    fun expenseLine(e: Expense, amountText: String): String {
        val cancelled = if (e.status == ExpenseStatus.CANCELLED) " [cancelled]" else ""
        return "${e.description} (${e.category}) — $amountText$cancelled"
    }
}
