package com.yourname.expensetrackerapp

import android.content.Context

/** Whether voice transcripts go to Gemini ([GeminiTransactionParser]) first, falling back to the
 *  offline [TransactionVoiceParser] if Gemini is unreachable, slow or returns nothing usable.
 *  Defaults to false (off), so the offline parser stays the default and nothing leaves the
 *  device unless the user opts in from Settings. */
object SmartVoicePrefs {
    private const val PREFS_NAME = "SmartVoicePrefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
