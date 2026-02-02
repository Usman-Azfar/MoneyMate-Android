package com.yourname.expensetrackerapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


data class Transaction(
    val description: String,
    val amount: Double,
    val type: String,
    val category : String? = null,
    val date: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var balanceTextView: TextView
    private lateinit var descriptionInput: EditText
    private lateinit var amountInput: EditText
    private lateinit var radioGroup: RadioGroup
    private lateinit var spinner: Spinner
    private lateinit var addIncomeBtn: Button
    private lateinit var addExpenseBtn: Button

    private lateinit var totalIncomeShow: TextView
    private lateinit var totalExpenseShow: TextView

    val incomeOptions = listOf("Select Income Category", "Salary", "Freelance", "Investment" , "Gift")
    val expenseOptions = listOf("Select Expense Category", "Food", "Transportation", "Entertainment", "Shopping", "Health", "Education", "Bills", "Other")

    private lateinit var recyclerView: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter
    private val transactionList = mutableListOf<Transaction>()

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson

    companion object {
        private const val PREFS_NAME = "ExpenseTrackerPrefs"
        private const val KEY_TRANSACTIONS = "transactions"
    }

    private val filteredList = mutableListOf<Transaction>()
    private var currentFilter = "All"
    private var searchQuery = ""

    private lateinit var emptyStateText: TextView

    var totalBalance = 0.0
    var totalIncome = 0.0
    var totalExpense = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set up the toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeSharedPreferences()
        initializeViews()
        setupRadioListener()
        setupRecyclerView()
        setupListeners()
        loadTransactions()
    }

    private fun initializeSharedPreferences(){
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        gson = Gson()
    }

    private fun saveTransactions() {
        val transactionsJson = gson.toJson(transactionList)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_TRANSACTIONS, transactionsJson)
        editor.apply()
        println("Transactions saved: ${transactionList.size} items")
    }

    private fun loadTransactions() {
        val transactionsJson = sharedPreferences.getString(KEY_TRANSACTIONS, null)

        if (transactionsJson != null) {
            val type = object : TypeToken<MutableList<Transaction>>() {}.type
            val loadedTransactions: MutableList<Transaction> = gson.fromJson(transactionsJson, type)

            transactionList.clear()
            transactionList.addAll(loadedTransactions)

            applyFilterAndSearch()
            updateBalance()
            updateEmptyState()

            println("Transactions loaded: ${transactionList.size} items")
        } else {
            println("No saved transactions found")
            updateEmptyState()
        }
    }

    override fun onPause() {
        super.onPause()
        saveTransactions()
        println("App paused - data saved")
    }

    override fun onStop() {
        super.onStop()
        saveTransactions()
        println("App stopped - data saved")
    }

    private fun initializeViews(){
        balanceTextView = findViewById(R.id.Balance)
        descriptionInput = findViewById(R.id.Description)
        amountInput = findViewById(R.id.Amount)
        radioGroup = findViewById(R.id.RadioGroup1)
        spinner = findViewById(R.id.Spinner1)
        addIncomeBtn = findViewById(R.id.AddIncomeBtn)
        addExpenseBtn = findViewById(R.id.AddExpenseBtn)
        totalIncomeShow = findViewById(R.id.totalIncomeBalance)
        totalExpenseShow = findViewById(R.id.totalExpenseBalance)
        emptyStateText = findViewById(R.id.emptyStateText)
    }

    private fun setupRadioListener() {
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.RadioButtonIncome -> setupSpinner(incomeOptions)
                R.id.RadioButtonExpense -> setupSpinner(expenseOptions)
                else -> spinner.adapter = null
            }
        }
    }

    private fun setupSpinner(options: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(0)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long){
                // Item selected
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        filteredList.clear()
        filteredList.addAll(transactionList)

        transactionAdapter = TransactionAdapter(filteredList) { position ->
            deleteTransaction(position)
        }
        recyclerView.adapter = transactionAdapter
    }

    private fun updateEmptyState() {
        if (filteredList.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE

            emptyStateText.text = when {
                searchQuery.isNotEmpty() -> "No transactions match '$searchQuery'"
                currentFilter != "All" -> "No ${currentFilter.lowercase()} transactions"
                else -> "No transactions yet.\nAdd your first transaction!"
            }
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        addIncomeBtn.setOnClickListener {
            addTransaction("Income")
        }
        addExpenseBtn.setOnClickListener {
            addTransaction("Expense")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

        setupSearchView(searchView)

        return true
    }

    private fun setupSearchView(searchView: androidx.appcompat.widget.SearchView?) {
        searchView?.apply {
            queryHint = "Search transactions..."
            isSubmitButtonEnabled = false

            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf("All Transactions", "Income Only", "Expense Only")

        val currentSelection = when (currentFilter) {
            "All" -> 0
            "Income" -> 1
            "Expense" -> 2
            else -> 0
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Filter Transactions")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                currentFilter = when (which) {
                    0 -> "All"
                    1 -> "Income"
                    2 -> "Expense"
                    else -> "All"
                }

                applyFilterAndSearch()
                dialog.dismiss()

                Toast.makeText(
                    this,
                    "Showing: ${options[which]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyFilterAndSearch() {
        // Step 1: Apply filter first
        val filtered = when (currentFilter) {
            "Income" -> transactionList.filter { it.type == "Income" }
            "Expense" -> transactionList.filter { it.type == "Expense" }
            else -> transactionList.toList()
        }

        // Step 2: Apply search on filtered results
        val finalList = if (searchQuery.isNotEmpty()) {
            filtered.filter { transaction ->
                transaction.description.contains(searchQuery, ignoreCase = true)
            }
        } else {
            filtered
        }

        // Step 3: Update filtered list
        filteredList.clear()
        filteredList.addAll(finalList)
        transactionAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun deleteTransaction(position: Int) {
        if(position >= 0 && position < filteredList.size){
            val transactionToDelete = filteredList[position]

            // Check if deleting would cause negative balance
            if(transactionToDelete.type == "Income") {
                val newBalance = totalBalance - transactionToDelete.amount
                if(newBalance < 0) {
                    Toast.makeText(this, "Cannot delete: Would result in negative balance", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            // Remove from main list
            transactionList.remove(transactionToDelete)

            // Reapply filter and search
            applyFilterAndSearch()

            updateBalance()
            saveTransactions()

            Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addTransaction(transactionType: String) {
        val desc = descriptionInput.text.toString()
        val amt = amountInput.text.toString().toDoubleOrNull() ?: 0.0

        if (desc.isEmpty() || amt <= 0.0) {
            Toast.makeText(this, "Please enter a valid description and amount", Toast.LENGTH_SHORT).show()
            return
        }

        val type = when (radioGroup.checkedRadioButtonId) {
            R.id.RadioButtonIncome -> "Income"
            R.id.RadioButtonExpense -> "Expense"
            else -> ""
        }

        if (type.isEmpty()) {
            Toast.makeText(this, "Please select a transaction type", Toast.LENGTH_SHORT).show()
            return
        }

        if ((type == "Income" && transactionType != "Income") ||
            (type == "Expense" && transactionType != "Expense")) {
            Toast.makeText(this, "Please select the correct transaction type", Toast.LENGTH_SHORT).show()
            return
        }

        if (spinner.selectedItemPosition == 0){
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }

        if(type == "Expense" && amt > totalBalance){
            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
            return
        }

        val category = spinner.selectedItem.toString()
        val currentDate = Date()
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(currentDate)
        val transaction = Transaction(desc, amt, type, category, date)

        // Add to main list
        transactionList.add(0, transaction)

        // Reapply filter and search to update filtered list
        applyFilterAndSearch()

        // Scroll to top if new item is visible
        if (filteredList.isNotEmpty() && filteredList[0] == transaction) {
            recyclerView.smoothScrollToPosition(0)
        }

        updateBalance()
        clearInputs()
        saveTransactions()

        Toast.makeText(this, "$type added successfully", Toast.LENGTH_SHORT).show()
    }

    private fun updateBalance(){
        totalIncome = transactionList.sumOf {
            if (it.type == "Income") it.amount else 0.0
        }
        totalExpense = transactionList.sumOf {
            if (it.type == "Expense") it.amount else 0.0
        }

        totalBalance = totalIncome - totalExpense

        balanceTextView.text = String.format("$%.2f", totalBalance)
        totalIncomeShow.text = String.format("$%.2f", totalIncome)
        totalExpenseShow.text = String.format("$%.2f", totalExpense)
    }

    private fun clearInputs(){
        descriptionInput.text?.clear()
        amountInput.text?.clear()
        spinner.setSelection(0)
        radioGroup.clearCheck()
    }
}

class TransactionAdapter(
    private val filteredList: MutableList<Transaction>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>(){

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val description: TextView = itemView.findViewById(R.id.Description)
        val date: TextView = itemView.findViewById(R.id.Date)
        val category: TextView = itemView.findViewById(R.id.Category)
        val amount: TextView = itemView.findViewById(R.id.Amount)
        val deleteButton: Button = itemView.findViewById(R.id.Delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = filteredList[position]
        holder.description.text = transaction.description
        holder.date.text = transaction.date
        holder.category.text = transaction.category ?: ""
        holder.amount.text = String.format("$%.2f", transaction.amount)
        if (transaction.type == "Income"){
            holder.amount.setTextColor(holder.itemView.context.getColor(R.color.green))
        }
        else{
            holder.amount.setTextColor(holder.itemView.context.getColor(R.color.red))
        }

        holder.deleteButton.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                onDeleteClick(adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }
}