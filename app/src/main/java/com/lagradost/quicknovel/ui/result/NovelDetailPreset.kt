package com.lagradost.quicknovel.ui.result

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.ui.theme.FluidMeshPalette
import com.lagradost.quicknovel.ui.theme.FluidMeshPalettes

enum class ParticleType {
    NONE,
    GOLD_DUST,
    SAKURA_PETALS,
    TWINKLING_STARS
}

sealed class NovelDetailPreset(
    val name: String,
    val key: String,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val gradientBrush: Brush,
    val backgroundOverlay: Color,
    val particleType: ParticleType,
    val symbolicDraw: (DrawScope.(Color) -> Unit)?,
    /** Optional fluid mesh gradient palette. Null = no animated background. */
    val fluidMeshPalette: FluidMeshPalette? = null,
) {
    object None : NovelDetailPreset(
        name = "None",
        key = "none",
        primaryAccent = Color.Unspecified,
        secondaryAccent = Color.Unspecified,
        gradientBrush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
        backgroundOverlay = Color.Transparent,
        particleType = ParticleType.NONE,
        symbolicDraw = null,
        fluidMeshPalette = null
    )

    object GoldenFeather : NovelDetailPreset(
        name = "Golden Feather",
        key = "golden",
        primaryAccent = Color(0xFFE5A93C),
        secondaryAccent = Color(0xFFB8860B),
        gradientBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFF7D070), Color(0xFFC5922C))
        ),
        backgroundOverlay = Color(0x0DE5A93C),
        particleType = ParticleType.GOLD_DUST,
        fluidMeshPalette = FluidMeshPalettes.DesertParchment,
        symbolicDraw = { color ->
            val w = size.width
            val h = size.height
            
            // Draw quill shaft
            val quillPath = Path().apply {
                moveTo(w * 0.15f, h * 0.85f)
                quadraticBezierTo(w * 0.5f, h * 0.5f, w * 0.85f, h * 0.15f)
            }
            drawPath(
                path = quillPath,
                color = color.copy(alpha = 0.8f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Draw feather vanes (leaf shape with transparency)
            val vanePath = Path().apply {
                moveTo(w * 0.85f, h * 0.15f)
                cubicTo(w * 0.95f, h * 0.45f, w * 0.75f, h * 0.75f, w * 0.18f, h * 0.82f)
                lineTo(w * 0.18f, h * 0.82f)
                cubicTo(w * 0.25f, h * 0.55f, w * 0.55f, h * 0.25f, w * 0.85f, h * 0.15f)
                close()
            }
            drawPath(
                path = vanePath,
                color = color.copy(alpha = 0.12f)
            )
            drawPath(
                path = vanePath,
                color = color.copy(alpha = 0.45f),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Add tiny diagonal strands along the feather
            for (i in 3..7) {
                val fraction = i / 10f
                val xBase = w * (0.15f + fraction * 0.7f)
                val yBase = h * (0.85f - fraction * 0.7f)
                val length = w * 0.12f
                
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = Offset(xBase, yBase),
                    end = Offset(xBase + length, yBase + length * 0.3f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = Offset(xBase, yBase),
                    end = Offset(xBase - length * 0.3f, yBase - length),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    )

    object CherryBlossom : NovelDetailPreset(
        name = "Cherry Blossom",
        key = "cherry",
        primaryAccent = Color(0xFFFF8DA1),
        secondaryAccent = Color(0xFFD94D6C),
        gradientBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFABC0), Color(0xFFE25B7E))
        ),
        backgroundOverlay = Color(0x0DFF8DA1),
        particleType = ParticleType.SAKURA_PETALS,
        fluidMeshPalette = FluidMeshPalettes.CoastalMist,
        symbolicDraw = { color ->
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val centerY = h / 2f
            val radius = minOf(w, h) * 0.4f
            
            // Draw 5 sakura petals rotated radially
            for (i in 0 until 5) {
                val angle = i * 72f
                val petalPath = Path().apply {
                    moveTo(centerX, centerY)
                    val cp1X = centerX - radius * 0.4f
                    val cp1Y = centerY - radius * 0.6f
                    val cp2X = centerX + radius * 0.4f
                    val cp2Y = centerY - radius * 0.6f
                    
                    val tipLeftX = centerX - radius * 0.15f
                    val tipLeftY = centerY - radius * 0.95f
                    val tipNotchX = centerX
                    val tipNotchY = centerY - radius * 0.85f
                    val tipRightX = centerX + radius * 0.15f
                    val tipRightY = centerY - radius * 0.95f
                    
                    cubicTo(cp1X, cp1Y, cp1X, tipLeftY, tipLeftX, tipLeftY)
                    lineTo(tipNotchX, tipNotchY)
                    lineTo(tipRightX, tipRightY)
                    cubicTo(cp2X, tipRightY, cp2X, cp2Y, centerX, centerY)
                    close()
                }
                
                withTransform({
                    rotate(angle, Offset(centerX, centerY))
                }) {
                    drawPath(
                        path = petalPath,
                        color = color.copy(alpha = 0.22f)
                    )
                    drawPath(
                        path = petalPath,
                        color = color.copy(alpha = 0.55f),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }
            
            // Draw stamen filaments from center
            for (j in 0 until 8) {
                val stamenAngle = j * 45f
                val angleRad = Math.toRadians(stamenAngle.toDouble())
                val endX = centerX + (radius * 0.35f * Math.cos(angleRad)).toFloat()
                val endY = centerY + (radius * 0.35f * Math.sin(angleRad)).toFloat()
                
                drawLine(
                    color = color.copy(alpha = 0.6f),
                    start = Offset(centerX, centerY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = color.copy(alpha = 0.8f),
                    radius = 2.dp.toPx(),
                    center = Offset(endX, endY)
                )
            }
        }
    )

    object CosmicAbyss : NovelDetailPreset(
        name = "Cosmic Abyss",
        key = "cosmic",
        primaryAccent = Color(0xFF8A9AEC),
        secondaryAccent = Color(0xFF3CD1C2),
        gradientBrush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF8A9AEC), Color(0xFF62B6CB), Color(0xFF3CD1C2))
        ),
        backgroundOverlay = Color(0x0C8A9AEC),
        particleType = ParticleType.TWINKLING_STARS,
        fluidMeshPalette = FluidMeshPalettes.CrimsonVoid,
        symbolicDraw = { color ->
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val centerY = h / 2f
            val maxRadius = minOf(w, h) * 0.45f
            
            // Draw a four-pointed starburst path
            val starPath = Path().apply {
                moveTo(centerX, centerY - maxRadius)
                quadraticBezierTo(centerX, centerY, centerX + maxRadius, centerY)
                quadraticBezierTo(centerX, centerY, centerX, centerY + maxRadius)
                quadraticBezierTo(centerX, centerY, centerX - maxRadius, centerY)
                quadraticBezierTo(centerX, centerY, centerX, centerY - maxRadius)
                close()
            }
            
            drawPath(
                path = starPath,
                color = color.copy(alpha = 0.22f)
            )
            drawPath(
                path = starPath,
                color = color.copy(alpha = 0.75f),
                style = Stroke(width = 1.5.dp.toPx())
            )
            
            // Draw smaller star rotated 45 degrees
            val innerStarPath = Path().apply {
                val r = maxRadius * 0.4f
                moveTo(centerX, centerY - r)
                quadraticBezierTo(centerX, centerY, centerX + r, centerY)
                quadraticBezierTo(centerX, centerY, centerX, centerY + r)
                quadraticBezierTo(centerX, centerY, centerX - r, centerY)
                quadraticBezierTo(centerX, centerY, centerX, centerY - r)
                close()
            }
            
            withTransform({
                rotate(45f, Offset(centerX, centerY))
            }) {
                drawPath(
                    path = innerStarPath,
                    color = color.copy(alpha = 0.45f)
                )
                drawPath(
                    path = innerStarPath,
                    color = color.copy(alpha = 0.85f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            
            // Soft center circular aura glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.5f
                ),
                radius = maxRadius * 0.5f,
                center = Offset(centerX, centerY)
            )
        }
    )

    companion object {
        fun fromKey(key: String?): NovelDetailPreset {
            return when (key) {
                "golden" -> GoldenFeather
                "cherry" -> CherryBlossom
                "cosmic" -> CosmicAbyss
                else -> None
            }
        }
    }
}
