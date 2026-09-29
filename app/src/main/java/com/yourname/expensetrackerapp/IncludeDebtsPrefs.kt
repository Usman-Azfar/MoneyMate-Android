package com.yourname.expensetrackerapp

import android.content.Context

/** Whether Debt transactions (Payable/Receivable) count toward Income/Expense totals app-wide —
 *  see [DebtAccounting] for the exact rule. Defaults to false (off) so existing installs keep
 *  today's behavior: debts stay purely in "Outstanding Debts" and never touch Income/Expense. */
object IncludeDebtsPrefs {
    private const val PREFS_NAME = "IncludeDebtsPrefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
