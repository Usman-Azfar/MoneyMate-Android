package com.yourname.expensetrackerapp

import android.app.Activity
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Wraps Credential Manager's "Sign in with Google" flow, shared by Login and Sign Up so neither
 *  screen duplicates the boilerplate. Uses the callback-based entry point (not the suspend one)
 *  to match this app's plain-listener style rather than pulling in coroutines. */
object GoogleAuthHelper {
    fun signIn(activity: Activity, onResult: (Result<String>) -> Unit) {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            CancellationSignal(),
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    onResult(extractIdToken(result))
                }

                override fun onError(e: GetCredentialException) {
                    onResult(Result.failure(e))
                }
            }
        )
    }

    private fun extractIdToken(result: GetCredentialResponse): Result<String> {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException("Unexpected credential type from Credential Manager"))
    }
}
