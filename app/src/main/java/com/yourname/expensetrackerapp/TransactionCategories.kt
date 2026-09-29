package com.yourname.expensetrackerapp

/** Shared category lists — used by Home's add-transaction form and the History screen's edit
 *  dialog, so both stay in sync with a single list. Position 0 in each is a "choose one"
 *  placeholder, not a real category. */
object TransactionCategories {
    val income = listOf("Select Income Category", "Salary", "Freelance", "Investment", "Gift")
    val expense = listOf(
        "Select Expense Category", "Food", "Transportation", "Entertainment",
        "Shopping", "Health", "Education", "Bills", "Other"
    )
}
