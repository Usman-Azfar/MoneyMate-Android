package com.yourname.expensetrackerapp

import android.app.Activity
import android.content.Intent
import com.google.firebase.auth.FirebaseUser

/** The sequence every successful sign-in (email/password login, sign-up, or Google) runs before
 *  landing on Home: make sure the Firestore profile doc exists, drop any pre-login local data
 *  (per this app's "start clean" policy), pull this account's cloud data into the local cache,
 *  then navigate. A brand-new sign-up simply pulls back an empty transaction list. */
object AuthFlow {
    fun completeSignIn(activity: Activity, user: FirebaseUser) {
        UserRepository.ensureUserDocument(user)
        SyncManager.clearLocalData(activity)
        SyncManager.pullInitialData(activity, user.uid) {
            val intent = Intent(activity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            activity.startActivity(intent)
            activity.finish()
        }
    }
}
