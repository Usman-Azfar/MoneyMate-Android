package com.yourname.expensetrackerapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** One reusable PIN-entry flow for every passcode-setup scenario Settings can trigger:
 *  - [Mode.SET_NEW]: turning App Lock on for the first time — go straight to picking a passcode.
 *  - [Mode.CHANGE]: verify the current passcode first, then pick a new one.
 *  - [Mode.DISABLE]: verify the current passcode, then turn App Lock off entirely.
 *  Finishes with RESULT_OK once its job is done, RESULT_CANCELED if the user backs out. */
class PasscodeSetupActivity : AppCompatActivity() {

    enum class Mode { SET_NEW, CHANGE, DISABLE }
    private enum class Step { VERIFY_OLD, ENTER_NEW, CONFIRM_NEW }

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var errorText: TextView

    private val enteredDigits = StringBuilder()
    private var firstNewPasscode: String? = null
    private lateinit var mode: Mode
    private lateinit var step: Step

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode_setup)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        titleText = findViewById(R.id.setupTitleText)
        subtitleText = findViewById(R.id.setupSubtitleText)
        errorText = findViewById(R.id.setupErrorText)

        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.SET_NEW.name)
        step = if (mode == Mode.SET_NEW) Step.ENTER_NEW else Step.VERIFY_OLD

        NumericKeypad.bind(
            findViewById(android.R.id.content),
            onDigit = { digit -> onDigitEntered(digit) },
            onBackspace = { onBackspace() }
        )

        updateUiForStep()
    }

    private fun updateUiForStep() {
        supportActionBar?.title = getString(R.string.label_app_lock)
        titleText.text = when (step) {
            Step.VERIFY_OLD -> getString(R.string.title_current_passcode)
            Step.ENTER_NEW -> getString(R.string.title_create_passcode)
            Step.CONFIRM_NEW -> getString(R.string.title_confirm_passcode)
        }
        subtitleText.text = when (step) {
            Step.VERIFY_OLD -> if (mode == Mode.DISABLE) {
                getString(R.string.subtitle_current_passcode_disable)
            } else {
                getString(R.string.subtitle_current_passcode_change)
            }
            Step.ENTER_NEW -> getString(R.string.subtitle_create_passcode, getString(R.string.app_display_name))
            Step.CONFIRM_NEW -> getString(R.string.subtitle_confirm_passcode)
        }
        errorText.visibility = View.INVISIBLE
    }

    private fun onDigitEntered(digit: Int) {
        if (enteredDigits.length >= AppLockPrefs.PASSCODE_LENGTH) return
        enteredDigits.append(digit)
        renderDots()
        if (enteredDigits.length == AppLockPrefs.PASSCODE_LENGTH) {
            handleStepComplete()
        }
    }

    private fun onBackspace() {
        if (enteredDigits.isNotEmpty()) {
            enteredDigits.deleteCharAt(enteredDigits.length - 1)
            renderDots()
        }
    }

    private fun renderDots() {
        PinDots.render(findViewById(android.R.id.content), enteredDigits.length)
    }

    private fun handleStepComplete() {
        val value = enteredDigits.toString()
        when (step) {
            Step.VERIFY_OLD -> {
                if (AppLockPrefs.verifyPasscode(this, value)) {
                    if (mode == Mode.DISABLE) {
                        AppLockPrefs.disable(this)
                        Toast.makeText(this, R.string.toast_app_lock_disabled, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        step = Step.ENTER_NEW
                        clearEntry()
                        updateUiForStep()
                    }
                } else {
                    enteredDigits.clear()
                    renderDots()
                    showError(R.string.error_passcode_incorrect)
                }
            }
            Step.ENTER_NEW -> {
                firstNewPasscode = value
                step = Step.CONFIRM_NEW
                clearEntry()
                updateUiForStep()
            }
            Step.CONFIRM_NEW -> {
                if (value == firstNewPasscode) {
                    AppLockPrefs.setPasscode(this, value)
                    Toast.makeText(
                        this,
                        if (mode == Mode.CHANGE) R.string.toast_passcode_changed else R.string.toast_passcode_set,
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    firstNewPasscode = null
                    step = Step.ENTER_NEW
                    clearEntry()
                    updateUiForStep()
                    showError(R.string.error_passcode_mismatch)
                }
            }
        }
    }

    private fun clearEntry() {
        enteredDigits.clear()
        renderDots()
    }

    private fun showError(messageRes: Int) {
        errorText.setText(messageRes)
        errorText.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_MODE = "mode"
    }
}
