package com.yourname.expensetrackerapp

import android.content.Context

/** Persists the user's chosen display window for Home's Total Balance / Income / Expense cards.
 *  Defaults to ALL_TIME, matching the app's original (unfiltered, sum-of-everything) behavior
 *  for anyone who never opens this setting. */
object HomePeriodPrefs {
    private const val PREFS_NAME = "HomePeriodPrefs"
    private const val KEY_TYPE = "type"
    private const val KEY_CYCLE_START = "cycle_start"
    private const val KEY_CUSTOM_START = "custom_start"
    private const val KEY_CUSTOM_END = "custom_end"

    fun getType(context: Context): PeriodType {
        val name = prefs(context).getString(KEY_TYPE, null) ?: return PeriodType.ALL_TIME
        return try {
            PeriodType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            PeriodType.ALL_TIME
        }
    }

    fun getCycleStartMillis(context: Context): Long = prefs(context).getLong(KEY_CYCLE_START, 0L)
    fun getCustomStartMillis(context: Context): Long = prefs(context).getLong(KEY_CUSTOM_START, 0L)
    fun getCustomEndMillis(context: Context): Long = prefs(context).getLong(KEY_CUSTOM_END, 0L)

    fun save(context: Context, type: PeriodType, cycleStartMillis: Long, customStartMillis: Long, customEndMillis: Long) {
        prefs(context).edit()
            .putString(KEY_TYPE, type.name)
            .putLong(KEY_CYCLE_START, cycleStartMillis)
            .putLong(KEY_CUSTOM_START, customStartMillis)
            .putLong(KEY_CUSTOM_END, customEndMillis)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
