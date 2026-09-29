package com.yourname.expensetrackerapp

/**
 * Keyword/synonym tables for [TransactionVoiceParser]. Kept separate from the parsing logic so
 * the dictionaries can grow (more verbs, more category synonyms) without touching the parser
 * itself.
 */
object VoiceKeywords {

    // Debt verbs are checked before Income/Expense verbs since they're the most specific and
    // least likely to be a false positive ("borrowed", "lent" rarely mean anything else).
    val payableVerbs = listOf("borrowed", "borrow", "borrowing", "owe", "owed", "owing")
    val receivableVerbs = listOf("lent", "lend", "lending", "loaned", "loan")

    val expenseVerbs = listOf(
        "spent", "spend", "spending", "paid", "pay", "paying", "bought", "buy", "buying",
        "purchased", "purchase", "purchasing", "put", "cost", "costs", "costing", "used",
        "billed", "withdrew", "withdraw"
    )
    val incomeVerbs = listOf(
        "received", "receive", "receiving", "got", "getting", "earned", "earn", "earning",
        "salary", "income", "credited", "deposited", "deposit", "refund", "refunded",
        "cashback", "bonus", "profit"
    )

    /** Category display name -> spoken synonyms. Deliberately conservative and generic — no
     *  brand/merchant names (e.g. "KFC", "Carrefour"), since a merchant name doesn't reliably
     *  imply a category and guessing wrong is worse than leaving the category unresolved. This
     *  list is intentionally broad (many everyday words per category) since the whole point is
     *  to recognize as much real, casual phrasing as safely possible — see [TransactionVoiceParser]. */
    val categorySynonyms: Map<String, List<String>> = mapOf(
        "Food" to listOf(
            "food", "grocery", "groceries", "restaurant", "dining", "lunch", "dinner", "breakfast",
            "meal", "meals", "snack", "snacks", "coffee", "tea", "cafe", "café", "burger", "pizza",
            "takeaway", "eating", "eat"
        ),
        "Transportation" to listOf(
            "transport", "transportation", "cab", "taxi", "uber", "careem", "rickshaw", "fuel",
            "gas", "petrol", "diesel", "bus", "train", "flight", "ticket", "tickets", "parking",
            "toll", "ride", "fare"
        ),
        "Entertainment" to listOf(
            "movie", "movies", "netflix", "cinema", "games", "gaming", "concert", "show", "party",
            "outing", "subscription", "streaming"
        ),
        "Shopping" to listOf(
            "shopping", "clothes", "clothing", "shoes", "dress", "mall", "store", "apparel",
            "electronics", "gadget", "phone", "shirt", "shirts", "pants", "trousers", "jacket",
            "jeans", "kurta"
        ),
        "Health" to listOf(
            "health", "doctor", "medicine", "hospital", "pharmacy", "medical", "clinic",
            "checkup", "dentist", "dental", "gym", "fitness"
        ),
        "Education" to listOf(
            "education", "tuition", "course", "courses", "school", "book", "books", "fee", "fees",
            "university", "college", "class", "classes", "exam"
        ),
        "Bills" to listOf(
            "bill", "bills", "rent", "electricity", "utility", "utilities", "internet", "wifi",
            "water", "mobile", "recharge", "maintenance"
        ),
        "Salary" to listOf("salary", "paycheck", "wage", "wages", "payroll"),
        "Freelance" to listOf("freelance", "gig", "contract", "project", "client"),
        "Investment" to listOf(
            "investment", "dividend", "dividends", "stock", "stocks", "shares", "interest",
            "crypto", "bitcoin"
        ),
        "Gift" to listOf("gift", "gifted", "present", "donation", "charity", "eidi")
    )

    /** Words that are money-related but never part of the numeric amount itself — stripped out
     *  when composing the leftover description text. */
    val currencyWords = listOf(
        "rupees", "rupee", "pkr", "rs", "dollars", "dollar", "usd", "bucks", "cents", "cent"
    )

    val fillerWords = listOf(
        "i", "a", "an", "the", "on", "at", "for", "that", "was", "is", "of", "to", "from", "with",
        "today", "yesterday", "tomorrow", "due", "around", "about", "approximately", "almost",
        "my", "your", "his", "her", "our", "their", "its",
        "add", "log", "record", "note", "enter", "expense", "expenses", "transaction",
        // Not treated as Debt/type-detection verbs (too ambiguous with plain gifting/expense —
        // see expenseVerbs/receivableVerbs), but still just noise once left over in a description.
        "gave", "give", "giving"
    )

    /** Possessive determiners that can precede a debt person reference ("from *my* brother") —
     *  skipped over when capturing the actual person/relation word so it isn't itself mistaken
     *  for the name (see [TransactionVoiceParser]'s person-name extractor). */
    val possessiveDeterminers = listOf("my", "his", "her", "our", "their", "its")

    val numberWords: Map<String, Int> = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    /** Magnitude words that scale whatever number came before them, e.g. "5 hundred" = 500,
     *  "130 k" = 130000, "1 lakh 30 thousand" = 130000. Includes the South Asian units (lakh =
     *  100,000; crore = 10,000,000, with the common "lac"/"crores" spelling variants) alongside
     *  the standard English ones, since these come up constantly in day-to-day amount phrasing
     *  ("1 lakh 30 thousand rupees") that a hundred/thousand-only parser would otherwise mangle. */
    val magnitudeWords: Map<String, Long> = mapOf(
        "hundred" to 100L,
        "thousand" to 1_000L, "k" to 1_000L,
        "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L,
        "million" to 1_000_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L,
        "billion" to 1_000_000_000L
    )

    val monthNames: Map<String, Int> = mapOf(
        "january" to 0, "jan" to 0, "february" to 1, "feb" to 1, "march" to 2, "mar" to 2,
        "april" to 3, "apr" to 3, "may" to 4, "june" to 5, "jun" to 5, "july" to 6, "jul" to 6,
        "august" to 7, "aug" to 7, "september" to 8, "sep" to 8, "sept" to 8, "october" to 9,
        "oct" to 9, "november" to 10, "nov" to 10, "december" to 11, "dec" to 11
    )

    val weekdayNames: Map<String, Int> = mapOf(
        "sunday" to java.util.Calendar.SUNDAY, "monday" to java.util.Calendar.MONDAY,
        "tuesday" to java.util.Calendar.TUESDAY, "wednesday" to java.util.Calendar.WEDNESDAY,
        "thursday" to java.util.Calendar.THURSDAY, "friday" to java.util.Calendar.FRIDAY,
        "saturday" to java.util.Calendar.SATURDAY
    )
}
