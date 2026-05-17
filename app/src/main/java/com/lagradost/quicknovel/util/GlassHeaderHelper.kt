package com.lagradost.quicknovel.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import kotlin.math.min

/**
 * A Premium utility to handle Adaptive Glass Header transitions.
 * Morphs a toolbar from transparent to a frosted glass state with a glowing border.
 */
object GlassHeaderHelper {

    private const val MAX_SCROLL_DP = 80f

    fun applyGlassHeader(
        toolbar: View,
        scrollable: View,
        baseColor: Int = Color.BLACK
    ) {
        val density = toolbar.resources.displayMetrics.density
        val maxScroll = MAX_SCROLL_DP * density
        
        // Ensure the toolbar has the glass background
        if (toolbar.background !is LayerDrawable) {
            toolbar.setBackgroundResource(R.drawable.glass_header_background)
        }
        
        val layerDrawable = toolbar.background as LayerDrawable
        val bgShape = layerDrawable.getDrawable(0) as GradientDrawable
        val strokeShape = layerDrawable.getDrawable(1) as GradientDrawable

        var lastProgress = -1f

        val updateHeader = { scrollY: Int ->
            val progress = min(scrollY.toFloat() / maxScroll, 1f)
            
            // Performance Guard: Only update if change is > 1% to save CPU cycles
            if (Math.abs(progress - lastProgress) > 0.01f || progress == 0f || progress == 1f) {
                lastProgress = progress

                // 1. Update Background Blur (Alpha)
                // Start with a slight 12% alpha (30/255) even at top for visibility
                val minAlpha = 30
                val maxAlpha = 220 // ~86%
                val bgAlpha = (minAlpha + (progress * (maxAlpha - minAlpha))).toInt()
                bgShape.setColor(ColorUtils.setAlphaComponent(baseColor, bgAlpha))
                
                // 2. Update Bottom Border (Glow)
                val strokeAlpha = (progress * 0.4f * 255).toInt() // Max 40% opacity (increased for vividness)
                strokeShape.setStroke((1 * density).toInt(), ColorUtils.setAlphaComponent(Color.WHITE, strokeAlpha))
                
                // 3. Dynamic Elevation (Subtle shadow)
                toolbar.elevation = progress * 6 * density // Increased to 6dp for more skip/depth
            }
        }

        when (scrollable) {
            is NestedScrollView -> {
                scrollable.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    updateHeader(scrollY)
                }
            }
            is RecyclerView -> {
                scrollable.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        // Debounce by checking dy if available, otherwise absolute offset
                        if (dy != 0) {
                            val absoluteOffset = recyclerView.computeVerticalScrollOffset()
                            updateHeader(absoluteOffset)
                        }
                    }
                })
            }
        }
        
        // Initial State
        updateHeader(0)
    }
}
