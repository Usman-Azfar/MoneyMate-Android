package com.yourname.expensetrackerapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseUser

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginBtn: Button
    private lateinit var googleSignInBtn: Button
    private lateinit var forgotPasswordText: TextView
    private lateinit var goToSignUpText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        findViewById<TextView>(R.id.appNameBrand).text = BrandName.styled(this)

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginBtn = findViewById(R.id.loginBtn)
        googleSignInBtn = findViewById(R.id.googleSignInBtn)
        forgotPasswordText = findViewById(R.id.forgotPasswordText)
        goToSignUpText = findViewById(R.id.goToSignUpText)

        loginBtn.setOnClickListener { attemptLogin() }
        googleSignInBtn.setOnClickListener { signInWithGoogle() }
        forgotPasswordText.setOnClickListener { showForgotPasswordDialog() }
        goToSignUpText.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (email.isEmpty() || password.length < 6) {
            Toast.makeText(this, R.string.error_enter_valid_credentials, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        AuthRepository.signIn(email, password) { result ->
            result.onSuccess { user ->
                user.reload().addOnCompleteListener {
                    setLoading(false)
                    if (user.isEmailVerified) {
                        AuthFlow.completeSignIn(this, user)
                    } else {
                        AuthRepository.signOut()
                        showUnverifiedEmailDialog(user)
                    }
                }
            }.onFailure {
                setLoading(false)
                showError(it)
            }
        }
    }

    private fun showUnverifiedEmailDialog(user: FirebaseUser) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_verify_email)
            .setMessage(getString(R.string.msg_email_not_verified, user.email))
            .setPositiveButton(R.string.btn_resend_verification) { _, _ ->
                AuthRepository.sendEmailVerification(user) { result ->
                    result.onSuccess {
                        Toast.makeText(this, R.string.toast_verification_resent, Toast.LENGTH_LONG).show()
                    }.onFailure { showError(it) }
                }
            }
            .setNegativeButton(R.string.btn_ok, null)
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

    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val input = dialogView.findViewById<EditText>(R.id.resetEmailInput)
        input.setText(emailInput.text.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_reset_password)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_send_reset_email) { _, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, R.string.error_enter_email, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AuthRepository.sendPasswordReset(email) { result ->
                    result.onSuccess {
                        Toast.makeText(this, R.string.toast_reset_email_sent, Toast.LENGTH_LONG).show()
                    }.onFailure { showError(it) }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        loginBtn.isEnabled = !loading
        googleSignInBtn.isEnabled = !loading
    }

    private fun showError(throwable: Throwable) {
        Toast.makeText(this, throwable.message ?: "Something went wrong", Toast.LENGTH_LONG).show()
    }
}
