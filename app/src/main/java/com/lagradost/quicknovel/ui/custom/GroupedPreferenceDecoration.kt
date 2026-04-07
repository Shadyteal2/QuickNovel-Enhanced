package com.lagradost.quicknovel.ui.custom

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.util.toPx

class GroupedPreferenceDecoration : RecyclerView.ItemDecoration() {
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
    }
    
    // Slightly lighter charcoal for the card background
    private val cardColor = 0x0DFFFFFF.toInt() // 5% White glass
    private val strokeColor = 0x26FFFFFF.toInt() // 15% White border
    private val strokeWidth = 1.toPx.toFloat()
    private val cornerRadius = 32.toPx.toFloat()
    private val horizontalMargin = 16.toPx

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue

            val pref = adapter.getItem(position) ?: continue
            
            // Draw a divider below Category title
            if (pref is PreferenceCategory) {
                drawCategoryDivider(c, child)
            }
        }
    }

    private fun drawCategoryDivider(c: Canvas, view: View) {
        val left = 16.toPx.toFloat()
        val right = view.width - 16.toPx.toFloat()
        val y = view.bottom.toFloat() - 2.toPx
        
        backgroundPaint.color = 0x0AFFFFFF.toInt() // Soft 6% white divider
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = 1.toPx.toFloat()
        c.drawLine(left, y, right, y, backgroundPaint)
    }

    private fun drawSectionBackground(c: Canvas, parent: RecyclerView, startIdx: Int, endIdx: Int) {
        // Disabled: No longer drawing card backgrounds for settings sub-screens.
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val adapter = parent.adapter as? PreferenceGroupAdapter ?: return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val pref = adapter.getItem(position) ?: return
        
        // Ensure some spacing for the Discovery Header
        if (pref.key == "header_discovery") {
            outRect.bottom = 24.toPx // More space for title
            return
        }

        // Standard item height boost
        outRect.bottom = 8.toPx

        // Spacing between categories
        if (pref is PreferenceCategory) {
            outRect.top = 16.toPx
        }
    }
}
