package com.yourname.expensetrackerapp

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/** Thin callback-style wrapper around FirebaseAuth, matching this app's plain-listener style
 *  elsewhere rather than introducing coroutines just for this. */
object AuthRepository {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun signIn(email: String, password: String, onResult: (Result<FirebaseUser>) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) onResult(Result.success(user))
                else onResult(Result.failure(IllegalStateException("Sign-in succeeded but no user was returned")))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signUp(email: String, password: String, onResult: (Result<FirebaseUser>) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) onResult(Result.success(user))
                else onResult(Result.failure(IllegalStateException("Sign-up succeeded but no user was returned")))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signInWithGoogleIdToken(idToken: String, onResult: (Result<FirebaseUser>) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) onResult(Result.success(user))
                else onResult(Result.failure(IllegalStateException("Google sign-in succeeded but no user was returned")))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun sendEmailVerification(user: FirebaseUser, onResult: (Result<Unit>) -> Unit) {
        user.sendEmailVerification()
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun signOut() {
        auth.signOut()
    }

    /** True if this account signs in with an email/password credential — used to decide whether
     *  the "forgot passcode" flow should ask for a password or offer "Continue with Google". */
    fun hasPasswordProvider(user: FirebaseUser): Boolean =
        user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

    fun reauthenticateWithPassword(user: FirebaseUser, password: String, onResult: (Result<Unit>) -> Unit) {
        val email = user.email
        if (email == null) {
            onResult(Result.failure(IllegalStateException("This account has no email on file")))
            return
        }
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }

    fun reauthenticateWithGoogleIdToken(user: FirebaseUser, idToken: String, onResult: (Result<Unit>) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        user.reauthenticate(credential)
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
