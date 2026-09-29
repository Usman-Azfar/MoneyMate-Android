package com.yourname.expensetrackerapp

import android.content.Context
import java.util.Date
import java.util.UUID

/**
 * Call after a new EXPENSE transaction is saved (or a budget is edited). Checks every configured
 * budget whose cycle is currently active for two independent conditions — spend exceeding the
 * expected-spend amount, and spend exceeding income (i.e. negative savings) — and records/fires
 * a notification for whichever hasn't already been notified this cycle. A WEEKLY/MONTHLY/YEARLY
 * budget pinned to a past or future cycle is skipped — it only fires while "now" actually falls
 * within its own window.
 */
object BudgetNotifier {
    fun checkAndNotify(context: Context) {
        if (!NotificationPrefs.isEnabled(context)) return

        val transactions = TransactionRepository.getAll(context)
        val budgets = BudgetRepository.getAll(context)
        val now = Date()

        for (budget in budgets) {
            val (start, end) = budget.period.cycleWindow(budget.cycleStartMillis)
            if (now.before(start) || !now.before(end)) continue

            val cycleKey = budget.cycleKey
            val totals = BudgetRepository.cycleTotals(start, end, transactions, IncludeDebtsPrefs.isEnabled(context))
            var updated = budget

            if (budget.lastNotifiedCycleKey != cycleKey && totals.expense > budget.expectedSpendAmount) {
                val over = totals.expense - budget.expectedSpendAmount
                val title = "${budget.period.label} budget exceeded"
                val message = "You've spent ${CurrencyFormatter.format(context, totals.expense)} of your " +
                    "${CurrencyFormatter.format(context, budget.expectedSpendAmount)} ${budget.period.label.lowercase()} " +
                    "budget — over by ${CurrencyFormatter.format(context, over)}."
                NotificationRepository.add(
                    context,
                    NotificationEntry(
                        id = UUID.randomUUID().toString(),
                        period = budget.period,
                        title = title,
                        message = message,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                NotificationHelper.showBudgetExceeded(context, budget.period.ordinal, title, message)
                updated = updated.copy(lastNotifiedCycleKey = cycleKey)
            }

            if (budget.lastIncomeExceededCycleKey != cycleKey &&
                totals.income > 0 && totals.expense > totals.income
            ) {
                val over = totals.expense - totals.income
                val title = "${budget.period.label} spending exceeds income"
                val message = "You've spent ${CurrencyFormatter.format(context, totals.expense)} against " +
                    "${CurrencyFormatter.format(context, totals.income)} of ${budget.period.label.lowercase()} " +
                    "income — savings is negative by ${CurrencyFormatter.format(context, over)}."
                NotificationRepository.add(
                    context,
                    NotificationEntry(
                        id = UUID.randomUUID().toString(),
                        period = budget.period,
                        title = title,
                        message = message,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
                NotificationHelper.showBudgetExceeded(context, budget.period.ordinal + 100, title, message)
                updated = updated.copy(lastIncomeExceededCycleKey = cycleKey)
            }

            if (updated != budget) {
                BudgetRepository.save(context, updated)
            }
        }
    }
}
