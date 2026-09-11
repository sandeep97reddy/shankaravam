package com.shankaravam.festival.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.shankaravam.festival.core.util.formatInr
import com.shankaravam.festival.domain.model.Correction
import com.shankaravam.festival.domain.model.Donation
import com.shankaravam.festival.domain.model.Expense
import com.shankaravam.festival.domain.usecase.calculateBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Local report engine (plan §20): framework PdfDocument + plain file IO +
 * ACTION_SEND. No PDF/CSV library, no cloud — files land in cacheDir/reports
 * and are shared through FileProvider.
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
        val totals = calculateBalance(donations, expenses)
        val file = reportFile("shankaravam_report_${System.currentTimeMillis()}.pdf")
        val writer = PdfWriter()
        try {
            writer.title("$templeName — $eventName")
            writer.line("Event report • ${ReportContent.formatTime(System.currentTimeMillis())}")
            writer.gap()
            writer.section("Totals")
            writer.line("Collected: ${formatInr(totals.cashCollected)}")
            writer.line("Expenses: ${formatInr(totals.expenseTotal)}")
            writer.line("Balance: ${formatInr(totals.balance)}")
            writer.line("Pledged (excluded): ${formatInr(totals.pledgedTotal)}")
            writer.line("Donors: ${totals.donorCount} • Non-cash: ${totals.nonCashCount} • Corrections: ${corrections.size}")
            writer.gap()
            writer.section("Donations (${donations.size})")
            capped(donations, MAX_ROWS).forEach { d ->
                writer.line(ReportContent.donationLine(d, formatInr(d.amount)))
            }
            overflowNote(writer, donations.size)
            writer.gap()
            writer.section("Expenses (${expenses.size})")
            capped(expenses, MAX_ROWS).forEach { e ->
                writer.line(ReportContent.expenseLine(e, formatInr(e.amount)))
            }
            overflowNote(writer, expenses.size)
            if (corrections.isNotEmpty()) {
                writer.gap()
                writer.section("Corrections (${corrections.size})")
                capped(corrections, MAX_ROWS).forEach { c ->
                    writer.line(
                        "${formatInr(c.originalAmount)} → ${formatInr(c.effectiveAmount)} — ${c.reason}"
                    )
                }
                overflowNote(writer, corrections.size)
            }
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

    private fun overflowNote(writer: PdfWriter, total: Int) {
        if (total > MAX_ROWS) writer.line("…and ${total - MAX_ROWS} more (see CSV export).")
    }

    companion object {
        private const val MAX_ROWS = 150
    }

    /** Minimal paginated text renderer over PdfDocument (A4 points). */
    private class PdfWriter {
        private val document = PdfDocument()
        private val titlePaint = Paint().apply { textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        private val sectionPaint = Paint().apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        private val bodyPaint = Paint().apply { textSize = 11f }
        private var page = newPage()
        private var y = MARGIN

        fun title(text: String) {
            ensure(30f)
            page.canvas.drawText(fit(text, titlePaint), MARGIN, y, titlePaint)
            y += 26f
        }

        fun section(text: String) {
            ensure(24f)
            page.canvas.drawText(fit(text, sectionPaint), MARGIN, y, sectionPaint)
            y += 20f
        }

        fun line(text: String) {
            ensure(16f)
            page.canvas.drawText(fit(text, bodyPaint), MARGIN, y, bodyPaint)
            y += 15f
        }

        fun gap() {
            y += 8f
        }

        fun writeTo(file: File) {
            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
            finished = true
        }

        fun close() {
            if (!finished) {
                runCatching { document.finishPage(page) }
            }
            document.close()
        }

        private var finished = false

        private fun ensure(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                page = newPage()
                y = MARGIN
            }
        }

        private fun newPage(): PdfDocument.Page =
            document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create())

        private fun fit(text: String, paint: Paint): String {
            var out = text.replace('\n', ' ')
            while (paint.measureText(out) > PAGE_WIDTH - 2 * MARGIN && out.length > 4) {
                out = out.dropLast(2) + "…"
            }
            return out
        }

        companion object {
            private const val PAGE_WIDTH = 595
            private const val PAGE_HEIGHT = 842
            private const val MARGIN = 40f
        }
    }
}
