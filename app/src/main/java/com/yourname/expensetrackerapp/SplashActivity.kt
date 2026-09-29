package com.yourname.expensetrackerapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    // Splash screen display duration in milliseconds
    private val splashDuration = 4000L  // Splash screen display duration in milliseconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Optional: Add animations to elements
        addAnimations()

        // Navigate to MainActivity after delay
        navigateToMainActivity()
    }

    private fun addAnimations() {
        // Get references to views
        val appLogo = findViewById<ImageView>(R.id.appLogo)
        val appTagline = findViewById<TextView>(R.id.appTagline)

        // Load animations from XML
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        // Apply animations
        appLogo.startAnimation(fadeIn)
        appTagline.startAnimation(slideUp)
    }

    private fun navigateToMainActivity() {
        // Handler posts a delayed action to the main thread
        Handler(Looper.getMainLooper()).postDelayed({
            val targetClass = if (AuthRepository.currentUser != null) {
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }
            startActivity(Intent(this, targetClass))

            // Finish SplashActivity so user can't go back to it
            finish()
        }, splashDuration)
    }
}