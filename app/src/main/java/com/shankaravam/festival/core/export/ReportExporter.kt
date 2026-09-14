package com.shankaravam.festival.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.core.util.formatReceiptDateTime
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.DonationStatus
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.model.ExpenseStatus
import com.shankaravam.festival.domain.usecase.BalanceSnapshot
import com.shankaravam.festival.domain.usecase.calculateBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Local report engine: Framework PdfDocument + plain file IO + ACTION_SEND.
 * Generates audit-ready, prestigious temple & festival financial statements.
 * Zero external PDF library, 100% offline, fully vector rendered.
 */
class ReportExporter(context: Context) {

    private val appContext = context.applicationContext

    suspend fun exportPdf(
        eventName: String,
        templeName: String,
        donations: List<Donation>,
        expenses: List<Expense>,
        corrections: List<Correction>
    ): File = withContext(Dispatchers.IO) {
        // T0.2: executive summary uses effective figures; ledger tables keep
        // raw rows with the audit-trail section showing corrections.
        val totals = calculateBalance(
            donations,
            expenses,
            com.shankaravam.festival.domain.model.groupCorrectionsByTarget(corrections)
        )
        val file = reportFile("shankaravam_report_${System.currentTimeMillis()}.pdf")
        val writer = PdfStatementWriter(
            templeName = templeName.trim().ifBlank { "ఉత్సవ సమితి" },
            eventName = eventName.trim().ifBlank { "Festival Event" }
        )
        try {
            writer.startNewPage()
            writer.drawHeaderBanner()
            writer.drawExecutiveSummaryCard(totals, expenses.size)

            // 1. Donations Ledger
            writer.drawSectionTitle("DONATIONS LEDGER / దాతల కానుకల వివరాలు", donations.size)
            if (donations.isEmpty()) {
                writer.drawEmptyNotice("No donations recorded yet for this festival.")
            } else {
                writer.drawDonationTableHeader()
                capped(donations, MAX_ROWS).forEachIndexed { idx, d ->
                    writer.drawDonationRow(idx, d, formatInr(d.amount))
                }
                if (donations.size > MAX_ROWS) {
                    writer.drawOverflowNote(donations.size)
                }
            }

            // 2. Expenses Breakdown
            writer.gap(12f)
            writer.drawSectionTitle("EXPENSES BREAKDOWN / ఉత్సవ ఖర్చుల వివరాలు", expenses.size)
            if (expenses.isEmpty()) {
                writer.drawEmptyNotice("No expenses recorded yet for this festival.")
            } else {
                writer.drawExpenseTableHeader()
                capped(expenses, MAX_ROWS).forEachIndexed { idx, e ->
                    writer.drawExpenseRow(idx, e, formatInr(e.amount))
                }
                if (expenses.size > MAX_ROWS) {
                    writer.drawOverflowNote(expenses.size)
                }
            }

            // 3. Financial Corrections & Audit Trail
            if (corrections.isNotEmpty()) {
                writer.gap(12f)
                writer.drawSectionTitle("AUDIT TRAIL & CORRECTIONS / సవరణల నివేదిక", corrections.size)
                writer.drawCorrectionTableHeader()
                capped(corrections, MAX_ROWS).forEachIndexed { idx, c ->
                    writer.drawCorrectionRow(idx, c)
                }
                if (corrections.size > MAX_ROWS) {
                    writer.drawOverflowNote(corrections.size)
                }
            }

            // 4. Official Certification Block
            writer.drawCertificationBlock()

            writer.writeTo(file)
        } finally {
            writer.close()
        }
        file
    }

    suspend fun exportDonationsCsv(donations: List<Donation>): File = withContext(Dispatchers.IO) {
        reportFile("donations_${System.currentTimeMillis()}.csv")
            .apply { writeText(ReportContent.donationsCsv(donations)) }
    }

    suspend fun exportExpensesCsv(expenses: List<Expense>): File = withContext(Dispatchers.IO) {
        reportFile("expenses_${System.currentTimeMillis()}.csv")
            .apply { writeText(ReportContent.expensesCsv(expenses)) }
    }

    fun shareText(text: String, title: String = "Share report") {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        appContext.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareFile(file: File, mimeType: String, title: String = "Share report") {
        val uri = fileUri(file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        appContext.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)

    private fun reportFile(name: String): File =
        File(appContext.cacheDir, "reports").apply { mkdirs() }.let { File(it, name) }

    private fun <T> capped(list: List<T>, max: Int): List<T> = list.take(max)

    companion object {
        private const val MAX_ROWS = 250
    }

    /**
     * Prestigious Temple & Festival PDF Statement Renderer.
     * Features branded deep maroon banners, executive financial summary box,
     * alternating row tables, status badges, page footers, and committee sign-off block.
     */
    private class PdfStatementWriter(
        private val templeName: String,
        private val eventName: String
    ) {
        private val document = PdfDocument()
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = MARGIN_TOP
        private var finished = false

        // Theme Paints
        private val maroonPaint = Paint().apply {
            color = Color.rgb(0x4A, 0x0E, 0x17)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val goldPaint = Paint().apply {
            color = Color.rgb(0xFF, 0xD7, 0x00)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val heroBgPaint = Paint().apply {
            color = Color.rgb(0xFA, 0xF6, 0xF0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val borderPaint = Paint().apply {
            color = Color.rgb(0xE2, 0xD6, 0xC5)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        private val rowAltPaint = Paint().apply {
            color = Color.rgb(0xF9, 0xF7, 0xF3)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        private val rowBorderPaint = Paint().apply {
            color = Color.rgb(0xEA, 0xE4, 0xDA)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
            isAntiAlias = true
        }
        private val sigLinePaint = Paint().apply {
            color = Color.rgb(0xB0, 0xA4, 0x94)
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        // Text & Metric Paints
        private val bannerTitlePaint = Paint().apply {
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            isAntiAlias = true
        }
        private val bannerSubTitlePaint = Paint().apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xFF, 0xE0, 0x82)
            isAntiAlias = true
        }
        private val bannerSubHeaderPaint = Paint().apply {
            textSize = 7.5f
            color = Color.rgb(0xFF, 0xFD, 0xF7)
            isAntiAlias = true
        }
        private val bannerAppTitlePaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xFF, 0xD7, 0x00)
            isAntiAlias = true
        }
        private val whiteHeaderSmallPaint = Paint().apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            isAntiAlias = true
        }
        private val goldHeaderSmallPaint = Paint().apply {
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xFF, 0xD7, 0x00)
            isAntiAlias = true
        }
        private val tableHeaderPaint = Paint().apply {
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            isAntiAlias = true
        }
        private val heroLabelPaint = Paint().apply {
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x5A, 0x4D, 0x41)
            isAntiAlias = true
        }
        private val heroGreenPaint = Paint().apply {
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x1B, 0x5E, 0x20)
            isAntiAlias = true
        }
        private val heroRedPaint = Paint().apply {
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xB7, 0x1C, 0x1C)
            isAntiAlias = true
        }
        private val heroMaroonPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x4A, 0x0E, 0x17)
            isAntiAlias = true
        }
        private val heroSubPaint = Paint().apply {
            textSize = 7.5f
            color = Color.rgb(0x5A, 0x4D, 0x41)
            isAntiAlias = true
        }
        private val heroAmberSubPaint = Paint().apply {
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xB2, 0x6A, 0x00)
            isAntiAlias = true
        }
        private val sectionTitlePaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x1A, 0x12, 0x0B)
            isAntiAlias = true
        }
        private val textBoldPaint = Paint().apply {
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x1A, 0x12, 0x0B)
            isAntiAlias = true
        }
        private val textRedBoldPaint = Paint().apply {
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xB7, 0x1C, 0x1C)
            isAntiAlias = true
        }
        private val textMutedSmallPaint = Paint().apply {
            textSize = 7.5f
            color = Color.rgb(0x5A, 0x4D, 0x41)
            isAntiAlias = true
        }
        private val textCancelledPaint = Paint().apply {
            textSize = 8f
            color = Color.rgb(0x88, 0x88, 0x88)
            flags = Paint.STRIKE_THRU_TEXT_FLAG
            isAntiAlias = true
        }
        private val statusGreenPaint = Paint().apply {
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x1B, 0x5E, 0x20)
            isAntiAlias = true
        }
        private val statusAmberPaint = Paint().apply {
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xB2, 0x6A, 0x00)
            isAntiAlias = true
        }
        private val statusRedPaint = Paint().apply {
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0xB7, 0x1C, 0x1C)
            isAntiAlias = true
        }
        private val footerLeftPaint = Paint().apply {
            textSize = 7f
            color = Color.rgb(0x7A, 0x6E, 0x63)
            isAntiAlias = true
        }
        private val footerRightPaint = Paint().apply {
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(0x5A, 0x4D, 0x41)
            isAntiAlias = true
        }
        private val italicNoticePaint = Paint().apply {
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            color = Color.rgb(0x6E, 0x62, 0x55)
            isAntiAlias = true
        }

        fun startNewPage() {
            if (::page.isInitialized) {
                drawPageFooter()
                document.finishPage(page)
            }
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN_TOP
            if (pageNumber > 1) {
                drawContinuationHeader()
            }
        }

        private fun drawPageFooter() {
            canvas.drawLine(MARGIN_LEFT, FOOTER_LINE_Y, MARGIN_RIGHT_BOUND, FOOTER_LINE_Y, borderPaint)
            canvas.drawText("ShankaRavam • Festival & Temple Management • Tamper-Evident Ledger", MARGIN_LEFT, FOOTER_TEXT_Y, footerLeftPaint)
            val pageStr = "Page $pageNumber"
            canvas.drawText(pageStr, MARGIN_RIGHT_BOUND - footerRightPaint.measureText(pageStr), FOOTER_TEXT_Y, footerRightPaint)
        }

        private fun drawContinuationHeader() {
            val rect = RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT_BOUND, MARGIN_TOP + 26f)
            canvas.drawRoundRect(rect, 6f, 6f, maroonPaint)
            val goldRect = RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT_BOUND, MARGIN_TOP + 3f)
            canvas.drawRect(goldRect, goldPaint)
            val title = "$templeName — $eventName • Financial Statement (Continued)"
            canvas.drawText(truncate(title, whiteHeaderSmallPaint, 360f), MARGIN_LEFT + 10f, MARGIN_TOP + 17f, whiteHeaderSmallPaint)
            val auditTag = "OFFICIAL AUDIT"
            canvas.drawText(auditTag, MARGIN_RIGHT_BOUND - 10f - goldHeaderSmallPaint.measureText(auditTag), MARGIN_TOP + 17f, goldHeaderSmallPaint)
            y = MARGIN_TOP + 36f
        }

        fun drawHeaderBanner() {
            val bannerH = 70f
            val rect = RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT_BOUND, MARGIN_TOP + bannerH)
            canvas.drawRoundRect(rect, 10f, 10f, maroonPaint)

            // Top Gold Strip
            val goldRect = RectF(MARGIN_LEFT, MARGIN_TOP, MARGIN_RIGHT_BOUND, MARGIN_TOP + 4f)
            canvas.drawRect(goldRect, goldPaint)

            // Left: Temple & Event Info
            canvas.drawText(truncate(templeName, bannerTitlePaint, 340f), MARGIN_LEFT + 14f, MARGIN_TOP + 25f, bannerTitlePaint)
            canvas.drawText(truncate(eventName, bannerSubTitlePaint, 340f), MARGIN_LEFT + 14f, MARGIN_TOP + 45f, bannerSubTitlePaint)
            canvas.drawText("OFFICIAL FINANCIAL AUDIT STATEMENT / అధికారిక ఆడిట్ నివేదిక", MARGIN_LEFT + 14f, MARGIN_TOP + 60f, bannerSubHeaderPaint)

            // Right: Branding & Date
            val appTitle = "SHANKARAVAM"
            canvas.drawText(appTitle, MARGIN_RIGHT_BOUND - 14f - bannerAppTitlePaint.measureText(appTitle), MARGIN_TOP + 25f, bannerAppTitlePaint)
            val dateStr = formatReceiptDateTime(System.currentTimeMillis())
            canvas.drawText(dateStr, MARGIN_RIGHT_BOUND - 14f - bannerSubHeaderPaint.measureText(dateStr), MARGIN_TOP + 45f, bannerSubHeaderPaint)
            val auditStr = "VERIFIED LOCAL LEDGER"
            canvas.drawText(auditStr, MARGIN_RIGHT_BOUND - 14f - bannerSubHeaderPaint.measureText(auditStr), MARGIN_TOP + 60f, bannerSubHeaderPaint)

            y = MARGIN_TOP + bannerH + 12f
        }

        fun drawExecutiveSummaryCard(totals: BalanceSnapshot, expenseCount: Int) {
            val cardH = 72f
            val rect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + cardH)
            canvas.drawRoundRect(rect, 8f, 8f, heroBgPaint)
            canvas.drawRoundRect(rect, 8f, 8f, borderPaint)

            val colW = (MARGIN_RIGHT_BOUND - MARGIN_LEFT) / 3f

            // Vertical Dividers
            canvas.drawLine(MARGIN_LEFT + colW, y + 8f, MARGIN_LEFT + colW, y + cardH - 8f, borderPaint)
            canvas.drawLine(MARGIN_LEFT + 2 * colW, y + 8f, MARGIN_LEFT + 2 * colW, y + cardH - 8f, borderPaint)

            // Col 1: Collections
            val col1X = MARGIN_LEFT + 12f
            canvas.drawText("TOTAL COLLECTIONS / వసూళ్లు", col1X, y + 18f, heroLabelPaint)
            canvas.drawText(formatInr(totals.cashCollected), col1X, y + 38f, heroGreenPaint)
            val col1Sub = "${totals.donorCount} Donors • ${totals.nonCashCount} Non-Cash"
            canvas.drawText(col1Sub, col1X, y + 54f, heroSubPaint)

            // Col 2: Expenses
            val col2X = MARGIN_LEFT + colW + 12f
            canvas.drawText("TOTAL EXPENSES / ఖర్చులు", col2X, y + 18f, heroLabelPaint)
            canvas.drawText(formatInr(totals.expenseTotal), col2X, y + 38f, heroRedPaint)
            val col2Sub = "$expenseCount Expenses recorded"
            canvas.drawText(col2Sub, col2X, y + 54f, heroSubPaint)

            // Col 3: Net Balance
            val col3X = MARGIN_LEFT + 2 * colW + 12f
            canvas.drawText("NET BALANCE / నికర నిల్వ", col3X, y + 18f, heroMaroonPaint)
            canvas.drawText(formatInr(totals.balance), col3X, y + 38f, heroMaroonPaint)
            val col3Sub = "Pledged: ${formatInr(totals.pledgedTotal)}"
            canvas.drawText(col3Sub, col3X, y + 54f, heroAmberSubPaint)

            y += cardH + 14f
        }

        fun drawSectionTitle(title: String, count: Int) {
            ensureSpace(32f)
            // Left Accent Pill
            val pillRect = RectF(MARGIN_LEFT, y - 9f, MARGIN_LEFT + 3.5f, y + 3f)
            canvas.drawRoundRect(pillRect, 2f, 2f, maroonPaint)
            val titleWithCount = "$title ($count)"
            canvas.drawText(titleWithCount, MARGIN_LEFT + 10f, y, sectionTitlePaint)
            y += 8f
        }

        fun drawEmptyNotice(notice: String) {
            ensureSpace(20f)
            canvas.drawText(notice, MARGIN_LEFT + 10f, y + 10f, textMutedSmallPaint)
            y += 18f
        }

        fun drawDonationTableHeader() {
            ensureSpace(24f)
            val hRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + 18f)
            canvas.drawRoundRect(hRect, 4f, 4f, maroonPaint)
            canvas.drawText("#", MARGIN_LEFT + 6f, y + 12f, tableHeaderPaint)
            canvas.drawText("DONOR NAME / దాత పేరు", MARGIN_LEFT + 28f, y + 12f, tableHeaderPaint)
            canvas.drawText("PAYMENT", MARGIN_LEFT + 238f, y + 12f, tableHeaderPaint)
            canvas.drawText("STATUS", MARGIN_LEFT + 332f, y + 12f, tableHeaderPaint)
            val amtHdr = "AMOUNT (₹)"
            canvas.drawText(amtHdr, MARGIN_RIGHT_BOUND - 8f - tableHeaderPaint.measureText(amtHdr), y + 12f, tableHeaderPaint)
            y += 18f
        }

        fun drawDonationRow(index: Int, d: Donation, amountText: String) {
            if (y + 16f > CONTENT_BOTTOM) {
                startNewPage()
                drawDonationTableHeader()
            }
            val rowRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + 16f)
            if (index % 2 == 1) {
                canvas.drawRect(rowRect, rowAltPaint)
            }
            canvas.drawLine(MARGIN_LEFT, y + 16f, MARGIN_RIGHT_BOUND, y + 16f, rowBorderPaint)

            canvas.drawText("${index + 1}", MARGIN_LEFT + 6f, y + 11.5f, textMutedSmallPaint)

            val nameText = if (d.isNonCash) {
                "${d.donorName} (🎁 ${d.itemDescription ?: "Item"})"
            } else {
                d.donorName
            }
            canvas.drawText(truncate(nameText, textBoldPaint, 205f), MARGIN_LEFT + 28f, y + 11.5f, textBoldPaint)

            val payText = listOfNotNull(d.paymentMethod.ifBlank { null }, d.tags.firstOrNull()).joinToString(" • ")
            canvas.drawText(truncate(payText, textMutedSmallPaint, 90f), MARGIN_LEFT + 238f, y + 11.5f, textMutedSmallPaint)

            val statusPaint = when (d.status) {
                DonationStatus.CONFIRMED, DonationStatus.RECEIVED -> statusGreenPaint
                DonationStatus.PLEDGED -> statusAmberPaint
                DonationStatus.CANCELLED -> statusRedPaint
                else -> textMutedSmallPaint
            }
            val statusLabel = when (d.status) {
                DonationStatus.CONFIRMED -> "Confirmed"
                DonationStatus.RECEIVED -> "Received"
                DonationStatus.PLEDGED -> "Pledged"
                DonationStatus.PARTIALLY_RECEIVED -> "Partial"
                DonationStatus.CANCELLED -> "Cancelled"
            }
            canvas.drawText(statusLabel, MARGIN_LEFT + 332f, y + 11.5f, statusPaint)

            val amt = if (d.isNonCash) "Non-Cash" else amountText
            val amtPaint = if (d.status == DonationStatus.CANCELLED) textCancelledPaint else textBoldPaint
            canvas.drawText(amt, MARGIN_RIGHT_BOUND - 8f - amtPaint.measureText(amt), y + 11.5f, amtPaint)

            y += 16f
        }

        fun drawExpenseTableHeader() {
            ensureSpace(24f)
            val hRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + 18f)
            canvas.drawRoundRect(hRect, 4f, 4f, maroonPaint)
            canvas.drawText("#", MARGIN_LEFT + 6f, y + 12f, tableHeaderPaint)
            canvas.drawText("EXPENSE DESCRIPTION / వివరాలు", MARGIN_LEFT + 28f, y + 12f, tableHeaderPaint)
            canvas.drawText("CATEGORY", MARGIN_LEFT + 238f, y + 12f, tableHeaderPaint)
            canvas.drawText("PAID BY", MARGIN_LEFT + 332f, y + 12f, tableHeaderPaint)
            val amtHdr = "AMOUNT (₹)"
            canvas.drawText(amtHdr, MARGIN_RIGHT_BOUND - 8f - tableHeaderPaint.measureText(amtHdr), y + 12f, tableHeaderPaint)
            y += 18f
        }

        fun drawExpenseRow(index: Int, e: Expense, amountText: String) {
            if (y + 16f > CONTENT_BOTTOM) {
                startNewPage()
                drawExpenseTableHeader()
            }
            val rowRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + 16f)
            if (index % 2 == 1) {
                canvas.drawRect(rowRect, rowAltPaint)
            }
            canvas.drawLine(MARGIN_LEFT, y + 16f, MARGIN_RIGHT_BOUND, y + 16f, rowBorderPaint)

            canvas.drawText("${index + 1}", MARGIN_LEFT + 6f, y + 11.5f, textMutedSmallPaint)
            canvas.drawText(truncate(e.description, textBoldPaint, 205f), MARGIN_LEFT + 28f, y + 11.5f, textBoldPaint)
            canvas.drawText(truncate(e.category, textMutedSmallPaint, 90f), MARGIN_LEFT + 238f, y + 11.5f, textMutedSmallPaint)
            canvas.drawText(truncate(e.paidBy, textMutedSmallPaint, 75f), MARGIN_LEFT + 332f, y + 11.5f, textMutedSmallPaint)

            val amtPaint = if (e.status == ExpenseStatus.CANCELLED) textCancelledPaint else textRedBoldPaint
            canvas.drawText(amountText, MARGIN_RIGHT_BOUND - 8f - amtPaint.measureText(amountText), y + 11.5f, amtPaint)

            y += 16f
        }

        fun drawCorrectionTableHeader() {
            ensureSpace(24f)
            val hRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + 18f)
            canvas.drawRoundRect(hRect, 4f, 4f, maroonPaint)
            canvas.drawText("#", MARGIN_LEFT + 6f, y + 12f, tableHeaderPaint)
            canvas.drawText("CORRECTION REASON / సవరణ కారణం", MARGIN_LEFT + 28f, y + 12f, tableHeaderPaint)
            canvas.drawText("ORIGINAL", MARGIN_LEFT + 310f, y + 12f, tableHeaderPaint)
            val amtHdr = "EFFECTIVE (₹)"
            canvas.drawText(amtHdr, MARGIN_RIGHT_BOUND - 8f - tableHeaderPaint.measureText(amtHdr), y + 12f, tableHeaderPaint)
            y += 18f
        }

        fun drawCorrectionRow(index: Int, c: Correction) {
            if (y + 16f > CONTENT_BOTTOM) {
                startNewPage()
                drawCorrectionTableHeader()
            }
            val rowRect = RectF(MARGIN_LEFT, y, MARGIN_RIGHT_BOUND, y + 16f)
            if (index % 2 == 1) {
                canvas.drawRect(rowRect, rowAltPaint)
            }
            canvas.drawLine(MARGIN_LEFT, y + 16f, MARGIN_RIGHT_BOUND, y + 16f, rowBorderPaint)

            canvas.drawText("${index + 1}", MARGIN_LEFT + 6f, y + 11.5f, textMutedSmallPaint)
            canvas.drawText(truncate(c.reason, textBoldPaint, 275f), MARGIN_LEFT + 28f, y + 11.5f, textBoldPaint)
            canvas.drawText(formatInr(c.originalAmount), MARGIN_LEFT + 310f, y + 11.5f, textMutedSmallPaint)
            val eff = formatInr(c.effectiveAmount)
            canvas.drawText(eff, MARGIN_RIGHT_BOUND - 8f - textBoldPaint.measureText(eff), y + 11.5f, textBoldPaint)

            y += 16f
        }

        fun drawCertificationBlock() {
            ensureSpace(70f)
            y += 14f

            val note1 = "Certified that the above financial statements are true, correct, and accurately recorded in the temple ledger."
            val note2 = "పై పేర్కొన్న లెక్కలు ఖచ్చితమైనవని మరియు రసీదుల ఆధారంగా సరిచూడబడినవని ధృవీకరించడమైనది."
            canvas.drawText(note1, MARGIN_LEFT, y, italicNoticePaint)
            y += 9f
            canvas.drawText(note2, MARGIN_LEFT, y, italicNoticePaint)
            y += 28f

            val slotWidth = 135f
            val slot1X = MARGIN_LEFT + 10f
            val slot2X = MARGIN_LEFT + 190f
            val slot3X = MARGIN_LEFT + 370f

            canvas.drawLine(slot1X, y, slot1X + slotWidth, y, sigLinePaint)
            canvas.drawLine(slot2X, y, slot2X + slotWidth, y, sigLinePaint)
            canvas.drawLine(slot3X, y, slot3X + slotWidth, y, sigLinePaint)

            y += 10f
            canvas.drawText("Prepared By (కౌంటర్ ఇన్‌చార్జ్)", slot1X + 5f, y, textMutedSmallPaint)
            canvas.drawText("Verified By (కోశాధికారి / Treasurer)", slot2X + 5f, y, textMutedSmallPaint)
            canvas.drawText("Approved By (అధ్యక్షుడు / Secretary)", slot3X + 5f, y, textMutedSmallPaint)
            y += 16f
        }

        fun drawOverflowNote(total: Int) {
            ensureSpace(16f)
            canvas.drawText("…and ${total - MAX_ROWS} more records (see full CSV export for complete data).", MARGIN_LEFT + 10f, y + 10f, textMutedSmallPaint)
            y += 16f
        }

        fun gap(amount: Float) {
            y += amount
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > CONTENT_BOTTOM) {
                startNewPage()
            }
        }

        fun writeTo(file: File) {
            if (::page.isInitialized) {
                drawPageFooter()
                document.finishPage(page)
            }
            FileOutputStream(file).use { document.writeTo(it) }
            finished = true
        }

        fun close() {
            if (!finished && ::page.isInitialized) {
                runCatching { document.finishPage(page) }
            }
            document.close()
        }

        private fun truncate(text: String, paint: Paint, maxWidth: Float): String {
            val singleLine = text.replace('\n', ' ').trim()
            if (paint.measureText(singleLine) <= maxWidth) return singleLine
            var t = singleLine
            while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) {
                t = t.dropLast(1)
            }
            return "$t…"
        }

        companion object {
            private const val PAGE_WIDTH = 595
            private const val PAGE_HEIGHT = 842
            private const val MARGIN_LEFT = 36f
            private const val MARGIN_RIGHT_BOUND = 559f
            private const val MARGIN_TOP = 36f
            private const val CONTENT_BOTTOM = 794f
            private const val FOOTER_LINE_Y = 812f
            private const val FOOTER_TEXT_Y = 824f
        }
    }
}
