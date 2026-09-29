package com.yourname.expensetrackerapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SignUpActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var signUpBtn: Button
    private lateinit var googleSignInBtn: Button
    private lateinit var goToLoginText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        findViewById<TextView>(R.id.appNameBrand).text = BrandName.styled(this)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        signUpBtn = findViewById(R.id.signUpBtn)
        googleSignInBtn = findViewById(R.id.googleSignInBtn)
        goToLoginText = findViewById(R.id.goToLoginText)

        signUpBtn.setOnClickListener { attemptSignUp() }
        googleSignInBtn.setOnClickListener { signInWithGoogle() }
        goToLoginText.setOnClickListener { finish() }
    }

    private fun attemptSignUp() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        if (email.isEmpty() || password.length < 6) {
            Toast.makeText(this, R.string.error_enter_valid_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, R.string.error_passwords_dont_match, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        AuthRepository.signUp(email, password) { result ->
            result.onSuccess { user ->
                AuthRepository.sendEmailVerification(user) {
                    setLoading(false)
                    AuthRepository.signOut()
                    showVerificationSentDialog(user.email ?: email)
                }
            }.onFailure {
                setLoading(false)
                showError(it)
            }
        }
    }

    private fun showVerificationSentDialog(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_verify_email)
            .setMessage(getString(R.string.msg_verification_email_sent, email))
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok) { _, _ -> finish() }
            .show()
    }

    private fun signInWithGoogle() {
        setLoading(true)
        GoogleAuthHelper.signIn(this) { tokenResult ->
            tokenResult.onSuccess { idToken ->
                AuthRepository.signInWithGoogleIdToken(idToken) { result ->
                    setLoading(false)
                    result.onSuccess { user -> AuthFlow.completeSignIn(this, user) }
                        .onFailure { showError(it) }
                }
            }.onFailure {
                setLoading(false)
                showError(it)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        signUpBtn.isEnabled = !loading
        googleSignInBtn.isEnabled = !loading
    }

    private fun showError(throwable: Throwable) {
        Toast.makeText(this, throwable.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
    }
}
