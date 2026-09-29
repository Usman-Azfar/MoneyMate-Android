package com.yourname.expensetrackerapp

import android.view.View
import android.widget.ImageView

/** Fills/empties the four dots in `pin_dots_row.xml` to reflect how many digits of a
 *  [AppLockPrefs.PASSCODE_LENGTH]-digit passcode have been entered so far. */
object PinDots {
    fun render(root: View, filledCount: Int) {
        val ids = intArrayOf(R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4)
        ids.forEachIndexed { index, id ->
            root.findViewById<ImageView>(id).setImageResource(
                if (index < filledCount) R.drawable.bg_pin_dot_filled else R.drawable.bg_pin_dot_empty
            )
        }
    }
}
