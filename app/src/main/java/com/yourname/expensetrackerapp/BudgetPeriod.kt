package com.yourname.expensetrackerapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Uses java.util.Calendar/Date rather than java.time: minSdk is 24 and this
 * project has no core-library desugaring enabled, so java.time isn't safely
 * available everywhere below API 26.
 *
 * DAILY always tracks "today" live and needs no user-picked date — its cycle
 * rolls over automatically at midnight. WEEKLY/MONTHLY/YEARLY are each pinned
 * to a specific user-chosen [cycleStartMillis] (see [Budget.cycleStartMillis]),
 * so a distinct budget can exist per week/month/year instead of always
 * following "the current one".
 */
enum class BudgetPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    /** Start (inclusive) and end (exclusive) of this period's cycle. [cycleStartMillis] is
     *  ignored for DAILY (always today); for the others it's normalized to the start of its
     *  own week/month/year regardless of which day within it was picked. */
    fun cycleWindow(cycleStartMillis: Long = 0L): Pair<Date, Date> {
        val cal = Calendar.getInstance()

        return when (this) {
            DAILY -> {
                normalizeToMidnight(cal)
                val start = cal.time
                cal.add(Calendar.DAY_OF_YEAR, 1)
                Pair(start, cal.time)
            }
            WEEKLY -> {
                cal.timeInMillis = cycleStartMillis
                normalizeToMidnight(cal)
                val start = cal.time
                cal.add(Calendar.DAY_OF_YEAR, 7)
                Pair(start, cal.time)
            }
            MONTHLY -> {
                cal.timeInMillis = cycleStartMillis
                normalizeToMidnight(cal)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = cal.time
                cal.add(Calendar.MONTH, 1)
                Pair(start, cal.time)
            }
            YEARLY -> {
                cal.timeInMillis = cycleStartMillis
                normalizeToMidnight(cal)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = cal.time
                cal.add(Calendar.YEAR, 1)
                Pair(start, cal.time)
            }
        }
    }

    /** Stable key identifying a specific cycle instance — DAILY has one constant key since it
     *  auto-rolls to whatever "today" is; the others key off the cycle's own start date, e.g.
     *  "2026-07" for July 2026, so each week/month/year gets independent budget storage and
     *  independent once-per-cycle notification dedupe. */
    fun cycleKeyFor(cycleStartMillis: Long): String {
        if (this == DAILY) return "DAILY"
        val (start, _) = cycleWindow(cycleStartMillis)
        val pattern = when (this) {
            WEEKLY -> "yyyy-MM-dd"
            MONTHLY -> "yyyy-MM"
            YEARLY -> "yyyy"
            DAILY -> ""
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(start)
    }

    /** Where to preselect the cycle picker before the user has chosen anything: the current
     *  week/month/year. Unused for DAILY. */
    fun defaultCycleStartMillis(): Long {
        val cal = Calendar.getInstance()
        normalizeToMidnight(cal)
        when (this) {
            WEEKLY -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
            YEARLY -> cal.set(Calendar.DAY_OF_YEAR, 1)
            DAILY -> {}
        }
        return cal.timeInMillis
    }

    private fun normalizeToMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
}
