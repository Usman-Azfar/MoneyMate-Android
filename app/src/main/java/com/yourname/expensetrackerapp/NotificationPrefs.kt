package com.yourname.expensetrackerapp

import android.content.Context

/** Master mute switch for budget-alert notifications, checked by [BudgetNotifier] before it
 *  ever fires one. */
object NotificationPrefs {
    private const val PREFS_NAME = "NotificationSettingsPrefs"
    private const val KEY_ENABLED = "budget_alerts_enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
