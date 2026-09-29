package com.yourname.expensetrackerapp

/**
 * Single source of truth for how a transaction counts toward Income/Expense totals, used
 * consistently by Home, Statistics, and the PDF/CSV reports so all four never disagree.
 *
 * Income and Expense transactions always count at face value. A Debt only counts when
 * [includeDebts] is true (see [IncludeDebtsPrefs]), based on which direction cash actually
 * moved given its current settled state:
 *  - Payable (money borrowed): unsettled means the cash is already in hand, so it counts as
 *    Income; once paid back, that outflow counts as an Expense instead.
 *  - Receivable (money lent out): unsettled means the cash already left, so it counts as an
 *    Expense; once collected back, that inflow counts as Income instead.
 *
 * A single debt transaction only ever contributes to one side at a time (based on its current
 * [Transaction.isSettled] flag), so a debt's full borrow-then-repay (or lend-then-collect)
 * lifecycle nets to zero balance impact overall, matching real cash flow.
 */
object DebtAccounting {

    fun incomeAmount(transaction: Transaction, includeDebts: Boolean): Double = when {
        transaction.type == "Income" -> transaction.amount
        !includeDebts || transaction.type != "Debt" -> 0.0
        transaction.category == "Payable" && !transaction.isSettled -> transaction.amount
        transaction.category == "Receivable" && transaction.isSettled -> transaction.amount
        else -> 0.0
    }

    fun expenseAmount(transaction: Transaction, includeDebts: Boolean): Double = when {
        transaction.type == "Expense" -> transaction.amount
        !includeDebts || transaction.type != "Debt" -> 0.0
        transaction.category == "Payable" && transaction.isSettled -> transaction.amount
        transaction.category == "Receivable" && !transaction.isSettled -> transaction.amount
        else -> 0.0
    }
}
