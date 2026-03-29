package com.lagradost.quicknovel.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import kotlin.math.abs

class GrdLayoutManager(val context: Context, var spanCoun: Int) : GridLayoutManager(context, spanCoun) {
    override fun onFocusSearchFailed(
        focused: View,
        focusDirection: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): View? {
        return try {
            val fromPos = getPosition(focused)
            val nextPos = getNextViewPos(fromPos, focusDirection)
            findViewByPosition(nextPos)
        } catch (e: Exception) {
            null
        }
    }

    override fun onRequestChildFocus(
        parent: RecyclerView,
        state: RecyclerView.State,
        child: View,
        focused: View?
    ): Boolean {
        return try {
            val pos = maxOf(0, getPosition(focused!!) - 2)
            parent.scrollToPosition(pos)
            super.onRequestChildFocus(parent, state, child, focused)
        } catch (e: Exception){
            false
        }
    }

    override fun onInterceptFocusSearch(focused: View, direction: Int): View? {
        return try {
            val fromPos = getPosition(focused)
            val nextPos = getNextViewPos(fromPos, direction)
            findViewByPosition(nextPos)
        } catch (e: Exception) {
            null
        }
    }

    private fun getNextViewPos(fromPos: Int, direction: Int): Int {
        val offset = calcOffsetToNextView(direction)
        if (hitBorder(fromPos, offset)) return fromPos
        return fromPos + offset
    }

    private fun calcOffsetToNextView(direction: Int): Int {
        val spanCount = this.spanCoun
        val orientation = this.orientation

        if (orientation == VERTICAL) {
            when (direction) {
                View.FOCUS_DOWN -> return spanCount
                View.FOCUS_UP -> return -spanCount
                View.FOCUS_RIGHT -> return 1
                View.FOCUS_LEFT -> return -1
            }
        } else if (orientation == HORIZONTAL) {
            when (direction) {
                View.FOCUS_DOWN -> return 1
                View.FOCUS_UP -> return -1
                View.FOCUS_RIGHT -> return spanCount
                View.FOCUS_LEFT -> return -spanCount
            }
        }
        return 0
    }

    private fun hitBorder(from: Int, offset: Int): Boolean {
        val spanCount = spanCount
        return if (abs(offset) == 1) {
            val spanIndex = from % spanCount
            val newSpanIndex = spanIndex + offset
            newSpanIndex < 0 || newSpanIndex >= spanCount
        } else {
            val newPos = from + offset
            newPos in spanCount..-1
        }
    }
}

class AutofitRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    RecyclerView(context, attrs) {

    private val gridManager = GrdLayoutManager(context, 2)
    private var columnWidth = -1

    var spanCount = 0
        set(value) {
            field = value
            if (value > 0) {
                (layoutManager as? GridLayoutManager)?.spanCount = value
                gridManager.spanCount = value
            }
        }

    /**
     * Pinterest Bento Evolution: Dynamic item width calculation
     * that supports both Grid and Staggered Masonry modes.
     */
    val itemWidth: Int
        get() {
            val manager = layoutManager
            val count = when (manager) {
                is GridLayoutManager -> manager.spanCount
                is StaggeredGridLayoutManager -> manager.spanCount
                else -> 2
            }
            return if (count > 0) measuredWidth / count else measuredWidth / 2
        }

    init {
        if (attrs != null) {
            val attrsArray = intArrayOf(android.R.attr.columnWidth)
            val array = context.obtainStyledAttributes(attrs, attrsArray)
            columnWidth = array.getDimensionPixelSize(0, -1)
            array.recycle()
        }
        layoutManager = gridManager
    }
}