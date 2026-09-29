package com.yourname.expensetrackerapp

import android.content.Context

object CurrencyPrefs {
    private const val PREFS_NAME = "CurrencyPrefs"
    private const val KEY_CODE = "currency_code"
    private const val DEFAULT_CODE = "USD"

    fun getSelectedCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CODE, DEFAULT_CODE) ?: DEFAULT_CODE
    }

    fun setSelectedCode(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, code)
            .apply()
    }

    fun getSelectedCurrency(context: Context): Currency {
        val code = getSelectedCode(context)
        return CurrencyList.all.firstOrNull { it.code == code }
            ?: CurrencyList.all.first { it.code == DEFAULT_CODE }
    }
}
