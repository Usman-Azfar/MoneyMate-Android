package com.yourname.expensetrackerapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TransactionsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var filterSummaryText: TextView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var transactionAdapter: TransactionAdapter

    private var transactionList: List<Transaction> = emptyList()
    private val filteredList = mutableListOf<Transaction>()
    private var currentFilter = "All"
    private var searchQuery = ""

    /** Start-of-day / start-of-next-day bounds for the date filter, or null when unset. */
    private var fromDateMillis: Long? = null
    private var toDateExclusiveMillis: Long? = null

    private val transactionDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                proceedWithDownload()
            } else {
                Toast.makeText(this, R.string.toast_storage_permission_required, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transactions)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        filterSummaryText = findViewById(R.id.filterSummaryText)
        bottomNav = findViewById(R.id.bottomNav)

        recyclerView.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(
            filteredList,
            onDeleteClick = { position -> deleteTransaction(position) },
            onEditClick = { position -> showEditDialog(position) },
            onSettleToggle = { position -> toggleSettled(position) }
        )
        recyclerView.adapter = transactionAdapter

        BottomNavHelper.setup(this, bottomNav, R.id.nav_history)
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
        transactionList = TransactionRepository.getAll(this)
        applyFilterAndSearch()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.transactions_menu, menu)
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        setupSearchView(searchView)
        return true
    }

    private fun setupSearchView(searchView: SearchView?) {
        searchView?.apply {
            queryHint = "Search transactions..."
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText ?: ""
                    applyFilterAndSearch()
                    return true
                }
            })
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            R.id.action_download -> {
                onDownloadClicked()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_filter_transactions, null)
        val typeGroup = dialogView.findViewById<RadioGroup>(R.id.filterTypeGroup)
        val fromBtn = dialogView.findViewById<Button>(R.id.filterFromDateBtn)
        val toBtn = dialogView.findViewById<Button>(R.id.filterToDateBtn)
        val clearDatesBtn = dialogView.findViewById<Button>(R.id.filterClearDatesBtn)

        when (currentFilter) {
            "Income" -> typeGroup.check(R.id.filterTypeIncome)
            "Expense" -> typeGroup.check(R.id.filterTypeExpense)
            "Debt" -> typeGroup.check(R.id.filterTypeDebt)
            else -> typeGroup.check(R.id.filterTypeAll)
        }

        var pendingFrom = fromDateMillis
        var pendingToExclusive = toDateExclusiveMillis

        fun refreshDateButtons() {
            fromBtn.text = "From: ${pendingFrom?.let { displayDateFormat.format(Date(it)) } ?: "Any"}"
            toBtn.text = "To: ${pendingToExclusive?.let {
                displayDateFormat.format(Date(it - 1))
            } ?: "Any"}"
        }
        refreshDateButtons()

        fromBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            pendingFrom?.let { cal.timeInMillis = it }
            DatePickerDialog(this, { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                pendingFrom = picked.timeInMillis
                refreshDateButtons()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        toBtn.setOnClickListener {
            val cal = Calendar.getInstance()
            pendingToExclusive?.let { cal.timeInMillis = it - 1 }
            DatePickerDialog(this, { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                picked.add(Calendar.DAY_OF_YEAR, 1)
                pendingToExclusive = picked.timeInMillis
                refreshDateButtons()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        clearDatesBtn.setOnClickListener {
            pendingFrom = null
            pendingToExclusive = null
            refreshDateButtons()
        }

        AlertDialog.Builder(this)
            .setTitle("Filter Transactions")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                currentFilter = when (typeGroup.checkedRadioButtonId) {
                    R.id.filterTypeIncome -> "Income"
                    R.id.filterTypeExpense -> "Expense"
                    R.id.filterTypeDebt -> "Debt"
                    else -> "All"
                }
                fromDateMillis = pendingFrom
                toDateExclusiveMillis = pendingToExclusive
                applyFilterAndSearch()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onDownloadClicked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        proceedWithDownload()
    }

    /** Uses the format set in Settings if there is one; otherwise asks which format to use for
     *  this download, same as before that setting existed. */
    private fun proceedWithDownload() {
        when (val defaultFormat = ReportFormatPrefs.getDefault(this)) {
            null -> showFormatChooserDialog()
            else -> exportReport(defaultFormat)
        }
    }

    private fun showFormatChooserDialog() {
        val options = arrayOf(getString(R.string.format_pdf), getString(R.string.format_csv))
        AlertDialog.Builder(this)
            .setTitle(R.string.title_choose_format)
            .setItems(options) { _, which ->
                exportReport(if (which == 0) ReportFormatPrefs.Format.PDF else ReportFormatPrefs.Format.CSV)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun exportReport(format: ReportFormatPrefs.Format) {
        val summary = buildFilterSummary()
        val saved = when (format) {
            ReportFormatPrefs.Format.PDF -> PdfReportGenerator.generate(this, filteredList.toList(), summary)
            ReportFormatPrefs.Format.CSV -> CsvReportGenerator.generate(this, filteredList.toList(), summary)
        }
        Toast.makeText(
            this,
            if (saved) R.string.toast_report_saved else R.string.toast_report_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun buildFilterSummary(): String {
        val parts = mutableListOf<String>()
        parts.add(
            when (currentFilter) {
                "Income" -> "Income Only"
                "Expense" -> "Expense Only"
                "Debt" -> "Debt Only"
                else -> "All Transactions"
            }
        )
        if (fromDateMillis != null || toDateExclusiveMillis != null) {
            val fromText = fromDateMillis?.let { displayDateFormat.format(Date(it)) } ?: "Any"
            val toText = toDateExclusiveMillis?.let { displayDateFormat.format(Date(it - 1)) } ?: "Any"
            parts.add("Date: $fromText – $toText")
        }
        if (searchQuery.isNotEmpty()) {
            parts.add("Search: '$searchQuery'")
        }
        return "Filter: " + parts.joinToString("  •  ")
    }

    private fun updateFilterSummaryLabel() {
        val hasActiveFilter = currentFilter != "All" || fromDateMillis != null || toDateExclusiveMillis != null ||
            searchQuery.isNotEmpty()
        if (!hasActiveFilter) {
            filterSummaryText.visibility = View.GONE
            return
        }
        filterSummaryText.visibility = View.VISIBLE
        filterSummaryText.text = buildFilterSummary()
    }

    private fun applyFilterAndSearch() {
        val typeFiltered = when (currentFilter) {
            "Income" -> transactionList.filter { it.type == "Income" }
            "Expense" -> transactionList.filter { it.type == "Expense" }
            "Debt" -> transactionList.filter { it.type == "Debt" }
            else -> transactionList.toList()
        }

        val searchFiltered = if (searchQuery.isNotEmpty()) {
            typeFiltered.filter {
                it.description.contains(searchQuery, ignoreCase = true) ||
                    it.personName?.contains(searchQuery, ignoreCase = true) == true
            }
        } else {
            typeFiltered
        }

        val dateFiltered = searchFiltered.filter { transaction ->
            val date = try {
                transactionDateFormat.parse(transaction.date)
            } catch (e: Exception) {
                null
            } ?: return@filter true
            val afterFrom = fromDateMillis?.let { !date.before(Date(it)) } ?: true
            val beforeTo = toDateExclusiveMillis?.let { date.before(Date(it)) } ?: true
            afterFrom && beforeTo
        }

        filteredList.clear()
        filteredList.addAll(dateFiltered)
        transactionAdapter.notifyDataSetChanged()
        updateFilterSummaryLabel()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredList.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyStateText.text = when {
                searchQuery.isNotEmpty() -> "No transactions match '$searchQuery'"
                currentFilter != "All" || fromDateMillis != null || toDateExclusiveMillis != null ->
                    "No transactions match the selected filters"
                else -> "No transactions yet.\nAdd your first transaction!"
            }
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun deleteTransaction(position: Int) {
        if (position < 0 || position >= filteredList.size) return
        val transactionToDelete = filteredList[position]

        if (transactionToDelete.type == "Income") {
            val balance = transactionList.sumOf {
                when (it.type) {
                    "Income" -> it.amount
                    "Expense" -> -it.amount
                    else -> 0.0
                }
            }
            if (balance - transactionToDelete.amount < 0) {
                Toast.makeText(this, "Cannot delete: Would result in negative balance", Toast.LENGTH_SHORT).show()
                return
            }
        }

        TransactionRepository.delete(this, transactionToDelete)
        transactionList = TransactionRepository.getAll(this)
        applyFilterAndSearch()
        Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSettled(position: Int) {
        if (position < 0 || position >= filteredList.size) return
        val transaction = filteredList[position]
        if (transaction.type != "Debt") return
        val updated = transaction.copy(isSettled = !transaction.isSettled, updatedAt = System.currentTimeMillis())
        TransactionRepository.update(this, updated)
        transactionList = TransactionRepository.getAll(this)
        applyFilterAndSearch()
        Toast.makeText(
            this,
            if (updated.isSettled) R.string.toast_debt_settled else R.string.toast_debt_unsettled,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showEditDialog(position: Int) {
        if (position < 0 || position >= filteredList.size) return
        val original = filteredList[position]

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_transaction, null)
        val descriptionInputLayout = dialogView.findViewById<TextInputLayout>(R.id.editDescriptionInputLayout)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.editDescriptionInput)
        val amountInputLayout = dialogView.findViewById<TextInputLayout>(R.id.editAmountInputLayout)
        val amountInput = dialogView.findViewById<EditText>(R.id.editAmountInput)
        val dateInput = dialogView.findViewById<EditText>(R.id.editDateInput)
        val typeGroup = dialogView.findViewById<RadioGroup>(R.id.editTypeGroup)
        val categorySpinner = dialogView.findViewById<Spinner>(R.id.editCategorySpinner)
        val debtDirectionGroup = dialogView.findViewById<RadioGroup>(R.id.editDebtDirectionGroup)
        val personNameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.editPersonNameInputLayout)
        val personNameInput = dialogView.findViewById<EditText>(R.id.editPersonNameInput)
        val dueDateInputLayout = dialogView.findViewById<TextInputLayout>(R.id.editDueDateInputLayout)
        val dueDateInput = dialogView.findViewById<EditText>(R.id.editDueDateInput)
        val settledCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.editSettledCheckBox)

        amountInputLayout.hint = getString(R.string.hint_amount_with_symbol, CurrencyFormatter.symbol(this))
        descriptionInput.setText(original.description)
        amountInput.setText(trimAmount(original.amount))
        personNameInput.setText(original.personName)
        settledCheckBox.isChecked = original.isSettled

        var selectedDateMillis = try {
            transactionDateFormat.parse(original.date)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        dateInput.setText(displayDateFormat.format(Date(selectedDateMillis)))
        dateInput.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    picked.set(Calendar.MILLISECOND, 0)
                    selectedDateMillis = picked.timeInMillis
                    dateInput.setText(displayDateFormat.format(Date(selectedDateMillis)))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }

        var selectedDueDateMillis = try {
            transactionDateFormat.parse(original.dueDate ?: "")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        dueDateInput.setText(displayDateFormat.format(Date(selectedDueDateMillis)))
        dueDateInput.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = selectedDueDateMillis }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val picked = Calendar.getInstance()
                    picked.set(year, month, day, 0, 0, 0)
                    picked.set(Calendar.MILLISECOND, 0)
                    selectedDueDateMillis = picked.timeInMillis
                    dueDateInput.setText(displayDateFormat.format(Date(selectedDueDateMillis)))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        fun setCategoryAdapter(options: List<String>, preselect: String?) {
            val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            categorySpinner.adapter = adapter
            val index = preselect?.let { options.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            categorySpinner.setSelection(index)
        }

        fun showIncomeOrExpenseFields(income: Boolean) {
            categorySpinner.visibility = View.VISIBLE
            debtDirectionGroup.visibility = View.GONE
            personNameInputLayout.visibility = View.GONE
            dueDateInputLayout.visibility = View.GONE
            settledCheckBox.visibility = View.GONE
            descriptionInputLayout.hint = getString(R.string.hint_description)
        }

        fun showDebtFields() {
            categorySpinner.visibility = View.GONE
            debtDirectionGroup.visibility = View.VISIBLE
            personNameInputLayout.visibility = View.VISIBLE
            dueDateInputLayout.visibility = View.VISIBLE
            settledCheckBox.visibility = View.VISIBLE
            descriptionInputLayout.hint = getString(R.string.hint_debt_note)
        }

        fun updatePersonNameHint() {
            personNameInputLayout.hint = if (debtDirectionGroup.checkedRadioButtonId == R.id.editRadioDebtReceivable) {
                getString(R.string.hint_receivable_from)
            } else {
                getString(R.string.hint_payable_to)
            }
        }

        debtDirectionGroup.setOnCheckedChangeListener { _, _ -> updatePersonNameHint() }

        when (original.type) {
            "Income" -> {
                typeGroup.check(R.id.editRadioIncome)
                showIncomeOrExpenseFields(income = true)
                setCategoryAdapter(CategoryRepository.getIncome(this), original.category)
            }
            "Expense" -> {
                typeGroup.check(R.id.editRadioExpense)
                showIncomeOrExpenseFields(income = false)
                setCategoryAdapter(CategoryRepository.getExpense(this), original.category)
            }
            else -> {
                typeGroup.check(R.id.editRadioDebt)
                showDebtFields()
                debtDirectionGroup.check(
                    if (original.category == "Receivable") R.id.editRadioDebtReceivable else R.id.editRadioDebtPayable
                )
                updatePersonNameHint()
            }
        }

        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.editRadioIncome -> {
                    showIncomeOrExpenseFields(income = true)
                    setCategoryAdapter(CategoryRepository.getIncome(this), null)
                }
                R.id.editRadioExpense -> {
                    showIncomeOrExpenseFields(income = false)
                    setCategoryAdapter(CategoryRepository.getExpense(this), null)
                }
                R.id.editRadioDebt -> {
                    showDebtFields()
                    if (debtDirectionGroup.checkedRadioButtonId == -1) {
                        debtDirectionGroup.check(R.id.editRadioDebtPayable)
                    }
                    updatePersonNameHint()
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_edit_transaction)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val description = descriptionInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()

                val updated: Transaction = when (typeGroup.checkedRadioButtonId) {
                    R.id.editRadioDebt -> {
                        val personName = personNameInput.text.toString().trim()
                        if (amount == null || amount <= 0.0) {
                            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        if (personName.isEmpty()) {
                            Toast.makeText(this, R.string.error_enter_person_name, Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        val direction = if (debtDirectionGroup.checkedRadioButtonId == R.id.editRadioDebtReceivable) "Receivable" else "Payable"
                        original.copy(
                            description = description,
                            amount = amount,
                            type = "Debt",
                            category = direction,
                            date = transactionDateFormat.format(Date(selectedDateMillis)),
                            personName = personName,
                            dueDate = transactionDateFormat.format(Date(selectedDueDateMillis)),
                            isSettled = settledCheckBox.isChecked,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    else -> {
                        val type = if (typeGroup.checkedRadioButtonId == R.id.editRadioIncome) "Income" else "Expense"
                        if (description.isEmpty() || amount == null || amount <= 0.0 || categorySpinner.selectedItemPosition == 0) {
                            Toast.makeText(this, "Please enter a valid description, amount and category", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        original.copy(
                            description = description,
                            amount = amount,
                            type = type,
                            category = categorySpinner.selectedItem.toString(),
                            date = transactionDateFormat.format(Date(selectedDateMillis)),
                            personName = null,
                            dueDate = null,
                            isSettled = false,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                if (projectedBalanceAfterEdit(original, updated) < 0) {
                    Toast.makeText(this, R.string.error_negative_balance_edit, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                TransactionRepository.update(this, updated)
                transactionList = TransactionRepository.getAll(this)
                applyFilterAndSearch()
                Toast.makeText(this, R.string.toast_transaction_updated, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** What the overall balance would be if [original] were replaced by [updated] — used to
     *  block an edit that would push the balance negative, the same invariant delete enforces.
     *  Debt entries never move real cash, so they contribute 0 either way. */
    private fun projectedBalanceAfterEdit(original: Transaction, updated: Transaction): Double {
        fun effect(transaction: Transaction): Double = when (transaction.type) {
            "Income" -> transaction.amount
            "Expense" -> -transaction.amount
            else -> 0.0
        }
        val currentBalance = transactionList.sumOf { effect(it) }
        return currentBalance - effect(original) + effect(updated)
    }

    private fun trimAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }
}
