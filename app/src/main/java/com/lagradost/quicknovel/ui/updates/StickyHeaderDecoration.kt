package com.lagradost.quicknovel.ui.updates

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class StickyHeaderDecoration(private val listener: StickyHeaderInterface) : RecyclerView.ItemDecoration() {

    interface StickyHeaderInterface {
        fun getHeaderPositionForItem(itemPosition: Int): Int
        fun getHeaderLayout(headerPosition: Int): Int
        fun bindHeaderData(header: View, headerPosition: Int)
        fun isHeader(itemPosition: Int): Boolean
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        val topChild = parent.getChildAt(0) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)
        if (topChildPosition == RecyclerView.NO_POSITION) return

        val headerPos = listener.getHeaderPositionForItem(topChildPosition)
        if (headerPos == -1) return
        
        val header = inflateHeaderView(parent, listener.getHeaderLayout(headerPos))
        listener.bindHeaderData(header, headerPos)
        fixLayoutSize(parent, header)
        
        val contactPoint = parent.paddingTop + header.height
        val childInContact = getChildInContact(parent, contactPoint)

        if (childInContact != null && listener.isHeader(parent.getChildAdapterPosition(childInContact))) {
            moveHeader(c, header, childInContact, parent.paddingTop)
        } else {
            drawHeader(c, header, parent.paddingTop)
        }
    }

    private fun drawHeader(c: Canvas, header: View, paddingTop: Int) {
        c.save()
        c.translate(0f, paddingTop.toFloat())
        header.draw(c)
        c.restore()
    }

    private fun moveHeader(c: Canvas, header: View, nextHeader: View, paddingTop: Int) {
        c.save()
        val y = Math.min(paddingTop.toFloat(), (nextHeader.top - header.height).toFloat())
        c.translate(0f, y)
        header.draw(c)
        c.restore()
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.bottom > contactPoint && child.top <= contactPoint) {
                return child
            }
        }
        return null
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)
        val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, 0)
        val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, 0)
        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private var inflatedHeader: View? = null
    private var inflatedLayoutId: Int = -1

    private fun inflateHeaderView(parent: RecyclerView, layoutId: Int): View {
        if (inflatedHeader == null || inflatedLayoutId != layoutId) {
             inflatedHeader = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
             inflatedLayoutId = layoutId
        }
        return inflatedHeader!!
    }
}
