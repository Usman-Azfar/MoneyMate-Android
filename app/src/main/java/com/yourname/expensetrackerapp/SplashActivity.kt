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
    private val splashDuration = 4000L  // 4 seconds

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
        val appName = findViewById<TextView>(R.id.appName)
        val studentInfo = findViewById<TextView>(R.id.studentInfo)

        // Load animations from XML (we'll create these next)
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        val rotate = AnimationUtils.loadAnimation(this, R.anim.rotate)



        // Apply animations
        appLogo.startAnimation(fadeIn)
        appLogo.startAnimation(rotate)
        appName.startAnimation(slideUp)
        studentInfo.startAnimation(slideUp)
    }

    private fun navigateToMainActivity() {
        // Handler posts a delayed action to the main thread
        Handler(Looper.getMainLooper()).postDelayed({
            // Create an Intent to start MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            // Finish SplashActivity so user can't go back to it
            finish()
        }, splashDuration)
    }
}