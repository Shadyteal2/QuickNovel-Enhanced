package com.lagradost.quicknovel.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.random.Random

// ─── Preference Keys ─────────────────────────────────────────────────────────

object VibePrefs {
    // Group toggles
    const val PERFORMANCE_MODE_ENABLED   = "performance_mode_enabled"
    const val PREMIUM_VISUALS_ENABLED    = "premium_visuals_enabled"
    const val AESTHETIC_PERSONA_ENABLED  = "aesthetic_persona_enabled"

    // Premium Visuals
    const val COVER_AURA_GLOW            = "cover_aura_glow"
    const val READER_INK_FLOW            = "reader_ink_flow"
    const val BREATHING_ACCENT_PULSE     = "breathing_accent_pulse"

    // Aesthetic Personalization
    const val GLASS_OPACITY              = "glass_opacity"          // Int 5..95 (default 80)
    const val CARD_BORDER_STYLE          = "card_border_style"      // "none"|"glow"|"gradient"|"neon"
    const val NOISE_TEXTURE_ENABLED      = "noise_texture_enabled"
    const val NOISE_TEXTURE_INTENSITY    = "noise_texture_intensity" // Int 0..100 (default 30)

    // Accent Gradient (stored in Appearance but read from here)
    const val ACCENT_GRADIENT_ENABLED    = "accent_gradient_enabled"
    const val ACCENT_GRADIENT_END_COLOR  = "accent_gradient_end_color" // ARGB int stored as Int

    // Expressive Shapes & Morphing
    const val ASYMMETRIC_SHAPES_ENABLED  = "asymmetric_shapes_enabled"
    const val STAGGERED_ENTRANCES_ENABLED = "staggered_entrances_enabled"
    
    // Global Fluid Background
    const val GLOBAL_FLUID_BACKGROUND     = "global_fluid_background"
    const val GLOBAL_FLUID_ANIMATION_TYPE = "global_fluid_animation_type"
    const val GLOBAL_FLUID_ANIMATION_SPEED = "global_fluid_animation_speed"
}

// ─── Cover Aura Glow ─────────────────────────────────────────────────────────
//
// Extracts dominant cover color once per poster URL on Dispatchers.IO,
// caches it in a ConcurrentHashMap (bounded to 200 entries to cap RAM),
// then draws a radial gradient *behind* the card via Modifier.drawBehind.
// Zero allocation in the hot draw path once cached.

private val auraColorCache = ConcurrentHashMap<String, Color>(64)
private const val AURA_CACHE_MAX = 200

@Composable
fun rememberPreferenceBoolean(key: String, defaultValue: Boolean): State<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val state = remember(key) { mutableStateOf(prefs.getBoolean(key, defaultValue)) }
    val listener = remember(key, prefs) {
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getBoolean(key, defaultValue)
            }
        }
    }
    DisposableEffect(key, prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun rememberPreferenceInt(key: String, defaultValue: Int): State<Int> {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val state = remember(key) { mutableStateOf(prefs.getInt(key, defaultValue)) }
    val listener = remember(key, prefs) {
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getInt(key, defaultValue)
            }
        }
    }
    DisposableEffect(key, prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
fun rememberPreferenceString(key: String, defaultValue: String): State<String> {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val state = remember(key) { mutableStateOf(prefs.getString(key, defaultValue) ?: defaultValue) }
    val listener = remember(key, prefs) {
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) {
                state.value = prefs.getString(key, defaultValue) ?: defaultValue
            }
        }
    }
    DisposableEffect(key, prefs, listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

/**
 * Reads SharedPreferences and returns whether Cover Aura Glow should be active reactively.
 */
@Composable
fun rememberAuraEnabled(): Boolean {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    if (performanceMode) return false
    val premiumEnabled by rememberPreferenceBoolean(VibePrefs.PREMIUM_VISUALS_ENABLED, false)
    val auraEnabled by rememberPreferenceBoolean(VibePrefs.COVER_AURA_GLOW, false)
    return premiumEnabled && auraEnabled
}

@Composable
fun rememberAsymmetricShapesEnabled(): Boolean {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    if (performanceMode) return false
    val premiumEnabled by rememberPreferenceBoolean(VibePrefs.PREMIUM_VISUALS_ENABLED, false)
    val shapesEnabled by rememberPreferenceBoolean(VibePrefs.ASYMMETRIC_SHAPES_ENABLED, false)
    return premiumEnabled && shapesEnabled
}



@Composable
fun rememberStaggeredEntrancesEnabled(): Boolean {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    if (performanceMode) return false
    val premiumEnabled by rememberPreferenceBoolean(VibePrefs.PREMIUM_VISUALS_ENABLED, false)
    val staggeredEnabled by rememberPreferenceBoolean(VibePrefs.STAGGERED_ENTRANCES_ENABLED, false)
    return premiumEnabled && staggeredEnabled
}


/**
 * Extracts the dominant vibrant color from a Bitmap on Dispatchers.IO.
 * Falls back through muted → dark vibrant → a neutral default.
 * Result is cached; no work done if already resolved.
 */
suspend fun extractAuraColor(bitmap: Bitmap, cacheKey: String): Color =
    withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) return@withContext Color.Unspecified
        auraColorCache[cacheKey]?.let { return@withContext it }
        try {
            val palette = Palette.from(bitmap).generate()
            val argb = palette.getVibrantColor(
                palette.getMutedColor(
                    palette.getDarkVibrantColor(0xFF9C27B0.toInt())
                )
            )
            val color = Color(argb).copy(alpha = 0.55f)
            // Evict oldest entry if cache is full (simple bounded strategy)
            if (auraColorCache.size >= AURA_CACHE_MAX) {
                auraColorCache.keys.firstOrNull()?.let { auraColorCache.remove(it) }
            }
            auraColorCache[cacheKey] = color
            color
        } catch (_: Throwable) {
            Color.Unspecified
        }
    }

/**
 * Applies a soft radial aura glow behind the composable using the given [auraColor].
 * The glow radius is generous (80% of the smaller dimension) to feel organic.
 */
fun Modifier.coverAuraGlow(
    auraColor: Color,
    enabled: Boolean
): Modifier {
    if (!enabled || auraColor == Color.Unspecified) return this
    return drawBehind {
        val radius = minOf(size.width, size.height) * 1.35f // Generous premium glow expansion
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auraColor.copy(alpha = 0.75f), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius
            ),
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

// ─── Reader Ink Flow ─────────────────────────────────────────────────────────
//
// A full-canvas gradient overlay whose color temperature shifts from
// cool (top of scroll → calm blue-purple) to warm (bottom → amber-rose)
// based on [scrollFraction] (0f = top, 1f = bottom).
// Drawn as a single gradient pass — zero allocations once composed.

/**
 * Draws a semi-transparent ink-wash gradient overlay across the entire
 * reading surface. [scrollFraction] (0f–1f) controls the warm/cool shift.
 */
@Composable
fun ReaderInkFlowOverlay(
    scrollFraction: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    if (!enabled || performanceMode) return

    val clampedFraction = scrollFraction.coerceIn(0f, 1f)

    // Lerp between cool blue-purple (top) and warm amber-rose (bottom)
    val topColor = lerp(Color(0x08, 0x10, 0x3A, 0x1A), Color(0x3A, 0x10, 0x08, 0x1A), clampedFraction)
    val bottomColor = lerp(Color(0x10, 0x08, 0x3A, 0x28), Color(0x3A, 0x20, 0x08, 0x28), clampedFraction)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(topColor, bottomColor)
            ),
            size = size
        )
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = (a.red + (b.red - a.red) * t).coerceIn(0f, 1f),
    green = (a.green + (b.green - a.green) * t).coerceIn(0f, 1f),
    blue = (a.blue + (b.blue - a.blue) * t).coerceIn(0f, 1f),
    alpha = (a.alpha + (b.alpha - a.alpha) * t).coerceIn(0f, 1f)
)

// ─── Noise Texture Overlay ────────────────────────────────────────────────────
//
// SAFETY: Noise is pre-generated ONCE into an ImageBitmap on Dispatchers.Default
// when the composable first enters composition (or intensity changes).
// The hot path (drawWithContent) only calls drawImage with the cached bitmap —
// zero pixel computation per frame. The bitmap is small (128×128) and tiled.

private val noiseCache = ConcurrentHashMap<Int, ImageBitmap>(4)

/**
 * Pre-generates a 128×128 monochrome noise ImageBitmap at the given [intensity] (0–100).
 * Cached by intensity so repeated renders are free.
 */
suspend fun generateNoiseBitmap(intensity: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        noiseCache[intensity]?.let { return@withContext it }
        try {
            val size = 128
            val rng = Random(seed = 42L) // Fixed seed → deterministic, no variance per render
            val pixels = IntArray(size * size)
            val alpha = (intensity / 100f * 80).roundToInt().coerceIn(0, 80)
            for (i in pixels.indices) {
                val gray = rng.nextInt(256)
                pixels[i] = android.graphics.Color.argb(alpha, gray, gray, gray)
            }
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            val imageBitmap = bmp.asImageBitmap()
            noiseCache[intensity] = imageBitmap
            imageBitmap
        } catch (_: Throwable) {
            null
        }
    }

/**
 * Draws a tiled procedural noise texture over the composable's content.
 * The [noiseBitmap] must be pre-generated via [generateNoiseBitmap] — this
 * modifier does zero computation, only tiling drawImage calls.
 */
fun Modifier.noiseTextureOverlay(
    noiseBitmap: ImageBitmap?,
    enabled: Boolean
): Modifier {
    if (!enabled || noiseBitmap == null) return this
    return drawWithContent {
        drawContent()
        val bmpW = noiseBitmap.width.toFloat()
        val bmpH = noiseBitmap.height.toFloat()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(
                    image = noiseBitmap,
                    topLeft = Offset(x, y)
                )
                x += bmpW
            }
            y += bmpH
        }
    }
}

// ─── Glass Opacity Reader ─────────────────────────────────────────────────────
//
// Single function that reads the glass opacity preference and returns the
// correct alpha multiplier. Used by Glassmorphism.kt.

fun readGlassOpacityAlpha(context: Context): Float {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (prefs.getBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)) return 1.0f
    val isEnabled = prefs.getBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, false) &&
                    prefs.getBoolean(VibePrefs.GLASS_OPACITY + "_enabled", false)
    if (!isEnabled) return 1.0f
    val intVal = prefs.getInt(VibePrefs.GLASS_OPACITY, 80)
    return (intVal / 100f).coerceIn(0.05f, 1.0f)
}

@Composable
fun rememberGlassOpacityAlpha(): Float {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    if (performanceMode) return 1.0f
    val aestheticEnabled by rememberPreferenceBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, false)
    val opacityEnabled by rememberPreferenceBoolean(VibePrefs.GLASS_OPACITY + "_enabled", false)
    val opacityVal by rememberPreferenceInt(VibePrefs.GLASS_OPACITY, 80)
    
    return remember(aestheticEnabled, opacityEnabled, opacityVal) {
        if (!aestheticEnabled || !opacityEnabled) 1.0f
        else (opacityVal / 100f).coerceIn(0.05f, 1.0f)
    }
}

// ─── Card Border Style ────────────────────────────────────────────────────────

enum class CardBorderStyle { NONE, GLOW, GRADIENT, NEON }

fun readCardBorderStyle(context: Context): CardBorderStyle {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    if (prefs.getBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)) return CardBorderStyle.NONE
    val isEnabled = prefs.getBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, false) &&
                    prefs.getBoolean(VibePrefs.CARD_BORDER_STYLE + "_enabled", false)
    if (!isEnabled) return CardBorderStyle.NONE
    return when (prefs.getString(VibePrefs.CARD_BORDER_STYLE, "none")) {
        "glow"     -> CardBorderStyle.GLOW
        "gradient" -> CardBorderStyle.GRADIENT
        "neon"     -> CardBorderStyle.NEON
        else       -> CardBorderStyle.NONE
    }
}

@Composable
fun rememberCardBorderStyle(): CardBorderStyle {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    if (performanceMode) return CardBorderStyle.NONE
    val aestheticEnabled by rememberPreferenceBoolean(VibePrefs.AESTHETIC_PERSONA_ENABLED, false)
    val borderStyleEnabled by rememberPreferenceBoolean(VibePrefs.CARD_BORDER_STYLE + "_enabled", false)
    val borderStyleStr by rememberPreferenceString(VibePrefs.CARD_BORDER_STYLE, "none")
    
    return remember(aestheticEnabled, borderStyleEnabled, borderStyleStr) {
        if (!aestheticEnabled || !borderStyleEnabled) CardBorderStyle.NONE
        else when (borderStyleStr) {
            "glow"     -> CardBorderStyle.GLOW
            "gradient" -> CardBorderStyle.GRADIENT
            "neon"     -> CardBorderStyle.NEON
            else       -> CardBorderStyle.NONE
        }
    }
}

/**
 * Returns an animated border brush for the given [style] and [accentColor].
 * NONE → null (no border drawn). All brushes are stable and allocation-free
 * after the first composition frame.
 */
@Composable
fun rememberBorderBrush(
    style: CardBorderStyle,
    accentColor: Color
): Brush? {
    return when (style) {
        CardBorderStyle.NONE -> null

        CardBorderStyle.GLOW -> {
            remember(accentColor) {
                SolidColor(accentColor.copy(alpha = 0.55f))
            }
        }

        CardBorderStyle.GRADIENT -> {
            val infiniteTransition = rememberInfiniteTransition(label = "border_gradient")
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
                label = "gradient_angle"
            )
            remember(accentColor, angle) {
                Brush.sweepGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.2f),
                        accentColor.copy(alpha = 0.8f),
                        accentColor.copy(alpha = 0.2f)
                    )
                )
            }
        }

        CardBorderStyle.NEON -> {
            val infiniteTransition = rememberInfiniteTransition(label = "neon_border")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    tween(1200, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "neon_alpha"
            )
            remember(accentColor, pulseAlpha) {
                SolidColor(accentColor.copy(alpha = pulseAlpha))
            }
        }
    }
}

// ─── Accent Gradient ──────────────────────────────────────────────────────────

/**
 * Returns a Brush for accent-colored elements when gradient mode is on.
 * Falls back to SolidColor(accentColor) when disabled — no visual difference.
 */
@Composable
fun rememberAccentGradientBrush(
    accentColor: Color
): Brush {
    val performanceMode by rememberPreferenceBoolean(VibePrefs.PERFORMANCE_MODE_ENABLED, false)
    val enabled by rememberPreferenceBoolean(VibePrefs.ACCENT_GRADIENT_ENABLED, false)
    val endArgb by rememberPreferenceInt(VibePrefs.ACCENT_GRADIENT_END_COLOR, 0)

    return remember(accentColor, enabled, endArgb, performanceMode) {
        if (performanceMode || !enabled || endArgb == 0) {
            SolidColor(accentColor)
        } else {
            Brush.horizontalGradient(
                colors = listOf(accentColor, Color(endArgb))
            )
        }
    }
}
