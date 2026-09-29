package com.yourname.expensetrackerapp

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The CSV/Excel alternative to [PdfReportGenerator] — a plain, spreadsheet-friendly export of the
 * same filtered transaction list. Amounts are written as plain decimals (no currency symbol, and
 * always with a "." decimal point regardless of device locale) so spreadsheet apps read them as
 * numbers rather than text.
 */
object CsvReportGenerator {

    private val timestampFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    fun generate(context: Context, transactions: List<Transaction>, filterSummary: String): Boolean {
        val sb = StringBuilder()
        sb.append(context.getString(R.string.app_display_name)).append(" - Transaction Report\n")
        // Escaped like every other field: the timestamp itself contains a comma ("29 Sep 2026, 10:16 AM"),
        // which would otherwise split this line across two spreadsheet cells.
        sb.append(escape("Generated on ${timestampFormat.format(Date())}")).append('\n')
        sb.append("${escape(filterSummary)}\n\n")

        sb.append("Date,Description,Category,Type,Amount,Person,Due Date,Settled\n")
        for (transaction in transactions) {
            sb.append(escape(transaction.date)).append(',')
            sb.append(escape(transaction.description)).append(',')
            sb.append(escape(transaction.category ?: "")).append(',')
            sb.append(escape(transaction.type)).append(',')
            sb.append(amount(transaction.amount)).append(',')
            sb.append(escape(transaction.personName ?: "")).append(',')
            sb.append(escape(transaction.dueDate ?: "")).append(',')
            sb.append(if (transaction.type == "Debt") (if (transaction.isSettled) "Yes" else "No") else "")
            sb.append('\n')
        }

        val includeDebts = IncludeDebtsPrefs.isEnabled(context)
        val totalIncome = transactions.sumOf { DebtAccounting.incomeAmount(it, includeDebts) }
        val totalExpense = transactions.sumOf { DebtAccounting.expenseAmount(it, includeDebts) }
        val unsettledDebts = transactions.filter { it.type == "Debt" && !it.isSettled }

        sb.append('\n')
        sb.append("Total Income,,,,${amount(totalIncome)}\n")
        sb.append("Total Expense,,,,${amount(totalExpense)}\n")
        sb.append("Net Balance,,,,${amount(totalIncome - totalExpense)}\n")
        if (!includeDebts && unsettledDebts.isNotEmpty()) {
            val totalPayable = unsettledDebts.filter { it.category == "Payable" }.sumOf { it.amount }
            val totalReceivable = unsettledDebts.filter { it.category == "Receivable" }.sumOf { it.amount }
            sb.append("Outstanding Payable,,,,${amount(totalPayable)}\n")
            sb.append("Outstanding Receivable,,,,${amount(totalReceivable)}\n")
        }
        sb.append("${transactions.size} transaction${if (transactions.size == 1) "" else "s"}\n")

        val fileName = "ExpenseReport_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        return FileDownloader.writeToDownloads(context, fileName, "text/csv") { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** Quotes a field if it contains a comma, quote or newline, doubling any embedded quotes —
     *  the standard CSV escaping rule. */
    private fun escape(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
