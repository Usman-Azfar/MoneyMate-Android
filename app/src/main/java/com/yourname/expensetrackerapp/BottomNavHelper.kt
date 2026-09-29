package com.yourname.expensetrackerapp

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Identical bottom-nav wiring shared by Home, History, Statistics and Notifications. */
object BottomNavHelper {
    fun setup(activity: Activity, bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                return@setOnItemSelectedListener true
            }
            val targetClass = when (item.itemId) {
                R.id.nav_home -> MainActivity::class.java
                R.id.nav_history -> TransactionsActivity::class.java
                R.id.nav_statistics -> StatisticsActivity::class.java
                R.id.nav_notifications -> NotificationsActivity::class.java
                R.id.nav_settings -> SettingsActivity::class.java
                else -> null
            }
            if (targetClass != null) {
                val intent = Intent(activity, targetClass)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                activity.startActivity(intent)
            }
            // Returning false keeps this screen's own nav item checked instead of letting the
            // tapped item show as selected here too — FLAG_ACTIVITY_REORDER_TO_FRONT reuses the
            // target Activity instance without re-running onCreate, so if this view's internal
            // checked state changed to the tapped item, it would stay wrong the next time this
            // screen is brought back to front.
            false
        }
    }
}
