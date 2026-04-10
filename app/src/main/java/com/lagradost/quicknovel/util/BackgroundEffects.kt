package com.lagradost.quicknovel.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.view.isVisible
import coil3.load
import coil3.request.crossfade
import coil3.request.transformations
import coil3.transform.Transformation
import com.lagradost.quicknovel.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt



enum class BackgroundEffectMode(val value: String) {
    NONE("none"),
    NOIR("noir"),
    SEPIA("sepia"),
    BLOCKIFY("blockify"),
    THRESHOLD("threshold"),
    EDGE_DETECTION("edge_detection"),
    DOTS("dots"),
    DITHERING("dithering"),
    VORONOI("voronoi"),
    CLASSIC("classic"),
    GRAIN("grain"),
    FILM("film"),
    DREAM("dream");

    companion object {
        fun from(value: String?): BackgroundEffectMode {
            return when (value) {
                NONE.value -> NONE
                NOIR.value -> NOIR
                SEPIA.value -> SEPIA
                BLOCKIFY.value -> BLOCKIFY
                THRESHOLD.value -> THRESHOLD
                EDGE_DETECTION.value -> EDGE_DETECTION
                DOTS.value -> DOTS
                DITHERING.value -> DITHERING
                VORONOI.value -> VORONOI
                CLASSIC.value,
                GRAIN.value,
                FILM.value,
                DREAM.value,
                null,
                "" -> NONE

                else -> NOIR
            }
        }

        fun activeModes(): List<BackgroundEffectMode> = listOf(
            NONE,
            NOIR,
            SEPIA,
            BLOCKIFY,
            THRESHOLD,
            EDGE_DETECTION,
            DOTS,
            DITHERING,
            VORONOI,
        )
    }
}

data class BackgroundEffectState(
    val mode: BackgroundEffectMode,
    val blur: Int,
    val dim: Int,
    val grain: Int,
    val vignette: Int,
)

fun SharedPreferences.getBackgroundEffectState(context: Context): BackgroundEffectState {
    return BackgroundEffectState(
        mode = BackgroundEffectMode.from(getString(context.getString(R.string.background_effect_mode_key), BackgroundEffectMode.NONE.value)),
        blur = getSafeInt(context.getString(R.string.background_blur_key), 0),
        dim = getSafeInt(context.getString(R.string.background_dim_key), 0),
        grain = getSafeInt(context.getString(R.string.background_grain_key), 0),
        vignette = getSafeInt(context.getString(R.string.background_vignette_key), 0),
    )
}

fun backgroundEffectLabels(context: Context): List<Pair<String, String>> {
    return BackgroundEffectMode.activeModes().map { mode ->
        mode.displayLabel(context) to mode.value
    }
}

fun backgroundEffectDisplayLabel(context: Context, rawValue: String?): String {
    return BackgroundEffectMode.from(rawValue).displayLabel(context)
}

fun bindBackgroundEffects(
    context: Context,
    imageView: ImageView,
    dimView: View,
    lightScrimView: View,
    grainView: View,
    vignetteView: View,
    imageUri: String?,
    enabled: Boolean,
    state: BackgroundEffectState,
) {
    if (!enabled || imageUri.isNullOrBlank()) {
        imageView.isVisible = false
        dimView.isVisible = false
        lightScrimView.isVisible = false
        grainView.isVisible = false
        vignetteView.isVisible = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            imageView.setRenderEffect(null)
        }
        imageView.colorFilter = null
        return
    }

    val activeMode = BackgroundEffectMode.from(state.mode.value)
    val requestTransformations = buildList {
        when (activeMode) {
            BackgroundEffectMode.NONE -> Unit
            BackgroundEffectMode.CLASSIC,
            BackgroundEffectMode.GRAIN,
            BackgroundEffectMode.FILM,
            BackgroundEffectMode.DREAM,
            BackgroundEffectMode.NOIR -> add(NoirTransformation())
            BackgroundEffectMode.SEPIA -> add(SepiaTransformation())
            BackgroundEffectMode.BLOCKIFY -> add(BlockifyTransformation())
            BackgroundEffectMode.THRESHOLD -> add(ThresholdTransformation())
            BackgroundEffectMode.EDGE_DETECTION -> add(EdgeDetectionTransformation())
            BackgroundEffectMode.DOTS -> add(DotsTransformation())
            BackgroundEffectMode.DITHERING -> add(DitheringTransformation())
            BackgroundEffectMode.VORONOI -> add(VoronoiTransformation())
        }

        if (state.grain > 0) {
            add(GrainTransformation(state.grain))
        }
    }

    imageView.isVisible = true
    dimView.isVisible = true
    lightScrimView.isVisible = true
    grainView.isVisible = false
    vignetteView.isVisible = state.vignette > 0

    imageView.load(imageUri) {
        crossfade(true)
        if (requestTransformations.isNotEmpty()) {
            transformations(*requestTransformations.toTypedArray())
        }
    }

    imageView.colorFilter = null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        imageView.setRenderEffect(
            if (state.blur > 0) {
                RenderEffect.createBlurEffect(
                    state.blur.toFloat(),
                    state.blur.toFloat(),
                    Shader.TileMode.CLAMP
                )
            } else {
                null
            }
        )
    }

    dimView.alpha = state.dim / 100f
    lightScrimView.alpha = 0f

    // The vignette is intentionally much stronger than the previous pass.
    vignetteView.alpha = vignetteAlpha(state.vignette)
}

private fun BackgroundEffectMode.displayLabel(context: Context): String {
    val res = context.resources
    return when (this) {
        BackgroundEffectMode.NONE -> res.getString(R.string.background_effect_none)
        BackgroundEffectMode.NOIR -> res.getString(R.string.background_effect_noir)
        BackgroundEffectMode.SEPIA -> res.getString(R.string.background_effect_sepia)
        BackgroundEffectMode.BLOCKIFY -> res.getString(R.string.background_effect_blockify)
        BackgroundEffectMode.THRESHOLD -> res.getString(R.string.background_effect_threshold)
        BackgroundEffectMode.EDGE_DETECTION -> res.getString(R.string.background_effect_edge_detection)
        BackgroundEffectMode.DOTS -> res.getString(R.string.background_effect_dots)
        BackgroundEffectMode.DITHERING -> res.getString(R.string.background_effect_dithering)
        BackgroundEffectMode.VORONOI -> res.getString(R.string.background_effect_voronoi)
        BackgroundEffectMode.CLASSIC -> res.getString(R.string.background_effect_none)
        BackgroundEffectMode.GRAIN -> res.getString(R.string.background_effect_none)
        BackgroundEffectMode.FILM -> res.getString(R.string.background_effect_none)
        BackgroundEffectMode.DREAM -> res.getString(R.string.background_effect_none)
    }
}

private fun vignetteAlpha(vignette: Int): Float {
    if (vignette <= 0) return 0f
    return (0.18f + (vignette / 100f) * 0.82f).coerceIn(0f, 1f)
}

private fun noirToneMatrix(): ColorMatrix {
    return ColorMatrix().apply {
        setSaturation(0f)
        postConcat(contrastMatrix(1.22f))
    }
}

private fun sepiaToneMatrix(): ColorMatrix {
    return ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private fun contrastMatrix(contrast: Float): ColorMatrix {
    val translate = (1f - contrast) * 128f
    return ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

private class NoirTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return applyMatrix(input, noirToneMatrix())
    }
}

private class SepiaTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return applyMatrix(input, sepiaToneMatrix())
    }
}

private class BlockifyTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val minDim = min(input.width, input.height)
            val blockSize = (minDim / 180).coerceIn(7, 11)
            val smallW = max(1, input.width / blockSize)
            val smallH = max(1, input.height / blockSize)
            val reduced = input.scale(smallW, smallH, false)
            Bitmap.createScaledBitmap(reduced, input.width, input.height, false)
        }
    }
}

private class VoronoiTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val work = prepareWorkingBitmap(input, 960)
            val minDim = min(work.width, work.height)
            val cellSize = (minDim / 32).coerceIn(18, 36)
            val result = voronoiBitmap(work, cellSize)
            if (work === input) {
                result
            } else {
                Bitmap.createScaledBitmap(result, input.width, input.height, true)
            }
        }
    }
}

private data class SeedPoint(
    val x: Int,
    val y: Int,
    val r: Int,
    val g: Int,
    val b: Int,
)

private fun prepareWorkingBitmap(input: Bitmap, maxEdge: Int): Bitmap {
    val largest = max(input.width, input.height)
    if (largest <= maxEdge) return input
    val ratio = maxEdge.toFloat() / largest.toFloat()
    val w = max(1, (input.width * ratio).roundToInt())
    val h = max(1, (input.height * ratio).roundToInt())
    return input.scale(w, h, false)
}

private fun voronoiBitmap(input: Bitmap, cellSize: Int): Bitmap {
    val w = input.width
    val h = input.height
    val cellsX = (w + cellSize - 1) / cellSize
    val cellsY = (h + cellSize - 1) / cellSize
    val seeds = Array(cellsY) { row ->
        Array(cellsX) { col ->
            val baseX = col * cellSize
            val baseY = row * cellSize
            val seedX = (baseX + cellSize * 0.5f + pseudoNoise(col, row, 91) * cellSize * 0.24f)
                .roundToInt().coerceIn(0, w - 1)
            val seedY = (baseY + cellSize * 0.5f + pseudoNoise(col, row, 129) * cellSize * 0.24f)
                .roundToInt().coerceIn(0, h - 1)
            val color = averageCellColor(input, baseX, baseY, cellSize)
            SeedPoint(seedX, seedY, Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    val output = createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(w * h)

    for (y in 0 until h) {
        for (x in 0 until w) {
            val cx = (x / cellSize).coerceIn(0, cellsX - 1)
            val cy = (y / cellSize).coerceIn(0, cellsY - 1)

            var best = Float.MAX_VALUE
            var second = Float.MAX_VALUE
            var bestSeed = seeds[cy][cx]

            for (ny in max(0, cy - 1)..min(cellsY - 1, cy + 1)) {
                for (nx in max(0, cx - 1)..min(cellsX - 1, cx + 1)) {
                    val seed = seeds[ny][nx]
                    val dist = dist2(x, y, seed.x, seed.y)
                    if (dist < best) {
                        second = best
                        best = dist
                        bestSeed = seed
                    } else if (dist < second) {
                        second = dist
                    }
                }
            }

            val boundaryStrength = ((second - best) / (cellSize.toFloat() * cellSize.toFloat()))
                .coerceIn(0f, 1f)
            val edgeProximity = 1f - boundaryStrength
            val baseBrightness = ((bestSeed.r + bestSeed.g + bestSeed.b) / 3f) / 255f
            val fillBoost = (0.96f + boundaryStrength * 0.04f).coerceIn(0.96f, 1f)
            val r = clamp((bestSeed.r * fillBoost).roundToInt())
            val g = clamp((bestSeed.g * fillBoost).roundToInt())
            val b = clamp((bestSeed.b * fillBoost).roundToInt())
            pixels[y * w + x] = if (edgeProximity > 0.78f) {
                Color.argb(255, r, g, b)
            } else {
                val edgeShade = (baseBrightness * 255f * 0.10f).roundToInt()
                Color.argb(255, edgeShade, edgeShade, edgeShade)
            }
        }
    }

    output.setPixels(pixels, 0, w, 0, 0, w, h)
    return output
}

private fun averageCellColor(input: Bitmap, startX: Int, startY: Int, cellSize: Int): Int {
    val endX = min(input.width, startX + cellSize)
    val endY = min(input.height, startY + cellSize)
    val stepX = max(1, cellSize / 3)
    val stepY = max(1, cellSize / 3)
    var r = 0
    var g = 0
    var b = 0
    var count = 0
    var y = startY
    while (y < endY) {
        var x = startX
        while (x < endX) {
            val color = input.getPixel(x, y)
            r += Color.red(color)
            g += Color.green(color)
            b += Color.blue(color)
            count++
            x += stepX
        }
        y += stepY
    }
    if (count == 0) {
        return input.getPixel(startX.coerceIn(0, input.width - 1), startY.coerceIn(0, input.height - 1))
    }
    return Color.argb(255, r / count, g / count, b / count)
}

private class DitheringTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val out = ensureMutableCopy(input)
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val x = i % out.width
                val y = i / out.width
                val gray = luminance(c)
                val threshold = ((BAYER_8X8[y and 7][x and 7] + 0.5f) / 64f) * 255f
                val boosted = (gray * 1.05f).coerceIn(0f, 255f)
                val value = if (boosted >= threshold) 255 else 0
                pixels[i] = Color.argb(Color.alpha(c), value, value, value)
            }
            out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            out
        }
    }
}

private val BAYER_8X8 = arrayOf(
    intArrayOf(0, 48, 12, 60, 3, 51, 15, 63),
    intArrayOf(32, 16, 44, 28, 35, 19, 47, 31),
    intArrayOf(8, 56, 4, 52, 11, 59, 7, 55),
    intArrayOf(40, 24, 36, 20, 43, 27, 39, 23),
    intArrayOf(2, 50, 14, 62, 1, 49, 13, 61),
    intArrayOf(34, 18, 46, 30, 33, 17, 45, 29),
    intArrayOf(10, 58, 6, 54, 9, 57, 5, 53),
    intArrayOf(42, 26, 38, 22, 41, 25, 37, 21),
)

private fun dist2(x1: Int, y1: Int, x2: Int, y2: Int): Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return (dx * dx + dy * dy).toFloat()
}

private class ThresholdTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val out = ensureMutableCopy(input)
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val x = i % out.width
                val y = i / out.width
                val luma = luminance(c) + pseudoNoise(x, y, 11) * 28f
                val value = if (luma >= 132f) 255 else 0
                pixels[i] = Color.argb(Color.alpha(c), value, value, value)
            }
            out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            out
        }
    }
}

private class EdgeDetectionTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val gray = IntArray(input.width * input.height)
            val pixels = IntArray(input.width * input.height)
            input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
            for (i in pixels.indices) {
                gray[i] = luminance(pixels[i]).roundToInt()
            }

            val out = createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
            val result = IntArray(gray.size)
            val w = input.width
            val h = input.height
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    val gx =
                        -gray[idx - w - 1] - 2 * gray[idx - 1] - gray[idx + w - 1] +
                        gray[idx - w + 1] + 2 * gray[idx + 1] + gray[idx + w + 1]
                    val gy =
                        -gray[idx - w - 1] - 2 * gray[idx - w] - gray[idx - w + 1] +
                        gray[idx + w - 1] + 2 * gray[idx + w] + gray[idx + w + 1]
                    val edge = hypot(gx.toDouble(), gy.toDouble()).toFloat()
                    val intensity = ((edge / 3.8f) - 26f).coerceIn(0f, 255f).roundToInt()
                    result[idx] = Color.argb(255, intensity, intensity, intensity)
                }
            }
            for (x in 0 until w) {
                result[x] = Color.BLACK
                result[(h - 1) * w + x] = Color.BLACK
            }
            for (y in 0 until h) {
                result[y * w] = Color.BLACK
                result[y * w + (w - 1)] = Color.BLACK
            }
            out.setPixels(result, 0, w, 0, 0, w, h)
            out
        }
    }
}

private class DotsTransformation : Transformation() {
    override val cacheKey: String = javaClass.name

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val cell = max(4, min(input.width, input.height) / 72)
            val output = createBitmap(input.width, input.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            canvas.drawColor(Color.BLACK)

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            val pixels = IntArray(cell * cell)

            var y = 0
            while (y < input.height) {
                var x = 0
                while (x < input.width) {
                    val sampleW = min(cell, input.width - x)
                    val sampleH = min(cell, input.height - y)
                    input.getPixels(pixels, 0, cell, x, y, sampleW, sampleH)

                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (py in 0 until sampleH) {
                        for (px in 0 until sampleW) {
                            val c = pixels[py * cell + px]
                            r += Color.red(c)
                            g += Color.green(c)
                            b += Color.blue(c)
                            count++
                        }
                    }
                    if (count > 0) {
                        r /= count
                        g /= count
                        b /= count
                        val bright = ((r + g + b) / 3f) / 255f
                        val radius = (cell * 0.48f * bright).coerceAtLeast(0.8f)
                        paint.color = Color.argb(255, r, g, b)
                        canvas.drawCircle(
                            x + sampleW / 2f,
                            y + sampleH / 2f,
                            radius,
                            paint
                        )
                    }
                    x += cell
                }
                y += cell
            }
            output
        }
    }
}

private class GrainTransformation(private val strength: Int) : Transformation() {
    override val cacheKey: String = "${javaClass.name}-$strength"

    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        return withContext(Dispatchers.Default) {
            val out = ensureMutableCopy(input)
            val pixels = IntArray(out.width * out.height)
            out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            val amount = (strength / 100f) * 48f
            for (i in pixels.indices) {
                val x = i % out.width
                val y = i / out.width
                val base = pixels[i]
                val noise = pseudoNoise(x, y, strength) * amount
                val r = clamp(Color.red(base) + noise.roundToInt())
                val g = clamp(Color.green(base) + noise.roundToInt())
                val b = clamp(Color.blue(base) + noise.roundToInt())
                pixels[i] = Color.argb(Color.alpha(base), r, g, b)
            }
            out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
            out
        }
    }
}

private fun applyMatrix(input: Bitmap, matrix: ColorMatrix): Bitmap {
    val out = ensureMutableCopy(input)
    val pixels = IntArray(out.width * out.height)
    out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
    val filter = ColorMatrixColorFilter(matrix)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = filter
    }
    val canvas = android.graphics.Canvas(out)
    canvas.drawBitmap(input, 0f, 0f, paint)
    return out
}

private fun ensureMutableCopy(input: Bitmap): Bitmap {
    val config = input.config ?: Bitmap.Config.ARGB_8888
    return input.copy(config, true) ?: input.scale(input.width, input.height, false)
}

private fun luminance(color: Int): Float {
    return (Color.red(color) * 0.299f) + (Color.green(color) * 0.587f) + (Color.blue(color) * 0.114f)
}

private fun pseudoNoise(x: Int, y: Int, seed: Int): Float {
    var n = x * 374761393 + y * 668265263 + seed * 362437
    n = (n xor (n ushr 13)) * 1274126177
    n = n xor (n ushr 16)
    return ((n and 0x7fffffff) / 1073741824f) - 1f
}

private fun clamp(value: Int): Int = value.coerceIn(0, 255)
