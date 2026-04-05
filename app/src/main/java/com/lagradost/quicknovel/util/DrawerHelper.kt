package com.lagradost.quicknovel.util

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Helper to handle the premium "Vaul-style" background scaling and blur animations
 * for BottomSheetDialogs.
 */
object DrawerHelper {

    /**
     * Applies scaling and blur animation to a background view based on bottom sheet slide offset.
     * 
     * @param backgroundView The view to animate (e.g., the main root layout)
     * @param slideOffset The offset from BottomSheetBehavior.BottomSheetCallback (0.0L to 1.0L)
     */
    fun applyScalingAnimation(backgroundView: View?, slideOffset: Float) {
        if (backgroundView == null) return
        
        // Ensure slideOffset is within 0..1 for UI calculations
        val offset = slideOffset.coerceIn(0f, 1f)
        
        // 1. Scale from 1.00 down to 0.96 (4% reduction for premium feel)
        val scale = 1.0f - (offset * 0.04f)
        backgroundView.scaleX = scale
        backgroundView.scaleY = scale
        
        // 2. Apply Blur (Real for API 31+, Fake with Alpha for older)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val blurRadius = offset * 12f // Max 12px blur for clarity
            if (blurRadius > 0.5f) {
                backgroundView.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        blurRadius, 
                        blurRadius, 
                        android.graphics.Shader.TileMode.CLAMP
                    )
                )
            } else {
                backgroundView.setRenderEffect(null)
            }
        } else {
            // "Pseudo-blur" for older devices - Slight dimming instead of blurring
            // to avoid rendering overhead on low-end hardware.
            backgroundView.alpha = 1.0f - (offset * 0.15f)
        }
    }
    
    /**
     * Resets the background view to its original state (scale 1.0, no blur).
     */
    fun resetScaling(backgroundView: View?) {
        if (backgroundView == null) return
        
        backgroundView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(250)
            .withEndAction {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    backgroundView.setRenderEffect(null)
                }
            }
            .start()
    }
}
