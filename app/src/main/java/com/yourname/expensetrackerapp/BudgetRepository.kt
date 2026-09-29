package com.yourname.expensetrackerapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persists one independently-configured Budget per (BudgetPeriod, cycle) — e.g. a separate
 *  budget row for "July 2026" and "August 2026" under MONTHLY, but a single ongoing row for
 *  DAILY since that one always tracks "today" live. */
object BudgetRepository {
    private const val PREFS_NAME = "BudgetPrefs"
    private const val KEY_BUDGETS = "budgets"

    private val gson = Gson()
    private val transactionDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun getAll(context: Context): MutableList<Budget> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BUDGETS, null) ?: return mutableListOf()
        val type = object : TypeToken<MutableList<Budget>>() {}.type
        return gson.fromJson(json, type) ?: mutableListOf()
    }

    fun getForCycle(context: Context, period: BudgetPeriod, cycleKey: String): Budget? {
        return getAll(context).find { it.period == period && it.cycleKey == cycleKey }
    }

    /** Upserts by (period, cycleKey) — saving a budget for a cycle that already has one replaces it. */
    fun save(context: Context, budget: Budget) {
        val all = getAll(context)
        all.removeAll { it.period == budget.period && it.cycleKey == budget.cycleKey }
        all.add(budget)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_BUDGETS, gson.toJson(all)).apply()
    }

    /** Sums income/expense from [transactions] that fall within [start, end). When [includeDebts]
     *  is true, Payable/Receivable debts count too, per [DebtAccounting]. */
    fun cycleTotals(start: Date, end: Date, transactions: List<Transaction>, includeDebts: Boolean): CycleTotals {
        var income = 0.0
        var expense = 0.0
        for (transaction in transactions) {
            val date = try {
                transactionDateFormat.parse(transaction.date)
            } catch (e: Exception) {
                null
            } ?: continue

            if (!date.before(start) && date.before(end)) {
                income += DebtAccounting.incomeAmount(transaction, includeDebts)
                expense += DebtAccounting.expenseAmount(transaction, includeDebts)
            }
        }
        return CycleTotals(income, expense)
    }

    /** Expense grouped by category within [start, end), sorted highest-first. When [includeDebts]
     *  is true, debts that currently count as an expense (see [DebtAccounting]) are folded in as
     *  two extra pseudo-categories, "Payable Debt" and "Receivable Debt", so the same list total
     *  matches [cycleTotals]'s expense figure exactly. */
    fun cycleExpenseByCategory(
        start: Date,
        end: Date,
        transactions: List<Transaction>,
        includeDebts: Boolean
    ): List<Pair<String, Double>> {
        val totals = LinkedHashMap<String, Double>()
        for (transaction in transactions) {
            val date = try {
                transactionDateFormat.parse(transaction.date)
            } catch (e: Exception) {
                null
            } ?: continue
            if (date.before(start) || !date.before(end)) continue

            if (transaction.type == "Expense") {
                val category = transaction.category ?: "Other"
                totals[category] = (totals[category] ?: 0.0) + transaction.amount
            } else if (includeDebts && transaction.type == "Debt") {
                val amount = DebtAccounting.expenseAmount(transaction, includeDebts = true)
                if (amount > 0.0) {
                    val label = if (transaction.category == "Payable") "Payable Debt" else "Receivable Debt"
                    totals[label] = (totals[label] ?: 0.0) + amount
                }
            }
        }
        return totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    data class CycleTotals(val income: Double, val expense: Double)
}
