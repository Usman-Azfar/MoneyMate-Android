package com.yourname.expensetrackerapp

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/** The gate App Lock shows in front of everything else — see [AppLockManager] for when it gets
 *  launched. Finishing this Activity (only ever done after a correct passcode, a successful
 *  fingerprint, or a verified passcode reset) reveals whatever screen was underneath, so the
 *  back button is disabled here: it backgrounds the whole app instead of bypassing the lock. */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var subtitleText: TextView
    private lateinit var errorText: TextView
    private lateinit var biometricKey: ImageButton
    private lateinit var forgotPasscodeText: TextView

    private val enteredDigits = StringBuilder()
    private var failedAttempts = 0
    private var lockoutTimer: CountDownTimer? = null

    private val resetLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            AppLockManager.markUnlocked()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        subtitleText = findViewById(R.id.lockSubtitleText)
        errorText = findViewById(R.id.lockErrorText)
        biometricKey = findViewById(R.id.keyBiometric)
        forgotPasscodeText = findViewById(R.id.forgotPasscodeText)

        AuthRepository.currentUser?.email?.let {
            subtitleText.text = getString(R.string.label_signed_in_as, it)
        }

        NumericKeypad.bind(
            findViewById(android.R.id.content),
            onDigit = { digit -> onDigitEntered(digit) },
            onBackspace = { onBackspace() }
        )

        if (canUseBiometric()) {
            biometricKey.visibility = View.VISIBLE
            biometricKey.setOnClickListener { showBiometricPrompt() }
        }

        forgotPasscodeText.setOnClickListener { showForgotPasscodeConfirm() }

        renderDots()
    }

    override fun onResume() {
        super.onResume()
        if (canUseBiometric() && enteredDigits.isEmpty() && failedAttempts == 0) {
            showBiometricPrompt()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
    }

    /** Backgrounds the app instead of letting the back button dismiss the lock screen. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun canUseBiometric(): Boolean {
        if (!AppLockPrefs.isBiometricEnabled(this)) return false
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLockManager.markUnlocked()
                finish()
            }
            // Errors (including user cancellation) just fall back to the PIN pad — no toast needed,
            // the passcode is always right there as the primary path.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            override fun onAuthenticationFailed() {}
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title, getString(R.string.app_display_name)))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.title_enter_passcode))
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun onDigitEntered(digit: Int) {
        if (lockoutTimer != null) return
        if (enteredDigits.length >= AppLockPrefs.PASSCODE_LENGTH) return
        enteredDigits.append(digit)
        renderDots()
        if (enteredDigits.length == AppLockPrefs.PASSCODE_LENGTH) {
            verifyEnteredPasscode()
        }
    }

    private fun onBackspace() {
        if (lockoutTimer != null) return
        if (enteredDigits.isNotEmpty()) {
            enteredDigits.deleteCharAt(enteredDigits.length - 1)
            renderDots()
            errorText.visibility = View.INVISIBLE
        }
    }

    private fun renderDots() {
        PinDots.render(findViewById(android.R.id.content), enteredDigits.length)
    }

    private fun verifyEnteredPasscode() {
        val passcode = enteredDigits.toString()
        if (AppLockPrefs.verifyPasscode(this, passcode)) {
            AppLockManager.markUnlocked()
            finish()
            return
        }

        failedAttempts++
        enteredDigits.clear()
        renderDots()
        errorText.visibility = View.VISIBLE
        errorText.text = getString(R.string.error_passcode_incorrect)

        if (failedAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            startLockout()
        }
    }

    private fun startLockout() {
        var secondsLeft = LOCKOUT_SECONDS
        errorText.text = getString(R.string.error_passcode_locked_out, secondsLeft)
        lockoutTimer = object : CountDownTimer(LOCKOUT_SECONDS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000L).toInt() + 1
                errorText.text = getString(R.string.error_passcode_locked_out, secondsLeft)
            }
            override fun onFinish() {
                lockoutTimer = null
                failedAttempts = 0
                errorText.visibility = View.INVISIBLE
            }
        }.start()
    }

    private fun showForgotPasscodeConfirm() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_reset_passcode)
            .setMessage(R.string.msg_reset_passcode_intro)
            .setPositiveButton(R.string.btn_verify_and_continue) { _, _ ->
                resetLauncher.launch(android.content.Intent(this, PasscodeResetActivity::class.java))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    companion object {
        private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val LOCKOUT_SECONDS = 30
    }
}
