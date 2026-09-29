package com.yourname.expensetrackerapp

import android.app.Application

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.applySavedNightMode(this)
        NotificationHelper.createChannel(this)
        SyncManager.registerConnectivityListener(this)
        registerActivityLifecycleCallbacks(AppLockManager)
    }
}
