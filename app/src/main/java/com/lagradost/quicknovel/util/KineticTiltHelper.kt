package com.lagradost.quicknovel.util

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.R

/**
 * Kinetic Evolution v4: Premium 3D Touch tracking.
 * Includes user-toggleable state, battery-friendly tracking,
 * and buttery smooth ViewPropertyAnimator chasing for MOVE events.
 */
object KineticTiltHelper {
    private const val MAX_TILT_TOUCH = 7.5f // Adjusted for subtle, premium feel
    private val interpolator = DecelerateInterpolator(1.5f)

    /**
     * Applies the tactile 3D tilt effect to a specific view.
     */
    fun applyKineticTilt(view: View) {
        view.setOnTouchListener { v, event ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(v.context)
            if (!prefs.getBoolean("library_tactile_response", true)) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val centerX = v.width / 2f
                    val centerY = v.height / 2f
                    val touchX = event.x
                    val touchY = event.y

                    val percentX = ((touchX - centerX) / centerX).coerceIn(-1f, 1f)
                    val percentY = ((touchY - centerY) / centerY).coerceIn(-1f, 1f)

                    // Use swift ViewPropertyAnimator to smooth jagged ACTION_MOVE events
                    v.animate()
                        .rotationY(percentX * MAX_TILT_TOUCH)
                        .rotationX(-percentY * MAX_TILT_TOUCH)
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(80) // 80ms is perfect for fluid tracking without lagging behind
                        .setInterpolator(interpolator)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .rotationX(0f)
                        .rotationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(400) // Slightly longer release for a soft drop
                        .setInterpolator(interpolator)
                        .start()
                }
            }
            false
        }
    }
}
