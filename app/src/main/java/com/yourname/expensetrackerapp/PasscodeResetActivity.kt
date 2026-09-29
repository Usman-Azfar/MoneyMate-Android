package com.yourname.expensetrackerapp

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** "Forgot Passcode?" lands here: proves it's really the account owner via the same credential
 *  they sign in with (password re-auth, or a fresh Google sign-in), then hands off to
 *  [PasscodeSetupActivity] to pick a brand new passcode. Resetting the passcode is only possible
 *  by re-proving account ownership — there's no "just clear it" shortcut. */
class PasscodeResetActivity : AppCompatActivity() {

    private lateinit var passwordInputLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var passwordInput: EditText
    private lateinit var verifyBtn: MaterialButton
    private lateinit var googleBtn: MaterialButton

    private val setupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passcode_reset)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.resetEmailText).text =
            AuthRepository.currentUser?.email?.let { getString(R.string.label_signed_in_as, it) } ?: ""

        passwordInputLayout = findViewById(R.id.resetPasswordInputLayout)
        passwordInput = findViewById(R.id.resetPasswordInput)
        verifyBtn = findViewById(R.id.resetVerifyBtn)
        googleBtn = findViewById(R.id.resetGoogleBtn)

        val user = AuthRepository.currentUser
        val hasPassword = user != null && AuthRepository.hasPasswordProvider(user)

        passwordInputLayout.visibility = if (hasPassword) android.view.View.VISIBLE else android.view.View.GONE
        verifyBtn.visibility = if (hasPassword) android.view.View.VISIBLE else android.view.View.GONE
        googleBtn.visibility = if (hasPassword) android.view.View.GONE else android.view.View.VISIBLE

        verifyBtn.setOnClickListener { verifyWithPassword() }
        googleBtn.setOnClickListener { verifyWithGoogle() }
    }

    private fun verifyWithPassword() {
        val user = AuthRepository.currentUser ?: return
        val password = passwordInput.text.toString()
        if (password.isEmpty()) {
            Toast.makeText(this, R.string.error_enter_valid_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        AuthRepository.reauthenticateWithPassword(user, password) { result ->
            setLoading(false)
            result.onSuccess { onVerified() }
                .onFailure { showError(it) }
        }
    }

    private fun verifyWithGoogle() {
        setLoading(true)
        GoogleAuthHelper.signIn(this) { tokenResult ->
            tokenResult.onSuccess { idToken ->
                val user = AuthRepository.currentUser
                if (user == null) {
                    setLoading(false)
                    return@onSuccess
                }
                AuthRepository.reauthenticateWithGoogleIdToken(user, idToken) { result ->
                    setLoading(false)
                    result.onSuccess { onVerified() }
                        .onFailure { showError(it) }
                }
            }.onFailure {
                setLoading(false)
                showError(it)
            }
        }
    }

    private fun onVerified() {
        setupLauncher.launch(
            Intent(this, PasscodeSetupActivity::class.java)
                .putExtra(PasscodeSetupActivity.EXTRA_MODE, PasscodeSetupActivity.Mode.SET_NEW.name)
        )
    }

    private fun setLoading(loading: Boolean) {
        verifyBtn.isEnabled = !loading
        googleBtn.isEnabled = !loading
    }

    private fun showError(throwable: Throwable) {
        Toast.makeText(this, throwable.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
    }
}
