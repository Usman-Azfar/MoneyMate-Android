package com.yourname.expensetrackerapp

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists the user's explicit light/dark/system mode choice, made from Settings, so it sticks
 * across app restarts.
 */
object ThemePrefs {
    private const val PREFS_NAME = "ThemePrefs"
    private const val KEY_NIGHT_MODE = "night_mode"

    /** Call once, as early as possible (Application.onCreate), before any Activity inflates. */
    fun applySavedNightMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(currentMode(context))
    }

    /** The raw stored AppCompatDelegate.MODE_NIGHT_* value, defaulting to following the system. */
    fun currentMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    /** Persists [mode] and applies it immediately — the caller is responsible for recreating
     *  whichever Activity needs to repaint right away. */
    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NIGHT_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun isNightMode(context: Context): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val nightFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightFlags == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }
}
