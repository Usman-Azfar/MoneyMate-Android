package com.yourname.expensetrackerapp

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/** Local, device-level app lock — independent of which Firebase account happens to be signed in,
 *  much like the phone's own lock screen. The passcode itself is never stored, only a salted
 *  SHA-256 hash, so reading this file's raw contents doesn't reveal it. This guards local app
 *  access, not the underlying data (already sitting in plain SharedPreferences regardless), so a
 *  simple salted hash is a proportionate amount of protection for what it's actually gating. */
object AppLockPrefs {
    private const val PREFS_NAME = "AppLockPrefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HASH = "passcode_hash"
    private const val KEY_SALT = "passcode_salt"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    const val PASSCODE_LENGTH = 4

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun isBiometricEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /** Sets a new passcode and turns the lock on — used both for the first-time setup and for
     *  "Change Passcode" (after the old one has already been verified). */
    fun setPasscode(context: Context, passcode: String) {
        val salt = generateSalt()
        prefs(context).edit()
            .putString(KEY_SALT, salt)
            .putString(KEY_HASH, hash(passcode, salt))
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }

    fun verifyPasscode(context: Context, passcode: String): Boolean {
        val salt = prefs(context).getString(KEY_SALT, null) ?: return false
        val storedHash = prefs(context).getString(KEY_HASH, null) ?: return false
        return hash(passcode, salt) == storedHash
    }

    /** Turns the lock off and forgets the passcode entirely. */
    fun disable(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH)
            .remove(KEY_SALT)
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(passcode: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        val bytes = digest.digest(passcode.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
