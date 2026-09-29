package com.yourname.expensetrackerapp

import android.content.Context

/** The user's default export format, set once in Settings so future downloads (from Transaction
 *  History) skip the "PDF or CSV?" prompt. Unset (null) means "always ask" — the original,
 *  unconfigured behavior. */
object ReportFormatPrefs {
    private const val PREFS_NAME = "ReportFormatPrefs"
    private const val KEY_FORMAT = "default_format"

    enum class Format { PDF, CSV }

    fun getDefault(context: Context): Format? {
        val name = prefs(context).getString(KEY_FORMAT, null) ?: return null
        return try {
            Format.valueOf(name)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun setDefault(context: Context, format: Format?) {
        prefs(context).edit().apply {
            if (format == null) remove(KEY_FORMAT) else putString(KEY_FORMAT, format.name)
        }.apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
