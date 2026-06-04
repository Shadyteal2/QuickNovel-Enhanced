package com.lagradost.quicknovel.ui.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.UsageStatsManager
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Highly optimized, lightweight particle for the Canvas particle system.
 * Keeps fields mutable to avoid memory allocations in the hot path.
 */
private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var alpha: Float,
    val color: Color,
    var isExplosive: Boolean = false,
    var decay: Float = 1.0f
)

@Composable
fun AboutSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    // ─── Reading Statistics (Developer Shrine) ───────────────────────────
    val totalReadingTime = remember { context.getKey<Long>("TOTAL_READING_TIME", 0L) ?: 0L }
    val chaptersRead = remember { context.getKey<Int>("TOTAL_CHAPTERS_READ", 0) ?: 0 }
    val activeStreak = remember { UsageStatsManager.getActiveStreak(context) }
    val bestStreak = remember { context.getKey<Int>("BEST_STREAK", 0) ?: 0 }
    val customizationCount = remember { context.getKey<Int>("CUSTOMIZATION_COUNT", 0) ?: 0 }
    val midnightOilCount = remember { context.getKey<Int>("MIDNIGHT_OIL_COUNT", 0) ?: 0 }

    // ─── State Management ────────────────────────────────────────────────
    var tapCount by remember { mutableStateOf(0) }
    var isAwakened by remember { mutableStateOf(false) }
    var explosionTriggered by remember { mutableStateOf(false) }
    
    // Tap count reset timer
    LaunchedEffect(tapCount) {
        if (tapCount > 0 && tapCount < 7) {
            delay(2000)
            tapCount = 0
        }
    }

    // Handle awakening transformation
    LaunchedEffect(isAwakened) {
        if (isAwakened) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            explosionTriggered = true
        }
    }

    // ─── Particle Tick / High Performance Loop ──────────────────────────
    var frameTick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { time ->
                frameTick = time
            }
        }
    }

    // Stable particles list
    val particles = remember { mutableStateListOf<Particle>() }
    var particlesInitialized by remember { mutableStateOf(false) }

    // ─── Glowing Blob Animations (Spatial Depth) ─────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "spatial_glow")
    
    // Dynamic color shift based on mode
    val blobColor1 = if (isAwakened) Color(0x66FFD700) else Color(0x339C27B0) // Gold vs Purple
    val blobColor2 = if (isAwakened) Color(0x55FF5722) else Color(0x3300E676) // Red-Orange vs Green

    val blob1X by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1X"
    )
    val blob1Y by infiniteTransition.animateFloat(
        initialValue = 100f,
        targetValue = 500f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1Y"
    )
    val blob2X by infiniteTransition.animateFloat(
        initialValue = 350f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2X"
    )
    val blob2Y by infiniteTransition.animateFloat(
        initialValue = 600f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2Y"
    )

    // Breathing logo glow effect
    val logoGlowScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoGlowScale"
    )
    val logoGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoGlowAlpha"
    )

    // Stagger items animation trigger
    var staggerLevel by remember { mutableStateOf(0) }
    LaunchedEffect(isAwakened) {
        staggerLevel = 0
        delay(100)
        staggerLevel = 1
        delay(80)
        staggerLevel = 2
        delay(80)
        staggerLevel = 3
        delay(80)
        staggerLevel = 4
        delay(80)
        staggerLevel = 5
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF060608)) // High-premium AMOLED dark baseline
    ) {
        
        // ─── Canvas Layer: Glowing Blobs & Floating Particles ────────────────
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val tick = frameTick // Bind composition to high frequency frame tick
            val w = size.width
            val h = size.height

            if (w > 0 && h > 0) {
                // Initialize background floating ambient particles once
                if (!particlesInitialized) {
                    repeat(20) {
                        particles.add(
                            Particle(
                                x = (0..w.toInt()).random().toFloat(),
                                y = (0..h.toInt()).random().toFloat(),
                                vx = ((-12..12).random() / 15f),
                                vy = ((-15..-5).random() / 10f), // Upward drift
                                radius = (4..10).random().toFloat(),
                                alpha = (2..6).random() / 10f,
                                color = if (it % 2 == 0) Color(0x889C27B0) else Color(0x8800E676)
                            )
                        )
                    }
                    particlesInitialized = true
                }

                // Handle explosion trigger
                if (explosionTriggered) {
                    val cx = w / 2f
                    val cy = h / 3f
                    repeat(60) {
                        val angle = Math.toRadians((0..360).random().toDouble())
                        val speed = (4..24).random().toFloat()
                        particles.add(
                            Particle(
                                x = cx,
                                y = cy,
                                vx = (cos(angle) * speed).toFloat(),
                                vy = (sin(angle) * speed).toFloat(),
                                radius = (5..14).random().toFloat(),
                                alpha = 1f,
                                color = when ((0..3).random()) {
                                    0 -> Color(0xFFFFD700) // Gold
                                    1 -> Color(0xFFFF5722) // Orange-Red
                                    2 -> Color(0xFF00E676) // Neon Green
                                    else -> Color(0xFF9C27B0) // Deep Purple
                                },
                                isExplosive = true,
                                decay = (92..97).random() / 100f
                            )
                        )
                    }
                    explosionTriggered = false
                }

                // Update ambient glowing blobs in backdrop
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor1, Color.Transparent),
                        center = Offset(blob1X.dp.toPx(), blob1Y.dp.toPx()),
                        radius = 220.dp.toPx()
                    ),
                    radius = 220.dp.toPx(),
                    center = Offset(blob1X.dp.toPx(), blob1Y.dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(blobColor2, Color.Transparent),
                        center = Offset(blob2X.dp.toPx(), blob2Y.dp.toPx()),
                        radius = 240.dp.toPx()
                    ),
                    radius = 240.dp.toPx(),
                    center = Offset(blob2X.dp.toPx(), blob2Y.dp.toPx())
                )

                // Update and draw particles in one combined loop to avoid allocations
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    
                    p.x += p.vx
                    p.y += p.vy

                    if (p.isExplosive) {
                        p.vx *= p.decay
                        p.vy *= p.decay
                        p.alpha -= 0.015f
                        if (p.alpha <= 0f) {
                            iterator.remove()
                            continue
                        }
                    } else {
                        // Standard wraps for normal ambient particles
                        if (p.x < 0) p.x = w
                        if (p.x > w) p.x = 0f
                        if (p.y < 0) p.y = h
                        if (p.y > h) p.y = h // resets or loops
                    }

                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = p.radius,
                        center = Offset(p.x, p.y)
                    )
                }
            }
        }

        // ─── Header Navigation Row ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .glassCard(shape = CircleShape, backgroundColor = Color(0x22FFFFFF))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                text = if (isAwakened) "Developer Shrine" else "NeoQN",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White
            )
            
            // Invisible anchor to align title beautifully in center
            Spacer(modifier = Modifier.width(48.dp))
        }

        // ─── Content Layer ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 64.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Animated content transition based on layer
            AnimatedContent(
                targetState = isAwakened,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith
                    fadeOut(animationSpec = tween(400))
                },
                label = "layer_transition"
            ) { awakened ->
                if (!awakened) {
                    // ─── Layer 1: Normal About ──────────────────────────────────
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Pulsing Logo Box
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(140.dp)
                                .graphicsLayer {
                                    scaleX = logoGlowScale
                                    scaleY = logoGlowScale
                                }
                        ) {
                            // Pulsing glow behind the logo
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .alpha(logoGlowAlpha)
                                    .glassCard(
                                        shape = RoundedCornerShape(32.dp),
                                        backgroundColor = Color(0xFF9C27B0).copy(alpha = 0.4f),
                                        strokeColor = Color(0xFF9C27B0).copy(alpha = 0.6f),
                                        strokeWidth = 3.dp
                                    )
                            )
                            
                            // Core logo container
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .glassCard(
                                        shape = RoundedCornerShape(24.dp),
                                        backgroundColor = Color(0x33FFFFFF)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_quicknovel),
                                    contentDescription = "QuickNovel Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // App Name
                        StaggeredItem(visible = staggerLevel >= 1) {
                            Text(
                                text = "NeoQN",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp
                                ),
                                color = Color.White
                            )
                        }

                        // Tagline
                        StaggeredItem(visible = staggerLevel >= 2) {
                            Text(
                                text = "High Performance • Pure Aesthetics",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Version Pill with interactive tap listener for easter egg
                        StaggeredItem(visible = staggerLevel >= 3) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(30.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        tapCount++
                                        if (tapCount >= 7) {
                                            isAwakened = true
                                        }
                                    }
                                    .glassCard(
                                        shape = RoundedCornerShape(30.dp),
                                        backgroundColor = Color(0x11FFFFFF)
                                    )
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (tapCount > 0) Color(0xFFFFD700) else Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        // Premium Description Card
                        StaggeredItem(visible = staggerLevel >= 4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .glassCard(shape = RoundedCornerShape(24.dp))
                                    .padding(20.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "About NeoQN",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "A modern refactoring of QuickNovel designed to focus on flawless responsiveness, lightweight state synchronization, and premium visual elegance with the Spatial Glass design ecosystem.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            lineHeight = 22.sp
                                        ),
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ─── Layer 2: Developer Shrine (Easter Egg) ──────────────────
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Glowing Sage Trophy
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(140.dp)
                                .graphicsLayer {
                                    scaleX = logoGlowScale
                                    scaleY = logoGlowScale
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .alpha(logoGlowAlpha)
                                    .glassCard(
                                        shape = RoundedCornerShape(32.dp),
                                        backgroundColor = Color(0xFFFFD700).copy(alpha = 0.35f),
                                        strokeColor = Color(0xFFFFD700).copy(alpha = 0.7f),
                                        strokeWidth = 3.dp
                                    )
                            )
                            
                            Text(
                                text = "🏆",
                                fontSize = 64.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sage Rank Title
                        StaggeredItem(visible = staggerLevel >= 1) {
                            Text(
                                text = getRankTitle(chaptersRead),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFFFFD700), // Gold
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        StaggeredItem(visible = staggerLevel >= 2) {
                            Text(
                                text = "Scroll of Reader Telemetry",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Telemetry Grid
                        StaggeredItem(visible = staggerLevel >= 3) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ShrineStatCard(
                                        modifier = Modifier.weight(1f),
                                        value = formatReadingTime(totalReadingTime),
                                        label = "Time Read",
                                        icon = "⏱️"
                                    )
                                    ShrineStatCard(
                                        modifier = Modifier.weight(1f),
                                        value = chaptersRead.toString(),
                                        label = "Chapters Read",
                                        icon = "📖"
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ShrineStatCard(
                                        modifier = Modifier.weight(1f),
                                        value = "${activeStreak}d",
                                        label = "Current Streak",
                                        icon = "🔥"
                                    )
                                    ShrineStatCard(
                                        modifier = Modifier.weight(1f),
                                        value = "${bestStreak}d",
                                        label = "Best Streak",
                                        icon = "👑"
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ShrineStatCard(
                                        modifier = Modifier.weight(1f),
                                        value = midnightOilCount.toString(),
                                        label = "Midnight Oil",
                                        icon = "🌌"
                                    )
                                    ShrineStatCard(
                                        modifier = Modifier.weight(1f),
                                        value = customizationCount.toString(),
                                        label = "Layout Customizes",
                                        icon = "🎨"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Reset button to normal About
                        StaggeredItem(visible = staggerLevel >= 4) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isAwakened = false
                                    tapCount = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x22FFFFFF),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .padding(bottom = 32.dp)
                                    .glassCard(shape = RoundedCornerShape(20.dp), backgroundColor = Color.Transparent)
                            ) {
                                Text(
                                    text = "Return to Reality",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun StaggeredItem(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "stagger_alpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "stagger_offset"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .graphicsLayer {
                translationY = offsetY
            }
    ) {
        content()
    }
}

@Composable
private fun ShrineStatCard(
    value: String,
    label: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .glassCard(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x0CFFFFFF),
                strokeColor = Color(0x16FFFFFF)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = icon, fontSize = 20.sp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White
            )
        }
    }
}

// ─── Helper Functions ────────────────────────────────────────────────

private fun getRankTitle(chapters: Int): String {
    return when {
        chapters < 10 -> "Novice of the Scroll 📜"
        chapters < 50 -> "Adept Scholar of Texts 📚"
        chapters < 200 -> "Grand Bookworm Sage 🧠"
        chapters < 500 -> "Venerable Sage of Novels 🔮"
        else -> "Realm Emperor of Scrolls 👑"
    }
}

private fun formatReadingTime(ms: Long): String {
    if (ms <= 0) return "0m"
    val totalSecs = ms / 1000
    val totalMins = totalSecs / 60
    val totalHours = totalMins / 60
    val remainingMins = totalMins % 60
    return if (totalHours > 0) {
        "${totalHours}h ${remainingMins}m"
    } else {
        "${totalMins}m"
    }
}
