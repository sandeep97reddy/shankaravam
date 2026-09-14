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
     * WhatsApp share text: Devotional, elegant, structured committee announcement.
     * Combines Telugu-first clarity, WhatsApp bolding, unicode separators, and emojis.
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
        appendLine("🚩 *శ్రీ శుభమస్తు • SHANKARAVAM* 🚩")
        val temple = templeName.trim().ifBlank { "ఉత్సవ సమితి" }
        appendLine("🛕 *$temple*")
        appendLine("🎉 *$eventName*")
        appendLine("📅 నివేదిక • Report: ${formatTime(System.currentTimeMillis())}")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("📊 *ఆర్థిక సారాంశం / FINANCIAL SUMMARY*")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("💰 *వసూళ్లు (Collected):* $cashCollectedText")
        appendLine("💸 *ఖర్చులు (Expenses):* $expenseTotalText")
        appendLine("────────────────────")
        appendLine("⚖️ *నికర మిగులు (Net Balance):* $balanceText")
        appendLine("🤝 *రావలసిన వాగ్దానాలు (Pledged):* $pledgedText")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("📈 *కార్యకలాపాల వివరాలు / METRICS*")
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("👥 *దాతలు / Donors: ${totals.donorCount}*")
        appendLine("📦 *వస్తు కానుకలు / Non-cash: ${totals.nonCashCount}*")
        if (corrections.isNotEmpty()) {
            appendLine("✏️ *సవరణలు / Corrections: ${corrections.size}*")
        }
        appendLine("━━━━━━━━━━━━━━━━━━━━")
        appendLine("📄 *వివరాలకు PDF చూడండి • See PDF for details.*")
        appendLine("🙏 *భక్తులందరికీ ఆయురారోగ్య ఐశ్వర్యాలు కలగాలని ప్రార్థనలు!*")
        append("⚡ _Generated via ShankaRavam Festival App_")
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
