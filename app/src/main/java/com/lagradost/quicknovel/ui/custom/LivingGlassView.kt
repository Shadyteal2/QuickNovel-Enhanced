package com.lagradost.quicknovel.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * LivingGlassView — Atmospheric Nebula Engine.
 *
 * Algorithmic Philosophy: "Atmospheric Drift"
 * Three massive overlapping luminous blobs travel on independent, irrational-ratio
 * Lissajous paths around the screen center. Their radial gradients overlap via SCREEN
 * blending, physically simulating photon stacking. The result: a large, centered,
 * slowly morphing blob of pure atmospheric light that fades softly to black at
 * screen edges. No fast pulsing. No orbiting the edges. Only slow, cinematic drift.
 */
class LivingGlassView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startTime = System.currentTimeMillis()

    private var intensity: Float = 0.8f
    private var speedMult: Float = 1.0f
    private var currentPalette: Palette = Palette.NEBULA

    enum class Palette(val colors: IntArray) {
        NEBULA(intArrayOf(
            Color.parseColor("#38BDF8"),
            Color.parseColor("#7C3AED"),
            Color.parseColor("#A78BFA")
        )),
        GARDEN(intArrayOf(
            Color.parseColor("#163832"),
            Color.parseColor("#8EB69B"),
            Color.parseColor("#DAF1DE")
        )),
        MINIMAL(intArrayOf(
            Color.parseColor("#98A77C"),
            Color.parseColor("#B6C99B"),
            Color.parseColor("#E7F5DC")
        )),
        SHADY(intArrayOf(
            Color.parseColor("#242E49"),
            Color.parseColor("#FDA481"),
            Color.parseColor("#B4182D")
        )),
        BROWNY(intArrayOf(
            Color.parseColor("#2F3A32"),
            Color.parseColor("#DB9F75"),
            Color.parseColor("#804012")
        ))
    }

    fun setAuraIntensity(value: Int) { this.intensity = value / 100f; invalidate() }
    fun setAuraSpeed(value: Int) { this.speedMult = value / 100f; invalidate() }
    fun setAuraPalette(paletteName: String) {
        this.currentPalette = try {
            Palette.valueOf(paletteName.uppercase())
        } catch (e: Exception) { Palette.NEBULA }
        invalidate()
    }
    fun setAuraEngine(engineName: String) {}
    fun setAuraFPS(value: Int) {}

    fun getCurrentAuraColor(): Int {
        return modifyAlpha(currentPalette.colors[0], 0.8f, intensity.coerceIn(0.3f, 1.0f))
    }

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val t = (System.currentTimeMillis() - startTime) / 1000f * speedMult
        val colors = currentPalette.colors

        // Deep black base — the glow should own the darkness
        canvas.drawColor(Color.argb(255, 0, 0, 0))

        // SCREEN blending: blobs stack light additively like real photons
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)

        val cx = w / 2f
        val cy = h / 2f

        // Each blob is HUGE — larger than the screen — so the glow fills the display
        // and fades to black at corners, exactly like the reference images.
        val r0 = max(w, h) * 1.05f
        val r1 = max(w, h) * 0.90f
        val r2 = max(w, h) * 0.78f

        // Small drift radius — blobs stay near center, creating a "breathing" rather than "orbiting"
        val dx = w * 0.15f
        val dy = h * 0.15f

        // Irrational frequency ratios ensure the paths never exactly repeat (Lissajous drift)
        // Very slow: full cycle takes ~200 seconds at speed=100
        val s0 = t * 0.032f
        val s1 = t * 0.020f  // φ ratio approximation
        val s2 = t * 0.051f

        val x0 = cx + dx * sin(s0.toDouble()).toFloat()
        val y0 = cy + dy * cos((s0 * 1.37).toDouble()).toFloat()

        val x1 = cx + dx * cos((s1 * 1.73).toDouble()).toFloat()
        val y1 = cy + dy * sin((s1 * 0.91).toDouble()).toFloat()

        val x2 = cx + dx * sin((s2 * 0.83).toDouble()).toFloat()
        val y2 = cy + dy * cos((s2 * 1.17).toDouble()).toFloat()

        drawBlob(canvas, x0, y0, r0, colors[0])
        drawBlob(canvas, x1, y1, r1, colors[1])
        drawBlob(canvas, x2, y2, r2, colors[2 % colors.size])

        paint.xfermode = null
        postInvalidateDelayed(33)
    }

    private fun drawBlob(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        val eff = (intensity * 1.25f).coerceIn(0.1f, 1.0f)

        // NO white core — pure atmospheric color from center outward, like the reference images.
        // The overlapping of 3 blobs via SCREEN blending will naturally brighten the center
        // without needing an explicit white hotspot.
        val core = modifyAlpha(color, 1.00f, eff) // Full saturated color at center
        val mid  = modifyAlpha(color, 0.70f, eff) // Color body
        val soft = modifyAlpha(color, 0.35f, eff) // Soft outer fade
        val halo = modifyAlpha(color, 0.08f, eff) // Barely-there halo

        paint.shader = RadialGradient(
            x, y, radius,
            intArrayOf(core, mid, soft, halo, Color.TRANSPARENT),
            floatArrayOf(0f, 0.20f, 0.50f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun modifyAlpha(color: Int, factor: Float, baseIntensity: Float): Int {
        val alpha = (255f * factor * baseIntensity).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
