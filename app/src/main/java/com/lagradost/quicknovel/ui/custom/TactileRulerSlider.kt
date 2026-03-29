package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.widget.OverScroller
import com.lagradost.quicknovel.R
import kotlin.math.*

/**
 * A Compact Premium "Tactile Ruler" Slider (Industrial Dial).
 * Optimized for density and zero-clipping of side icons.
 */
class TactileRulerSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    
    // Values
    var valueFrom = 0f
    var valueTo = 100f
    var stepSize = 1f
    var valueSuffix = ""
    private var _value = 0f
    var value: Float
        get() = _value
        set(v) {
            val clamped = v.coerceIn(valueFrom, valueTo)
            if (_value != clamped) {
                _value = clamped
                if (!isDragging) {
                    scrollToValue(_value)
                }
                invalidate()
            }
        }

    private var onValueChangeListener: ((TactileRulerSlider, Float, Boolean) -> Unit)? = null

    // Colors
    private var tickColor = Color.parseColor("#80FFFFFF")
    private var needleColor = Color.CYAN
    private var textColor = Color.WHITE

    // Mechanical Constants
    private val tickSpacing = 20f * resources.displayMetrics.density
    private var scrollOffset = 0f
    private var maxScroll = 0f
    
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    
    private var lastX = 0f
    private var isDragging = false
    private var lastTickIndex = -1

    init {
        // Resolve Theme Colors
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        needleColor = typedValue.data
        context.theme.resolveAttribute(R.attr.textColor, typedValue, true)
        textColor = typedValue.data
        tickColor = Color.argb(120, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.TactileRulerSlider)
            valueFrom = a.getFloat(R.styleable.TactileRulerSlider_valueFrom, 0f)
            valueTo = a.getFloat(R.styleable.TactileRulerSlider_valueTo, 100f)
            stepSize = a.getFloat(R.styleable.TactileRulerSlider_stepSize, 1f)
            
            needleColor = a.getColor(R.styleable.TactileRulerSlider_needleColor, needleColor)
            tickColor = a.getColor(R.styleable.TactileRulerSlider_tickColor, tickColor)
            a.recycle()
        }

        textPaint.color = textColor
        textPaint.textSize = 10f * resources.displayMetrics.density // Smaller text for better fit
        
        _value = valueFrom
    }

    fun setOnValueChangeListener(listener: (TactileRulerSlider, Float, Boolean) -> Unit) {
        onValueChangeListener = listener
    }

    fun setValueRounded(v: Float) {
        value = v
    }

    private fun scrollToValue(v: Float) {
        val range = valueTo - valueFrom
        if (range == 0f) {
            scrollOffset = 0f
        } else {
            val progress = (v - valueFrom) / range
            scrollOffset = - (progress * maxScroll)
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val rangeCount = if (stepSize > 0) (valueTo - valueFrom) / stepSize else 1f
        maxScroll = rangeCount * tickSpacing
        scrollToValue(_value)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Compact height (60dp) to prevent layout crowding
        val h = (60 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(resolveSize(200, widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height * 0.55f // Centered lower to accommodate labels above
        val rangeCount = if (stepSize > 0) (valueTo - valueFrom) / stepSize else 1f

        // 1. Draw Ruler Ticks
        paint.strokeWidth = 2f * resources.displayMetrics.density
        paint.strokeCap = Paint.Cap.ROUND
        
        val startTick = floor(-scrollOffset / tickSpacing).toInt() - (width / tickSpacing / 2).toInt() - 2
        val endTick = ceil(-scrollOffset / tickSpacing).toInt() + (width / tickSpacing / 2).toInt() + 2

        for (i in startTick..endTick) {
            if (i < 0 || i > rangeCount) continue
            
            val tickX = centerX + scrollOffset + (i * tickSpacing)
            val distToCenter = abs(centerX - tickX)
            val normalizedDist = (distToCenter / (width / 2f)).coerceIn(0f, 1f)
            
            // Dynamic Zoom/Fade: subtle to keep it readable
            val scale = 1f - (normalizedDist * 0.4f)
            val alpha = (1f - normalizedDist).pow(1.5f) * 255
            
            paint.alpha = alpha.toInt()
            paint.color = tickColor
            textPaint.alpha = alpha.toInt()

            val isMajor = (i % 5 == 0)
            val tickHeight = if (isMajor) height * 0.3f else height * 0.15f
            
            canvas.drawLine(
                tickX, centerY - (tickHeight * scale / 2f),
                tickX, centerY + (tickHeight * scale / 2f),
                paint
            )

            if (isMajor) {
                val tickValue = valueFrom + (i * stepSize)
                val label = if (tickValue % 1f == 0f) tickValue.toInt().toString() else "%.1f".format(tickValue)
                canvas.drawText(label + valueSuffix, tickX, centerY + (tickHeight/2f) + (14f * resources.displayMetrics.density), textPaint)
            }
        }

        // 2. Draw Static Needle (Transparent center lock)
        paint.alpha = 255
        paint.color = needleColor
        paint.strokeWidth = 3f * resources.displayMetrics.density
        paint.setShadowLayer(10f, 0f, 0f, needleColor)
        
        canvas.drawLine(centerX, centerY - (height * 0.3f), centerX, centerY + (height * 0.3f), paint)
        paint.clearShadowLayer()

        // (Removed Top Bubble Value as it is redundant with scale labels)
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 10f * resources.displayMetrics.density
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastX = event.x
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                if (!isDragging && abs(dx) > touchSlop) {
                    isDragging = true
                }
                
                if (isDragging) {
                    scrollOffset += dx
                    scrollOffset = scrollOffset.coerceIn(-maxScroll, 0f)
                    updateValueFromScroll(true)
                    lastX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocity = velocityTracker?.xVelocity ?: 0f
                    // Add Snap logic: ensure it ends on a tick
                    scroller.fling(scrollOffset.toInt(), 0, velocity.toInt(), 0, -maxScroll.toInt(), 0, 0, 0)
                    postInvalidateOnAnimation()
                } else if (event.action == MotionEvent.ACTION_UP) {
                    // Tap to jump if needed
                }
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currX.toFloat()
            updateValueFromScroll(true)
            postInvalidateOnAnimation()
        }
    }

    private fun updateValueFromScroll(fromUser: Boolean) {
        if (maxScroll == 0f) return
        val progress = abs(scrollOffset) / maxScroll
        var newValue = valueFrom + (progress * (valueTo - valueFrom))
        
        // Accurate Snapping
        newValue = (round(newValue / stepSize) * stepSize).coerceIn(valueFrom, valueTo)
        
        if (_value != newValue) {
            _value = newValue
            onValueChangeListener?.invoke(this, newValue, fromUser)
            
            // Haptic Pulse
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}
