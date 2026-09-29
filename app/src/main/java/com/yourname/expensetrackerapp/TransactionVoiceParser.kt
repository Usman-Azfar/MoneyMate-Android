package com.yourname.expensetrackerapp

import java.util.Calendar

/**
 * The result of parsing a spoken transcript into transaction fields. Every field is nullable
 * (description aside) because "couldn't determine this" is a first-class outcome, not an error —
 * see [TransactionVoiceParser]'s class doc for why nothing here is ever guessed.
 */
data class ParsedTransaction(
    val type: String? = null,
    val debtDirection: String? = null,
    val amount: Double? = null,
    val category: String? = null,
    val personName: String? = null,
    val dateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val description: String = ""
)

/**
 * Turns a spoken transcript (e.g. "Spent 500 on groceries yesterday") into a [ParsedTransaction],
 * entirely offline and dependency-free. A pure function of its inputs — no Android framework
 * calls — so it's trivially unit-testable and swappable later for a cloud-NLU-backed parser
 * behind the same signature.
 *
 * Every extractor here only ever fills a field on concrete evidence (an exact keyword, a curated
 * synonym, a matched date/number pattern). There is no fuzzy "closest word" guessing anywhere —
 * a merchant name like "Carrefour" that isn't in the category synonym table simply stays out of
 * every field except the leftover description, rather than getting forced into a category. A
 * caller pre-fills its form from the non-null fields and leaves everything else at its existing
 * default (today's date, the category placeholder, etc.), so an unresolved field is always
 * visibly unresolved rather than silently wrong.
 */
object TransactionVoiceParser {

    /** A digit run directly followed by "k"/"K" ("130k") is normalized to "130 k" up front, so
     *  the rest of the pipeline only ever has to deal with one, space-separated token shape —
     *  this runs on the case-preserving text too, before it's lowercased, so the same
     *  normalization applies consistently to both the parsing pass and the description text. */
    private val attachedKSuffixRegex = Regex("""(\d)([kK])\b""")

    fun parse(
        transcript: String,
        incomeCategories: List<String>,
        expenseCategories: List<String>,
        referenceMillis: Long = System.currentTimeMillis()
    ): ParsedTransaction {
        val normalizedOriginal = transcript.trim().replace(attachedKSuffixRegex, "$1 $2")
        if (normalizedOriginal.isEmpty()) return ParsedTransaction()

        var remaining = normalizedOriginal.lowercase()
        val (detectedType, direction) = detectType(remaining)

        val dueDateMatch = extractDueDate(remaining, referenceMillis)
        if (dueDateMatch != null) remaining = remaining.replaceFirst(dueDateMatch.matchedText, " ")

        val dateMatch = extractDate(remaining, referenceMillis)
        if (dateMatch != null) remaining = remaining.replaceFirst(dateMatch.matchedText, " ")

        val amountMatch = extractAmount(remaining)
        if (amountMatch != null) remaining = remaining.replaceFirst(amountMatch.matchedText, " ")

        // No verb told us what kind of transaction this is, but there's clearly an amount being
        // logged — Expense is by far the most common entry in a tracker like this, and it's a
        // one-tap fix if wrong (switch the type radio), which beats leaving the whole form
        // uninitialized just because the utterance skipped a verb ("500 on groceries").
        val type = detectedType ?: if (amountMatch != null) "Expense" else null

        val category = when (type) {
            "Income" -> matchCategory(remaining, incomeCategories)
            "Expense" -> matchCategory(remaining, expenseCategories)
            else -> null
        }

        var personName: String? = null
        var personMatch: PersonMatch? = null
        if (type == "Debt") {
            personMatch = extractPersonName(remaining, direction)
            personName = personMatch?.name
        }

        // Built from the case-preserving original (not the lowercased working copy above) so a
        // proper noun the recognizer already capitalized correctly ("KFC", "Ali") keeps that
        // casing instead of being forced through a lowercase-then-recapitalize round trip.
        var descriptionSource = normalizedOriginal
        listOfNotNull(dueDateMatch?.matchedText, dateMatch?.matchedText, amountMatch?.matchedText, personMatch?.matchedText)
            .forEach { matched -> descriptionSource = descriptionSource.replaceFirst(matched, " ", ignoreCase = true) }

        return ParsedTransaction(
            type = type,
            debtDirection = direction,
            amount = amountMatch?.value,
            category = category,
            personName = personName,
            dateMillis = dateMatch?.millis,
            dueDateMillis = dueDateMatch?.millis,
            description = buildDescription(descriptionSource)
        )
    }

    // ---- Type / direction ----------------------------------------------------------------

    /** Debt verbs are checked first since they're the most specific — "borrowed"/"lent" are
     *  rarely anything else — so an utterance mentioning both a debt verb and, say, "paid" (as
     *  in "paid back") still resolves to Debt rather than Expense. */
    private fun detectType(text: String): Pair<String?, String?> {
        if (containsAny(text, VoiceKeywords.payableVerbs)) return "Debt" to "Payable"
        if (containsAny(text, VoiceKeywords.receivableVerbs)) return "Debt" to "Receivable"
        if (containsAny(text, VoiceKeywords.expenseVerbs)) return "Expense" to null
        if (containsAny(text, VoiceKeywords.incomeVerbs)) return "Income" to null
        return null to null
    }

    private fun containsAny(text: String, words: List<String>) = words.any { containsWord(text, it) }

    private fun containsWord(text: String, phrase: String): Boolean =
        Regex("\\b" + Regex.escape(phrase) + "\\b").containsMatchIn(text)

    // ---- Amount ----------------------------------------------------------------------------

    private data class AmountMatch(val value: Double, val matchedText: String)

    private val plainNumberRegex = Regex("""^\d{1,3}(?:,\d{3})*(?:\.\d+)?$|^\d+(?:\.\d+)?$""")

    /** A bare digit token ("130", "1,200.50") or a spelled-out number word ("five") — the two
     *  interchangeable "value" building blocks a magnitude word ("hundred"/"k"/"lakh"/"crore")
     *  can scale. Digits and words are handled by one path rather than two so mixed phrasing like
     *  "1 lakh 30 thousand" (digit, word, digit, word) parses as a single amount instead of the
     *  pieces being found independently and only the last/largest one kept. */
    private fun tokenValue(token: String): Double? {
        if (plainNumberRegex.matches(token)) return token.replace(",", "").toDoubleOrNull()
        return VoiceKeywords.numberWords[token]?.toDouble()
    }

    /** Scans left to right for the first run of value/magnitude tokens and sums it, e.g.
     *  "1 lakh 30 thousand" -> (1 * 100,000) + (30 * 1,000) = 130,000, "130 k" -> 130 * 1,000,
     *  "five hundred" -> 5 * 100, "twenty five thousand" -> (20 + 5) * 1,000. A bare "and" inside
     *  an active run is skipped rather than ending it, so "one hundred and thirty" still parses
     *  as one number. Only ever starts a run at a genuine value token, never at a lone magnitude
     *  word, so stray "thousand"/"lakh" noise can't kick off a match on its own. */
    private fun extractAmount(text: String): AmountMatch? {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (start in tokens.indices) {
            val startValue = tokenValue(tokens[start]) ?: continue
            var current = startValue
            var total = 0.0
            var end = start + 1
            while (end < tokens.size) {
                val token = tokens[end]
                val magnitude = VoiceKeywords.magnitudeWords[token]
                val value = tokenValue(token)
                when {
                    token == "and" -> { /* transparent inside a run, e.g. "hundred and thirty" */ }
                    magnitude != null -> {
                        total += current * magnitude
                        current = 0.0
                    }
                    value != null -> current += value
                    else -> break
                }
                end++
            }
            total += current
            if (total > 0.0) {
                return AmountMatch(total, tokens.subList(start, end).joinToString(" "))
            }
        }
        return null
    }

    // ---- Category ----------------------------------------------------------------------------

    /** Only matches on an exact category name or a curated synonym — see class doc. [categories]
     *  includes the "Select X Category" placeholder at index 0, which is never matched against. */
    private fun matchCategory(text: String, categories: List<String>): String? {
        val real = categories.drop(1)
        for (category in real) {
            if (containsWord(text, category.lowercase())) return category
        }
        for (category in real) {
            val synonyms = VoiceKeywords.categorySynonyms[category] ?: continue
            if (synonyms.any { containsWord(text, it) }) return category
        }
        return null
    }

    // ---- Person name (Debt only) --------------------------------------------------------------

    private data class PersonMatch(val name: String, val matchedText: String)

    private fun extractPersonName(text: String, direction: String?): PersonMatch? {
        val prepositions = when (direction) {
            "Payable" -> listOf("from")
            "Receivable" -> listOf("to")
            else -> listOf("from", "to", "with")
        }
        // An optional possessive ("from *my* brother") is matched but not captured as the name
        // itself — otherwise "my" would be captured instead of "brother".
        val possessive = VoiceKeywords.possessiveDeterminers.joinToString("|")
        for (prep in prepositions) {
            val match = Regex("\\b$prep\\s+(?:(?:$possessive)\\s+)?([a-z][a-z'-]*)").find(text) ?: continue
            val captured = match.groupValues[1]
            if (captured in VoiceKeywords.fillerWords) continue
            return PersonMatch(captured.replaceFirstChar { it.uppercase() }, match.value)
        }
        return null
    }

    // ---- Dates ----------------------------------------------------------------------------

    private data class DateMatch(val millis: Long, val matchedText: String)

    /** "due (on) <phrase>" — captured and removed before the generic date scan below runs, so a
     *  due date and a transaction date in the same sentence ("...due next Friday") don't collide. */
    private fun extractDueDate(text: String, referenceMillis: Long): DateMatch? {
        val match = Regex("""\bdue\s+(?:on\s+)?([a-z0-9/ ]+?)(?:[.,]|$)""").find(text) ?: return null
        val phrase = match.groupValues[1].trim()
        val millis = parseDatePhrase(phrase, referenceMillis) ?: return null
        return DateMatch(millis, match.value)
    }

    /** Scans word windows (longest first, so "next friday" wins over a stray bare word) for any
     *  phrase [parseDatePhrase] recognizes. */
    private fun extractDate(text: String, referenceMillis: Long): DateMatch? {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (windowSize in minOf(4, words.size) downTo 1) {
            for (start in 0..(words.size - windowSize)) {
                val phrase = words.subList(start, start + windowSize).joinToString(" ")
                val millis = parseDatePhrase(phrase, referenceMillis)
                if (millis != null) return DateMatch(millis, phrase)
            }
        }
        return null
    }

    private fun parseDatePhrase(phraseRaw: String, referenceMillis: Long): Long? {
        val phrase = phraseRaw.trim().trimEnd('.', ',')
        if (phrase.isEmpty()) return null

        fun midnight(cal: Calendar): Long {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun today() = Calendar.getInstance().apply { timeInMillis = referenceMillis }

        when (phrase) {
            "today" -> return midnight(today())
            "yesterday" -> return midnight(today().apply { add(Calendar.DAY_OF_YEAR, -1) })
            "tomorrow" -> return midnight(today().apply { add(Calendar.DAY_OF_YEAR, 1) })
        }

        Regex("""^(\d+)\s*days?\s*ago$""").matchEntire(phrase)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return null
            return midnight(today().apply { add(Calendar.DAY_OF_YEAR, -n) })
        }
        Regex("""^(\d+)\s*weeks?\s*ago$""").matchEntire(phrase)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return null
            return midnight(today().apply { add(Calendar.DAY_OF_YEAR, -n * 7) })
        }
        // Future offsets — mainly for due dates ("due in 2 weeks", "due in a month").
        Regex("""^in\s+(a|an|\d+)\s*(day|days|week|weeks|month|months|year|years)$""").matchEntire(phrase)?.let {
            val n = it.groupValues[1].let { g -> if (g == "a" || g == "an") 1 else g.toIntOrNull() } ?: return null
            val cal = today()
            when {
                it.groupValues[2].startsWith("day") -> cal.add(Calendar.DAY_OF_YEAR, n)
                it.groupValues[2].startsWith("week") -> cal.add(Calendar.DAY_OF_YEAR, n * 7)
                it.groupValues[2].startsWith("month") -> cal.add(Calendar.MONTH, n)
                else -> cal.add(Calendar.YEAR, n)
            }
            return midnight(cal)
        }

        // "next/last week/month/year" — a relative period, not tied to any specific weekday.
        // Checked before the weekday-name case below so "next month" doesn't fall through to a
        // failed weekday lookup for the word "month".
        Regex("""^(next|last)\s+(day|week|month|year)$""").matchEntire(phrase)?.let {
            val step = if (it.groupValues[1] == "next") 1 else -1
            val cal = today()
            when (it.groupValues[2]) {
                "day" -> cal.add(Calendar.DAY_OF_YEAR, step)
                "week" -> cal.add(Calendar.DAY_OF_YEAR, step * 7)
                "month" -> cal.add(Calendar.MONTH, step)
                else -> cal.add(Calendar.YEAR, step)
            }
            return midnight(cal)
        }

        Regex("""^(next|last)\s+([a-z]+)$""").matchEntire(phrase)?.let {
            val weekday = VoiceKeywords.weekdayNames[it.groupValues[2]] ?: return@let
            val cal = today()
            val step = if (it.groupValues[1] == "next") 1 else -1
            do { cal.add(Calendar.DAY_OF_YEAR, step) } while (cal.get(Calendar.DAY_OF_WEEK) != weekday)
            return midnight(cal)
        }

        // dd/MM or dd/MM/yyyy
        Regex("""^(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?$""").matchEntire(phrase)?.let {
            val day = it.groupValues[1].toIntOrNull() ?: return null
            val month = it.groupValues[2].toIntOrNull() ?: return null
            if (day !in 1..31 || month !in 1..12) return null
            val year = it.groupValues[3].toIntOrNull() ?: today().get(Calendar.YEAR)
            val cal = Calendar.getInstance()
            cal.set(year, month - 1, day, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        // "5th september" / "5 september"
        Regex("""^(\d{1,2})(?:st|nd|rd|th)?\s+([a-z]+)$""").matchEntire(phrase)?.let {
            val day = it.groupValues[1].toIntOrNull() ?: return null
            val month = VoiceKeywords.monthNames[it.groupValues[2]] ?: return null
            if (day !in 1..31) return null
            val cal = today()
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            return midnight(cal)
        }
        // "september 5" / "september 5th"
        Regex("""^([a-z]+)\s+(\d{1,2})(?:st|nd|rd|th)?$""").matchEntire(phrase)?.let {
            val month = VoiceKeywords.monthNames[it.groupValues[1]] ?: return null
            val day = it.groupValues[2].toIntOrNull() ?: return null
            if (day !in 1..31) return null
            val cal = today()
            cal.set(Calendar.MONTH, month)
            cal.set(Calendar.DAY_OF_MONTH, day)
            return midnight(cal)
        }

        return null
    }

    // ---- Description (leftover) --------------------------------------------------------------

    private val allVerbs by lazy {
        VoiceKeywords.payableVerbs + VoiceKeywords.receivableVerbs +
            VoiceKeywords.expenseVerbs + VoiceKeywords.incomeVerbs
    }

    private fun isDescriptionFiller(word: String): Boolean {
        val lower = word.lowercase()
        return lower in VoiceKeywords.fillerWords || lower in VoiceKeywords.currencyWords || lower in allVerbs
    }

    /** Trims filler/verb words only from the leading and trailing edges of what's left after the
     *  amount/date/person matches are removed, rather than filtering them out everywhere — so an
     *  internal connector word survives ("KFC *with* Ali", not "Kfc Ali") and the phrase still
     *  reads naturally instead of turning into a bare word salad. Casing is left exactly as the
     *  recognizer produced it; a capital is only forced on the very first letter if the result
     *  came back all-lowercase, so a proper noun the recognizer already capitalized ("KFC") isn't
     *  second-guessed. */
    private fun buildDescription(remainingText: String): String {
        val rawWords = remainingText.split(Regex("\\s+"))
            .map { it.trim('.', ',', '!', '?') }
            .filter { it.isNotBlank() }

        // Removing a matched span (amount/date/person) from the middle of a sentence often
        // leaves the same filler word stranded on both sides of the gap ("books for [1200] for
        // school" -> "books for for school") — collapse an immediately-repeated filler instead
        // of leaving that visible seam in the description.
        val words = mutableListOf<String>()
        for (word in rawWords) {
            val isDupeFiller = words.isNotEmpty() &&
                words.last().equals(word, ignoreCase = true) && isDescriptionFiller(word)
            if (!isDupeFiller) words.add(word)
        }

        while (words.isNotEmpty() && isDescriptionFiller(words.first())) words.removeAt(0)
        while (words.isNotEmpty() && isDescriptionFiller(words.last())) words.removeAt(words.lastIndex)

        if (words.isEmpty()) return ""
        val joined = words.joinToString(" ")
        return if (joined.first().isUpperCase()) joined else joined.replaceFirstChar { it.uppercase() }
    }
}
