package com.lagradost.quicknovel.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// ─── Staggered Content Entrance ──────────────────────────────────────────────

/**
 * Applies a cascaded fade-in and translation transition sequentially based on the element index.
 * Relies purely on the non-recomposing graphicsLayer scope for optimal scrolling performance.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val enabled = rememberStaggeredEntrancesEnabled()
    if (!enabled) return@composed this

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val delayMs = (index * 45).coerceAtMost(350).toLong()
        kotlinx.coroutines.delay(delayMs)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    this.graphicsLayer {
        val progress = animProgress.value
        alpha = progress
        translationY = (1f - progress) * 32.dp.toPx()
    }
}
