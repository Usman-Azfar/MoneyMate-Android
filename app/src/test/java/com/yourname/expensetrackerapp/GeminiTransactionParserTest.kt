package com.yourname.expensetrackerapp

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Offline tests of the request building and response validation — no network involved. */
class GeminiTransactionParserTest {

    private val income = TransactionCategories.income
    private val expense = TransactionCategories.expense

    /** Fixed "now": 29 Sep 2026, 14:30 local time. */
    private val reference: Long = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 29, 14, 30, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun midnightOf(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Wraps [inner] (the model's JSON answer) in a generateContent response envelope. */
    private fun envelope(inner: String, finishReason: String = "STOP", extraParts: String = ""): String {
        val text = Gson().toJson(inner)
        return """{"candidates":[{"content":{"role":"model","parts":[$extraParts{"text":$text}]},"finishReason":"$finishReason"}]}"""
    }

    private fun parse(inner: String) = GeminiTransactionParser.parseResponse(envelope(inner), income, expense, reference)

    @Test
    fun `request sends transcript, real categories without the placeholder, and today's date`() {
        val body = GeminiTransactionParser.buildRequestBody("spent 500 on petrol today", income, expense, reference)
        val root = JsonParser.parseString(body).asJsonObject
        val instruction = root.getAsJsonObject("systemInstruction").getAsJsonArray("parts")[0].asJsonObject["text"].asString
        val userText = root.getAsJsonArray("contents")[0].asJsonObject.getAsJsonArray("parts")[0].asJsonObject["text"].asString

        assertTrue(userText.contains("spent 500 on petrol today"))
        assertTrue(instruction.contains("2026-09-29"))
        assertTrue(instruction.contains("\"Transportation\""))
        assertTrue(instruction.contains("\"Salary\""))
        assertFalse(instruction.contains("Select Expense Category"))
        assertFalse(instruction.contains("Select Income Category"))
        assertEquals("application/json", root.getAsJsonObject("generationConfig")["responseMimeType"].asString)
    }

    @Test
    fun `valid expense is mapped with Gemini's own description`() {
        val result = parse("""{"type":"Expense","debtDirection":null,"amount":500,"category":"Transportation","personName":null,"date":"2026-09-29","dueDate":null,"description":"Petrol Expense"}""")!!
        assertEquals("Expense", result.type)
        assertEquals(500.0, result.amount!!, 0.001)
        assertEquals("Transportation", result.category)
        assertEquals("Petrol Expense", result.description)
        assertEquals(midnightOf(2026, Calendar.SEPTEMBER, 29), result.dateMillis)
        assertNull(result.personName)
    }

    @Test
    fun `category is matched case-insensitively onto the user's exact spelling`() {
        val result = parse("""{"type":"Expense","amount":120,"category":"food","description":"Lunch"}""")!!
        assertEquals("Food", result.category)
    }

    @Test
    fun `invented category is dropped instead of trusted`() {
        val result = parse("""{"type":"Expense","amount":120,"category":"Fuel","description":"Fuel"}""")!!
        assertNull(result.category)
        assertEquals(120.0, result.amount!!, 0.001)
    }

    @Test
    fun `placeholder category is never accepted`() {
        val result = parse("""{"type":"Expense","amount":50,"category":"Select Expense Category","description":"x"}""")!!
        assertNull(result.category)
    }

    @Test
    fun `income category from the expense list is rejected`() {
        val result = parse("""{"type":"Income","amount":50000,"category":"Food","description":"Salary"}""")!!
        assertNull(result.category)
    }

    @Test
    fun `debt keeps person and due date, category is not used`() {
        val result = parse("""{"type":"Debt","debtDirection":"Payable","amount":15000,"category":"Food","personName":"Brother","date":null,"dueDate":"2026-10-29","description":"Loan From Brother"}""")!!
        assertEquals("Debt", result.type)
        assertEquals("Payable", result.debtDirection)
        assertEquals("Brother", result.personName)
        assertNull(result.category)
        assertEquals(midnightOf(2026, Calendar.OCTOBER, 29), result.dueDateMillis)
        assertNull(result.dateMillis)
    }

    @Test
    fun `debt-only fields are dropped for an expense`() {
        val result = parse("""{"type":"Expense","debtDirection":"Payable","amount":10,"personName":"Ali","dueDate":"2026-10-01","description":"Tea"}""")!!
        assertNull(result.debtDirection)
        assertNull(result.personName)
        assertNull(result.dueDateMillis)
    }

    @Test
    fun `future transaction date is discarded, past date kept`() {
        assertNull(parse("""{"type":"Expense","amount":10,"date":"2026-09-30","description":"Tea"}""")!!.dateMillis)
        assertEquals(
            midnightOf(2026, Calendar.SEPTEMBER, 28),
            parse("""{"type":"Expense","amount":10,"date":"2026-09-28","description":"Tea"}""")!!.dateMillis
        )
    }

    @Test
    fun `impossible or malformed dates are discarded`() {
        assertNull(parse("""{"type":"Expense","amount":10,"date":"2026-02-31","description":"Tea"}""")!!.dateMillis)
        assertNull(parse("""{"type":"Expense","amount":10,"date":"yesterday","description":"Tea"}""")!!.dateMillis)
    }

    @Test
    fun `non-positive amount is dropped and amount is rounded to cents`() {
        assertNull(parse("""{"type":"Expense","amount":-5,"description":"x"}""")!!.amount)
        assertEquals(12.35, parse("""{"type":"Expense","amount":12.345,"description":"x"}""")!!.amount!!, 0.0001)
    }

    @Test
    fun `nothing usable returns null so the caller falls back`() {
        assertNull(parse("""{"type":null,"amount":null,"description":""}"""))
    }

    @Test
    fun `blank description gets a sensible fallback`() {
        assertEquals("Food Expense", parse("""{"type":"Expense","amount":300,"category":"Food","description":""}""")!!.description)
        assertEquals("Lent To Ali", parse("""{"type":"Debt","debtDirection":"Receivable","amount":300,"personName":"Ali","description":"  "}""")!!.description)
    }

    @Test
    fun `thought parts are ignored`() {
        val thought = """{"text":"reasoning...","thought":true},"""
        val response = envelope("""{"type":"Income","amount":80000,"category":"Salary","description":"Monthly Salary"}""", extraParts = thought)
        val result = GeminiTransactionParser.parseResponse(response, income, expense, reference)
        assertNotNull(result)
        assertEquals("Salary", result!!.category)
    }

    @Test
    fun `blocked or truncated response returns null`() {
        val inner = """{"type":"Expense","amount":10,"description":"Tea"}"""
        assertNull(GeminiTransactionParser.parseResponse(envelope(inner, finishReason = "SAFETY"), income, expense, reference))
        assertNull(GeminiTransactionParser.parseResponse(envelope(inner, finishReason = "MAX_TOKENS"), income, expense, reference))
    }

    @Test
    fun `garbage or error bodies return null`() {
        assertNull(GeminiTransactionParser.parseResponse("not json", income, expense, reference))
        assertNull(GeminiTransactionParser.parseResponse("""{"error":{"code":503}}""", income, expense, reference))
        assertNull(GeminiTransactionParser.parseResponse(envelope("not json either"), income, expense, reference))
    }
}
