package com.yourname.expensetrackerapp

/** Extends [BudgetPeriod]'s four recurring cycles with two pure display-range options that don't
 *  fit a recurring-cycle model (there's no "next cycle" to key a budget or a notification off
 *  of): ALL_TIME and CUSTOM. Used to filter Home's totals and (for the two new options)
 *  Statistics' category breakdown — kept separate from BudgetPeriod itself since budgets stay
 *  tied to a real recurring cycle. */
enum class PeriodType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    CUSTOM("Custom Duration"),
    ALL_TIME("All Time");

    fun toBudgetPeriod(): BudgetPeriod? = when (this) {
        DAILY -> BudgetPeriod.DAILY
        WEEKLY -> BudgetPeriod.WEEKLY
        MONTHLY -> BudgetPeriod.MONTHLY
        YEARLY -> BudgetPeriod.YEARLY
        CUSTOM, ALL_TIME -> null
    }

    companion object {
        fun fromBudgetPeriod(period: BudgetPeriod): PeriodType = when (period) {
            BudgetPeriod.DAILY -> DAILY
            BudgetPeriod.WEEKLY -> WEEKLY
            BudgetPeriod.MONTHLY -> MONTHLY
            BudgetPeriod.YEARLY -> YEARLY
        }
    }
}
