package com.yourname.expensetrackerapp

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/** Creates/refreshes the per-user profile document at users/{uid} — separate from the
 *  transactions subcollection underneath it. Safe to call on every sign-in: merge means it
 *  never clobbers fields on a returning user, and creates them on a brand-new one. */
object UserRepository {
    fun ensureUserDocument(user: FirebaseUser) {
        val doc = FirebaseFirestore.getInstance().collection("users").document(user.uid)
        val data = mapOf(
            "email" to user.email,
            "displayName" to user.displayName,
            "lastSignInAt" to System.currentTimeMillis()
        )
        doc.set(data, SetOptions.merge())
    }

    /** Keeps the Firestore profile doc's displayName in sync after a Settings-driven name edit —
     *  ensureUserDocument only runs at sign-in, so without this the two would drift apart. */
    fun updateDisplayName(uid: String, name: String) {
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid)
        doc.set(mapOf("displayName" to name), SetOptions.merge())
    }
}
