package com.cardcopyautomat.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.cardcopyautomat.app.databinding.ActivitySplashBinding
import kotlin.random.Random

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()

        // Navigate to MainActivity after 2.5 seconds
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }

    private fun setupAnimations() {
        // 1. Loading bar progress (0 to 100 over 2.5s)
        val progressAnimator = ObjectAnimator.ofInt(binding.splashProgressBar, "progress", 0, 100)
        progressAnimator.duration = 2500
        progressAnimator.interpolator = LinearInterpolator()
        progressAnimator.start()

        // 2. Continuous moving scanline
        binding.scanline.visibility = View.VISIBLE
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val scanlineAnimator = ObjectAnimator.ofFloat(binding.scanline, "translationY", -100f, screenHeight)
        scanlineAnimator.duration = 1500
        scanlineAnimator.repeatCount = ValueAnimator.INFINITE
        scanlineAnimator.interpolator = LinearInterpolator()
        scanlineAnimator.start()

        // 3. Glitch & Chromatic Aberration loop
        startGlitchLoop()
    }

    private fun startGlitchLoop() {
        val glitchRunnable = object : Runnable {
            override fun run() {
                // Randomly trigger a glitch
                val glitchDuration = Random.nextLong(50, 150)
                
                // Screen shake
                binding.mainContent.translationX = Random.nextInt(-8, 8).toFloat()
                binding.mainContent.translationY = Random.nextInt(-4, 4).toFloat()
                binding.mainContent.alpha = Random.nextFloat() * 0.2f + 0.8f
                
                // Reset after glitchDuration
                handler.postDelayed({
                    binding.mainContent.translationX = 0f
                    binding.mainContent.translationY = 0f
                    binding.mainContent.alpha = 1f
                }, glitchDuration)

                // Schedule next glitch at random interval
                handler.postDelayed(this, Random.nextLong(200, 1000))
            }
        }
        handler.post(glitchRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up handler to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }
}
