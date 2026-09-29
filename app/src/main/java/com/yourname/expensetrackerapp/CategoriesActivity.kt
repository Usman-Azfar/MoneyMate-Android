package com.yourname.expensetrackerapp

import android.os.Bundle
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/** Lets the user add/remove their own Income and Expense categories. The "Select X Category"
 *  placeholder CategoryRepository seeds at index 0 is deliberately never shown here — it's
 *  filtered out of the displayed list, though the repository's own delete guard still refuses
 *  to remove it as a defensive backstop. */
class CategoriesActivity : AppCompatActivity() {

    private lateinit var typeGroup: RadioGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var newCategoryInput: EditText
    private lateinit var adapter: CategoryListAdapter

    private val displayedCategories = mutableListOf<String>()
    private var currentType = "Income"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        typeGroup = findViewById(R.id.typeToggleGroup)
        recyclerView = findViewById(R.id.recyclerView)
        newCategoryInput = findViewById(R.id.newCategoryInput)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CategoryListAdapter(displayedCategories) { position -> deleteCategory(position) }
        recyclerView.adapter = adapter

        currentType = intent.getStringExtra(EXTRA_TYPE)?.takeIf { it == "Expense" } ?: "Income"
        typeGroup.check(if (currentType == "Income") R.id.radioTypeIncome else R.id.radioTypeExpense)

        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            currentType = if (checkedId == R.id.radioTypeIncome) "Income" else "Expense"
            refreshList()
        }

        findViewById<MaterialButton>(R.id.addCategoryBtn).setOnClickListener { addCategory() }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
    }

    private fun refreshList() {
        val source = if (currentType == "Income") CategoryRepository.getIncome(this) else CategoryRepository.getExpense(this)
        displayedCategories.clear()
        displayedCategories.addAll(source.drop(1))
        adapter.notifyDataSetChanged()
    }

    private fun addCategory() {
        val name = newCategoryInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.error_category_blank, Toast.LENGTH_SHORT).show()
            return
        }
        val added = CategoryRepository.addCategory(this, currentType, name)
        if (!added) {
            Toast.makeText(this, R.string.toast_category_exists, Toast.LENGTH_SHORT).show()
            return
        }
        newCategoryInput.text?.clear()
        Toast.makeText(this, R.string.toast_category_added, Toast.LENGTH_SHORT).show()
        refreshList()
    }

    private fun deleteCategory(position: Int) {
        if (position < 0 || position >= displayedCategories.size) return
        val name = displayedCategories[position]
        val deleted = CategoryRepository.deleteCategory(this, currentType, name)
        if (!deleted) {
            Toast.makeText(this, R.string.error_category_min, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, R.string.toast_category_deleted, Toast.LENGTH_SHORT).show()
        refreshList()
    }

    companion object {
        const val EXTRA_TYPE = "type"
    }
}
