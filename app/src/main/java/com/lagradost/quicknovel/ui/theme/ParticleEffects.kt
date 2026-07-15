package com.lagradost.quicknovel.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.ui.result.ParticleType
import kotlin.random.Random

private data class Particle(
    val x: Float,
    val y: Float,
    val speedX: Float,
    val speedY: Float,
    val radius: Float,
    val alpha: Float,
    val maxAlpha: Float,
    val angle: Float,
    val speedRotation: Float,
    val isSecondary: Boolean = false
)

@Composable
fun ParticleEffect(
    type: ParticleType,
    color: Color,
    modifier: Modifier = Modifier,
    secondaryColor: Color = color
) {
    if (type == ParticleType.NONE) return

    var particles by remember(type) {
        mutableStateOf(emptyList<Particle>())
    }

    LaunchedEffect(type) {
        val list = mutableListOf<Particle>()
        val count = when (type) {
            ParticleType.GOLD_DUST -> 20
            ParticleType.SAKURA_PETALS -> 15
            ParticleType.TWINKLING_STARS -> 18
            else -> 0
        }
        for (i in 0 until count) {
            list.add(
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speedX = when (type) {
                        ParticleType.GOLD_DUST -> (Random.nextFloat() - 0.5f) * 0.006f
                        ParticleType.SAKURA_PETALS -> (Random.nextFloat() * 0.008f + 0.004f)
                        ParticleType.TWINKLING_STARS -> 0f
                        else -> 0f
                    },
                    speedY = when (type) {
                        ParticleType.GOLD_DUST -> -(Random.nextFloat() * 0.01f + 0.005f)
                        ParticleType.SAKURA_PETALS -> (Random.nextFloat() * 0.012f + 0.006f)
                        ParticleType.TWINKLING_STARS -> 0f
                        else -> 0f
                    },
                    radius = when (type) {
                        ParticleType.GOLD_DUST -> Random.nextFloat() * 3f + 1.5f
                        ParticleType.SAKURA_PETALS -> Random.nextFloat() * 5f + 3f
                        ParticleType.TWINKLING_STARS -> Random.nextFloat() * 2f + 1.5f
                        else -> 0f
                    },
                    alpha = Random.nextFloat(),
                    maxAlpha = Random.nextFloat() * 0.5f + 0.3f,
                    angle = if (type == ParticleType.SAKURA_PETALS) Random.nextFloat() * 360f else 0f,
                    speedRotation = if (type == ParticleType.SAKURA_PETALS) (Random.nextFloat() - 0.5f) * 2f else 0f,
                    isSecondary = Random.nextBoolean()
                )
            )
        }
        particles = list

        val fps = 60
        val frameDuration = 1000L / fps
        while (true) {
            val current = particles.map { p ->
                val newX = (p.x + p.speedX + 1f) % 1f
                val newY = (p.y + p.speedY + 1f) % 1f
                
                val newAlpha = when (type) {
                    ParticleType.TWINKLING_STARS -> {
                        val delta = (Random.nextFloat() - 0.5f) * 0.06f
                        (p.alpha + delta).coerceIn(0.1f, p.maxAlpha)
                    }
                    else -> p.alpha
                }

                val newAngle = p.angle + p.speedRotation

                p.copy(x = newX, y = newY, alpha = newAlpha, angle = newAngle)
            }
            particles = current
            kotlinx.coroutines.delay(frameDuration)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w == 0f || h == 0f || particles.isEmpty()) return@Canvas

        particles.forEach { p ->
            val px = p.x * w
            val py = p.y * h
            val pRadius = p.radius.dp.toPx()

            when (type) {
                ParticleType.GOLD_DUST -> {
                    drawCircle(
                        color = color.copy(alpha = p.alpha * 0.6f),
                        radius = pRadius,
                        center = Offset(px, py)
                    )
                    drawCircle(
                        color = color.copy(alpha = p.alpha * 0.2f),
                        radius = pRadius * 2.2f,
                        center = Offset(px, py)
                    )
                }

                ParticleType.SAKURA_PETALS -> {
                    withTransform({
                        rotate(p.angle, Offset(px, py))
                    }) {
                        val petalPath = Path().apply {
                            val r = pRadius
                            moveTo(px, py - r)
                            quadraticBezierTo(px - r * 0.8f, py - r * 0.2f, px, py + r)
                            quadraticBezierTo(px + r * 0.8f, py - r * 0.2f, px, py - r)
                            close()
                        }
                        drawPath(
                            path = petalPath,
                            color = color.copy(alpha = p.maxAlpha * 0.65f)
                        )
                        drawPath(
                            path = petalPath,
                            color = color.copy(alpha = p.maxAlpha * 0.9f),
                            style = Stroke(width = 0.75.dp.toPx())
                        )
                    }
                }

                ParticleType.TWINKLING_STARS -> {
                    val starPath = Path().apply {
                        val r = pRadius
                        moveTo(px, py - r * 1.5f)
                        quadraticBezierTo(px, py, px + r * 1.5f, py)
                        quadraticBezierTo(px, py, px, py + r * 1.5f)
                        quadraticBezierTo(px, py, px - r * 1.5f, py)
                        quadraticBezierTo(px, py, px, py - r * 1.5f)
                        close()
                    }
                    val starColor = if (p.isSecondary) secondaryColor else color
                    drawPath(
                        path = starPath,
                        color = starColor.copy(alpha = p.alpha)
                    )
                }

                else -> {}
            }
        }
    }
}
