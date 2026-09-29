package com.yourname.expensetrackerapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    private lateinit var balanceTextView: TextView
    private lateinit var descriptionInputLayout: TextInputLayout
    private lateinit var descriptionInput: EditText
    private lateinit var amountInputLayout: TextInputLayout
    private lateinit var amountInput: EditText
    private lateinit var typeGroup: RadioGroup
    private lateinit var debtDirectionGroup: RadioGroup
    private lateinit var spinner: Spinner
    private lateinit var personNameInputLayout: TextInputLayout
    private lateinit var personNameInput: EditText
    private lateinit var dueDateInputLayout: TextInputLayout
    private lateinit var dueDateInput: EditText
    private lateinit var addTransactionBtn: MaterialButton
    private lateinit var viewAllBtn: Button
    private lateinit var dateInput: EditText
    private lateinit var micButton: ImageButton
    private lateinit var bottomNav: BottomNavigationView

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleSpeechResult(result.resultCode, result.data)
        }

    private var selectedDateMillis: Long = System.currentTimeMillis()
    private var selectedDueDateMillis: Long = System.currentTimeMillis()
    private val transactionDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private lateinit var totalIncomeShow: TextView
    private lateinit var totalExpenseShow: TextView
    private lateinit var totalIncomeCountShow: TextView
    private lateinit var totalExpenseCountShow: TextView
    private lateinit var incomeCardView: View
    private lateinit var expenseCardView: View
    private lateinit var balanceLabelText: TextView

    private lateinit var totalPayableShow: TextView
    private lateinit var totalReceivableShow: TextView
    private lateinit var totalPayableCountShow: TextView
    private lateinit var totalReceivableCountShow: TextView
    private lateinit var debtSummaryRow: View
    private lateinit var payableCardView: View
    private lateinit var receivableCardView: View
    private lateinit var overdueDebtText: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter

    /** Full data, reloaded from TransactionRepository whenever the screen is shown. */
    private var transactionList: List<Transaction> = emptyList()

    /** Home only ever shows the 5 most recent transactions; full history lives in TransactionsActivity. */
    private val previewList = mutableListOf<Transaction>()

    private lateinit var emptyStateText: TextView

    var totalBalance = 0.0
    var totalIncome = 0.0
    var totalExpense = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        // With no app:title on the Toolbar itself, AppCompat would otherwise fall back to
        // showing the activity/app label as a plain, unstyled, left-aligned default title —
        // suppress that so only the custom brand chip + subtitle overlay below is visible.
        supportActionBar?.setDisplayShowTitleEnabled(false)
        findViewById<TextView>(R.id.toolbarBrandText).text = BrandName.styled(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupTypeListener()
        setupDebtDirectionListener()
        setupRecyclerView()
        setupListeners()
        setupDateField()
        setupDueDateField()
        setupVoiceInput()
        resetToNoTypeSelected()
        BottomNavHelper.setup(this, bottomNav, R.id.nav_home)
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
        amountInputLayout.hint = getString(R.string.hint_amount_with_symbol, CurrencyFormatter.symbol(this))
        refreshCurrentSpinner()
        refreshData()
    }

    private fun initializeViews() {
        balanceTextView = findViewById(R.id.Balance)
        descriptionInputLayout = findViewById(R.id.descriptionInputLayout)
        descriptionInput = findViewById(R.id.Description)
        amountInputLayout = findViewById(R.id.amountInputLayout)
        amountInput = findViewById(R.id.Amount)
        typeGroup = findViewById(R.id.TypeGroup)
        debtDirectionGroup = findViewById(R.id.DebtDirectionGroup)
        spinner = findViewById(R.id.Spinner1)
        personNameInputLayout = findViewById(R.id.personNameInputLayout)
        personNameInput = findViewById(R.id.PersonNameInput)
        dueDateInputLayout = findViewById(R.id.dueDateInputLayout)
        dueDateInput = findViewById(R.id.DueDateInput)
        addTransactionBtn = findViewById(R.id.AddTransactionBtn)
        viewAllBtn = findViewById(R.id.ViewAllBtn)
        dateInput = findViewById(R.id.DateInput)
        micButton = findViewById(R.id.micButton)
        bottomNav = findViewById(R.id.bottomNav)
        totalIncomeShow = findViewById(R.id.totalIncomeBalance)
        totalExpenseShow = findViewById(R.id.totalExpenseBalance)
        totalIncomeCountShow = findViewById(R.id.totalIncomeTransactions)
        totalExpenseCountShow = findViewById(R.id.totalExpenseTransactions)
        incomeCardView = findViewById(R.id.incomeCardView)
        expenseCardView = findViewById(R.id.expenseCardView)
        emptyStateText = findViewById(R.id.emptyStateText)
        balanceLabelText = findViewById(R.id.TotalBalance)
        totalPayableShow = findViewById(R.id.totalPayableBalance)
        totalReceivableShow = findViewById(R.id.totalReceivableBalance)
        totalPayableCountShow = findViewById(R.id.totalPayableCount)
        totalReceivableCountShow = findViewById(R.id.totalReceivableCount)
        debtSummaryRow = findViewById(R.id.debtSummaryRow)
        payableCardView = findViewById(R.id.payableCardView)
        receivableCardView = findViewById(R.id.receivableCardView)
        overdueDebtText = findViewById(R.id.overdueDebtText)
    }

    private fun setupTypeListener() {
        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.RadioTypeIncome -> showIncomeOrExpenseForm(income = true)
                R.id.RadioTypeExpense -> showIncomeOrExpenseForm(income = false)
                R.id.RadioTypeDebt -> showDebtForm()
                else -> resetToNoTypeSelected()
            }
        }
    }

    private fun showIncomeOrExpenseForm(income: Boolean) {
        spinner.visibility = View.VISIBLE
        debtDirectionGroup.visibility = View.GONE
        personNameInputLayout.visibility = View.GONE
        dueDateInputLayout.visibility = View.GONE
        descriptionInputLayout.hint = getString(R.string.hint_description)
        setupSpinner(if (income) CategoryRepository.getIncome(this) else CategoryRepository.getExpense(this))

        addTransactionBtn.isEnabled = true
        if (income) {
            addTransactionBtn.text = getString(R.string.btn_add_income)
            addTransactionBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.income)
            addTransactionBtn.setTextColor(ContextCompat.getColor(this, R.color.on_income))
        } else {
            addTransactionBtn.text = getString(R.string.btn_add_expense)
            addTransactionBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.expense)
            addTransactionBtn.setTextColor(ContextCompat.getColor(this, R.color.on_expense))
        }
    }

    private fun showDebtForm() {
        spinner.visibility = View.GONE
        debtDirectionGroup.visibility = View.VISIBLE
        personNameInputLayout.visibility = View.VISIBLE
        dueDateInputLayout.visibility = View.VISIBLE
        descriptionInputLayout.hint = getString(R.string.hint_debt_note)
        if (debtDirectionGroup.checkedRadioButtonId == -1) {
            debtDirectionGroup.check(R.id.RadioDebtPayable)
        }
        updateDebtButtonState()
    }

    private fun setupDebtDirectionListener() {
        debtDirectionGroup.setOnCheckedChangeListener { _, _ -> updateDebtButtonState() }
    }

    private fun updateDebtButtonState() {
        addTransactionBtn.isEnabled = true
        if (debtDirectionGroup.checkedRadioButtonId == R.id.RadioDebtReceivable) {
            personNameInputLayout.hint = getString(R.string.hint_receivable_from)
            addTransactionBtn.text = getString(R.string.btn_add_receivable)
            addTransactionBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.income)
            addTransactionBtn.setTextColor(ContextCompat.getColor(this, R.color.on_income))
        } else {
            personNameInputLayout.hint = getString(R.string.hint_payable_to)
            addTransactionBtn.text = getString(R.string.btn_add_payable)
            addTransactionBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.expense)
            addTransactionBtn.setTextColor(ContextCompat.getColor(this, R.color.on_expense))
        }
    }

    private fun resetToNoTypeSelected() {
        spinner.visibility = View.VISIBLE
        spinner.adapter = null
        debtDirectionGroup.visibility = View.GONE
        personNameInputLayout.visibility = View.GONE
        dueDateInputLayout.visibility = View.GONE
        descriptionInputLayout.hint = getString(R.string.hint_description)
        addTransactionBtn.isEnabled = false
        addTransactionBtn.text = getString(R.string.btn_select_type)
        addTransactionBtn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.on_surface_variant)
        addTransactionBtn.setTextColor(ContextCompat.getColor(this, R.color.surface))
    }

    /** Categories can change from Settings while Home stays resident in the back stack (reused
     *  via FLAG_ACTIVITY_REORDER_TO_FRONT), so re-populate whichever spinner is currently backed
     *  by a radio selection every time this screen is shown again — preserving the current pick
     *  when it's still valid, rather than always resetting to the placeholder. */
    private fun refreshCurrentSpinner() {
        val current = spinner.selectedItem as? String
        when (typeGroup.checkedRadioButtonId) {
            R.id.RadioTypeIncome -> setupSpinner(CategoryRepository.getIncome(this), current)
            R.id.RadioTypeExpense -> setupSpinner(CategoryRepository.getExpense(this), current)
        }
    }

    private fun setupSpinner(options: List<String>, preselect: String? = null) {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = adapter
        val index = preselect?.let { options.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        spinner.setSelection(index)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDateField() {
        dateInput.setText(displayDateFormat.format(Date(selectedDateMillis)))
        dateInput.setOnClickListener { showDatePicker() }
    }

    /** Capped to today — this field is for backdating a transaction you forgot to log, not for
     *  scheduling a future one. */
    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                picked.set(Calendar.MILLISECOND, 0)
                selectedDateMillis = picked.timeInMillis
                dateInput.setText(displayDateFormat.format(Date(selectedDateMillis)))
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun setupDueDateField() {
        dueDateInput.setText(displayDateFormat.format(Date(selectedDueDateMillis)))
        dueDateInput.setOnClickListener { showDueDatePicker() }
    }

    /** Unlike the record date above, a due date is naturally in the future — no min/max cap. */
    private fun showDueDatePicker() {
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

    /** Hides the mic button entirely on a device with no speech-recognition app installed,
     *  rather than showing a button that would just fail every time it's tapped. */
    private fun setupVoiceInput() {
        if (SpeechInputHelper.isAvailable(this)) {
            micButton.setOnClickListener { launchVoiceInput() }
        } else {
            micButton.visibility = View.GONE
        }
    }

    private fun launchVoiceInput() {
        try {
            speechRecognizerLauncher.launch(SpeechInputHelper.buildIntent(this))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.toast_voice_no_recognizer, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSpeechResult(resultCode: Int, data: Intent?) {
        val transcript = SpeechInputHelper.extractTranscript(resultCode, data)
        if (transcript == null) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, R.string.toast_voice_no_speech, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val incomeCategories = CategoryRepository.getIncome(this)
        val expenseCategories = CategoryRepository.getExpense(this)

        val smartVoiceOn = SmartVoicePrefs.isEnabled(this) && GeminiClient.isConfigured()
        if (!smartVoiceOn) {
            applyParsedTransaction(TransactionVoiceParser.parse(transcript, incomeCategories, expenseCategories))
            return
        }
        if (!GeminiClient.hasNetwork(this)) {
            // Offline: no point waiting out a timeout — go straight to the offline parser.
            Toast.makeText(this, R.string.toast_voice_smart_fallback, Toast.LENGTH_SHORT).show()
            applyParsedTransaction(TransactionVoiceParser.parse(transcript, incomeCategories, expenseCategories))
            return
        }

        setVoiceBusy(true)
        Toast.makeText(this, R.string.toast_voice_understanding, Toast.LENGTH_SHORT).show()
        GeminiTransactionParser.parse(transcript, incomeCategories, expenseCategories) { smartResult ->
            if (isFinishing || isDestroyed) return@parse
            setVoiceBusy(false)
            if (smartResult != null) {
                applyParsedTransaction(smartResult)
            } else {
                Toast.makeText(this, R.string.toast_voice_smart_fallback, Toast.LENGTH_SHORT).show()
                applyParsedTransaction(TransactionVoiceParser.parse(transcript, incomeCategories, expenseCategories))
            }
        }
    }

    /** Blocks a second voice capture while a Smart Voice request is still in flight, so two
     *  answers can't race each other into the form. */
    private fun setVoiceBusy(busy: Boolean) {
        micButton.isEnabled = !busy
        micButton.alpha = if (busy) 0.4f else 1f
    }

    /** Pre-fills the Add Transaction form from a parsed voice command, exactly as if the user had
     *  typed/tapped each field themselves — nothing here saves the transaction. Any field the
     *  parser couldn't confidently determine is simply left untouched at its existing default
     *  (category placeholder, today's date), so what voice didn't catch stays visibly unresolved
     *  rather than guessed. */
    private fun applyParsedTransaction(parsed: ParsedTransaction) {
        var appliedAnything = false

        when (parsed.type) {
            "Income" -> {
                typeGroup.check(R.id.RadioTypeIncome)
                appliedAnything = true
            }
            "Expense" -> {
                typeGroup.check(R.id.RadioTypeExpense)
                appliedAnything = true
            }
            "Debt" -> {
                typeGroup.check(R.id.RadioTypeDebt)
                debtDirectionGroup.check(
                    if (parsed.debtDirection == "Receivable") R.id.RadioDebtReceivable else R.id.RadioDebtPayable
                )
                appliedAnything = true
            }
        }

        if (parsed.category != null && (parsed.type == "Income" || parsed.type == "Expense")) {
            val options = if (parsed.type == "Income") CategoryRepository.getIncome(this) else CategoryRepository.getExpense(this)
            setupSpinner(options, parsed.category)
        }

        if (parsed.amount != null) {
            amountInput.setText(formatAmountForInput(parsed.amount))
            appliedAnything = true
        } else {
            Toast.makeText(this, R.string.toast_voice_amount_missing, Toast.LENGTH_SHORT).show()
        }

        if (parsed.dateMillis != null) {
            selectedDateMillis = parsed.dateMillis
            dateInput.setText(displayDateFormat.format(Date(selectedDateMillis)))
        }
        if (parsed.dueDateMillis != null) {
            selectedDueDateMillis = parsed.dueDateMillis
            dueDateInput.setText(displayDateFormat.format(Date(selectedDueDateMillis)))
        }
        if (!parsed.personName.isNullOrBlank()) {
            personNameInput.setText(parsed.personName)
        }
        if (parsed.description.isNotBlank()) {
            descriptionInput.setText(parsed.description)
            appliedAnything = true
        }

        if (AutoAddVoicePrefs.isEnabled(this)) {
            // Reuses the exact same save path (and validation) the Add button calls — an
            // incomplete parse just surfaces the same "please select a category"/"enter a valid
            // amount" Toast a manual submit would, with the form left pre-filled to finish by hand.
            addTransaction()
        } else if (appliedAnything) {
            Toast.makeText(this, R.string.toast_voice_applied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatAmountForInput(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        transactionAdapter = TransactionAdapter(
            previewList,
            onDeleteClick = { position -> deleteTransaction(position) },
            onSettleToggle = { position -> toggleSettled(position) }
        )
        recyclerView.adapter = transactionAdapter
    }

    private fun refreshData() {
        transactionList = TransactionRepository.getAll(this)
        previewList.clear()
        previewList.addAll(transactionList.take(5))
        transactionAdapter.notifyDataSetChanged()
        updateBalance()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (previewList.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyStateText.text = "No transactions yet.\nAdd your first transaction!"
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        addTransactionBtn.setOnClickListener { addTransaction() }
        viewAllBtn.setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Signs out and wipes the local cache — so whoever signs in next on this device doesn't
     *  start out seeing this account's cached transactions. */
    private fun logOut() {
        AuthRepository.signOut()
        SyncManager.clearLocalData(this)
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun deleteTransaction(position: Int) {
        if (position < 0 || position >= previewList.size) return
        val transactionToDelete = previewList[position]

        if (transactionToDelete.type == "Income") {
            val newBalance = totalBalance - transactionToDelete.amount
            if (newBalance < 0) {
                Toast.makeText(this, "Cannot delete: Would result in negative balance", Toast.LENGTH_SHORT).show()
                return
            }
        }

        TransactionRepository.delete(this, transactionToDelete)
        refreshData()
        Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
    }

    private fun toggleSettled(position: Int) {
        if (position < 0 || position >= previewList.size) return
        val transaction = previewList[position]
        if (transaction.type != "Debt") return
        val updated = transaction.copy(isSettled = !transaction.isSettled, updatedAt = System.currentTimeMillis())
        TransactionRepository.update(this, updated)
        refreshData()
        Toast.makeText(
            this,
            if (updated.isSettled) R.string.toast_debt_settled else R.string.toast_debt_unsettled,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun addTransaction() {
        val desc = descriptionInput.text.toString()
        val amt = amountInput.text.toString().toDoubleOrNull() ?: 0.0

        when (typeGroup.checkedRadioButtonId) {
            R.id.RadioTypeIncome -> addIncomeOrExpense("Income", desc, amt)
            R.id.RadioTypeExpense -> addIncomeOrExpense("Expense", desc, amt)
            R.id.RadioTypeDebt -> addDebt(desc, amt)
            else -> Toast.makeText(this, "Please select a transaction type", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addIncomeOrExpense(type: String, desc: String, amt: Double) {
        if (desc.isEmpty() || amt <= 0.0) {
            Toast.makeText(this, "Please enter a valid description and amount", Toast.LENGTH_SHORT).show()
            return
        }
        if (spinner.selectedItemPosition == 0 || spinner.adapter == null) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }
        if (type == "Expense" && amt > totalBalance) {
            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
            return
        }

        val category = spinner.selectedItem.toString()
        val date = transactionDateFormat.format(Date(selectedDateMillis))
        val transaction = Transaction(desc, amt, type, category, date)

        TransactionRepository.add(this, transaction)
        finishAddingTransaction(transaction)

        if (type == "Expense") {
            BudgetNotifier.checkAndNotify(this)
        }

        Toast.makeText(this, "$type added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun addDebt(desc: String, amt: Double) {
        val personName = personNameInput.text.toString().trim()
        val direction = if (debtDirectionGroup.checkedRadioButtonId == R.id.RadioDebtReceivable) "Receivable" else "Payable"

        if (amt <= 0.0) {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        if (personName.isEmpty()) {
            Toast.makeText(this, R.string.error_enter_person_name, Toast.LENGTH_SHORT).show()
            return
        }

        val date = transactionDateFormat.format(Date(selectedDateMillis))
        val dueDate = transactionDateFormat.format(Date(selectedDueDateMillis))
        val transaction = Transaction(
            description = desc,
            amount = amt,
            type = "Debt",
            category = direction,
            date = date,
            personName = personName,
            dueDate = dueDate
        )

        TransactionRepository.add(this, transaction)
        finishAddingTransaction(transaction)
        Toast.makeText(this, R.string.toast_debt_added, Toast.LENGTH_SHORT).show()
    }

    private fun finishAddingTransaction(transaction: Transaction) {
        refreshData()
        if (previewList.isNotEmpty() && previewList[0] == transaction) {
            recyclerView.smoothScrollToPosition(0)
        }
        clearInputs()
    }

    /** [totalBalance]/[totalIncome]/[totalExpense] stay all-time regardless of the display period
     *  below — real available balance (used to guard against overspending or deleting into the
     *  negative) can't depend on which window the user happens to be looking at. Whether debts
     *  feed into these totals is controlled by [IncludeDebtsPrefs] — see [DebtAccounting]; when
     *  off (the default), a Payable/Receivable record is treated as an obligation, not cash that
     *  has actually moved, and is tracked in its own outstanding-totals section instead. */
    private fun updateBalance() {
        val includeDebts = IncludeDebtsPrefs.isEnabled(this)
        totalIncome = transactionList.sumOf { DebtAccounting.incomeAmount(it, includeDebts) }
        totalExpense = transactionList.sumOf { DebtAccounting.expenseAmount(it, includeDebts) }
        totalBalance = totalIncome - totalExpense

        val periodType = HomePeriodPrefs.getType(this)
        val (start, end) = PeriodWindow.window(
            periodType,
            HomePeriodPrefs.getCycleStartMillis(this),
            HomePeriodPrefs.getCustomStartMillis(this),
            HomePeriodPrefs.getCustomEndMillis(this)
        )
        val displayedTransactions = transactionList.filter { transaction ->
            val date = try { transactionDateFormat.parse(transaction.date) } catch (e: Exception) { null } ?: return@filter false
            !date.before(start) && date.before(end)
        }
        val displayedIncome = displayedTransactions.sumOf { DebtAccounting.incomeAmount(it, includeDebts) }
        val displayedExpense = displayedTransactions.sumOf { DebtAccounting.expenseAmount(it, includeDebts) }
        val incomeCount = displayedTransactions.count { it.type == "Income" }
        val expenseCount = displayedTransactions.count { it.type == "Expense" }

        balanceLabelText.text = if (periodType == PeriodType.ALL_TIME) {
            getString(R.string.label_total_balance)
        } else {
            getString(
                R.string.label_total_balance_for_period,
                PeriodWindow.label(
                    periodType,
                    HomePeriodPrefs.getCycleStartMillis(this),
                    HomePeriodPrefs.getCustomStartMillis(this),
                    HomePeriodPrefs.getCustomEndMillis(this)
                )
            )
        }
        balanceTextView.text = CurrencyFormatter.format(this, displayedIncome - displayedExpense)
        totalIncomeShow.text = CurrencyFormatter.format(this, displayedIncome)
        totalExpenseShow.text = CurrencyFormatter.format(this, displayedExpense)
        totalIncomeCountShow.text = resources.getQuantityString(R.plurals.transaction_count, incomeCount, incomeCount)
        totalExpenseCountShow.text = resources.getQuantityString(R.plurals.transaction_count, expenseCount, expenseCount)
        equalizeCardHeights(incomeCardView, expenseCardView)

        updateDebtTotals(displayedTransactions, includeDebts)
    }

    /** Outstanding (unsettled) debts, filtered by the same display period as everything else on
     *  Home for consistency. The overdue banner, however, always checks every unsettled debt
     *  regardless of period — a debt already overdue shouldn't silently disappear just because
     *  the user is looking at "This Week".
     *
     *  When [includeDebts] is on, the Payable/Receivable row is hidden instead — those amounts
     *  are already folded into Total Income/Total Expense above, so showing them again here
     *  would double them up. The overdue banner stays regardless, since it's a due-date warning
     *  rather than a running total. */
    private fun updateDebtTotals(displayedTransactions: List<Transaction>, includeDebts: Boolean) {
        debtSummaryRow.visibility = if (includeDebts) View.GONE else View.VISIBLE
        if (!includeDebts) {
            val displayedDebts = displayedTransactions.filter { it.type == "Debt" && !it.isSettled }
            val totalPayable = displayedDebts.filter { it.category == "Payable" }.sumOf { it.amount }
            val totalReceivable = displayedDebts.filter { it.category == "Receivable" }.sumOf { it.amount }
            val payableCount = displayedDebts.count { it.category == "Payable" }
            val receivableCount = displayedDebts.count { it.category == "Receivable" }

            totalPayableShow.text = CurrencyFormatter.format(this, totalPayable)
            totalReceivableShow.text = CurrencyFormatter.format(this, totalReceivable)
            totalPayableCountShow.text = resources.getQuantityString(R.plurals.transaction_count, payableCount, payableCount)
            totalReceivableCountShow.text = resources.getQuantityString(R.plurals.transaction_count, receivableCount, receivableCount)
            equalizeCardHeights(payableCardView, receivableCardView)
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
        val overdueCount = transactionList.count { transaction ->
            if (transaction.type != "Debt" || transaction.isSettled) return@count false
            val due = try { transactionDateFormat.parse(transaction.dueDate ?: return@count false) } catch (e: Exception) { null }
            due != null && due.before(today)
        }
        if (overdueCount > 0) {
            overdueDebtText.visibility = View.VISIBLE
            overdueDebtText.text = resources.getQuantityString(R.plurals.debt_overdue_count, overdueCount, overdueCount)
        } else {
            overdueDebtText.visibility = View.GONE
        }
    }

    /** A pair of side-by-side summary cards sizes to its own text, so a longer-wrapping amount in
     *  one makes it taller than the other — measure both after this layout pass and stretch the
     *  shorter one to match, so they always read as a matched pair. */
    private fun equalizeCardHeights(first: View, second: View) {
        first.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        second.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        first.requestLayout()
        second.requestLayout()
        first.post {
            val tallest = maxOf(first.height, second.height)
            if (tallest > 0) {
                first.layoutParams.height = tallest
                second.layoutParams.height = tallest
                first.requestLayout()
                second.requestLayout()
            }
        }
    }

    private fun clearInputs() {
        descriptionInput.text?.clear()
        amountInput.text?.clear()
        personNameInput.text?.clear()
        spinner.setSelection(0)
        typeGroup.clearCheck()
        debtDirectionGroup.clearCheck()
        selectedDateMillis = System.currentTimeMillis()
        dateInput.setText(displayDateFormat.format(Date(selectedDateMillis)))
        selectedDueDateMillis = System.currentTimeMillis()
        dueDateInput.setText(displayDateFormat.format(Date(selectedDueDateMillis)))
        resetToNoTypeSelected()
    }
}
