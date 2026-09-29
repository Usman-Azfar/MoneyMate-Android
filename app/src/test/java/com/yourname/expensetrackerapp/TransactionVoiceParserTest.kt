package com.yourname.expensetrackerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TransactionVoiceParserTest {

    private val incomeCategories = listOf("Select Income Category", "Salary", "Freelance", "Investment", "Gift")
    private val expenseCategories = listOf(
        "Select Expense Category", "Food", "Transportation", "Entertainment",
        "Shopping", "Health", "Education", "Bills", "Other"
    )

    /** Fixed at a known Tuesday so "yesterday"/"tomorrow"/"next Friday" are deterministic. */
    private val reference = midnight(2026, Calendar.SEPTEMBER, 22)

    private fun midnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun offsetDays(from: Long, days: Int): Long =
        Calendar.getInstance().apply { timeInMillis = from; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    private fun nextWeekday(from: Long, weekday: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (cal.get(Calendar.DAY_OF_WEEK) != weekday)
        return cal.timeInMillis
    }

    private fun parse(text: String) =
        TransactionVoiceParser.parse(text, incomeCategories, expenseCategories, reference)

    @Test
    fun `simple expense with digits`() {
        val result = parse("Spent 500 on groceries today")
        assertEquals("Expense", result.type)
        assertEquals(500.0, result.amount)
        assertEquals("Food", result.category)
        assertEquals(reference, result.dateMillis)
    }

    @Test
    fun `spelled-out amount with currency word`() {
        val result = parse("I paid five hundred rupees for groceries")
        assertEquals("Expense", result.type)
        assertEquals(500.0, result.amount)
        assertEquals("Food", result.category)
    }

    @Test
    fun `k-suffix amount and unknown merchant does not get a category guess`() {
        val result = parse("Spent around 2.5k at Carrefour yesterday")
        assertEquals("Expense", result.type)
        assertEquals(2500.0, result.amount)
        assertNull("A merchant not in the synonym table must not be forced into a category", result.category)
        assertEquals(offsetDays(reference, -1), result.dateMillis)
    }

    @Test
    fun `yesterday plus spelled-out amount`() {
        val result = parse("Yesterday I bought groceries for five hundred")
        assertEquals("Expense", result.type)
        assertEquals(500.0, result.amount)
        assertEquals("Food", result.category)
        assertEquals(offsetDays(reference, -1), result.dateMillis)
    }

    @Test
    fun `comma-separated amount and unknown merchant, no false debt person`() {
        val result = parse("I spent 1,200 at KFC with Ali")
        assertEquals("Expense", result.type)
        assertEquals(1200.0, result.amount)
        assertNull("KFC is not a category synonym and must not be guessed", result.category)
        assertNull("Only Debt utterances should ever populate a person name", result.personName)
        assertTrue(result.description.contains("Ali", ignoreCase = true))
        assertEquals(
            "Internal connector words should survive so the description reads naturally, and the " +
                "recognizer's own casing on proper nouns should be kept rather than lowercased",
            "KFC with Ali", result.description
        )
    }

    @Test
    fun `plain leftover description is just the content word, properly capitalized`() {
        val result = parse("Spent 500 on groceries today")
        assertEquals("Groceries", result.description)
    }

    @Test
    fun `attached k-suffix amount with no space`() {
        val result = parse("Spent 130k on rent")
        assertEquals("Expense", result.type)
        assertEquals(130000.0, result.amount)
        assertEquals("Bills", result.category)
    }

    @Test
    fun `lakh magnitude word`() {
        val result = parse("Received 1 lakh 30 thousand salary")
        assertEquals("Income", result.type)
        assertEquals(130000.0, result.amount)
    }

    @Test
    fun `lac spelling variant alone`() {
        val result = parse("Paid 2 lac for a course")
        assertEquals(200000.0, result.amount)
        assertEquals("Education", result.category)
    }

    @Test
    fun `crore and lac combined`() {
        val result = parse("Paid 2 crore 50 lac for a house")
        assertEquals(25_000_000.0, result.amount)
    }

    @Test
    fun `spelled number with 'and' stays one amount`() {
        val result = parse("Spent one hundred and thirty on fuel")
        assertEquals("Transportation", result.category)
        assertEquals(130.0, result.amount)
    }

    @Test
    fun `decimal comma amount`() {
        val result = parse("Paid 1,200.50 for bills")
        assertEquals("Expense", result.type)
        assertEquals(1200.50, result.amount)
        assertEquals("Bills", result.category)
    }

    @Test
    fun `income with category synonym`() {
        val result = parse("Received 2000 salary")
        assertEquals("Income", result.type)
        assertEquals(2000.0, result.amount)
        assertEquals("Salary", result.category)
    }

    @Test
    fun `payable debt with person name`() {
        val result = parse("I borrowed 300 from Ali")
        assertEquals("Debt", result.type)
        assertEquals("Payable", result.debtDirection)
        assertEquals(300.0, result.amount)
        assertEquals("Ali", result.personName)
    }

    @Test
    fun `receivable debt with person name and due date`() {
        val result = parse("Lent 100 to Sara due next Friday")
        assertEquals("Debt", result.type)
        assertEquals("Receivable", result.debtDirection)
        assertEquals(100.0, result.amount)
        assertEquals("Sara", result.personName)
        assertEquals(nextWeekday(reference, Calendar.FRIDAY), result.dueDateMillis)
    }

    @Test
    fun `no verb at all still defaults to Expense when there is a clear amount`() {
        val result = parse("500 on groceries")
        assertEquals("Expense", result.type)
        assertEquals(500.0, result.amount)
        assertEquals("Food", result.category)
    }

    @Test
    fun `category-first phrasing with no verb`() {
        val result = parse("Fuel 2000")
        assertEquals("Expense", result.type)
        assertEquals(2000.0, result.amount)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun `expanded expense verb 'used'`() {
        val result = parse("I used 300 for parking")
        assertEquals("Expense", result.type)
        assertEquals(300.0, result.amount)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun `expanded income verb 'bonus'`() {
        val result = parse("Got a bonus of 5000")
        assertEquals("Income", result.type)
        assertEquals(5000.0, result.amount)
    }

    @Test
    fun `gift category synonym eidi`() {
        val result = parse("Received 5000 eidi")
        assertEquals("Income", result.type)
        assertEquals(5000.0, result.amount)
        assertEquals("Gift", result.category)
    }

    @Test
    fun `possessive determiner is skipped so the actual relation is captured as the person`() {
        val result = parse("I borrowed 15000 from my brother due next month")
        assertEquals("Brother", result.personName)
        assertEquals(midnight(2026, Calendar.OCTOBER, 22), result.dueDateMillis)
        assertEquals("", result.description)
    }

    @Test
    fun `future offset due date, 'in N weeks'`() {
        val result = parse("Gave a loan of 10000 to Ahmed due in 2 weeks")
        assertEquals("Ahmed", result.personName)
        assertEquals(offsetDays(reference, 14), result.dueDateMillis)
    }

    @Test
    fun `duplicate filler word left on both sides of a removed amount is collapsed`() {
        val result = parse("Bought books for 1200 for school")
        assertEquals("Education", result.category)
        assertEquals("Books for school", result.description)
    }

    @Test
    fun `command-style phrasing strips the command word instead of leaving it in the description`() {
        val result = parse("Log 2000 for shopping yesterday")
        assertEquals("Shopping", result.category)
        assertEquals("Shopping", result.description)
        assertEquals(offsetDays(reference, -1), result.dateMillis)
    }

    @Test
    fun `clothing item is a Shopping synonym`() {
        val result = parse("Bought a shirt for 1800")
        assertEquals("Shopping", result.category)
    }

    @Test
    fun `garbled phrase leaves everything unresolved`() {
        val result = parse("asdf qwerty zzz")
        assertNull(result.type)
        assertNull(result.debtDirection)
        assertNull(result.amount)
        assertNull(result.category)
        assertNull(result.personName)
        assertNull(result.dateMillis)
        assertNull(result.dueDateMillis)
    }

    @Test
    fun `blank transcript returns an all-null result`() {
        val result = parse("   ")
        assertNull(result.type)
        assertNull(result.amount)
        assertEquals("", result.description)
    }
}
