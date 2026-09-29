package com.yourname.expensetrackerapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Builds a formatted, multi-page PDF transaction report and saves it straight to the device's
 * Downloads folder using android.graphics.pdf.PdfDocument — no PDF library dependency needed,
 * consistent with the rest of this app's "no external dependency beyond Gson" approach.
 */
object PdfReportGenerator {

    private const val PAGE_WIDTH = 595   // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val ROW_HEIGHT = 22f
    private const val HEADER_ROW_HEIGHT = 24f
    private const val FIRST_PAGE_HEADER_RESERVE = 132f
    private const val OTHER_PAGE_HEADER_RESERVE = 34f
    private const val FOOTER_RESERVE = 40f
    private const val SUMMARY_BLOCK_HEIGHT = 92f

    private const val COL_DATE = 62f
    private const val COL_TYPE = 50f
    private const val COL_AMOUNT = 80f
    private const val COL_CATEGORY = 90f

    private val timestampFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    private val colorPrimary = Color.parseColor("#3454D1")
    private val colorMuted = Color.parseColor("#62636B")
    private val colorText = Color.parseColor("#131417")

    // Matches the app's brand purple (colors.xml's "primary") — used only to draw the "Money"
    // half of the report title in the same two-tone treatment as the app's logo artwork
    // ("Money" in brand purple, "Mate" in near-black, which colorText above already matches).
    private val colorBrandMoney = Color.parseColor("#4F46E5")
    private val colorAltRow = Color.parseColor("#F5F6F8")
    private val colorDivider = Color.parseColor("#E1E2E6")
    private val colorIncome = Color.parseColor("#1F8A4C")
    private val colorExpense = Color.parseColor("#B3261E")
    private val colorFooter = Color.parseColor("#9A9AA3")

    fun generate(context: Context, transactions: List<Transaction>, filterSummary: String): Boolean {
        val document = PdfDocument()
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val colDescription = contentWidth - COL_DATE - COL_TYPE - COL_AMOUNT - COL_CATEGORY

        val rowsPerFirstPage = ((PAGE_HEIGHT - 2 * MARGIN - FIRST_PAGE_HEADER_RESERVE - FOOTER_RESERVE) / ROW_HEIGHT)
            .toInt().coerceAtLeast(1)
        val rowsPerOtherPage = ((PAGE_HEIGHT - 2 * MARGIN - OTHER_PAGE_HEADER_RESERVE - FOOTER_RESERVE) / ROW_HEIGHT)
            .toInt().coerceAtLeast(1)

        val includeDebts = IncludeDebtsPrefs.isEnabled(context)
        val totalIncome = transactions.sumOf { DebtAccounting.incomeAmount(it, includeDebts) }
        val totalExpense = transactions.sumOf { DebtAccounting.expenseAmount(it, includeDebts) }
        val unsettledDebts = transactions.filter { it.type == "Debt" && !it.isSettled }
        val totalPayable = unsettledDebts.filter { it.category == "Payable" }.sumOf { it.amount }
        val totalReceivable = unsettledDebts.filter { it.category == "Receivable" }.sumOf { it.amount }
        val showOutstandingDebts = !includeDebts && unsettledDebts.isNotEmpty()

        val debtSummaryLines = if (showOutstandingDebts) 2 else 0
        val summaryBlockHeight = SUMMARY_BLOCK_HEIGHT + debtSummaryLines * 18f
        val totalPages = estimatePageCount(transactions.size, rowsPerFirstPage, rowsPerOtherPage, summaryBlockHeight)

        val subtitlePaint = Paint().apply { color = colorMuted; textSize = 11f; isAntiAlias = true }
        val labelPaint = Paint().apply { color = colorText; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
        val headerBgPaint = Paint().apply { color = colorPrimary }
        val headerTextPaint = Paint().apply { color = Color.WHITE; textSize = 10.5f; isFakeBoldText = true; isAntiAlias = true }
        val rowTextPaint = Paint().apply { color = colorText; textSize = 10f; isAntiAlias = true }
        val rowAltBgPaint = Paint().apply { color = colorAltRow }
        val incomeTextPaint = Paint().apply { color = colorIncome; textSize = 10f; isAntiAlias = true; isFakeBoldText = true }
        val expenseTextPaint = Paint().apply { color = colorExpense; textSize = 10f; isAntiAlias = true; isFakeBoldText = true }
        val netTextPaint = Paint().apply { color = colorText; textSize = 12.5f; isFakeBoldText = true; isAntiAlias = true }
        val dividerPaint = Paint().apply { color = colorDivider; strokeWidth = 1f }
        val footerPaint = Paint().apply { color = colorFooter; textSize = 8.5f; isAntiAlias = true }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var rowIndexOnPage = 0
        var rowsLimitOnPage = rowsPerFirstPage

        /** Draws "Money" in the brand purple and "Mate" in near-black — the same two-tone
         *  wordmark treatment as the app's logo artwork — since this is the one spot in the PDF
         *  (a plain white page) where that treatment actually has the contrast to read well. */
        fun drawBrandTitle(c: Canvas, x: Float, baselineY: Float) {
            val moneyPaint = Paint().apply { color = colorBrandMoney; textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
            val matePaint = Paint().apply { color = colorText; textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
            c.drawText("Money", x, baselineY, moneyPaint)
            c.drawText("Mate", x + moneyPaint.measureText("Money"), baselineY, matePaint)
        }

        fun drawTableHeaderRow() {
            val c = canvas!!
            c.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + HEADER_ROW_HEIGHT, headerBgPaint)
            var x = MARGIN + 8f
            c.drawText("Date", x, y + 16f, headerTextPaint); x += COL_DATE
            c.drawText("Description", x, y + 16f, headerTextPaint); x += colDescription
            c.drawText("Category", x, y + 16f, headerTextPaint); x += COL_CATEGORY
            c.drawText("Type", x, y + 16f, headerTextPaint)
            val amountLabelWidth = headerTextPaint.measureText("Amount")
            c.drawText("Amount", PAGE_WIDTH - MARGIN - 12f - amountLabelWidth, y + 16f, headerTextPaint)
            y += HEADER_ROW_HEIGHT
        }

        fun startPage(isFirst: Boolean) {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            y = MARGIN

            if (isFirst) {
                drawBrandTitle(canvas!!, MARGIN, y + 18f)
                canvas!!.drawText("Transaction Report", MARGIN, y + 36f, subtitlePaint)
                canvas!!.drawText("Generated on ${timestampFormat.format(Date())}", MARGIN, y + 51f, subtitlePaint)
                canvas!!.drawText(filterSummary, MARGIN, y + 66f, subtitlePaint)
                y += 80f
                canvas!!.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
                y += 14f
            } else {
                y += 8f
            }
            drawTableHeaderRow()
            rowIndexOnPage = 0
            rowsLimitOnPage = if (isFirst) rowsPerFirstPage else rowsPerOtherPage
        }

        fun finishPage() {
            val footerLineY = PAGE_HEIGHT - MARGIN - 22f
            canvas!!.drawLine(MARGIN, footerLineY, PAGE_WIDTH - MARGIN, footerLineY, dividerPaint)
            canvas!!.drawText(
                "${context.getString(R.string.app_display_name)} • Generated ${timestampFormat.format(Date())}",
                MARGIN, PAGE_HEIGHT - MARGIN - 8f, footerPaint
            )
            val pageLabel = "Page $pageNumber of $totalPages"
            val w = footerPaint.measureText(pageLabel)
            canvas!!.drawText(pageLabel, PAGE_WIDTH - MARGIN - w, PAGE_HEIGHT - MARGIN - 8f, footerPaint)
            document.finishPage(page)
        }

        startPage(true)

        if (transactions.isEmpty()) {
            canvas!!.drawText("No transactions match the selected filters.", MARGIN + 8f, y + 16f, rowTextPaint)
            y += ROW_HEIGHT
        }

        for (transaction in transactions) {
            if (rowIndexOnPage >= rowsLimitOnPage) {
                finishPage()
                startPage(false)
            }

            if (rowIndexOnPage % 2 == 1) {
                canvas!!.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + ROW_HEIGHT, rowAltBgPaint)
            }
            var x = MARGIN + 8f
            val textY = y + ROW_HEIGHT - 7f
            canvas!!.drawText(transaction.date, x, textY, rowTextPaint); x += COL_DATE
            val descriptionText = transaction.description.ifBlank { transaction.personName ?: "" }
            canvas!!.drawText(truncate(descriptionText, colDescription - 8f, rowTextPaint), x, textY, rowTextPaint)
            x += colDescription
            canvas!!.drawText(truncate(transaction.category ?: "-", COL_CATEGORY - 8f, rowTextPaint), x, textY, rowTextPaint)
            x += COL_CATEGORY
            canvas!!.drawText(transaction.type, x, textY, rowTextPaint)
            val amountPaint = when {
                transaction.type == "Income" -> incomeTextPaint
                transaction.type == "Expense" -> expenseTextPaint
                transaction.category == "Receivable" -> incomeTextPaint
                else -> expenseTextPaint
            }
            val amountText = CurrencyFormatter.format(context, transaction.amount)
            val amountWidth = amountPaint.measureText(amountText)
            canvas!!.drawText(amountText, PAGE_WIDTH - MARGIN - 12f - amountWidth, textY, amountPaint)

            y += ROW_HEIGHT
            rowIndexOnPage++
        }

        if (y + summaryBlockHeight > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) {
            finishPage()
            startPage(false)
        }

        y += 14f
        canvas!!.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
        y += 20f
        canvas!!.drawText("Total Income:", MARGIN, y, labelPaint)
        canvas!!.drawText(CurrencyFormatter.format(context, totalIncome), MARGIN + 130f, y, incomeTextPaint)
        y += 18f
        canvas!!.drawText("Total Expense:", MARGIN, y, labelPaint)
        canvas!!.drawText(CurrencyFormatter.format(context, totalExpense), MARGIN + 130f, y, expenseTextPaint)
        y += 18f
        canvas!!.drawText("Net Balance:", MARGIN, y, labelPaint)
        canvas!!.drawText(CurrencyFormatter.format(context, totalIncome - totalExpense), MARGIN + 130f, y, netTextPaint)
        y += 18f
        if (showOutstandingDebts) {
            canvas!!.drawText("Outstanding Payable:", MARGIN, y, labelPaint)
            canvas!!.drawText(CurrencyFormatter.format(context, totalPayable), MARGIN + 130f, y, expenseTextPaint)
            y += 18f
            canvas!!.drawText("Outstanding Receivable:", MARGIN, y, labelPaint)
            canvas!!.drawText(CurrencyFormatter.format(context, totalReceivable), MARGIN + 130f, y, incomeTextPaint)
            y += 18f
        }
        canvas!!.drawText(
            "${transactions.size} transaction${if (transactions.size == 1) "" else "s"}",
            MARGIN, y, subtitlePaint
        )

        finishPage()

        val fileName = "ExpenseReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
        val saved = FileDownloader.writeToDownloads(context, fileName, "application/pdf") { document.writeTo(it) }
        document.close()
        return saved
    }

    private fun estimatePageCount(rowCount: Int, rowsPerFirstPage: Int, rowsPerOtherPage: Int, summaryBlockHeight: Float): Int {
        if (rowCount == 0) return 1
        var remaining = rowCount
        var pages = 1
        var usedOnLastPage = min(remaining, rowsPerFirstPage)
        remaining -= usedOnLastPage
        while (remaining > 0) {
            pages++
            usedOnLastPage = min(remaining, rowsPerOtherPage)
            remaining -= usedOnLastPage
        }
        val limitOnLastPage = if (pages == 1) rowsPerFirstPage else rowsPerOtherPage
        val leftoverHeight = (limitOnLastPage - usedOnLastPage) * ROW_HEIGHT
        if (leftoverHeight < summaryBlockHeight) pages++
        return pages
    }

    private fun truncate(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

}
