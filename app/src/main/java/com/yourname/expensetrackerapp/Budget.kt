package com.yourname.expensetrackerapp

/** Income isn't stored here — it's fetched live from actual Income transactions in the budget's
 *  cycle window (see [BudgetRepository.cycleTotals]) rather than typed in, so it's always current
 *  even if transactions change after this budget was saved. */
data class Budget(
    val period: BudgetPeriod,
    val expectedSpendAmount: Double,
    /** Start-of-cycle timestamp (epoch millis) this budget is pinned to. Ignored for DAILY,
     *  which always tracks "today" live instead of a stored date. */
    val cycleStartMillis: Long = 0L,
    val lastNotifiedCycleKey: String? = null,
    /** Dedupes the separate "spending exceeded income" alert, independent of [lastNotifiedCycleKey]
     *  which only tracks the expected-spend alert. */
    val lastIncomeExceededCycleKey: String? = null
) {
    /** Identity of the specific cycle instance this budget applies to — see [BudgetPeriod.cycleKeyFor]. */
    val cycleKey: String
        get() = period.cycleKeyFor(cycleStartMillis)
}
