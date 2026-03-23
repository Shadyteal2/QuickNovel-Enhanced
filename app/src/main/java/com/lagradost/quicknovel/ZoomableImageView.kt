package com.lagradost.quicknovel

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var currentMatrix = Matrix()
    private var mode = State.NONE

    private enum class State { NONE, DRAG, ZOOM }

    private var last = PointF()
    private var start = PointF()
    private var minScale = 1f
    private var maxScale = 5f
    private var mGestureDetector: android.view.GestureDetector
    private lateinit var mScaleDetector: ScaleGestureDetector

    private var viewWidth = 0
    private var viewHeight = 0
    private var saveScale = 1f
    private var origWidth = 0f
    private var origHeight = 0f
    private var m = FloatArray(9)

    init {
        scaleType = ScaleType.FIT_CENTER
        mScaleDetector = ScaleGestureDetector(context, ScaleListener())
        mGestureDetector = android.view.GestureDetector(context, DoubleTapListener())

        setOnTouchListener { _, event ->
            mScaleDetector.onTouchEvent(event)
            mGestureDetector.onTouchEvent(event)
            val curr = PointF(event.x, event.y)

            if (saveScale > 1f) {
                parent.requestDisallowInterceptTouchEvent(true)
            } else {
                parent.requestDisallowInterceptTouchEvent(false)
                // If it's a DoubleTap, the gesture detector will consume it or return true.
                // But generally, don't return false IF we want double taps to process on non-zoom state!
                // Wait! If return false, does GestureDetector still capture DOWN/MOVE/UP?
                // No, if you return false on DOWN, you don't get MOVE/UP!
                // To support double tap on saveScale == 1f, we MUST return true on ACTION_DOWN!
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    last.set(curr)
                    start.set(last)
                    mode = State.DRAG
                }
                MotionEvent.ACTION_MOVE -> if (mode == State.DRAG && saveScale > 1f) {
                    val deltaX = curr.x - last.x
                    val deltaY = curr.y - last.y
                    val fixTransX = getFixDragTrans(deltaX, viewWidth.toFloat(), origWidth * saveScale)
                    val fixTransY = getFixDragTrans(deltaY, viewHeight.toFloat(), origHeight * saveScale)
                    currentMatrix.postTranslate(fixTransX, fixTransY)
                    fixTrans()
                    last.set(curr.x, curr.y)
                }
                MotionEvent.ACTION_UP -> {
                    mode = State.NONE
                }
                MotionEvent.ACTION_POINTER_UP -> mode = State.NONE
            }
            if (scaleType == ScaleType.MATRIX) {
                imageMatrix = currentMatrix
                invalidate()
            }
            true // Consume to preserve gesture stream
        }
    }

    private inner class DoubleTapListener : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (saveScale > 1f) {
                resetScale()
            } else {
                saveScale = 2.0f
                scaleType = ScaleType.MATRIX
                val drawable = drawable ?: return true
                val drawableWidth = drawable.intrinsicWidth
                val drawableHeight = drawable.intrinsicHeight
                val scaleX = viewWidth.toFloat() / drawableWidth.toFloat()
                val scaleY = viewHeight.toFloat() / drawableHeight.toFloat()
                val scale = minOf(scaleX, scaleY)
                currentMatrix.setScale(scale, scale)

                var redundantYSpace = viewHeight.toFloat() - scale * drawableHeight.toFloat()
                var redundantXSpace = viewWidth.toFloat() - scale * drawableWidth.toFloat()
                redundantYSpace /= 2f
                redundantXSpace /= 2f

                currentMatrix.postTranslate(redundantXSpace, redundantYSpace)
                origWidth = viewWidth - 2 * redundantXSpace
                origHeight = viewHeight - 2 * redundantYSpace
                
                // Zoom in 2x around tap coordinate
                currentMatrix.postScale(2.0f, 2.0f, e.x, e.y)
                fixTrans()
                imageMatrix = currentMatrix
                invalidate()
            }
            return true
        }

        // Add this to make sure clicks aren't eaten incorrectly
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            performClick()
            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = State.ZOOM
            if (scaleType != ScaleType.MATRIX) {
                scaleType = ScaleType.MATRIX
                // Copy current scaling from view into the matrix for transition
                val drawable = drawable ?: return true
                val drawableWidth = drawable.intrinsicWidth
                val drawableHeight = drawable.intrinsicHeight
                val scaleX = viewWidth.toFloat() / drawableWidth.toFloat()
                val scaleY = viewHeight.toFloat() / drawableHeight.toFloat()
                val scale = minOf(scaleX, scaleY)
                currentMatrix.setScale(scale, scale)

                var redundantYSpace = viewHeight.toFloat() - scale * drawableHeight.toFloat()
                var redundantXSpace = viewWidth.toFloat() - scale * drawableWidth.toFloat()
                redundantYSpace /= 2f
                redundantXSpace /= 2f

                currentMatrix.postTranslate(redundantXSpace, redundantYSpace)
                origWidth = viewWidth - 2 * redundantXSpace
                origHeight = viewHeight - 2 * redundantYSpace
                imageMatrix = currentMatrix
            }
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            var mScaleFactor = detector.scaleFactor
            val origScale = saveScale
            saveScale *= mScaleFactor
            if (saveScale > maxScale) {
                saveScale = maxScale
                mScaleFactor = maxScale / origScale
            } else if (saveScale < minScale) {
                saveScale = minScale
                mScaleFactor = minScale / origScale
                if (saveScale == 1f) {
                    scaleType = ScaleType.FIT_CENTER // Revert to native centering
                }
            }

            if (origWidth * saveScale <= viewWidth || origHeight * saveScale <= viewHeight) {
                currentMatrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2f, viewHeight / 2f)
            } else {
                currentMatrix.postScale(mScaleFactor, mScaleFactor, detector.focusX, detector.focusY)
            }
            fixTrans()
            return true
        }
    }

    private fun fixTrans() {
        if (origWidth <= 0f || origHeight <= 0f) return
        currentMatrix.getValues(m)
        val transX = m[Matrix.MTRANS_X]
        val transY = m[Matrix.MTRANS_Y]
        val fixTransX = getFixTrans(transX, viewWidth.toFloat(), origWidth * saveScale)
        val fixTransY = getFixTrans(transY, viewHeight.toFloat(), origHeight * saveScale)
        if (fixTransX != 0f || fixTransY != 0f) currentMatrix.postTranslate(fixTransX, fixTransY)
    }

    private fun getFixTrans(trans: Float, viewSize: Float, contentSize: Float): Float {
        if (contentSize <= viewSize) return 0f
        val minTrans: Float
        val maxTrans: Float
        if (contentSize <= viewSize) {
            minTrans = 0f
            maxTrans = viewSize - contentSize
        } else {
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }
        if (trans < minTrans) return -trans + minTrans
        if (trans > maxTrans) return -trans + maxTrans
        return 0f
    }

    private fun getFixDragTrans(delta: Float, viewSize: Float, contentSize: Float): Float {
        if (contentSize <= viewSize) return 0f
        return delta
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        viewHeight = MeasureSpec.getSize(heightMeasureSpec)
    }

    fun resetScale() {
        saveScale = 1f
        scaleType = ScaleType.FIT_CENTER
        requestLayout()
    }
}
