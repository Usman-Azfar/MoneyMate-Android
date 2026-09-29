package com.yourname.expensetrackerapp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * User-editable Income/Expense category lists, seeded once from [TransactionCategories]'
 * defaults (including the "Select X Category" placeholder at index 0, so callers never need to
 * special-case it) and stored locally from then on. Mirrors [TransactionRepository]'s
 * SharedPreferences+Gson shape.
 */
object CategoryRepository {
    private const val PREFS_NAME = "CategoryPrefs"
    private const val KEY_INCOME = "income_categories"
    private const val KEY_EXPENSE = "expense_categories"

    private val gson = Gson()

    fun getIncome(context: Context): List<String> = getList(context, KEY_INCOME, TransactionCategories.income)

    fun getExpense(context: Context): List<String> = getList(context, KEY_EXPENSE, TransactionCategories.expense)

    /** Returns false (and adds nothing) if [name] is blank or already present, case-insensitively. */
    fun addCategory(context: Context, type: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val key = keyFor(type)
        val current = getList(context, key, defaultsFor(type))
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        saveList(context, key, current + trimmed)
        return true
    }

    /** Returns false (and deletes nothing) for the placeholder, or when it would leave zero real
     *  categories behind (list size <= 2, since index 0 is always the placeholder). */
    fun deleteCategory(context: Context, type: String, name: String): Boolean {
        val key = keyFor(type)
        val current = getList(context, key, defaultsFor(type))
        if (current.indexOf(name) == 0) return false
        if (current.size <= 2) return false
        saveList(context, key, current.filterNot { it == name })
        return true
    }

    private fun keyFor(type: String) = if (type == "Income") KEY_INCOME else KEY_EXPENSE

    private fun defaultsFor(type: String) =
        if (type == "Income") TransactionCategories.income else TransactionCategories.expense

    private fun getList(context: Context, key: String, seedDefaults: List<String>): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key, null)
        if (json == null) {
            saveList(context, key, seedDefaults)
            return seedDefaults
        }
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: seedDefaults
    }

    private fun saveList(context: Context, key: String, list: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }
}
