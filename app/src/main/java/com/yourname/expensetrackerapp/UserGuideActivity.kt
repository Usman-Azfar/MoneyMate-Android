package com.yourname.expensetrackerapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Static, informational — every section's copy already lives in strings.xml, so there's no
 *  adapter or data source, just wiring each included card to its title/body pair. */
class UserGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_guide)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        bindSection(R.id.section1, R.string.guide_title_add, R.string.guide_body_add)
        bindSection(R.id.section2, R.string.guide_title_edit, R.string.guide_body_edit)
        bindSection(R.id.section3, R.string.guide_title_history, R.string.guide_body_history)
        bindSection(R.id.section4, R.string.guide_title_statistics, R.string.guide_body_statistics)
        bindSection(R.id.section5, R.string.guide_title_categories, R.string.guide_body_categories)
        bindSection(R.id.section6, R.string.guide_title_currency, R.string.guide_body_currency)
        bindSection(R.id.section7, R.string.guide_title_notifications, R.string.guide_body_notifications)
        bindSection(R.id.section8, R.string.guide_title_sync, R.string.guide_body_sync)
        bindSection(R.id.section9, R.string.guide_title_debts, R.string.guide_body_debts)
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.guard(this)
    }

    private fun bindSection(sectionId: Int, titleRes: Int, bodyRes: Int) {
        val section = findViewById<View>(sectionId)
        section.findViewById<TextView>(R.id.guideSectionTitle).setText(titleRes)
        section.findViewById<TextView>(R.id.guideSectionBody).setText(bodyRes)
    }
}
