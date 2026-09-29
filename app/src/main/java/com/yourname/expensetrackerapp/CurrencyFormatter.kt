package com.yourname.expensetrackerapp

import android.content.Context
import java.util.Locale

/** Single choke point for turning an amount into display text with the user's selected currency
 *  symbol — every screen and the PDF report format amounts through this instead of hardcoding "$". */
object CurrencyFormatter {
    fun symbol(context: Context): String = CurrencyPrefs.getSelectedCurrency(context).symbol

    fun format(context: Context, amount: Double): String =
        String.format(Locale.getDefault(), "%s%.2f", symbol(context), amount)
}
