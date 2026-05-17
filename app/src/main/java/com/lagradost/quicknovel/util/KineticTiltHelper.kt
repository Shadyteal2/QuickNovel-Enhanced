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

    private var cachedTactileEnabled: Boolean = true

    /**
     * QN-Enhanced: Scroll-Aware Lock.
     * When true, tilt animations are entirely bypassed to prioritize UI thread for recycling.
     */
    var isLocked: Boolean = false

    /**
     * Applies the tactile 3D tilt effect to a specific view.
     */
    fun applyKineticTilt(view: View) {
        view.setOnTouchListener { v, event ->
            if (isLocked) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // QN-Enhanced Optimization: Read settings on touch start, NOT on move.
                    // This prevents thousands of disk reads per second on low-end devices.
                    cachedTactileEnabled = PreferenceManager.getDefaultSharedPreferences(v.context)
                        .getBoolean("library_tactile_response", true)
                    
                    if (!cachedTactileEnabled) return@setOnTouchListener false

                    val centerX = v.width / 2f
                    val centerY = v.height / 2f
                    val touchX = event.x
                    val touchY = event.y

                    val percentX = ((touchX - centerX) / centerX).coerceIn(-1f, 1f)
                    val percentY = ((touchY - centerY) / centerY).coerceIn(-1f, 1f)

                    v.animate()
                        .rotationY(percentX * MAX_TILT_TOUCH)
                        .rotationX(-percentY * MAX_TILT_TOUCH)
                        .scaleX(0.98f) // QN-Enhanced: 0.98 is more premium than 0.96
                        .scaleY(0.98f)
                        .setDuration(120)
                        .setInterpolator(interpolator)
                        .start()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!cachedTactileEnabled) return@setOnTouchListener false

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
                        .scaleX(0.98f) 
                        .scaleY(0.98f)
                        .setDuration(80) // 80ms for fluid tracking
                        .setInterpolator(interpolator)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .rotationX(0f)
                        .rotationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(400) // Soft drop
                        .setInterpolator(interpolator)
                        .start()
                }
            }
            false
        }
    }
}
