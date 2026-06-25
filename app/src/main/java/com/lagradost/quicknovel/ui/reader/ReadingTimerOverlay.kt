package com.lagradost.quicknovel.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.glassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ReadingTimerOverlay(
    initialAnchor: Int,
    onAnchorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 1s ticker for the reading session time
    var secondsElapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsElapsed++
        }
    }

    val minutes = secondsElapsed / 60
    val displayTime = if (minutes < 60) {
        "${minutes}m"
    } else {
        val hours = minutes / 60
        val mins = minutes % 60
        "${hours}h ${mins}m"
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Screen bounds and pill size tracking
    var containerWidth by remember { mutableIntStateOf(0) }
    var containerHeight by remember { mutableIntStateOf(0) }
    var pillWidth by remember { mutableIntStateOf(0) }
    var pillHeight by remember { mutableIntStateOf(0) }

    // Position animatables
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    // Padding in pixels
    val paddingSidePx = with(density) { 20.dp.toPx() }
    val paddingTopPx = with(density) { 72.dp.toPx() }
    val paddingBottomPx = with(density) { 80.dp.toPx() }

    // Calculate corner positions for an anchor (0: TopStart, 1: TopEnd, 2: BottomStart, 3: BottomEnd)
    fun getCornerPosition(anchor: Int, cWidth: Int, cHeight: Int, pWidth: Int, pHeight: Int): Offset {
        val x = when (anchor) {
            0, 2 -> paddingSidePx // Left
            else -> cWidth - pWidth - paddingSidePx // Right
        }
        val y = when (anchor) {
            0, 1 -> paddingTopPx // Top
            else -> cHeight - pHeight - paddingBottomPx // Bottom
        }
        return Offset(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
    }

    // Keep track of the current anchor state
    var currentAnchor by remember { mutableIntStateOf(initialAnchor) }

    // Snap/animate to the current anchor whenever bounds change
    LaunchedEffect(currentAnchor, containerWidth, containerHeight, pillWidth, pillHeight) {
        if (containerWidth > 0 && containerHeight > 0 && pillWidth > 0 && pillHeight > 0) {
            val target = getCornerPosition(currentAnchor, containerWidth, containerHeight, pillWidth, pillHeight)
            launch {
                offsetX.animateTo(
                    target.x,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
            launch {
                offsetY.animateTo(
                    target.y,
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerWidth = coordinates.size.width
                containerHeight = coordinates.size.height
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .onGloballyPositioned { coordinates ->
                    pillWidth = coordinates.size.width
                    pillHeight = coordinates.size.height
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            coroutineScope.launch {
                                offsetX.stop()
                                offsetY.stop()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newX = (offsetX.value + dragAmount.x).coerceIn(
                                    paddingSidePx,
                                    (containerWidth - pillWidth - paddingSidePx).coerceAtLeast(paddingSidePx)
                                )
                                val newY = (offsetY.value + dragAmount.y).coerceIn(
                                    paddingTopPx,
                                    (containerHeight - pillHeight - paddingBottomPx).coerceAtLeast(paddingTopPx)
                                )
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                        },
                        onDragEnd = {
                            if (containerWidth > 0 && containerHeight > 0 && pillWidth > 0 && pillHeight > 0) {
                                var nearestAnchor = 0
                                var minDistance = Float.MAX_VALUE
                                val currentPos = Offset(offsetX.value, offsetY.value)

                                for (a in 0..3) {
                                    val cornerPos = getCornerPosition(a, containerWidth, containerHeight, pillWidth, pillHeight)
                                    val dist = (currentPos - cornerPos).getDistanceSquared()
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        nearestAnchor = a
                                    }
                                }

                                if (nearestAnchor != currentAnchor) {
                                    currentAnchor = nearestAnchor
                                    onAnchorChanged(nearestAnchor)
                                } else {
                                    val target = getCornerPosition(currentAnchor, containerWidth, containerHeight, pillWidth, pillHeight)
                                    coroutineScope.launch {
                                        offsetX.animateTo(
                                            target.x,
                                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                    coroutineScope.launch {
                                        offsetY.animateTo(
                                            target.y,
                                            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
                .glassCard(
                    shape = RoundedCornerShape(20.dp),
                    strokeWidth = 1.dp
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_history_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayTime,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
