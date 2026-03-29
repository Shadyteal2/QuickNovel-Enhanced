package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.lagradost.quicknovel.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A Premium Elastic Seekbar (Slider) Pro Max.
 * Featuring: 
 * - Ultra-smooth touch interception (no sticking).
 * - Floating Bubble Label with real-time numeric values.
 * - Physics-based thumb stretching & Track bulging.
 * - Haptic ticks and Spring dynamics.
 */
class ElasticSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()

    // Values
    var valueFrom = 0f
    var valueTo = 100f
    var stepSize = 0f
    var valueSuffix: String = "" 
    var valueFormatter: ((Float) -> String)? = null
    
    private var _value = 0f
    var value: Float
        get() = _value
        set(v) {
            val clamped = v.coerceIn(valueFrom, valueTo)
            if (_value != clamped) {
                _value = clamped
                if (!isDragging) {
                    syncThumbToValue()
                }
                invalidate()
            }
        }

    private var onValueChangeListener: ((ElasticSlider, Float, Boolean) -> Unit)? = null

    // Colors
    private var thumbColor = Color.WHITE
    private var trackColor = Color.parseColor("#40FFFFFF")
    private var progressColor = Color.CYAN
    private var labelColor = Color.BLACK

    // Pre-allocated
    private val trackRect = RectF()
    private val labelRect = RectF()
    private var trackHeight = 0f
    private var padding = 0f
    private var thumbRadius = 0f
    private var labelHeight = 0f
    private var labelPadding = 0f

    // Motion State
    private var thumbX = 0f
        set(v) {
            field = v
            invalidate()
        }
    
    private var stretch = 0f // -1 to 1 for thumb stretching
        set(v) {
            field = v
            invalidate()
        }

    private var labelAlpha = 0f // 0 to 1 for fading label
        set(v) {
            field = v
            invalidate()
        }

    private var isDragging = false
    private var lastX = 0f

    // Animations
    private val thumbProperty = object : FloatPropertyCompat<ElasticSlider>("thumbX") {
        override fun getValue(view: ElasticSlider): Float = view.thumbX
        override fun setValue(view: ElasticSlider, value: Float) { view.thumbX = value }
    }

    private val stretchProperty = object : FloatPropertyCompat<ElasticSlider>("stretch") {
        override fun getValue(view: ElasticSlider): Float = view.stretch
        override fun setValue(view: ElasticSlider, value: Float) { view.stretch = value }
    }

    private val labelAlphaProperty = object : FloatPropertyCompat<ElasticSlider>("labelAlpha") {
        override fun getValue(view: ElasticSlider): Float = view.labelAlpha
        override fun setValue(view: ElasticSlider, value: Float) { view.labelAlpha = value }
    }

    private val thumbAnimation = SpringAnimation(this, thumbProperty).apply {
        spring = SpringForce().apply {
            stiffness = 500f
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
    }

    private val stretchAnimation = SpringAnimation(this, stretchProperty).apply {
        spring = SpringForce().apply {
            stiffness = 180f
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
    }

    private val labelAnimation = SpringAnimation(this, labelAlphaProperty).apply {
        spring = SpringForce().apply {
            stiffness = SpringForce.STIFFNESS_MEDIUM
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
    }

    init {
        // Resolve Colors
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        progressColor = typedValue.data
        labelColor = progressColor

        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.ElasticSlider)
            valueFrom = a.getFloat(R.styleable.ElasticSlider_valueFrom, 0f)
            valueTo = a.getFloat(R.styleable.ElasticSlider_valueTo, 100f)
            stepSize = a.getFloat(R.styleable.ElasticSlider_stepSize, 0f)
            _value = valueFrom
            
            progressColor = a.getColor(R.styleable.ElasticSlider_progressColor, progressColor)
            trackColor = a.getColor(R.styleable.ElasticSlider_trackColor, trackColor)
            thumbColor = a.getColor(R.styleable.ElasticSlider_thumbColor, thumbColor)
            labelColor = progressColor
            a.recycle()
        }

        val density = context.resources.displayMetrics.density
        padding = 24 * density
        thumbRadius = 10 * density
        trackHeight = 4 * density
        labelHeight = 32 * density
        labelPadding = 8 * density
        
        textPaint.textSize = 14 * density
        
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setValueRounded(v: Float) {
        value = v
    }

    fun setOnValueChangeListener(listener: (ElasticSlider, Float, Boolean) -> Unit) {
        onValueChangeListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (72 * resources.displayMetrics.density).toInt() // Increased height for label
        setMeasuredDimension(resolveSize(200, widthMeasureSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        syncThumbToValue()
    }

    private fun getStartPos() = padding
    private fun getEndPos() = width - padding

    private fun syncThumbToValue() {
        if (width == 0) return
        val range = valueTo - valueFrom
        val progress = if (range == 0f) 0f else (value - valueFrom) / range
        thumbX = getStartPos() + (getEndPos() - getStartPos()) * progress
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerY = height * 0.7f // Lower track to make room for label
        val startX = getStartPos()
        val endX = getEndPos()

        // 1. Draw Background Track
        paint.color = trackColor
        paint.strokeWidth = trackHeight
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        canvas.drawLine(startX, centerY, endX, centerY, paint)

        // 2. Draw Progress with "Elastic Bulge"
        paint.color = progressColor
        paint.strokeWidth = trackHeight * 1.5f
        
        path.reset()
        path.moveTo(startX, centerY)
        
        val bulgeSize = abs(stretch) * 10f
        path.lineTo(thumbX - (thumbRadius * 2.5f), centerY)
        path.quadTo(thumbX, centerY - bulgeSize, thumbX + (thumbRadius * 2.5f), centerY)
        
        canvas.drawLine(startX, centerY, thumbX, centerY, paint)

        // 3. Draw Thumb (Premium Glow + Elastic Shape)
        paint.style = Paint.Style.FILL
        paint.color = thumbColor
        
        // Optimize: Shadow is expensive in lists, only show when interacting
        if (isDragging || stretch != 0f) {
            paint.setShadowLayer(16f, 0f, 8f, Color.parseColor("#80000000"))
        }

        val stretchX = stretch * thumbRadius * 1.2f
        val stretchY = -abs(stretch) * thumbRadius * 0.4f
        
        trackRect.set(
            thumbX - thumbRadius - stretchX,
            centerY - thumbRadius - stretchY,
            thumbX + thumbRadius + stretchX,
            centerY + thumbRadius + stretchY
        )
        canvas.drawOval(trackRect, paint)
        paint.clearShadowLayer()

        // 4. Draw Floating Bubble Label (The Numbers)
        if (labelAlpha > 0.01f) {
            val labelText = valueFormatter?.invoke(value) ?: if (stepSize % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
            val fullText = labelText + valueSuffix
            
            val textWidth = textPaint.measureText(fullText)
            val bubbleWidth = max(textWidth + labelPadding * 2.5f, labelHeight * 1.2f)
            val bubbleHeight = labelHeight
            val bubbleY = centerY - thumbRadius - labelPadding - (labelAlpha * 20f)
            
            labelRect.set(
                thumbX - bubbleWidth / 2f,
                bubbleY - bubbleHeight,
                thumbX + bubbleWidth / 2f,
                bubbleY
            )

            // Draw Bubble
            paint.color = labelColor
            paint.alpha = (labelAlpha * 255).toInt()
            canvas.drawRoundRect(labelRect, bubbleHeight / 2f, bubbleHeight / 2f, paint)
            
            // Draw Pointer (Little triangle under bubble)
            path.reset()
            path.moveTo(thumbX - 10f, bubbleY)
            path.lineTo(thumbX + 10f, bubbleY)
            path.lineTo(thumbX, bubbleY + 8f)
            path.close()
            canvas.drawPath(path, paint)

            // Draw Text
            textPaint.alpha = (labelAlpha * 255).toInt()
            val textBaseLine = labelRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(fullText, thumbX, textBaseLine, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true) // STOP SCROLL INTERFERENCE
                lastX = event.x
                thumbAnimation.cancel()
                labelAnimation.animateToFinalPosition(1f)
                updateValueFromPos(event.x, true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                stretch = (dx / 40f).coerceIn(-1.8f, 1.8f)
                lastX = event.x
                updateValueFromPos(event.x, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                stretchAnimation.animateToFinalPosition(0f)
                labelAnimation.animateToFinalPosition(0f)
                syncThumbToValue() // Snap to step visually if needed
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateValueFromPos(posX: Float, fromUser: Boolean) {
        val range = getEndPos() - getStartPos()
        if (range == 0f) return
        
        val progress = ((posX - getStartPos()) / range).coerceIn(0f, 1f)
        var newValue = valueFrom + (valueTo - valueFrom) * progress
        
        if (stepSize > 0) {
            newValue = (newValue / stepSize).roundToInt() * stepSize
        }
        
        if (_value != newValue) {
            _value = newValue
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onValueChangeListener?.invoke(this, newValue, fromUser)
        }
        thumbX = posX.coerceIn(getStartPos(), getEndPos())
    }
}
