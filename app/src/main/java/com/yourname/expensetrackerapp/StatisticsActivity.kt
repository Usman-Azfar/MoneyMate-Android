package com.yourname.expensetrackerapp

import android.Manifest
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatisticsActivity : AppCompatActivity() {

    private lateinit var periodSpinner: Spinner
    private lateinit var cyclePickerLayout: TextInputLayout
    private lateinit var cyclePickerInput: EditText
    private lateinit var customEndPickerLayout: TextInputLayout
    private lateinit var customEndPickerInput: EditText
    private lateinit var periodSummaryCard: View
    private lateinit var periodSummaryIncomeText: TextView
    private lateinit var periodSummaryExpenseText: TextView
    private lateinit var periodSummaryNetText: TextView
    private lateinit var budgetUnavailableText: TextView
    private lateinit var comparisonCard: View
    private lateinit var budgetPromptText: TextView
    private lateinit var statusText: TextView
    private lateinit var actualSpentText: TextView
    private lateinit var incomeAmountText: TextView
    private lateinit var expectedSpendText: TextView
    private lateinit var remainingText: TextView
    private lateinit var savingsText: TextView
    private lateinit var budgetBarFill: View
    private lateinit var budgetBarEmpty: View
    private lateinit var incomeAmountInputLayout: TextInputLayout
    private lateinit var incomeAmountInput: EditText
    private lateinit var expectedSpendInputLayout: TextInputLayout
    private lateinit var expectedSpendInput: EditText
    private lateinit var saveBudgetBtn: Button
    private lateinit var categoryBreakdownLabel: TextView
    private lateinit var donutChart: DonutChartView
    private lateinit var categoryContainer: LinearLayout
    private lateinit var outstandingDebtsLabel: View
    private lateinit var outstandingDebtsCard: View
    private lateinit var statsPayableText: TextView
    private lateinit var statsReceivableText: TextView
    private lateinit var statsOverdueCountText: TextView
    private lateinit var bottomNav: BottomNavigationView

    private var selectedPeriodType: PeriodType = PeriodType.MONTHLY
    private var selectedCycleStartMillis: Long = 0L
    private var customStartMillis: Long = 0L
    private var customEndMillis: Long = 0L

    /** The exact window and budget last rendered, handed to Ask MoneyMate AI so its answer talks
     *  about the same numbers the screen is showing. */
    private var shownWindow: Pair<Date, Date>? = null
    private var shownBudget: Budget? = null
    private var aiAdvisorDialog: AiAdvisorDialog? = null

    private val transactionDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val weekRangeFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private val chartColors by lazy {
        intArrayOf(
            ContextCompat.getColor(this, R.color.chart_1),
            ContextCompat.getColor(this, R.color.chart_2),
            ContextCompat.getColor(this, R.color.chart_3),
            ContextCompat.getColor(this, R.color.chart_4),
            ContextCompat.getColor(this, R.color.chart_5),
            ContextCompat.getColor(this, R.color.chart_6),
            ContextCompat.getColor(this, R.color.chart_7),
            ContextCompat.getColor(this, R.color.chart_8)
        )
    }

    // If the permission is denied, notifications are simply skipped elsewhere — no follow-up needed here.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        periodSpinner = findViewById(R.id.periodSpinner)
        cyclePickerLayout = findViewById(R.id.cyclePickerLayout)
        cyclePickerInput = findViewById(R.id.cyclePickerInput)
        customEndPickerLayout = findViewById(R.id.customEndPickerLayout)
        customEndPickerInput = findViewById(R.id.customEndPickerInput)
        periodSummaryCard = findViewById(R.id.periodSummaryCard)
        periodSummaryIncomeText = findViewById(R.id.periodSummaryIncomeText)
        periodSummaryExpenseText = findViewById(R.id.periodSummaryExpenseText)
        periodSummaryNetText = findViewById(R.id.periodSummaryNetText)
        budgetUnavailableText = findViewById(R.id.budgetUnavailableText)
        comparisonCard = findViewById(R.id.comparisonCard)
        budgetPromptText = findViewById(R.id.budgetPromptText)
        statusText = findViewById(R.id.statusText)
        actualSpentText = findViewById(R.id.actualSpentText)
        incomeAmountText = findViewById(R.id.incomeAmountText)
        expectedSpendText = findViewById(R.id.expectedSpendText)
        remainingText = findViewById(R.id.remainingText)
        savingsText = findViewById(R.id.savingsText)
        budgetBarFill = findViewById(R.id.budgetBarFill)
        budgetBarEmpty = findViewById(R.id.budgetBarEmpty)
        incomeAmountInputLayout = findViewById(R.id.incomeAmountInputLayout)
        incomeAmountInput = findViewById(R.id.incomeAmountInput)
        expectedSpendInputLayout = findViewById(R.id.expectedSpendInputLayout)
        expectedSpendInput = findViewById(R.id.expectedSpendInput)
        saveBudgetBtn = findViewById(R.id.saveBudgetBtn)
        categoryBreakdownLabel = findViewById(R.id.categoryBreakdownLabel)
        donutChart = findViewById(R.id.donutChart)
        categoryContainer = findViewById(R.id.categoryContainer)
        outstandingDebtsLabel = findViewById(R.id.outstandingDebtsLabel)
        outstandingDebtsCard = findViewById(R.id.outstandingDebtsCard)
        statsPayableText = findViewById(R.id.statsPayableText)
        statsReceivableText = findViewById(R.id.statsReceivableText)
        statsOverdueCountText = findViewById(R.id.statsOverdueCountText)
        bottomNav = findViewById(R.id.bottomNav)

        setupPeriodSpinner()
        cyclePickerInput.setOnClickListener { showCyclePickerForSelectedPeriod() }
        cyclePickerLayout.setEndIconOnClickListener { showCyclePickerForSelectedPeriod() }
        customEndPickerInput.setOnClickListener { showCustomEndPicker() }
        customEndPickerLayout.setEndIconOnClickListener { showCustomEndPicker() }
        saveBudgetBtn.setOnClickListener { saveBudget() }

        val aiAdvisorCard = findViewById<View>(R.id.aiAdvisorCard)
        if (GeminiClient.isConfigured()) {
            findViewById<Button>(R.id.aiAdvisorBtn).setOnClickListener { openAiAdvisor() }
        } else {
            aiAdvisorCard.visibility = View.GONE
        }

        BottomNavHelper.setup(this, bottomNav, R.id.nav_statistics)
    }

    override fun onDestroy() {
        aiAdvisorDialog?.dismiss()
        super.onDestroy()
    }

    private fun openAiAdvisor() {
        val (start, end) = shownWindow ?: return
        val label = if (selectedPeriodType == PeriodType.ALL_TIME) {
            PeriodType.ALL_TIME.label
        } else {
            "${selectedPeriodType.label} — ${cyclePickerSummary()}"
        }
        val snapshot = AiAdvisor.snapshot(
            label = label,
            periodType = selectedPeriodType,
            start = start,
            end = end,
            allTransactions = TransactionRepository.getAll(this),
            budget = shownBudget,
            includeDebts = IncludeDebtsPrefs.isEnabled(this),
            currency = CurrencyPrefs.getSelectedCurrency(this)
        )
        aiAdvisorDialog?.dismiss()
        aiAdvisorDialog = AiAdvisorDialog(this, snapshot).also { it.show() }
    }

    /** Human-readable version of the period shown in the picker(s) above, e.g. "September 2026". */
    private fun cyclePickerSummary(): String = when (selectedPeriodType) {
        PeriodType.DAILY -> getString(R.string.label_today)
        PeriodType.CUSTOM -> "${cyclePickerInput.text} – ${customEndPickerInput.text}"
        else -> cyclePickerInput.text.toString()
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
        val symbol = CurrencyFormatter.symbol(this)
        incomeAmountInputLayout.hint = getString(R.string.label_income_amount_auto_with_symbol, symbol)
        expectedSpendInputLayout.hint = getString(R.string.label_expected_spend_with_symbol, symbol)
        refreshForSelectedCycle()
    }

    private fun setupPeriodSpinner() {
        val periods = PeriodType.values()
        val labels = periods.map { it.label }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, labels)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        periodSpinner.adapter = adapter
        periodSpinner.setSelection(periods.indexOf(selectedPeriodType))

        periodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedPeriodType = periods[position]
                selectedCycleStartMillis = 0L
                refreshForSelectedCycle()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showCyclePickerForSelectedPeriod() {
        when (selectedPeriodType) {
            PeriodType.WEEKLY -> showWeekStartPicker()
            PeriodType.MONTHLY -> showMonthYearPicker()
            PeriodType.YEARLY -> showYearPicker()
            PeriodType.CUSTOM -> showCustomStartPicker()
            PeriodType.DAILY, PeriodType.ALL_TIME -> {}
        }
    }

    private fun currentCycleCalendar(): Calendar {
        val budgetPeriod = selectedPeriodType.toBudgetPeriod()!!
        val millis = if (selectedCycleStartMillis > 0L) selectedCycleStartMillis else budgetPeriod.defaultCycleStartMillis()
        return Calendar.getInstance().apply { timeInMillis = millis }
    }

    private fun showCustomStartPicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = if (customStartMillis > 0L) customStartMillis else System.currentTimeMillis() }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                customStartMillis = picked.timeInMillis
                refreshForSelectedCycle()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        if (customEndMillis > 0L) dialog.datePicker.maxDate = customEndMillis
        dialog.show()
    }

    private fun showCustomEndPicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = if (customEndMillis > 0L) customEndMillis else System.currentTimeMillis() }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                customEndMillis = picked.timeInMillis
                refreshForSelectedCycle()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        if (customStartMillis > 0L) dialog.datePicker.minDate = customStartMillis
        dialog.show()
    }

    private fun showWeekStartPicker() {
        val cal = currentCycleCalendar()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                selectedCycleStartMillis = picked.timeInMillis
                refreshForSelectedCycle()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showMonthYearPicker() {
        val cal = currentCycleCalendar()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val monthPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = monthNames
            value = cal.get(Calendar.MONTH)
        }
        val yearPicker = NumberPicker(this).apply {
            minValue = currentYear - 15
            maxValue = currentYear + 15
            value = cal.get(Calendar.YEAR)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = resources.getDimensionPixelSize(R.dimen.space_16)
            setPadding(pad, pad, pad, pad)
            addView(monthPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(yearPicker, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.label_select_month)
            .setView(container)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val picked = Calendar.getInstance()
                picked.set(yearPicker.value, monthPicker.value, 1, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                selectedCycleStartMillis = picked.timeInMillis
                refreshForSelectedCycle()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showYearPicker() {
        val cal = currentCycleCalendar()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val yearPicker = NumberPicker(this).apply {
            minValue = currentYear - 15
            maxValue = currentYear + 15
            value = cal.get(Calendar.YEAR)
        }
        val container = FrameLayout(this).apply {
            val pad = resources.getDimensionPixelSize(R.dimen.space_16)
            setPadding(pad, pad, pad, pad)
            addView(yearPicker)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.label_select_year)
            .setView(container)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val picked = Calendar.getInstance()
                picked.set(yearPicker.value, Calendar.JANUARY, 1, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                selectedCycleStartMillis = picked.timeInMillis
                refreshForSelectedCycle()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun updateCyclePickerUi() {
        when (selectedPeriodType) {
            PeriodType.DAILY, PeriodType.ALL_TIME -> {
                cyclePickerLayout.visibility = View.GONE
                customEndPickerLayout.visibility = View.GONE
            }
            PeriodType.CUSTOM -> {
                cyclePickerLayout.visibility = View.VISIBLE
                customEndPickerLayout.visibility = View.VISIBLE
                if (customStartMillis <= 0L) customStartMillis = System.currentTimeMillis()
                if (customEndMillis <= 0L) customEndMillis = System.currentTimeMillis()
                cyclePickerLayout.hint = getString(R.string.label_start_date)
                customEndPickerLayout.hint = getString(R.string.label_end_date)
                cyclePickerInput.setText(weekRangeFormat.format(Date(customStartMillis)))
                customEndPickerInput.setText(weekRangeFormat.format(Date(customEndMillis)))
            }
            else -> {
                cyclePickerLayout.visibility = View.VISIBLE
                customEndPickerLayout.visibility = View.GONE
                val budgetPeriod = selectedPeriodType.toBudgetPeriod()!!
                if (selectedCycleStartMillis <= 0L) {
                    selectedCycleStartMillis = budgetPeriod.defaultCycleStartMillis()
                }
                val (start, end) = budgetPeriod.cycleWindow(selectedCycleStartMillis)

                cyclePickerLayout.hint = when (selectedPeriodType) {
                    PeriodType.WEEKLY -> getString(R.string.label_week_range)
                    PeriodType.MONTHLY -> getString(R.string.label_select_month)
                    PeriodType.YEARLY -> getString(R.string.label_select_year)
                    else -> ""
                }
                cyclePickerInput.setText(
                    when (selectedPeriodType) {
                        PeriodType.WEEKLY -> {
                            val endInclusive = Calendar.getInstance().apply {
                                time = end
                                add(Calendar.DAY_OF_YEAR, -1)
                            }.time
                            "${weekRangeFormat.format(start)} – ${weekRangeFormat.format(endInclusive)}"
                        }
                        PeriodType.MONTHLY -> monthFormat.format(start)
                        PeriodType.YEARLY -> yearFormat.format(start)
                        else -> ""
                    }
                )
            }
        }
    }

    private fun activeCycleStart(): Long = if (selectedPeriodType == PeriodType.DAILY) 0L else selectedCycleStartMillis

    private fun refreshForSelectedCycle() {
        updateCyclePickerUi()

        val budgetPeriod = selectedPeriodType.toBudgetPeriod()
        val transactions = TransactionRepository.getAll(this)
        val (start, end) = if (budgetPeriod != null) {
            budgetPeriod.cycleWindow(activeCycleStart())
        } else {
            PeriodWindow.window(selectedPeriodType, 0L, customStartMillis, customEndMillis)
        }
        val includeDebts = IncludeDebtsPrefs.isEnabled(this)
        val totals = BudgetRepository.cycleTotals(start, end, transactions, includeDebts)
        shownWindow = start to end
        shownBudget = null

        if (budgetPeriod == null) {
            // ALL_TIME / CUSTOM: budgets need a recurring cycle to key notifications and storage
            // off of, so show a plain income/expense/net summary instead of the budget UI.
            comparisonCard.visibility = View.GONE
            budgetPromptText.visibility = View.GONE
            incomeAmountInputLayout.visibility = View.GONE
            expectedSpendInputLayout.visibility = View.GONE
            saveBudgetBtn.visibility = View.GONE
            budgetUnavailableText.visibility = View.VISIBLE
            periodSummaryCard.visibility = View.VISIBLE

            periodSummaryIncomeText.text = CurrencyFormatter.format(this, totals.income)
            periodSummaryExpenseText.text = CurrencyFormatter.format(this, totals.expense)
            val net = totals.income - totals.expense
            periodSummaryNetText.text = CurrencyFormatter.format(this, net)
            periodSummaryNetText.setTextColor(
                ContextCompat.getColor(this, if (net < 0) R.color.expense else R.color.income)
            )

            renderCategoryBreakdown(start, end, transactions, includeDebts)
            renderOutstandingDebts(start, end, transactions, includeDebts)
            return
        }

        incomeAmountInputLayout.visibility = View.VISIBLE
        expectedSpendInputLayout.visibility = View.VISIBLE
        saveBudgetBtn.visibility = View.VISIBLE
        budgetUnavailableText.visibility = View.GONE
        periodSummaryCard.visibility = View.GONE

        val cycleStart = activeCycleStart()
        val cycleKey = budgetPeriod.cycleKeyFor(cycleStart)
        val budget = BudgetRepository.getForCycle(this, budgetPeriod, cycleKey)
        shownBudget = budget

        incomeAmountInput.setText(trimAmount(totals.income))

        if (budget != null) {
            expectedSpendInput.setText(trimAmount(budget.expectedSpendAmount))

            comparisonCard.visibility = View.VISIBLE
            budgetPromptText.visibility = View.GONE

            val remaining = budget.expectedSpendAmount - totals.expense
            val overBudget = totals.expense > budget.expectedSpendAmount

            actualSpentText.text = CurrencyFormatter.format(this, totals.expense)
            incomeAmountText.text = CurrencyFormatter.format(this, totals.income)
            expectedSpendText.text = CurrencyFormatter.format(this, budget.expectedSpendAmount)
            remainingText.text = CurrencyFormatter.format(this, remaining)
            remainingText.setTextColor(
                ContextCompat.getColor(this, if (overBudget) R.color.expense else R.color.on_surface)
            )

            val savings = totals.income - totals.expense
            savingsText.text = CurrencyFormatter.format(this, savings)
            savingsText.setTextColor(
                ContextCompat.getColor(this, if (savings < 0) R.color.expense else R.color.income)
            )

            statusText.text = getString(if (overBudget) R.string.status_over_budget else R.string.status_on_track)
            statusText.setTextColor(
                ContextCompat.getColor(this, if (overBudget) R.color.expense else R.color.income)
            )
            budgetBarFill.setBackgroundColor(
                ContextCompat.getColor(this, if (overBudget) R.color.expense else R.color.income)
            )

            val ratio = if (budget.expectedSpendAmount > 0) {
                totals.expense / budget.expectedSpendAmount
            } else if (totals.expense > 0) 1.0 else 0.0
            animateBarWeights(budgetBarFill, budgetBarEmpty, ratio)
        } else {
            comparisonCard.visibility = View.GONE
            budgetPromptText.visibility = View.VISIBLE
            budgetPromptText.text = getString(R.string.empty_budget_prompt, budgetPeriod.label.lowercase())
            expectedSpendInput.setText("")
        }

        renderCategoryBreakdown(start, end, transactions, includeDebts)
        renderOutstandingDebts(start, end, transactions, includeDebts)
    }

    /** Debts, unlike Income/Expense/budgets, are shown for every period type including Daily and
     *  the recurring cycles above — outstanding money owed is relevant no matter which period
     *  the rest of the screen happens to be scoped to. Overdue is checked against every unsettled
     *  debt regardless of [start]/[end] though, same reasoning as Home's overdue banner.
     *
     *  When [includeDebts] is on, this whole card is hidden instead — those same amounts are
     *  already folded into the Income/Expense totals and category chart above, so showing them
     *  again here would double them up. */
    private fun renderOutstandingDebts(start: Date, end: Date, transactions: List<Transaction>, includeDebts: Boolean) {
        outstandingDebtsLabel.visibility = if (includeDebts) View.GONE else View.VISIBLE
        outstandingDebtsCard.visibility = if (includeDebts) View.GONE else View.VISIBLE
        if (includeDebts) return

        val displayedDebts = transactions.filter { transaction ->
            if (transaction.type != "Debt" || transaction.isSettled) return@filter false
            val date = try { transactionDateFormat.parse(transaction.date) } catch (e: Exception) { null } ?: return@filter false
            !date.before(start) && date.before(end)
        }
        val totalPayable = displayedDebts.filter { it.category == "Payable" }.sumOf { it.amount }
        val totalReceivable = displayedDebts.filter { it.category == "Receivable" }.sumOf { it.amount }
        statsPayableText.text = CurrencyFormatter.format(this, totalPayable)
        statsReceivableText.text = CurrencyFormatter.format(this, totalReceivable)

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val overdueCount = transactions.count { transaction ->
            if (transaction.type != "Debt" || transaction.isSettled) return@count false
            val due = try { transactionDateFormat.parse(transaction.dueDate ?: return@count false) } catch (e: Exception) { null }
            due != null && due.before(today)
        }
        statsOverdueCountText.text = overdueCount.toString()
        statsOverdueCountText.setTextColor(
            ContextCompat.getColor(this, if (overdueCount > 0) R.color.expense else R.color.on_surface)
        )
    }

    private fun renderCategoryBreakdown(start: Date, end: Date, transactions: List<Transaction>, includeDebts: Boolean) {
        categoryContainer.removeAllViews()
        val breakdown = BudgetRepository.cycleExpenseByCategory(start, end, transactions, includeDebts)

        if (breakdown.isEmpty()) {
            categoryBreakdownLabel.visibility = View.GONE
            donutChart.visibility = View.GONE
            donutChart.setSlices(emptyList())
            return
        }
        categoryBreakdownLabel.visibility = View.VISIBLE
        donutChart.visibility = View.VISIBLE

        val percentages = largestRemainderPercentages(breakdown.map { it.second })
        donutChart.setSlices(
            breakdown.mapIndexed { index, (category, amount) ->
                DonutChartView.Slice(category, amount, chartColors[index % chartColors.size], percentages[index])
            }
        )

        val maxAmount = breakdown.maxOf { it.second }
        val inflater = LayoutInflater.from(this)

        for ((index, entry) in breakdown.withIndex()) {
            val (category, amount) = entry
            val color = chartColors[index % chartColors.size]
            val row = inflater.inflate(R.layout.category_stat_row, categoryContainer, false)
            row.findViewById<TextView>(R.id.categoryName).text = category
            row.findViewById<TextView>(R.id.categoryAmount).text = CurrencyFormatter.format(this, amount)
            row.findViewById<TextView>(R.id.categoryPercent).text = String.format("%.1f%%", percentages[index])
            row.findViewById<View>(R.id.categoryColorDot).backgroundTintList = ColorStateList.valueOf(color)
            val fill = row.findViewById<View>(R.id.categoryBarFill)
            val empty = row.findViewById<View>(R.id.categoryBarEmpty)
            fill.backgroundTintList = ColorStateList.valueOf(color)
            val ratio = if (maxAmount > 0) amount / maxAmount else 0.0
            animateBarWeights(fill, empty, ratio)
            categoryContainer.addView(row)
        }
    }

    /** Rounds each share to one decimal place while guaranteeing the results sum to exactly
     *  100.0, using the largest-remainder method — plain independent rounding of each share can
     *  drift a fraction off 100 (e.g. 62.5% + 37.5% rounding to 63% + 38% = 101%). */
    private fun largestRemainderPercentages(amounts: List<Double>): List<Double> {
        val total = amounts.sum()
        if (total <= 0.0) return amounts.map { 0.0 }

        val rawTenths = amounts.map { it / total * 1000.0 }
        val floors = rawTenths.map { kotlin.math.floor(it).toInt() }
        var remainder = 1000 - floors.sum()
        val remainders = rawTenths.mapIndexed { i, v -> v - floors[i] }
        val distributionOrder = remainders.indices.sortedByDescending { remainders[it] }

        val result = floors.toMutableList()
        var i = 0
        while (remainder > 0 && i < distributionOrder.size) {
            result[distributionOrder[i]] += 1
            remainder--
            i++
        }
        return result.map { it / 10.0 }
    }

    private fun animateBarWeights(fill: View, empty: View, ratio: Double) {
        val target = ratio.coerceIn(0.0, 1.0).toFloat()
        val start = (fill.layoutParams as LinearLayout.LayoutParams).weight
        ValueAnimator.ofFloat(start, target).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                (fill.layoutParams as LinearLayout.LayoutParams).weight = value
                (empty.layoutParams as LinearLayout.LayoutParams).weight = 1f - value
                fill.requestLayout()
                empty.requestLayout()
            }
            start()
        }
    }

    private fun saveBudget() {
        val budgetPeriod = selectedPeriodType.toBudgetPeriod() ?: return
        val expectedSpend = expectedSpendInput.text.toString().toDoubleOrNull()

        if (expectedSpend == null || expectedSpend < 0) {
            Toast.makeText(this, R.string.error_enter_valid_amounts, Toast.LENGTH_SHORT).show()
            return
        }

        val cycleStart = activeCycleStart()
        val cycleKey = budgetPeriod.cycleKeyFor(cycleStart)
        val existing = BudgetRepository.getForCycle(this, budgetPeriod, cycleKey)
        val budget = Budget(
            period = budgetPeriod,
            expectedSpendAmount = expectedSpend,
            cycleStartMillis = cycleStart,
            lastNotifiedCycleKey = existing?.lastNotifiedCycleKey,
            lastIncomeExceededCycleKey = existing?.lastIncomeExceededCycleKey
        )
        BudgetRepository.save(this, budget)

        maybeRequestNotificationPermission()
        BudgetNotifier.checkAndNotify(this)
        refreshForSelectedCycle()
        Toast.makeText(this, getString(R.string.toast_budget_saved, budgetPeriod.label), Toast.LENGTH_SHORT).show()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun trimAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }
}
