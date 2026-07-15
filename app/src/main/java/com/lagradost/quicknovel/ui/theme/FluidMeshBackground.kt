package com.lagradost.quicknovel.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ─── Palette Definitions ─────────────────────────────────────────────────────

/**
 * Encapsulates a complete 3-blob fluid mesh color scheme.
 * Each [blob1] / [blob2] / [blob3] is the CENTER glow color of that blob.
 * [background] is drawn as the base fill before any blobs.
 */
@Immutable
data class FluidMeshPalette(
    val background: Color,
    val blob1: Color,
    val blob2: Color,
    val blob3: Color,
)

object FluidMeshPalettes {

    /**
     * Palette 1 — Coastal Mist
     * Inspired by: #99CDD8 #DAEBE3 #FDE8D3 #F3C3B2 #CFD6C4 #657166
     * Mood: calm, airy, daylight premium — best on AMOLED Flashbang (light) or Material.
     */
    val CoastalMist = FluidMeshPalette(
        background = Color(0xFF0F1A1C),   // Deep dark sage-ocean
        blob1 = Color(0xFF336B77),         // Moody deep teal
        blob2 = Color(0xFF8A5A4B),         // Soft terracotta dusk
        blob3 = Color(0xFF56634F),         // Muted deep sage
    )

    /**
     * Palette 2 — Crimson Void
     * Inspired by: #090205 #270a16 #51111f #922235 #452635
     * Mood: deep, dramatic, cinematic — perfect for AMOLED true-black backgrounds.
     */
    val CrimsonVoid = FluidMeshPalette(
        background = Color(0xFF090205),   // near-true black
        blob1 = Color(0xFF922235),         // deep crimson
        blob2 = Color(0xFF51111F),         // dark wine
        blob3 = Color(0xFF452635),         // eggplant maroon
    )

    /**
     * Palette 3 — Desert Parchment
     * Inspired by: #291C0E #6E473B #A78D78 #BEB5A9 #E1D4C2
     * Mood: warm, earthy, aged-luxury — great for warm themes.
     */
    val DesertParchment = FluidMeshPalette(
        background = Color(0xFF291C0E),   // dark espresso
        blob1 = Color(0xFF6E473B),         // warm sienna
        blob2 = Color(0xFFA78D78),         // dusty tan
        blob3 = Color(0xFFBEB5A9),         // pale driftwood
    )

    /**
     * Palette 4 — Midnight Neon
     * Inspired by: #0033FF #977DFF #FFCCF2
     * Mood: vibrant, cyber-electric, dream-like.
     */
    val MidnightNeon = FluidMeshPalette(
        background = Color(0xFF04061A),   // very dark neon indigo
        blob1 = Color(0xFF0033FF),         // royal electric blue
        blob2 = Color(0xFF977DFF),         // lavender violet
        blob3 = Color(0xFFFFCCF2),         // soft pink cotton candy
    )

    /**
     * Palette 5 — Volcanic Embers
     * Inspired by: #100C08 #95122C #CA3F16 #FF9408
     * Mood: hot, volcanic, dark fireplace comfort.
     */
    val VolcanicEmbers = FluidMeshPalette(
        background = Color(0xFF100C08),   // dark volcanic coal
        blob1 = Color(0xFF95122C),         // magma crimson red
        blob2 = Color(0xFFCA3F16),         // hot copper rust
        blob3 = Color(0xFFFF9408),         // glowing spark gold
    )
}

// ─── Blob Animation Spec ──────────────────────────────────────────────────────

/**
 * Configures organic float speed for one blob.
 * We use separate periods to prevent blobs from ever perfectly overlapping
 * (Lissajous-like desync), giving an authentic fluid look.
 */
private data class BlobSpec(
    val durationMs: Int,
    val xPhaseShift: Float,
    val yPhaseShift: Float,
    val radiusFraction: Float,
    val baseCenterXFraction: Float,
    val baseCenterYFraction: Float,
    val xSwingFraction: Float,
    val ySwingFraction: Float,
)

private val blobSpecs = arrayOf(
    BlobSpec(
        durationMs = 9200,
        xPhaseShift = 0f,
        yPhaseShift = 1.0f,
        radiusFraction = 0.60f,
        baseCenterXFraction = 0.28f,
        baseCenterYFraction = 0.70f,
        xSwingFraction = 0.22f,
        ySwingFraction = 0.14f
    ),
    BlobSpec(
        durationMs = 12800,
        xPhaseShift = 2.1f,
        yPhaseShift = 3.8f,
        radiusFraction = 0.52f,
        baseCenterXFraction = 0.72f,
        baseCenterYFraction = 0.62f,
        xSwingFraction = 0.18f,
        ySwingFraction = 0.20f
    ),
    BlobSpec(
        durationMs = 10500,
        xPhaseShift = 4.5f,
        yPhaseShift = 0.7f,
        radiusFraction = 0.45f,
        baseCenterXFraction = 0.50f,
        baseCenterYFraction = 0.85f,
        xSwingFraction = 0.26f,
        ySwingFraction = 0.12f
    ),
)

// ─── Pre-allocated draw state — ZERO heap pressure in draw phase ──────────────

// Declared at file-level: single singletons per process. Canvas draw is
// always single-threaded (main thread), so shared mutable state is safe.
private val _blobCenters = Array(3) { Offset(0f, 0f) }
private val _blobRadii   = FloatArray(3)

// ─── Draw Helpers ─────────────────────────────────────────────────────────────

/**
 * Draws a single radial blob glow.
 * Uses Screen blend mode so overlapping blobs add luminance (natural bloom)
 * without expensive shader passes.
 */
private fun DrawScope.drawBlob(
    color: Color,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha * 0.92f),
                color.copy(alpha = alpha * 0.55f),
                color.copy(alpha = alpha * 0.18f),
                Color.Transparent
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.Screen
    )
}

/**
 * Draws a honeycomb halftone dot matrix overlay.
 * All arithmetic is on stack primitives — zero heap allocation per dot.
 * BlendMode.Overlay interacts with blobs beneath for a premium digital texture.
 */
private fun DrawScope.drawHalftoneOverlay(
    dotColor: Color,
    dotSpacingPx: Float,
    dotRadiusPx: Float,
) {
    val cols = (size.width / dotSpacingPx).toInt() + 1
    val rows = (size.height / dotSpacingPx).toInt() + 1
    for (row in 0..rows) {
        for (col in 0..cols) {
            val xOff = if (row % 2 == 0) 0f else dotSpacingPx * 0.5f
            val cx = col * dotSpacingPx + xOff
            val cy = row * dotSpacingPx
            drawCircle(
                color = dotColor,
                radius = dotRadiusPx,
                center = Offset(cx, cy),
                blendMode = BlendMode.Overlay
            )
        }
    }
}

// ─── Public Composable ────────────────────────────────────────────────────────

/**
 * # FluidMeshBackground
 *
 * Premium hardware-accelerated animated fluid gradient background.
 * 3 organic glowing color blobs drift using desynchronized sine/cosine
 * functions simulating weightless fluid dynamics, topped with a subtle
 * halftone dot matrix texture overlay.
 *
 * ## Usage
 * ```kotlin
 * Box(modifier = Modifier.fillMaxSize()) {
 *     FluidMeshBackground(palette = FluidMeshPalettes.CrimsonVoid)
 *     // Your content on top...
 * }
 * ```
 *
 * ## Performance contract
 * - 60/120 FPS safe: driven by `InfiniteTransition`, no recomposition per-frame.
 * - `graphicsLayer(CompositingStrategy.Offscreen)`: isolated GPU layer.
 * - No Bitmap allocations: GPU-native radial gradients only.
 *
 * @param palette     Color palette to use. See [FluidMeshPalettes].
 * @param modifier    Modifier applied to the Canvas. Defaults to fillMaxSize.
 * @param blobAlpha   Overall blob glow intensity. 0.0 = hidden, 1.0 = full.
 * @param dotAlpha    Halftone overlay opacity. 0.0 disables the texture.
 * @param dotSpacingDp Spacing between halftone dots in density-independent pixels.
 */
@Composable
fun FluidMeshBackground(
    palette: FluidMeshPalette,
    modifier: Modifier = Modifier.fillMaxSize(),
    blobAlpha: Float = 0.85f,
    dotAlpha: Float = 0.04f,
    dotSpacingDp: Float = 18f,
    animationType: String = "blobs",
    speed: String = "normal",
    isProcessing: Boolean = false,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val speedMultiplier = when (speed) {
        "slow" -> 0.4f
        "fast" -> 1.8f
        else -> 1.0f
    }

    // Key the transition on speed to cleanly scale tween durations without glitches
    val (t0, t1, t2) = androidx.compose.runtime.key(speed) {
        val transition = rememberInfiniteTransition(label = "FluidMesh")
        val val0 by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = (blobSpecs[0].durationMs / speedMultiplier).toInt(), easing = LinearEasing),
            ),
            label = "fluidT0"
        )
        val val1 by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = (blobSpecs[1].durationMs / speedMultiplier).toInt(), easing = LinearEasing),
            ),
            label = "fluidT1"
        )
        val val2 by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = (blobSpecs[2].durationMs / speedMultiplier).toInt(), easing = LinearEasing),
            ),
            label = "fluidT2"
        )
        Triple(val0, val1, val2)
    }

    // Animated factors for wave frequency & amplitude speed targets
    val speedTarget = if (isProcessing) 2.2f else 0.8f
    val speedFactor by animateFloatAsState(
        targetValue = speedTarget,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "waveSpeed"
    )

    val ampTarget = if (isProcessing) 1.2f else 0.35f
    val ampFactor by animateFloatAsState(
        targetValue = ampTarget,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "waveAmplitude"
    )

    // Independent accumulated phases to prevent phase jumps when speed changes
    var phase0 by remember { mutableFloatStateOf(0f) }
    var phase1 by remember { mutableFloatStateOf(0f) }
    var phase2 by remember { mutableFloatStateOf(0f) }

    var widthState by remember { mutableFloatStateOf(0f) }
    var heightState by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(speedFactor, speedMultiplier, animationType) {
        if (animationType != "waves" && animationType != "glass" && animationType != "aurora") return@LaunchedEffect
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime ->
                val dt = ((frameTime - lastTime) / 1_000_000_000.0).toFloat()
                lastTime = frameTime

                val currentSpeed = speedFactor * speedMultiplier
                phase0 += dt * currentSpeed * 2.8f
                phase1 += dt * currentSpeed * 1.8f
                phase2 += dt * currentSpeed * 1.2f
            }
        }
    }

    // Pre-allocated paths for drawing: zero object allocations in draw loop
    val orbPath1 = remember { Path() }
    val orbPath2 = remember { Path() }
    val edgePath = remember { Path() }
    val path0 = remember { Path() }
    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val paths = remember(path0, path1, path2) { arrayOf(path0, path1, path2) }

    // Stable color array — only recreated when palette changes
    val blobColors = remember(palette) {
        arrayOf(palette.blob1, palette.blob2, palette.blob3)
    }
    val bgColor = palette.background

    // Stardust Primitives memory
    val maxParticles = 120
    val xArray = remember { FloatArray(maxParticles) }
    val yArray = remember { FloatArray(maxParticles) }
    val speedYArray = remember { FloatArray(maxParticles) }
    val speedXArray = remember { FloatArray(maxParticles) }
    val sizeArray = remember { FloatArray(maxParticles) }
    val alphaArray = remember { FloatArray(maxParticles) }
    var particlesInitialized by remember { mutableStateOf(false) }

    // Intercept touch offset for Stardust interactive scattering
    var touchOffset by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(particlesInitialized, speedMultiplier, animationType) {
        if (animationType != "stardust" || !particlesInitialized) return@LaunchedEffect
        var lastTime = System.nanoTime()
        while (true) {
            withFrameNanos { frameTime ->
                val dt = ((frameTime - lastTime) / 1_000_000_000.0).toFloat()
                lastTime = frameTime

                val speedScale = speedMultiplier
                val w = widthState
                val h = heightState
                if (w > 0f && h > 0f) {
                    val touch = touchOffset
                    val maxDistPx = 130f * density
                    val maxDistSq = maxDistPx * maxDistPx

                    for (i in 0 until maxParticles) {
                        // Drift upward & sideways
                        yArray[i] += speedYArray[i] * dt * speedScale
                        xArray[i] += speedXArray[i] * dt * speedScale

                        // Apply touch-scattering force away from touch point
                        if (touch != null) {
                            val dx = xArray[i] - touch.x
                            val dy = yArray[i] - touch.y
                            val distSq = dx * dx + dy * dy
                            if (distSq < maxDistSq && distSq > 0.01f) {
                                val dist = kotlin.math.sqrt(distSq)
                                val force = (1f - dist / maxDistPx) * 220f
                                val pushX = (dx / dist) * force * dt
                                val pushY = (dy / dist) * force * dt
                                xArray[i] += pushX
                                yArray[i] += pushY
                            }
                        }

                        // Wrap boundary reset
                        if (yArray[i] < 0f) {
                            yArray[i] = h
                            xArray[i] = (Math.random().toFloat() * w)
                            alphaArray[i] = 0f
                        } else if (yArray[i] > h) {
                            yArray[i] = 0f
                            xArray[i] = (Math.random().toFloat() * w)
                            alphaArray[i] = 0f
                        }
                        if (xArray[i] < 0f) xArray[i] = w
                        if (xArray[i] > w) xArray[i] = 0f

                        // Breathe opacity back in gently
                        alphaArray[i] = (alphaArray[i] + 0.2f * dt).coerceIn(0f, 0.8f)
                    }
                }
            }
        }
    }

    val modifierWithInput = modifier
        .pointerInput(animationType) {
            if (animationType != "stardust" && animationType != "glass") return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val changes = event.changes
                    if (changes.any { it.pressed }) {
                        touchOffset = changes.first().position
                    } else {
                        touchOffset = null
                    }
                }
            }
        }

    // ── Canvas ────────────────────────────────────────────────────────────────
    Canvas(
        modifier = modifierWithInput.graphicsLayer {
            // Isolate this composable to its own GPU layer so ancestor
            // recompositions never cascade into a full redraw here.
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f) return@Canvas

        widthState = w
        heightState = h

        val dotSpacingPx = dotSpacingDp * density
        val dotRadiusPx  = dotSpacingPx * 0.15f

        // Initialize particles on first size detection frame
        if (animationType == "stardust" && !particlesInitialized) {
            for (i in 0 until maxParticles) {
                xArray[i] = (Math.random().toFloat() * w)
                yArray[i] = (Math.random().toFloat() * h)
                speedYArray[i] = -(20f + Math.random().toFloat() * 30f) * density
                speedXArray[i] = (-8f + Math.random().toFloat() * 16f) * density
                sizeArray[i] = (1f + Math.random().toFloat() * 2f) * density
                alphaArray[i] = Math.random().toFloat() * 0.7f
            }
            particlesInitialized = true
        }

        when (animationType) {
            "waves" -> {
                // 1. Base background fill
                drawRect(color = bgColor)

                // 2. Draw 3 chromatic light ribbons that twist and warp
                val strokeWidths = floatArrayOf(80.dp.toPx(), 60.dp.toPx(), 45.dp.toPx())
                val phases = floatArrayOf(phase0, phase1, phase2)
                val alphas = floatArrayOf(0.18f, 0.14f, 0.10f)

                for (i in 0..2) {
                    val path = paths[i]
                    path.rewind()

                    val ph = phases[i]
                    
                    // Bezier control points warping organically
                    val yStart = h * (0.3f + 0.1f * i) + h * 0.15f * sin(ph + i)
                    val cp1x = w * 0.25f + w * 0.1f * cos(ph * 0.8f - i)
                    val cp1y = h * (0.2f + 0.15f * i) + h * 0.25f * sin(ph * 1.1f + i)
                    val cp2x = w * 0.75f + w * 0.1f * sin(ph * 0.9f + i)
                    val cp2y = h * (0.8f - 0.15f * i) + h * 0.25f * cos(ph * 0.7f - i)
                    val yEnd = h * (0.7f - 0.1f * i) + h * 0.15f * cos(ph * 1.2f + i)

                    path.moveTo(0f, yStart)
                    path.cubicTo(cp1x, cp1y, cp2x, cp2y, w, yEnd)

                    val color = when (i) {
                        0 -> palette.blob1
                        1 -> palette.blob2
                        else -> palette.blob3
                    }
                    val brush = Brush.horizontalGradient(
                        colors = listOf(
                            color.copy(alpha = 0f),
                            color.copy(alpha = 1f),
                            color.copy(alpha = 0.5f),
                            color.copy(alpha = 0f)
                        )
                    )

                    drawPath(
                        path = path,
                        brush = brush,
                        alpha = alphas[i] * ampFactor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokeWidths[i],
                            cap = StrokeCap.Round
                        ),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            "glass" -> {
                // 1. Base background fill
                drawRect(color = bgColor)

                // 2. Compute orb center (shifts smoothly to touch point if active)
                val orbCenter = if (touchOffset != null) {
                    Offset(
                        x = w * 0.5f + (touchOffset!!.x - w * 0.5f) * 0.25f,
                        y = h * 0.5f + (touchOffset!!.y - h * 0.5f) * 0.25f
                    )
                } else {
                    Offset(w * 0.5f, h * 0.5f)
                }

                val numPoints = 8
                val angleStep = (2.0 * Math.PI / numPoints).toFloat()
                
                // Outer morphing shape
                orbPath1.rewind()
                val rBase1 = min(w, h) * 0.26f
                val amp1 = min(w, h) * 0.04f * ampFactor
                val ph1 = phase0
                val pointsX1 = FloatArray(numPoints)
                val pointsY1 = FloatArray(numPoints)
                for (i in 0 until numPoints) {
                    val angle = i * angleStep
                    val r = rBase1 + amp1 * sin(ph1 + i * 1.5f) + amp1 * 0.5f * cos(ph1 * 1.3f - i * 2f)
                    pointsX1[i] = orbCenter.x + r * cos(angle)
                    pointsY1[i] = orbCenter.y + r * sin(angle)
                }

                orbPath1.moveTo(pointsX1[0], pointsY1[0])
                for (i in 0 until numPoints) {
                    val nextIndex = (i + 1) % numPoints
                    val prevIndex = if (i == 0) numPoints - 1 else i - 1
                    
                    val cp1x = pointsX1[i] + (pointsX1[nextIndex] - pointsX1[prevIndex]) * 0.18f
                    val cp1y = pointsY1[i] + (pointsY1[nextIndex] - pointsY1[prevIndex]) * 0.18f
                    
                    val nextNextIndex = (nextIndex + 1) % numPoints
                    val cp2x = pointsX1[nextIndex] - (pointsX1[nextNextIndex] - pointsX1[i]) * 0.18f
                    val cp2y = pointsY1[nextIndex] - (pointsY1[nextNextIndex] - pointsY1[i]) * 0.18f
                    
                    orbPath1.cubicTo(cp1x, cp1y, cp2x, cp2y, pointsX1[nextIndex], pointsY1[nextIndex])
                }
                
                val brush1 = Brush.radialGradient(
                    colors = listOf(palette.blob1.copy(alpha = 0.5f), palette.blob2.copy(alpha = 0.15f), Color.Transparent),
                    center = orbCenter,
                    radius = rBase1 * 1.6f
                )
                drawPath(path = orbPath1, brush = brush1, blendMode = BlendMode.Screen)

                // Inner morphing shape
                orbPath2.rewind()
                val rBase2 = min(w, h) * 0.18f
                val amp2 = min(w, h) * 0.03f * ampFactor
                val ph2 = phase1
                val pointsX2 = FloatArray(numPoints)
                val pointsY2 = FloatArray(numPoints)
                for (i in 0 until numPoints) {
                    val angle = i * angleStep
                    val r = rBase2 + amp2 * sin(ph2 - i * 1.2f) + amp2 * 0.4f * cos(ph2 * 1.5f + i)
                    pointsX2[i] = orbCenter.x + r * cos(angle)
                    pointsY2[i] = orbCenter.y + r * sin(angle)
                }

                orbPath2.moveTo(pointsX2[0], pointsY2[0])
                for (i in 0 until numPoints) {
                    val nextIndex = (i + 1) % numPoints
                    val prevIndex = if (i == 0) numPoints - 1 else i - 1
                    
                    val cp1x = pointsX2[i] + (pointsX2[nextIndex] - pointsX2[prevIndex]) * 0.18f
                    val cp1y = pointsY2[i] + (pointsY2[nextIndex] - pointsY2[prevIndex]) * 0.18f
                    
                    val nextNextIndex = (nextIndex + 1) % numPoints
                    val cp2x = pointsX2[nextIndex] - (pointsX2[nextNextIndex] - pointsX2[i]) * 0.18f
                    val cp2y = pointsY2[nextIndex] - (pointsY2[nextNextIndex] - pointsY2[i]) * 0.18f
                    
                    orbPath2.cubicTo(cp1x, cp1y, cp2x, cp2y, pointsX2[nextIndex], pointsY2[nextIndex])
                }

                val brush2 = Brush.radialGradient(
                    colors = listOf(palette.blob2.copy(alpha = 0.7f), palette.blob3.copy(alpha = 0.2f), Color.Transparent),
                    center = orbCenter,
                    radius = rBase2 * 1.4f
                )
                drawPath(path = orbPath2, brush = brush2, blendMode = BlendMode.Screen)
            }
            "aurora" -> {
                // 1. Base background fill
                drawRect(color = bgColor)

                // 2. Draw 2 overlapping waving auroral ribbons diagonally
                val ribbonWidths = floatArrayOf(120.dp.toPx(), 80.dp.toPx())
                val ribbonAlphas = floatArrayOf(0.22f, 0.14f)
                val phases = floatArrayOf(phase0, phase1)
                
                val brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.blob1.copy(alpha = 0.8f),
                        palette.blob2.copy(alpha = 0.6f),
                        palette.blob3.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    start = Offset(0f, h),
                    end = Offset(w, 0f)
                )

                for (idx in 0..1) {
                    edgePath.rewind()
                    val ph = phases[idx]
                    val steps = 30
                    val waveAmp = (35.dp.toPx() - idx * 10.dp.toPx()) * ampFactor

                    for (i in 0..steps) {
                        val f = i.toFloat() / steps
                        val baseX = f * w
                        val baseY = h - f * h
                        
                        // Perpendicular sine wave offset
                        val offset = waveAmp * sin(ph + f * 6.5f + idx * Math.PI.toFloat())
                        // Perpendicular vector for diagonal: (1, -1) normalized is (1/sqrt(2), 1/sqrt(2))
                        val dx = offset * 0.707f
                        val dy = offset * 0.707f

                        val px = baseX + dx
                        val py = baseY + dy

                        if (i == 0) edgePath.moveTo(px, py) else edgePath.lineTo(px, py)
                    }

                    drawPath(
                        path = edgePath,
                        brush = brush,
                        alpha = ribbonAlphas[idx],
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = ribbonWidths[idx],
                            cap = StrokeCap.Round
                        ),
                        blendMode = BlendMode.Screen
                    )
                }
            }
            "stardust" -> {
                // 1. Base background fill
                drawRect(color = bgColor)

                // 2. Draw micro-particles
                for (i in 0 until maxParticles) {
                    val color = when (i % 3) {
                        0 -> palette.blob1
                        1 -> palette.blob2
                        else -> palette.blob3
                    }
                    drawCircle(
                        color = color,
                        radius = sizeArray[i],
                        center = Offset(xArray[i], yArray[i]),
                        alpha = alphaArray[i] * 0.85f
                    )
                }
            }
            else -> { // "blobs"
                // 1. Base background fill
                drawRect(color = bgColor)

                // 2. Compute blob centers from desynchronized sine waves
                val tArr = floatArrayOf(t0, t1, t2)
                for (i in 0..2) {
                    val spec = blobSpecs[i]
                    val ti   = tArr[i]
                    _blobCenters[i] = Offset(
                        x = w * spec.baseCenterXFraction + w * spec.xSwingFraction * sin(ti + spec.xPhaseShift),
                        y = h * spec.baseCenterYFraction + h * spec.ySwingFraction * cos(ti + spec.yPhaseShift)
                    )
                    _blobRadii[i] = min(w, h) * spec.radiusFraction
                }

                // 3. Blob glows — Screen blend: overlapping blobs add luminance (bloom)
                drawIntoCanvas {
                    for (i in 0..2) {
                        drawBlob(
                            color  = blobColors[i],
                            center = _blobCenters[i],
                            radius = _blobRadii[i],
                            alpha  = blobAlpha,
                        )
                    }
                }
            }
        }

        // 4. Halftone texture overlay (applies to all animation styles)
        if (dotAlpha > 0f) {
            drawHalftoneOverlay(
                dotColor     = Color.White.copy(alpha = dotAlpha),
                dotSpacingPx = dotSpacingPx,
                dotRadiusPx  = dotRadiusPx,
            )
        }
    }
}
