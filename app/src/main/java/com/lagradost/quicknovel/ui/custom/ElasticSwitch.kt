package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.lagradost.quicknovel.R
import kotlin.math.abs
import kotlin.math.max

/**
 * A custom 'Elastic Rubber-Band' Toggle.
 * Stretches elastically using physics-based animations and provides haptic feedback.
 */
class ElasticSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private var _isChecked = false
    var isChecked: Boolean
        get() = _isChecked
        set(value) = setChecked(value)

    private var onCheckedChangedListener: ((View, Boolean) -> Unit)? = null

    // Colors
    private var accentColor = Color.CYAN
    private var trackColorOff = Color.LTGRAY 
    private var thumbColorOff = Color.WHITE
    private var thumbColorOn = Color.WHITE

    // Pre-allocated drawing objects for performance (2GB RAM optimization)
    private val trackRect = RectF()
    private val thumbPadding = 0f // Extra padding if needed

    // Dimensions
    private var trackHeight = 0f
    private var trackWidth = 0f
    private var thumbRadius = 0f
    private var padding = 0f

    // Animation Properties
    private var thumbX = 0f
        set(value) {
            field = value
            invalidate()
        }

    private var stretch = 0f // -1.0 to 1.0
        set(value) {
            field = value
            invalidate()
        }

    private val thumbProperty = object : FloatPropertyCompat<ElasticSwitch>("thumbX") {
        override fun getValue(view: ElasticSwitch): Float = view.thumbX
        override fun setValue(view: ElasticSwitch, value: Float) {
            view.thumbX = value
        }
    }

    private val stretchProperty = object : FloatPropertyCompat<ElasticSwitch>("stretch") {
        override fun getValue(view: ElasticSwitch): Float = view.stretch
        override fun setValue(view: ElasticSwitch, value: Float) {
            view.stretch = value
        }
    }

    private val thumbAnimation = SpringAnimation(this, thumbProperty).apply {
        spring = SpringForce().apply {
            stiffness = 180f // Custom low stiffness for slower, more elastic feel
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
    }

    private val stretchAnimation = SpringAnimation(this, stretchProperty).apply {
        spring = SpringForce().apply {
            stiffness = 150f // Slightly lower for the track "bulge" to stay visible longer
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
    }

    init {
        // Resolve colors from theme
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(R.attr.colorPrimary, typedValue, true)
        accentColor = typedValue.data

        context.theme.resolveAttribute(R.attr.grayTextColor, typedValue, true)
        trackColorOff = typedValue.data
        // Add 40% alpha to trackColorOff for a subtle look
        trackColorOff = Color.argb(
            100, 
            Color.red(trackColorOff), 
            Color.green(trackColorOff), 
            Color.blue(trackColorOff)
        )

        // Parse XML attributes for overrides
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.ElasticSwitch)
            _isChecked = a.getBoolean(R.styleable.ElasticSwitch_android_checked, false)
            accentColor = a.getColor(R.styleable.ElasticSwitch_checkedColor, accentColor)
            trackColorOff = a.getColor(R.styleable.ElasticSwitch_uncheckedColor, trackColorOff)
            thumbColorOn = a.getColor(R.styleable.ElasticSwitch_thumbColor, thumbColorOn)
            a.recycle()
        }

        // Minimum size
        val density = context.resources.displayMetrics.density
        padding = 4 * density
        
        // Optimize for hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setOnCheckedChangeListener(listener: ((View, Boolean) -> Unit)?) {
        onCheckedChangedListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (64 * resources.displayMetrics.density).toInt()
        val desiredHeight = (32 * resources.displayMetrics.density).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackHeight = h.toFloat() - (padding * 2)
        trackWidth = w.toFloat() - (padding * 2)
        thumbRadius = (trackHeight / 2) - padding
        
        // Initialize thumb position
        thumbX = if (isChecked) getEndPos() else getStartPos()
    }

    private fun getStartPos() = padding + thumbRadius + padding
    private fun getEndPos() = width - padding - thumbRadius - padding

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Track with Elastic Stretching
        val progress = (thumbX - getStartPos()) / (getEndPos() - getStartPos())
        val color = interpolateColor(trackColorOff, accentColor, progress)
        
        paint.color = color
        paint.style = Paint.Style.FILL
        
        // Calculate elastic track shape (Bulging Effect)
        path.reset()
        val r = trackHeight / 2
        val inset = padding
        val bulge = stretch * (trackHeight / 4f)
        
        // Cache dimensions into RectF for better performance
        trackRect.set(inset, inset, width - inset, height - inset)
        
        // Top Edge
        path.moveTo(inset + r, inset)
        path.quadTo(width / 2f, inset - bulge, width - inset - r, inset)
        
        // Right Arc
        path.arcTo(width - inset - 2*r, inset, width - inset, inset + 2*r, 270f, 180f, false)
        
        // Bottom Edge
        path.quadTo(width / 2f, height - inset + bulge, inset + r, height - inset)
        
        // Left Arc
        path.arcTo(inset, inset, inset + 2*r, inset + 2*r, 90f, 180f, false)
        
        path.close()
        canvas.drawPath(path, paint)

        // 2. Draw Thumb
        val thumbColor = interpolateColor(thumbColorOff, thumbColorOn, progress)
        paint.color = thumbColor
        
        // Performance: Only draw shadow if interacted with or if high-end device (simplified here to only interaction)
        if (isPressed || stretch != 0f) {
            paint.setShadowLayer(8f, 0f, 4f, Color.parseColor("#40000000"))
        }
        
        // Thumb "squashes" as it moves fast
        val squashX = abs(stretch) * 4f
        canvas.drawCircle(thumbX, height / 2f, thumbRadius - squashX, paint)
        paint.clearShadowLayer()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                toggle()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) // "Snap" feedback
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        if (_isChecked == checked) return
        _isChecked = checked
        onCheckedChangedListener?.invoke(this, checked)
        
        val targetX = if (isChecked) getEndPos() else getStartPos()
        if (animate && width > 0) {
            thumbAnimation.animateToFinalPosition(targetX)
            // Pulse stretch
            stretch = 1.0f
            stretchAnimation.animateToFinalPosition(0f)
        } else {
            thumbAnimation.skipToEnd()
            thumbX = targetX
            stretch = 0f
        }
    }

    fun toggle() {
        setChecked(!isChecked)
    }


    private fun interpolateColor(colorStart: Int, colorEnd: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val startA = Color.alpha(colorStart)
        val startR = Color.red(colorStart)
        val startG = Color.green(colorStart)
        val startB = Color.blue(colorStart)

        val endA = Color.alpha(colorEnd)
        val endR = Color.red(colorEnd)
        val endG = Color.green(colorEnd)
        val endB = Color.blue(colorEnd)

        return Color.argb(
            (startA + (endA - startA) * f).toInt(),
            (startR + (endR - startR) * f).toInt(),
            (startG + (endG - startG) * f).toInt(),
            (startB + (endB - startB) * f).toInt()
        )
    }
}
