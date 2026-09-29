package com.yourname.expensetrackerapp

import android.view.View
import android.widget.Button

/** Wires the 0-9 + backspace buttons in `keypad_numeric.xml` to callbacks — shared by
 *  LockScreenActivity and PasscodeSetupActivity so both PIN-entry screens behave identically. */
object NumericKeypad {
    fun bind(root: View, onDigit: (Int) -> Unit, onBackspace: () -> Unit) {
        val ids = intArrayOf(
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9
        )
        ids.forEachIndexed { digit, id ->
            root.findViewById<Button>(id).setOnClickListener { onDigit(digit) }
        }
        root.findViewById<View>(R.id.keyBackspace).setOnClickListener { onBackspace() }
    }
}
