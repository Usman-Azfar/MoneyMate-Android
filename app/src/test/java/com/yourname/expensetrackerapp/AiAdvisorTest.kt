package com.yourname.expensetrackerapp

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/** Offline tests of Ask MoneyMate AI's data summary, request body and answer clean-up. */
class AiAdvisorTest {

    private fun date(year: Int, month: Int, day: Int): Date = Calendar.getInstance().apply {
        set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private val sepStart = date(2026, Calendar.SEPTEMBER, 1)
    private val octStart = date(2026, Calendar.OCTOBER, 1)
    private val today = date(2026, Calendar.SEPTEMBER, 29).time + 14 * 3_600_000L
    private val pkr = CurrencyList.all.first { it.code == "PKR" }

    private val transactions = listOf(
        Transaction("Petrol Expense", 500.0, "Expense", "Transportation", "28/09/2026", id = "1"),
        Transaction("Groceries", 12000.0, "Expense", "Food", "15/09/2026", id = "2"),
        Transaction("Monthly Salary", 85000.0, "Income", "Salary", "01/09/2026", id = "3"),
        Transaction("Loan", 15000.0, "Debt", "Payable", "20/09/2026", id = "4", personName = "Brother", dueDate = "01/09/2026"),
        Transaction("August rent", 30000.0, "Expense", "Bills", "05/08/2026", id = "5"),
        Transaction("August salary", 80000.0, "Income", "Salary", "01/08/2026", id = "6")
    )

    private fun monthlySnapshot(budget: Budget? = null, all: List<Transaction> = transactions) = AiAdvisor.snapshot(
        label = "Monthly — September 2026",
        periodType = PeriodType.MONTHLY,
        start = sepStart,
        end = octStart,
        allTransactions = all,
        budget = budget,
        includeDebts = false,
        currency = pkr,
        todayMillis = today
    )

    @Test
    fun `previous window matches the period type`() {
        assertEquals(date(2026, Calendar.AUGUST, 1) to sepStart, AiAdvisor.previousWindow(PeriodType.MONTHLY, sepStart, octStart))
        assertEquals(date(2025, Calendar.SEPTEMBER, 1) to sepStart, AiAdvisor.previousWindow(PeriodType.YEARLY, sepStart, octStart))
        val weekStart = date(2026, Calendar.SEPTEMBER, 21)
        assertEquals(date(2026, Calendar.SEPTEMBER, 14) to weekStart, AiAdvisor.previousWindow(PeriodType.WEEKLY, weekStart, sepStart))
        assertNull(AiAdvisor.previousWindow(PeriodType.ALL_TIME, Date(0), Date(Long.MAX_VALUE)))
    }

    @Test
    fun `custom previous window has the same length`() {
        val start = date(2026, Calendar.SEPTEMBER, 10)
        val end = date(2026, Calendar.SEPTEMBER, 20)
        assertEquals(date(2026, Calendar.AUGUST, 31) to start, AiAdvisor.previousWindow(PeriodType.CUSTOM, start, end))
    }

    @Test
    fun `snapshot keeps only this period's transactions, newest first`() {
        val s = monthlySnapshot()
        assertEquals(listOf("1", "4", "2", "3"), s.transactionsInPeriod.map { it.id })
        assertEquals(85000.0, s.totals.income, 0.001)
        assertEquals(12500.0, s.totals.expense, 0.001)
        assertEquals(80000.0, s.previousTotals!!.income, 0.001)
        assertEquals(30000.0, s.previousTotals!!.expense, 0.001)
        assertEquals(1, s.overdueDebtCount)
    }

    @Test
    fun `summary carries totals, comparison, categories, debts and transaction lines`() {
        val summary = AiAdvisor.buildDataSummary(monthlySnapshot())
        assertTrue(summary.contains("Currency: PKR"))
        assertTrue(summary.contains("2026-09-01 to 2026-09-30"))
        assertTrue(summary.contains("income 85000.00; expense 12500.00; net 72500.00; transactions 4"))
        assertTrue(summary.contains("Previous period (2026-08-01 to 2026-08-31): income 80000.00; expense 30000.00"))
        assertTrue(summary.contains("Previous period expense by category: Bills 30000.00"))
        assertTrue(summary.contains("Food 12000.00 (96.0%)"))
        assertTrue(summary.contains("Income by category: Salary 85000.00"))
        assertTrue(summary.contains("payable (user owes) 15000.00 x1"))
        assertTrue(summary.contains("2026-09-28|Expense|Transportation|500.00|Petrol Expense|"))
        assertTrue(summary.contains("2026-09-20|Debt:Payable|-|15000.00|Loan|person=Brother; due=2026-09-01; settled=no"))
        assertTrue(summary.contains("Budget: none set for this period"))
        assertFalse(summary.contains("August rent"))
    }

    @Test
    fun `summary includes budget progress when a budget exists`() {
        val budget = Budget(BudgetPeriod.MONTHLY, 50000.0, sepStart.time)
        val summary = AiAdvisor.buildDataSummary(monthlySnapshot(budget))
        assertTrue(summary.contains("Budget (expected spend): 50000.00; spent 12500.00 (25%); remaining 37500.00"))
    }

    @Test
    fun `transaction list is capped but totals still include everything`() {
        val many = (1..200).map { i ->
            Transaction("Tea $i", 10.0, "Expense", "Food", "10/09/2026", id = "t$i")
        }
        val summary = AiAdvisor.buildDataSummary(monthlySnapshot(all = many))
        assertEquals(AiAdvisor.MAX_TRANSACTION_LINES, summary.lines().count { it.startsWith("2026-09-10|Expense") })
        assertTrue(summary.contains("(50 older transactions in this period are not listed"))
        assertTrue(summary.contains("expense 2000.00"))
        assertTrue(summary.contains("only keeps the most recent 200"))
    }

    @Test
    fun `descriptions can't break the line format`() {
        val tricky = listOf(Transaction("a|b\nc", 10.0, "Expense", "Food", "10/09/2026"))
        val summary = AiAdvisor.buildDataSummary(monthlySnapshot(all = tricky))
        assertTrue(summary.contains("|10.00|a/b c|"))
    }

    @Test
    fun `request has rules plus data in the system instruction and history as turns`() {
        val history = listOf(AiAdvisor.Exchange("Summarize", "You earned ₨85,000.00."))
        val body = AiAdvisor.buildRequestBody(monthlySnapshot(), history, "  How can I save more?  ")
        val root = JsonParser.parseString(body).asJsonObject
        val system = root.getAsJsonObject("systemInstruction").getAsJsonArray("parts")[0].asJsonObject["text"].asString
        assertTrue(system.startsWith("You are MoneyMate AI"))
        assertTrue(system.contains("\nDATA\nCurrency: PKR"))

        val contents = root.getAsJsonArray("contents")
        assertEquals(3, contents.size())
        assertEquals("user", contents[0].asJsonObject["role"].asString)
        assertEquals("model", contents[1].asJsonObject["role"].asString)
        assertEquals("How can I save more?", contents[2].asJsonObject.getAsJsonArray("parts")[0].asJsonObject["text"].asString)
    }

    @Test
    fun `only the last few exchanges are resent and long questions are cut`() {
        val history = (1..6).map { AiAdvisor.Exchange("q$it", "a$it") }
        val body = AiAdvisor.buildRequestBody(monthlySnapshot(), history, "x".repeat(500))
        val contents = JsonParser.parseString(body).asJsonObject.getAsJsonArray("contents")
        assertEquals(AiAdvisor.MAX_HISTORY_EXCHANGES * 2 + 1, contents.size())
        assertEquals("q4", contents[0].asJsonObject.getAsJsonArray("parts")[0].asJsonObject["text"].asString)
        assertEquals(AiAdvisor.MAX_QUESTION_LENGTH, contents.last().asJsonObject.getAsJsonArray("parts")[0].asJsonObject["text"].asString.length)
    }

    @Test
    fun `markdown is stripped for the plain text view`() {
        val raw = "## Summary\n**Total** spent: ₨12,500\n* Food is 96%\n- Cut takeaway\n\n\n\nDone `ok`"
        assertEquals("Summary\nTotal spent: ₨12,500\n• Food is 96%\n• Cut takeaway\n\nDone ok", AiAdvisor.cleanAnswer(raw, truncated = false))
        assertTrue(AiAdvisor.cleanAnswer("Partial answer", truncated = true).endsWith("…"))
    }

    // ---- GeminiClient.extractText ----

    private fun envelope(text: String, finishReason: String) =
        """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"$text"}]},"finishReason":"$finishReason"}]}"""

    @Test
    fun `extractText skips thoughts and handles truncation per caller`() {
        assertEquals("Answer", GeminiClient.extractText(envelope("Answer", "STOP"), allowTruncated = false)!!.text)
        assertNull(GeminiClient.extractText(envelope("Half", "MAX_TOKENS"), allowTruncated = false))
        val truncated = GeminiClient.extractText(envelope("Half", "MAX_TOKENS"), allowTruncated = true)
        assertNotNull(truncated)
        assertTrue(truncated!!.truncated)
    }

    @Test
    fun `extractText rejects blocked or empty responses`() {
        assertNull(GeminiClient.extractText(envelope("x", "SAFETY"), allowTruncated = true))
        assertNull(GeminiClient.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}""", allowTruncated = true))
        assertNull(GeminiClient.extractText(envelope("", "STOP"), allowTruncated = true))
        assertNull(GeminiClient.extractText("garbage", allowTruncated = true))
    }
}
