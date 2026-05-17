package com.lagradost.quicknovel.util

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R

/**
 * MagicAnimator — Centralized animation utilities for premium micro-interactions.
 *
 * Design principles:
 *  - All effects are additive: they stack cleanly on existing functionality
 *  - Spring physics via androidx.dynamicanimation (already in dependencies)
 *  - Staggered list reveals respect existing adapter data flow
 *  - All animations respect reduced-motion preferences (checked externally)
 *  - No exceptions thrown: all methods are safe to call in any state
 */
object MagicAnimator {

    // ─── Spring Configs ───────────────────────────────────────────────────────

    /** Snappy, bouncy spring — for icon presses, FAB taps */
    private val SPRING_BOUNCY = SpringForce(1f).apply {
        dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY   // 0.5 — visible but not jarring
        stiffness = SpringForce.STIFFNESS_MEDIUM                  // 500
    }

    /** Tight, responsive spring — for card presses */
    private val SPRING_TIGHT = SpringForce(1f).apply {
        dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY       // 0.75
        stiffness = SpringForce.STIFFNESS_HIGH                    // 10000
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Applies a spring scale effect to any view on touch.
     * Perfect for icon buttons, FABs, and chip-like interactive targets.
     *
     * @param pressDepth How far to scale down on press. Default 0.90f = 90%.
     */
    fun applySpringPress(view: View?, pressDepth: Float = 0.90f, springConfig: SpringForce = SPRING_BOUNCY) {
        view ?: return
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    springScaleTo(v, pressDepth, springConfig)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    springScaleTo(v, 1f, springConfig)
                }
            }
            false // Crucial: return false so click listeners still fire
        }
    }

    /**
     * Applies a subtle spring press to a card — tighter feel for larger surfaces.
     */
    fun applyCardPress(view: View?) {
        applySpringPress(view, pressDepth = 0.97f, springConfig = SPRING_TIGHT)
    }

    /**
     * Applies a crisp "tap" spring for icon-sized controls (back button, FABs, etc.)
     */
    fun applyIconPress(view: View?) {
        applySpringPress(view, pressDepth = 0.82f, springConfig = SPRING_BOUNCY)
    }

    /**
     * Animates a view entering the scene with a premium fade+translate+scale reveal.
     * Call after the view becomes visible for the first time.
     *
     * @param delay    Starting delay in ms (use with stagger).
     * @param from     Starting Y translation offset. Default 30px (subtle upward reveal).
     */
    fun revealView(view: View?, delay: Long = 0, fromY: Float = 40f) {
        view ?: return
        view.alpha = 0f
        view.translationY = fromY
        view.scaleX = 0.96f
        view.scaleY = 0.96f

        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(380)
            .setStartDelay(delay)
            .setInterpolator(DecelerateInterpolator(2f))
            .start()
    }

    /**
     * Staggered reveal for a collection of views (e.g., action buttons, chips).
     *
     * @param staggerMs   Delay between each item. Default 60ms.
     */
    fun revealStaggered(views: List<View?>, staggerMs: Long = 60L, startDelay: Long = 0L) {
        views.forEachIndexed { i, view ->
            revealView(view, delay = startDelay + i * staggerMs)
        }
    }

    /**
     * Trigger a one-shot "pop" pulse — great for bookmark confirmations,
     * download complete, or any success state.
     */
    fun popPulse(view: View?) {
        view ?: return
        view.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(120)
            .setInterpolator(OvershootInterpolator(1.5f))
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()
            }.start()
    }

    /**
     * Run a fade-in reveal on a RecyclerView — replaces static list appearances
     * with a smooth staggered cascade. Safe to call before or after data is set.
     *
     * Uses LayoutAnimation which works cleanly with submitList().
     */
    fun runGridReveal(recyclerView: RecyclerView?) {
        recyclerView ?: return
        val ctx = recyclerView.context ?: return
        try {
            val controller = AnimationUtils.loadLayoutAnimation(ctx, R.anim.grid_layout_animation)
            recyclerView.layoutAnimation = controller
            recyclerView.scheduleLayoutAnimation()
        } catch (e: Exception) {
            // Non-critical: swallow silently
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun springScaleTo(view: View, target: Float, springForce: SpringForce) {
        // X
        SpringAnimation(view, DynamicAnimation.SCALE_X, target).apply {
            spring = SpringForce(target).apply {
                dampingRatio = springForce.dampingRatio
                stiffness = springForce.stiffness
            }
            start()
        }
        // Y
        SpringAnimation(view, DynamicAnimation.SCALE_Y, target).apply {
            spring = SpringForce(target).apply {
                dampingRatio = springForce.dampingRatio
                stiffness = springForce.stiffness
            }
            start()
        }
    }
}
