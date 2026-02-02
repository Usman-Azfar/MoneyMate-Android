//package com.yourname.expensetrackerapp
//
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.AdapterView
//import android.widget.ArrayAdapter
//import android.widget.Button
//import android.widget.EditText
//import android.widget.RadioGroup
//import android.widget.Spinner
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.enableEdgeToEdge
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.view.ViewCompat
//import androidx.core.view.WindowInsetsCompat
//import androidx.recyclerview.widget.LinearLayoutManager
//import androidx.recyclerview.widget.RecyclerView
//import java.text.SimpleDateFormat
//import java.util.Date
//import java.util.Locale
//import android.content.Context
//import android.content.SharedPreferences
//import android.view.Menu
//import android.view.MenuItem
//import com.google.gson.Gson
//import com.google.gson.reflect.TypeToken
//
//
//data class Transaction(
//    val description: String,
//    val amount: Double,
//    val type: String,
//    val category : String? = null,
//    val date: String
//)
//
//class MainActivity : AppCompatActivity() {
//
//    private lateinit var balanceTextView: TextView  // Renamed to avoid confusion
//    private lateinit var descriptionInput: EditText  // Renamed to avoid confusion
//    private lateinit var amountInput: EditText       // Renamed to avoid confusion
//    private lateinit var radioGroup: RadioGroup
//    private lateinit var spinner: Spinner
//    private lateinit var addIncomeBtn: Button
//    private lateinit var addExpenseBtn: Button
//
//    private lateinit var totalIncomeShow: TextView
//    private lateinit var totalExpenseShow: TextView
//
//    val incomeOptions = listOf("Select Income Category", "Salary", "Freelance", "Investment" , "Gift")
//    val expenseOptions = listOf("Select Expense Category", "Food", "Transportation", "Entertainment", "Shopping", "Health", "Education", "Bills", "Other")
//
//    private lateinit var recyclerView: RecyclerView
//    private lateinit var transactionAdapter: TransactionAdapter
//    private val transactionList = mutableListOf<Transaction>()
//
//    // NEW: Add these SharedPreferences properties
//    private lateinit var sharedPreferences: SharedPreferences
//    private lateinit var gson: Gson
//
//    // Constants for SharedPreferences keys
//    companion object {
//        private const val PREFS_NAME = "ExpenseTrackerPrefs"
//        private const val KEY_TRANSACTIONS = "transactions"
//    }
//
//
//    // NEW: Add filtered list and current filter type
//    private val filteredList = mutableListOf<Transaction>()
//    private var currentFilter = "All" // Can be: "All", "Income", "Expense"
//    private var searchQuery = "" // Store current search text
//
//    private lateinit var emptyStateText: TextView
//
//    var totalBalance = 0.0
//    var totalIncome = 0.0
//    var totalExpense = 0.0
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
//        // NEW: Initialize SharedPreferences and Gson
//        initializeSharedPreferences()
//
//        initializeViews()
//        setupRadioListener()
//        setupRecyclerView()
//        setupListeners()
//
//        // NEW: Load saved transactions
//        loadTransactions()
//    }
//
//    private fun initializeSharedPreferences(){
//        // Get SharedPreferences instance
//        // MODE_PRIVATE means only this app can access this data
//        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
//
//        // Create Gson instance for JSON conversion
//        gson = Gson()
//    }
//
//    private fun saveTransactions() {
//        // Step 1: Convert transaction list to JSON string
//        val transactionsJson = gson.toJson(transactionList)
//
//        // Step 2: Get editor to modify SharedPreferences
//        val editor = sharedPreferences.edit()  //SharedPreferences is Read Only by default
//
//        // Step 3: Put the JSON string with a key
//        editor.putString(KEY_TRANSACTIONS, transactionsJson)
//
//        // Step 4: Apply changes (saves immediately in background)
//        editor.apply()   //or editor.commit() for foreground
//
//        // Optional: Log for debugging
//        println("Transactions saved: ${transactionList.size} items")
//    }
//
//    private fun loadTransactions() {
//        // Step 1: Get the JSON string from SharedPreferences
//        // null is the default value if key doesn't exist
//        val transactionsJson = sharedPreferences.getString(KEY_TRANSACTIONS, null)
//
//        // Step 2: Check if we have saved data
//        if (transactionsJson != null) {
//            // Step 3: Define the type for Gson (List<Transaction>)
//            val type = object : TypeToken<MutableList<Transaction>>() {}.type  //List<Transaction> is a generic type (type with another type inside), Java/Kotlin erase generic type info at runtime (called "type erasure"), TypeToken captures the full type information
//
//            // Step 4: Convert JSON string back to list
//            val loadedTransactions: MutableList<Transaction> = gson.fromJson(transactionsJson, type)
//
//            // Step 5: Clear current list and add loaded data
//            transactionList.clear()
//            transactionList.addAll(loadedTransactions)
//
//            // Step 6: Notify adapter and update UI
//            transactionAdapter.notifyDataSetChanged()
//            updateBalance()
//            updateEmptyState()
//
//            // Optional: Log for debugging
//            println("Transactions loaded: ${transactionList.size} items")
//        } else {
//            // No saved data found
//            println("No saved transactions found")
//        }
//    }
//
//    override fun onPause() {    //Save data only when app gets paused
//        super.onPause()
//        saveTransactions()
//        println("App paused - data saved")
//    }
//
//    //(Optional)  For additional Safety - Save data when app is stopped Called after onPause()
//    override fun onStop() {
//        super.onStop()
//        saveTransactions()
//        println("App stopped - data saved")
//    }
//
//    private fun initializeViews(){
//        balanceTextView = findViewById(R.id.Balance)
//        descriptionInput = findViewById(R.id.Description)
//        amountInput = findViewById(R.id.Amount)
//        radioGroup = findViewById(R.id.RadioGroup1)
//        spinner = findViewById(R.id.Spinner1)
//        addIncomeBtn = findViewById(R.id.AddIncomeBtn)
//        addExpenseBtn = findViewById(R.id.AddExpenseBtn)
//        totalIncomeShow = findViewById(R.id.totalIncomeBalance)
//        totalExpenseShow = findViewById(R.id.totalExpenseBalance)
//        emptyStateText = findViewById(R.id.emptyStateText)
//    }
//
//    private fun setupRadioListener() {
//        radioGroup.setOnCheckedChangeListener { _, checkedId ->
//            when (checkedId) {
//                R.id.RadioButtonIncome -> setupSpinner(incomeOptions)
//                R.id.RadioButtonExpense -> setupSpinner(expenseOptions)
//                else -> spinner.adapter = null
//            }
//        }
//    }
//
//    private fun setupSpinner(options: List<String>) {
//        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
//        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        spinner.adapter = adapter
//        spinner.setSelection(0)
//
//        // Removed the excessive toast messages - they're annoying for users
//        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
//            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long){
//                // Item selected - no need for toast on every selection
//            }
//
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//                // Do nothing
//            }
//        }
//    }
//
//    private fun setupRecyclerView() {
//        recyclerView = findViewById(R.id.recyclerView)
//        recyclerView.layoutManager = LinearLayoutManager(this)
//
//        // Initialize filtered list with all transactions
//        filteredList.clear()
//        filteredList.addAll(transactionList)
//
//        transactionAdapter = TransactionAdapter(transactionList, filteredList) { position ->
//            deleteTransaction(position)  // Simplified - only pass position
//        }
//        recyclerView.adapter = transactionAdapter
//    }
//
//    /**
//     * Show or hide empty state message
//     */
//    private fun updateEmptyState() {
//        if (filteredList.isEmpty()) {
//            emptyStateText.visibility = View.VISIBLE
//            recyclerView.visibility = View.GONE
//
//            // Customize message based on state
//            emptyStateText.text = when {
//                searchQuery.isNotEmpty() -> "No transactions match '$searchQuery'"
//                currentFilter != "All" -> "No ${currentFilter.lowercase()} transactions"
//                else -> "No transactions yet.\nAdd your first transaction!"
//            }
//        } else {
//            emptyStateText.visibility = View.GONE
//            recyclerView.visibility = View.VISIBLE
//        }
//    }
//
//    private fun setupListeners() {
//        addIncomeBtn.setOnClickListener {
//            addTransaction("Income")
//        }
//        addExpenseBtn.setOnClickListener {
//            addTransaction("Expense")
//        }
//    }
//
//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        // Inflate the menu XML into the toolbar
//        menuInflater.inflate(R.menu.main_menu, menu)
//
//        // Setup SearchView
//        val searchItem = menu?.findItem(R.id.action_search)
//        val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
//
//        setupSearchView(searchView)
//
//        return true
//    }
//
//    private fun setupSearchView(searchView: androidx.appcompat.widget.SearchView?) {
//        searchView?.apply {
//            // Hint text when search is empty
//            queryHint = "Search transactions..."
//
//            // Enable submit button
//            isSubmitButtonEnabled = false
//
//            // Listen for text changes
//            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
//                /**
//                 * Called when user presses search button on keyboard
//                 */
//                override fun onQueryTextSubmit(query: String?): Boolean {
//                    // We handle search in onQueryTextChange, so nothing here
//                    return false
//                }
//
//                /**
//                 * Called when text changes (real-time search)
//                 */
//                override fun onQueryTextChange(newText: String?): Boolean {
//                    searchQuery = newText ?: ""
//                    performSearch(searchQuery)
//                    return true
//                }
//            })
//        }
//    }
//
//    private fun performSearch(query: String) {
//        // Step 1: Start with transactions based on current filter
//        val baseList = when (currentFilter) {
//            "Income" -> transactionList.filter { it.type == "Income" }
//            "Expense" -> transactionList.filter { it.type == "Expense" }
//            else -> transactionList
//        }
//
//        // Step 2: If no search query, show filtered list
//        if (query.isEmpty()) {
//            filteredList.clear()
//            filteredList.addAll(baseList)
//            transactionAdapter.notifyDataSetChanged()
//            return
//        }
//
//        // Step 3: Search in description (case-insensitive)
//        val searchResults = baseList.filter { transaction ->
//            transaction.description.contains(query, ignoreCase = true)
//        }
//
//        // Step 4: Update filtered list with results
//        filteredList.clear()
//        filteredList.addAll(searchResults)
//        transactionAdapter.notifyDataSetChanged()
//
////        // Step 5: Show message if no results
////        if (searchResults.isEmpty()) {
////            Toast.makeText(this, "No transactions found", Toast.LENGTH_SHORT).show()
////        }
//        updateEmptyState()
//    }
//
//    /**
//     * Handle clicks on menu items (search, filter)
//     */
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        return when (item.itemId) {
//            R.id.action_filter -> {
//                showFilterDialog()
//                true
//            }
//            else -> super.onOptionsItemSelected(item)
//        }
//    }
//
//    /**
//     * Show dialog with filter options
//     * Radio buttons for: All, Income Only, Expense Only
//     */
//    private fun showFilterDialog() {
//        // Options for the dialog
//        val options = arrayOf("All Transactions", "Income Only", "Expense Only")
//
//        // Which option is currently selected
//        val currentSelection = when (currentFilter) {
//            "All" -> 0
//            "Income" -> 1
//            "Expense" -> 2
//            else -> 0
//        }
//
//        // Create and show AlertDialog
//        android.app.AlertDialog.Builder(this)
//            .setTitle("Filter Transactions")
//            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
//                // Called when user selects an option
//
//                // Update current filter based on selection
//                currentFilter = when (which) {
//                    0 -> "All"
//                    1 -> "Income"
//                    2 -> "Expense"
//                    else -> "All"
//                }
//
//                // Apply the filter
//                applyFilter()
//
//                // Close dialog
//                dialog.dismiss()
//
//                // Show confirmation
//                Toast.makeText(
//                    this,
//                    "Showing: ${options[which]}",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }
//
//    /**
//     * Apply current filter and search
//     * Shows filtered transactions in RecyclerView
//     */
//    private fun applyFilter() {
//        // Get base list according to filter
//        val filtered = when (currentFilter) {
//            "Income" -> transactionList.filter { it.type == "Income" }
//            "Expense" -> transactionList.filter { it.type == "Expense" }
//            else -> transactionList.toList() // "All"
//        }
//
//        // If there's also a search query active, apply it
//        val finalList = if (searchQuery.isNotEmpty()) {
//            filtered.filter { transaction ->
//                transaction.description.contains(searchQuery, ignoreCase = true)
//            }
//        } else {
//            filtered
//        }
//
//        // Update filtered list
//        filteredList.clear()
//        filteredList.addAll(finalList)
//        transactionAdapter.notifyDataSetChanged()
//
//        updateEmptyState()
//
//        // Show result count
//        val filterText = when (currentFilter) {
//            "Income" -> "income"
//            "Expense" -> "expense"
//            else -> ""
//        }
//
//        // Show count
//        Toast.makeText(
//            this,
//            "Showing ${filteredList.size} transaction(s)",
//            Toast.LENGTH_SHORT
//        ).show()
//    }
//
//
//
//    private fun deleteTransaction(position: Int) {
//        if(position >= 0 && position < transactionList.size){
//
//            if(transactionList[position].amount > totalBalance) {
//                Toast.makeText(this, "Cannot delete this transaction due to low balance", Toast.LENGTH_SHORT).show()
//                return
//            }
//
//            transactionAdapter.removeItem(position)
//
//            applyFilter()
//            updateBalance()
//            saveTransactions()    //Optional
//            Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun addTransaction(transactionType: String) {
//        // Use renamed variables to avoid confusion
//        val desc = descriptionInput.text.toString()
//        val amt = amountInput.text.toString().toDoubleOrNull() ?: 0.0
//
//        if (desc.isEmpty() || amt <= 0.0) {
//            Toast.makeText(this, "Please enter a valid description and amount", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val type = when (radioGroup.checkedRadioButtonId) {
//            R.id.RadioButtonIncome -> "Income"
//            R.id.RadioButtonExpense -> "Expense"
//            else -> ""
//        }
//
//        if (type.isEmpty()) {
//            Toast.makeText(this, "Please select a transaction type", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        if ((type == "Income" && transactionType != "Income") ||
//            (type == "Expense" && transactionType != "Expense")) {
//            Toast.makeText(this, "Please select the correct transaction type", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        if (spinner.selectedItemPosition == 0){
//            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        if(type == "Expense" && amt > totalBalance){
//            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val category = spinner.selectedItem.toString()
//        val currentDate = Date()
//        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(currentDate)
//        val transaction = Transaction(desc, amt, type, category, date)
//
//        transactionList.add(0, transaction)
//        transactionAdapter.notifyItemInserted(0)
//        recyclerView.smoothScrollToPosition(0)
//
//        // Check if transaction should be in filtered view
//        val shouldShow = when (currentFilter) {
//            "Income" -> type == "Income"
//            "Expense" -> type == "Expense"
//            else -> true
//        } && (searchQuery.isEmpty() ||
//                desc.contains(searchQuery, ignoreCase = true))
//
//        // Add to filtered list if it matches current filter/search
//        if (shouldShow) {
//            filteredList.add(0, transaction)
//            transactionAdapter.notifyItemInserted(0)
//            recyclerView.smoothScrollToPosition(0)
//        } else {
//            // Transaction added but not visible in current view
//            Toast.makeText(
//                this,
//                "$type added (not visible in current filter)",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//
//        updateBalance()
//        clearInputs()
//
////        saveTransactions()    //Optional to save after each transaction is added
//
//
//        Toast.makeText(this, "$type added successfully", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun updateBalance(){
//        totalIncome = transactionList.sumOf {
//            if (it.type == "Income") it.amount else 0.0
//        }
//        totalExpense = transactionList.sumOf {
//            if (it.type == "Expense") it.amount else 0.0
//        }
//
//        totalBalance = totalIncome - totalExpense
//
//        balanceTextView.text = String.format("$%.2f", totalBalance)
//        totalIncomeShow.text = String.format("$%.2f", totalIncome)
//        totalExpenseShow.text = String.format("$%.2f", totalExpense)
//    }
//
//    private fun clearInputs(){
//        descriptionInput.text?.clear()
//        amountInput.text?.clear()
//        spinner.setSelection(0)
//        radioGroup.clearCheck()
//    }
//}
//
//class TransactionAdapter(
//    private var transactions: MutableList<Transaction>,
//    private val filteredList: MutableList<Transaction>,
//    private val onDeleteClick: (Int) -> Unit  // Changed to only pass position
//) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>(){
//
//    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val description: TextView = itemView.findViewById(R.id.Description)
//        val date: TextView = itemView.findViewById(R.id.Date)
//        val category: TextView = itemView.findViewById(R.id.Category)
//        val amount: TextView = itemView.findViewById(R.id.Amount)
//        val deleteButton: Button = itemView.findViewById(R.id.Delete)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
//        val itemView = LayoutInflater.from(parent.context)
//            .inflate(R.layout.transaction_item, parent, false)
//        return TransactionViewHolder(itemView)
//    }
//
//    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
////        val transaction = transactions[position]
//        val transaction = filteredList[position]  //For filtered data
//        holder.description.text = transaction.description
//        holder.date.text = transaction.date
//        holder.category.text = transaction.category ?: ""
//        holder.amount.text = String.format("$%.2f", transaction.amount)
//
//        // CRITICAL FIX: Use holder.adapterPosition instead of position
//        holder.deleteButton.setOnClickListener {
//            val adapterPosition = holder.adapterPosition
//            if (adapterPosition != RecyclerView.NO_POSITION) {
////                onDeleteClick(adapterPosition)
//                val originalPosition = transactions.indexOf(filteredList[adapterPosition])
//                onDeleteClick(originalPosition)
//            }
//        }
//    }
//
//    override fun getItemCount(): Int {
////        return transactions.size
//        return filteredList.size
//    }
//
//    fun removeItem(position: Int){
//        if (position >= 0 && position < transactions.size) {
//
//            transactions.removeAt(position)
//            notifyItemRemoved(position)
//            notifyItemRangeChanged(position, transactions.size)
//        }
//    }
//
//    // NEW: Update filtered list
//    fun updateFilteredList(newList: List<Transaction>) {
//        filteredList.clear()
//        filteredList.addAll(newList)
//        notifyDataSetChanged()
//    }
//}