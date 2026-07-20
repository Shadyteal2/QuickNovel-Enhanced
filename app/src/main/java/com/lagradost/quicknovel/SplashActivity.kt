package com.lagradost.quicknovel

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full screen setup
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.BLACK
        }

        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splash_logo)
        val glow = findViewById<View>(R.id.glow_pulse)
        val text = findViewById<View>(R.id.splash_text)

        // 1. Trigger AVD Animation
        logo.post {
            (logo.drawable as? Animatable)?.start()
        }

        // 2. Glow Pulse timing (coincides with drawing finish)
        glow.animate()
            .alpha(0.3f)
            .scaleX(1.15f)
            .scaleY(1.15f)
            .setDuration(300)
            .setStartDelay(300)
            .withEndAction {
                glow.animate()
                    .alpha(0f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(400)
                    .start()
            }
            .start()

        // 3. NeoQN Text Fade & Slide In at 450ms delay
        text.translationY = 30f
        text.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(450)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // 4. Forward to MainActivity (optimized to 1.1s total delay)
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtras(this.intent)
            startActivity(intent)
            finish()
            overridePendingTransition(R.anim.premium_enter, R.anim.premium_exit)
        }, 1100)
    }
}
