package com.yourname.expensetrackerapp

import android.content.Context
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat

/**
 * The app name ("MoneyMate"), styled as a two-tone "Money"/"Mate" [SpannableString] matching the
 * logo artwork's wordmark treatment. Always meant to sit on the fixed-white `bg_brand_chip`
 * background (see that drawable) rather than directly on a colored/gradient surface — brand_money
 * and brand_mate are themselves fixed colors (not theme-aware), since the chip they sit on is
 * fixed too. Used by LoginActivity, SignUpActivity, and MainActivity's toolbar.
 */
object BrandName {
    private const val MONEY = "Money"
    private const val MATE = "Mate"

    fun styled(context: Context): CharSequence {
        val moneyColor = ContextCompat.getColor(context, R.color.brand_money)
        val mateColor = ContextCompat.getColor(context, R.color.brand_mate)
        return SpannableString(MONEY + MATE).apply {
            setSpan(ForegroundColorSpan(moneyColor), 0, MONEY.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(mateColor), MONEY.length, MONEY.length + MATE.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
