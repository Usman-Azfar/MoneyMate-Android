package com.yourname.expensetrackerapp

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class NotificationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        bottomNav = findViewById(R.id.bottomNav)
        recyclerView.layoutManager = LinearLayoutManager(this)

        BottomNavHelper.setup(this, bottomNav, R.id.nav_notifications)
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
        refreshList()
    }

    private fun refreshList() {
        val entries = NotificationRepository.getAll(this)
        recyclerView.adapter = NotificationAdapter(entries)

        if (entries.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.notifications_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_all -> {
                NotificationRepository.clearAll(this)
                refreshList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
