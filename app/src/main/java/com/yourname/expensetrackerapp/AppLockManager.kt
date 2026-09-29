package com.yourname.expensetrackerapp

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Tracks whether the whole app — not just one of its own Activities swapping for another — is
 *  currently in the background, using plain Application.ActivityLifecycleCallbacks. No extra
 *  lifecycle-process dependency needed: the started/stopped counter hitting zero/one is the
 *  standard way to detect a real backgrounding versus an in-app Activity transition.
 *
 *  When the app comes back to the foreground (or is cold-started) with App Lock enabled, the
 *  next screen to resume shows LockScreenActivity on top of itself via [guard] before anything
 *  underneath is usable. */
object AppLockManager : Application.ActivityLifecycleCallbacks {
    private var startedActivityCount = 0
    private var wasFullyStopped = false

    /** True once the user has unlocked for this "session" — reset to false whenever the app is
     *  backgrounded (with the lock enabled) so returning to it always demands another unlock. */
    private var isUnlocked = false

    fun markUnlocked() {
        isUnlocked = true
    }

    /** Call from onResume of every screen a user could land back on directly (from Recents, or
     *  after the lock screen finishes) — shows the lock screen on top if App Lock is on and this
     *  session hasn't been unlocked yet. */
    fun guard(activity: AppCompatActivity) {
        if (activity is LockScreenActivity) return
        if (!AppLockPrefs.isEnabled(activity)) return
        if (isUnlocked) return
        activity.startActivity(Intent(activity, LockScreenActivity::class.java))
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        if (startedActivityCount == 1 && wasFullyStopped) {
            wasFullyStopped = false
            if (AppLockPrefs.isEnabled(activity)) {
                isUnlocked = false
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount == 0) {
            wasFullyStopped = true
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
