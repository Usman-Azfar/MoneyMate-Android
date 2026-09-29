package com.yourname.expensetrackerapp

import android.content.Context

/** Whether a voice-parsed transaction saves itself immediately or just pre-fills the form for a
 *  manual Add tap — see [MainActivity]'s applyParsedTransaction. Defaults to false (off): the
 *  form is always pre-filled for review first, matching the safety-first default the rest of the
 *  voice feature already follows. Manual (typed) entry is never affected by this setting — it
 *  always requires the Add button, regardless of this preference. */
object AutoAddVoicePrefs {
    private const val PREFS_NAME = "AutoAddVoicePrefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
