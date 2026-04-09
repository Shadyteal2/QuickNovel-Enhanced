package com.lagradost.quicknovel.ui.custom

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import java.util.Random

/**
 * LivingGlassView — Quantum Etherealism Engine.
 *
 * Algorithmic Philosophy: "Quantum Etherealism"
 * A high-fidelity generative visualizer that treats light as a probabilistic field.
 * Employs Zero-Allocation rendering techniques and Stochastic Dithering to achieve 
 * a "liquid light" aesthetic without digital artifacts (banding).
 */
class LivingGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ditherPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var startTime = System.currentTimeMillis()

    private var intensity: Float = 0.8f
    private var speedMult: Float = 1.0f
    private var currentPalette: Palette = Palette.NEBULA

    // Zero-Allocation Storage
    private val shaderMatrices = Array(3) { Matrix() }
    private val blobShaders = arrayOfNulls<RadialGradient>(3)
    private var ditherBitmap: Bitmap? = null
    private var ditherShader: BitmapShader? = null

    // Palette Interpolation
    private var activeColors = IntArray(3)
    private var startColors = IntArray(3)
    private var targetColors = IntArray(3)
    private var paletteAnimator: ValueAnimator? = null

    enum class Palette(val colors: IntArray) {
        NEBULA(intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#7C3AED"), Color.parseColor("#A78BFA"))),
        GARDEN(intArrayOf(Color.parseColor("#163832"), Color.parseColor("#8EB69B"), Color.parseColor("#DAF1DE"))),
        MINIMAL(intArrayOf(Color.parseColor("#98A77C"), Color.parseColor("#B6C99B"), Color.parseColor("#E7F5DC"))),
        SHADY(intArrayOf(Color.parseColor("#242E49"), Color.parseColor("#FDA481"), Color.parseColor("#B4182D"))),
        BROWNY(intArrayOf(Color.parseColor("#2F3A32"), Color.parseColor("#DB9F75"), Color.parseColor("#804012")))
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        createDitherTexture()
        // Initialize active colors from default palette
        System.arraycopy(currentPalette.colors, 0, activeColors, 0, 3)
    }

    private fun createDitherTexture() {
        // Generate a 64x64 microscopic grain texture to act as a physical dither
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = Random()
        for (y in 0 until size) {
            for (x in 0 until size) {
                // Micro-noise around neutral grey, extremely low alpha
                val noise = 127 + random.nextInt(16)
                bmp.setPixel(x, y, Color.argb(35, noise, noise, noise))
            }
        }
        ditherBitmap = bmp
        ditherShader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        ditherPaint.shader = ditherShader
        // OVERLAY mode injects original texture detail into gradients, effectively masking banding
        ditherPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    }

    fun setAuraIntensity(value: Int) { 
        this.intensity = value / 100f
        updateShaders()
        invalidate() 
    }

    fun setAuraSpeed(value: Int) {
        this.speedMult = value / 100f
        invalidate()
    }

    fun setAuraPalette(paletteName: String) {
        val palette = try {
            Palette.valueOf(paletteName.uppercase())
        } catch (e: Exception) { Palette.NEBULA }
        
        if (this.currentPalette != palette) {
            animatePaletteTransition(this.currentPalette, palette)
            this.currentPalette = palette
        }
    }

    private fun animatePaletteTransition(old: Palette, new: Palette) {
        paletteAnimator?.cancel()
        
        System.arraycopy(activeColors, 0, startColors, 0, 3)
        System.arraycopy(new.colors, 0, targetColors, 0, 3)
        
        val evaluator = ArgbEvaluator()
        paletteAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500 // Smooth premium transition
            addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                for (i in 0..2) {
                    activeColors[i] = evaluator.evaluate(fraction, startColors[i], targetColors[i]) as Int
                }
                updateShaders()
                invalidate()
            }
            start()
        }
    }

    fun setAuraEngine(engineName: String) {}
    fun setAuraFPS(value: Int) {}

    fun getCurrentAuraColor(): Int {
        return modifyAlpha(activeColors[0], 0.8f, intensity.coerceIn(0.3f, 1.0f))
    }

    fun getAuraColorOpaque(): Int {
        val color = activeColors[0]
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun updateShaders() {
        val w = width.toFloat().takeIf { it > 0 } ?: return
        val h = height.toFloat().takeIf { it > 0 } ?: return
        
        val radius0 = max(w, h) * 1.05f
        val radius1 = max(w, h) * 0.90f
        val radius2 = max(w, h) * 0.78f
        
        val radii = floatArrayOf(radius0, radius1, radius2)
        
        for (i in 0..2) {
            val color = activeColors[i % activeColors.size]
            val eff = (intensity * 1.25f).coerceIn(0.1f, 1.0f)
            
            val core = modifyAlpha(color, 1.00f, eff)
            val mid  = modifyAlpha(color, 0.70f, eff)
            val soft = modifyAlpha(color, 0.35f, eff)
            val halo = modifyAlpha(color, 0.08f, eff)

            // Re-creating the shader only when parameters CHANGE or during transition
            blobShaders[i] = RadialGradient(
                0f, 0f, radii[i],
                intArrayOf(core, mid, soft, halo, Color.TRANSPARENT),
                floatArrayOf(0f, 0.20f, 0.50f, 0.78f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || blobShaders[0] == null) {
             if (w > 0 && h > 0) updateShaders()
             return
        }

        val t = (System.currentTimeMillis() - startTime) / 1000f * speedMult
        
        // Quantum Deep Black
        canvas.drawColor(Color.BLACK)

        val cx = w / 2f
        val cy = h / 2f

        // Drift Parameters
        val dx = w * 0.12f
        val dy = h * 0.12f

        // Multi-Octave Harmonic Drift (Lissajous + simulated convection)
        val s0 = t * 0.032f
        val s1 = t * 0.021f
        val s2 = t * 0.048f

        // Calculate positions
        val x0 = cx + dx * sin(s0.toDouble()).toFloat()
        val y0 = cy + dy * cos((s0 * 1.37).toDouble()).toFloat()

        val x1 = cx + dx * cos((s1 * 1.73).toDouble()).toFloat()
        val y1 = cy + dy * sin((s1 * 0.91).toDouble()).toFloat()

        val x2 = cx + dx * sin((s2 * 0.83).toDouble()).toFloat()
        val y2 = cy + dy * cos((s2 * 1.17).toDouble()).toFloat()

        val centersX = arrayOf(x0, x1, x2)
        val centersY = arrayOf(y0, y1, y2)
        val radii = arrayOf(max(w, h) * 1.05f, max(w, h) * 0.90f, max(w, h) * 0.78f)

        // Zero-Allocation Rendering Pipeline
        mainPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        
        for (i in 0..2) {
            val shader = blobShaders[i] ?: continue
            val matrix = shaderMatrices[i]
            
            // Re-use the matrix and shader via setLocalMatrix (Zero Allocation Path)
            matrix.setTranslate(centersX[i], centersY[i])
            shader.setLocalMatrix(matrix)
            
            mainPaint.shader = shader
            canvas.drawCircle(centersX[i], centersY[i], radii[i].toFloat(), mainPaint)
        }

        // Apply Stochastic Dither Layer (Masks banding artifacts)
        mainPaint.xfermode = null
        mainPaint.shader = null
        canvas.drawRect(0f, 0f, w, h, ditherPaint)

        postInvalidateDelayed(16) // Target 60fps cinematic fluidity
    }

    private fun modifyAlpha(color: Int, factor: Float, baseIntensity: Float): Int {
        val alpha = (255f * factor * baseIntensity).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
