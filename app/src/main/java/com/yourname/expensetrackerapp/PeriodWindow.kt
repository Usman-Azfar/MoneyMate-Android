package com.yourname.expensetrackerapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Pure date-window and label math for [PeriodType], shared by Home's totals filter and the
 *  Settings/Statistics period pickers. Kept free of Context/SharedPreferences so it's easy to
 *  reuse from anywhere that already has the raw (type, cycleStart, customStart, customEnd)
 *  tuple, regardless of where that tuple was persisted. */
object PeriodWindow {
    private val rangeDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

    /** [start, end) — end is exclusive. For CUSTOM, [customEndMillis] is treated as an inclusive
     *  end date (the whole end day counts) and bumped forward a day internally to make it
     *  exclusive like every other period. */
    fun window(type: PeriodType, cycleStartMillis: Long, customStartMillis: Long, customEndMillis: Long): Pair<Date, Date> {
        type.toBudgetPeriod()?.let { budgetPeriod ->
            val start = if (cycleStartMillis > 0L) cycleStartMillis else budgetPeriod.defaultCycleStartMillis()
            return budgetPeriod.cycleWindow(start)
        }
        return when (type) {
            PeriodType.ALL_TIME -> Pair(Date(0L), Date(Long.MAX_VALUE))
            PeriodType.CUSTOM -> {
                val start = normalizedMidnight(if (customStartMillis > 0L) customStartMillis else System.currentTimeMillis())
                val endInclusive = normalizedMidnight(if (customEndMillis > 0L) customEndMillis else System.currentTimeMillis())
                val end = Calendar.getInstance().apply { time = endInclusive; add(Calendar.DAY_OF_YEAR, 1) }.time
                Pair(start, end)
            }
            else -> error("unreachable: $type has a BudgetPeriod mapping")
        }
    }

    /** Short human-readable summary, e.g. "This Week", "January 2026", "2026",
     *  "1 Jan 2026 - 31 Jan 2026", or "All Time". */
    fun label(type: PeriodType, cycleStartMillis: Long, customStartMillis: Long, customEndMillis: Long): String {
        val (start, end) = window(type, cycleStartMillis, customStartMillis, customEndMillis)
        return when (type) {
            PeriodType.ALL_TIME -> type.label
            PeriodType.DAILY -> "Today"
            PeriodType.WEEKLY -> {
                val endInclusive = Calendar.getInstance().apply { time = end; add(Calendar.DAY_OF_YEAR, -1) }.time
                "${rangeDateFormat.format(start)} - ${rangeDateFormat.format(endInclusive)}"
            }
            PeriodType.MONTHLY -> monthFormat.format(start)
            PeriodType.YEARLY -> yearFormat.format(start)
            PeriodType.CUSTOM -> {
                val endInclusive = Calendar.getInstance().apply { time = end; add(Calendar.DAY_OF_YEAR, -1) }.time
                "${rangeDateFormat.format(start)} - ${rangeDateFormat.format(endInclusive)}"
            }
        }
    }

    private fun normalizedMidnight(millis: Long): Date {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.time
    }
}
