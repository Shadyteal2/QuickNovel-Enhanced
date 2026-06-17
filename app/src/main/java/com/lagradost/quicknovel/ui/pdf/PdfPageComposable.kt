package com.lagradost.quicknovel.ui.pdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.lagradost.quicknovel.ui.theme.LoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Global LruCache to cache rendered page bitmaps and prevent memory exhaustion
object PdfPageCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of runtime memory

    // Tracks bitmaps currently visible/active in any Composable
    val activeBitmaps = java.util.concurrent.ConcurrentHashMap<Bitmap, Boolean>()
    
    // Tracks bitmaps evicted from the cache but still active in Compose, pending recycling
    val evictedBitmaps = java.util.concurrent.ConcurrentHashMap<Bitmap, Boolean>()

    // Tracks viewport-rendered bitmaps (which are never cached) to recycle them when no longer displayed
    val viewportBitmaps = java.util.concurrent.ConcurrentHashMap<Bitmap, Boolean>()

    val bitmapCache = object : android.util.LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted) {
                if (activeBitmaps.containsKey(oldValue)) {
                    evictedBitmaps[oldValue] = true
                } else {
                    oldValue.recycle()
                }
            }
        }
    }
    
    val renderMutex = Mutex()
}

@Composable
fun PdfPageComposable(
    pdfDocument: PdfDocumentKt,
    pageIndex: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1f,
    panOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    onZoomChanged: ((Float, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    isActive: Boolean = true
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var pageHeight by remember { mutableStateOf(400.dp) }
    
    // stableScale and stableOffset record the parameters used for the currently rendered bitmap
    var stableScale by remember { mutableFloatStateOf(1f) }
    var stableOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Interactive zoom & pan states (updated dynamically during gestures)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Reset zoom and panning state when the page is scrolled/swiped off-screen
    LaunchedEffect(isActive) {
        if (!isActive) {
            scale = 1f
            offset = androidx.compose.ui.geometry.Offset.Zero
        }
    }

    val cacheKey = "${pdfDocument.hashCode()}_${pageIndex}_${isDarkMode}"

    // Track active bitmaps and recycle evicted/viewport bitmaps safely when composition drops references
    DisposableEffect(bitmap) {
        val bmp = bitmap
        if (bmp != null) {
            PdfPageCache.activeBitmaps[bmp] = true
        }
        onDispose {
            if (bmp != null) {
                PdfPageCache.activeBitmaps.remove(bmp)
                if (PdfPageCache.viewportBitmaps.remove(bmp) == true) {
                    // Recycle viewport-specific high-resolution bitmap with a safe delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!bmp.isRecycled) {
                            bmp.recycle()
                        }
                    }, 100)
                } else if (PdfPageCache.evictedBitmaps.remove(bmp) == true) {
                    // Recycle cached base bitmap that was evicted from LRU cache
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!bmp.isRecycled) {
                            bmp.recycle()
                        }
                    }, 100)
                }
            }
        }
    }

    // Viewport-based crisp rendering and cached base loading
    LaunchedEffect(pdfDocument, pageIndex, isDarkMode, scale, offset, isInteracting, size) {
        if (isInteracting || size.width == 0 || size.height == 0) return@LaunchedEffect

        if (scale == 1f && offset == androidx.compose.ui.geometry.Offset.Zero) {
            // Load base bitmap from cache
            val cached = PdfPageCache.bitmapCache.get(cacheKey)
            if (cached != null) {
                val oldBitmap = bitmap
                bitmap = cached
                stableScale = 1f
                stableOffset = androidx.compose.ui.geometry.Offset.Zero
                
                // If oldBitmap was a temporary viewport bitmap, recycle it safely
                if (oldBitmap != null && oldBitmap != cached) {
                    if (PdfPageCache.viewportBitmaps.containsKey(oldBitmap)) {
                        PdfPageCache.activeBitmaps.remove(oldBitmap)
                        PdfPageCache.viewportBitmaps.remove(oldBitmap)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!oldBitmap.isRecycled) {
                                oldBitmap.recycle()
                            }
                        }, 100)
                    }
                }
                isLoading = false
                return@LaunchedEffect
            }

            isLoading = true
            withContext(Dispatchers.IO) {
                PdfPageCache.renderMutex.withLock {
                    try {
                        pdfDocument.openPage(pageIndex).use { page ->
                            val renderWidth = size.width.coerceAtMost(3000)
                            val renderHeight = size.height.coerceAtMost(3000)
                            val bmp = Bitmap.createBitmap(
                                renderWidth.coerceAtLeast(1),
                                renderHeight.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            page.renderPageBitmap(bmp, 0, 0, renderWidth, renderHeight, true)
                            withContext(Dispatchers.Main) {
                                val oldBitmap = bitmap
                                bitmap = bmp
                                stableScale = 1f
                                stableOffset = androidx.compose.ui.geometry.Offset.Zero
                                PdfPageCache.bitmapCache.put(cacheKey, bmp)
                                
                                if (oldBitmap != null && oldBitmap != bmp) {
                                    if (PdfPageCache.viewportBitmaps.containsKey(oldBitmap)) {
                                        PdfPageCache.activeBitmaps.remove(oldBitmap)
                                        PdfPageCache.viewportBitmaps.remove(oldBitmap)
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            if (!oldBitmap.isRecycled) {
                                                oldBitmap.recycle()
                                            }
                                        }, 100)
                                    }
                                }
                                isLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        com.lagradost.quicknovel.mvvm.logError(e)
                        withContext(Dispatchers.Main) {
                            isLoading = false
                        }
                    }
                }
            }
        } else {
            // Render viewport zoom/pan bitmap at crisp resolution within OpenGL max size limit (3000px)
            isLoading = true
            withContext(Dispatchers.IO) {
                PdfPageCache.renderMutex.withLock {
                    try {
                        pdfDocument.openPage(pageIndex).use { page ->
                            val vw = size.width
                            val vh = size.height

                            val sizeX = (vw * scale).toInt()
                            val sizeY = (vh * scale).toInt()

                            val startX = (vw / 2f - sizeX / 2f + offset.x).toInt()
                            val startY = (vh / 2f - sizeY / 2f + offset.y).toInt()

                            val renderWidth = vw.coerceAtMost(3000)
                            val renderHeight = vh.coerceAtMost(3000)

                            val bmp = Bitmap.createBitmap(
                                renderWidth.coerceAtLeast(1),
                                renderHeight.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            page.renderPageBitmap(bmp, startX, startY, sizeX, sizeY, true)

                            withContext(Dispatchers.Main) {
                                val oldBitmap = bitmap
                                bitmap = bmp
                                stableScale = scale
                                stableOffset = offset
                                PdfPageCache.viewportBitmaps[bmp] = true

                                val cachedBase = PdfPageCache.bitmapCache.get(cacheKey)
                                if (oldBitmap != null && oldBitmap != cachedBase) {
                                    if (PdfPageCache.viewportBitmaps.containsKey(oldBitmap)) {
                                        PdfPageCache.activeBitmaps.remove(oldBitmap)
                                        PdfPageCache.viewportBitmaps.remove(oldBitmap)
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            if (!oldBitmap.isRecycled) {
                                                oldBitmap.recycle()
                                            }
                                        }, 100)
                                    }
                                }
                                isLoading = false
                            }
                        }
                    } catch (e: Exception) {
                        com.lagradost.quicknovel.mvvm.logError(e)
                        withContext(Dispatchers.Main) {
                            isLoading = false
                        }
                    }
                }
            }
        }
    }

    // Adapt layout height based on page dimensions
    LaunchedEffect(pdfDocument, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                pdfDocument.openPage(pageIndex).use { page ->
                    val w = page.getPageWidthPoint()
                    val h = page.getPageHeightPoint()
                    if (w > 0 && h > 0) {
                        val displayMetrics = context.resources.displayMetrics
                        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
                        val ratio = h.toFloat() / w.toFloat()
                        val calculatedHeight = (screenWidthDp * ratio).dp
                        withContext(Dispatchers.Main) {
                            pageHeight = calculatedHeight
                        }
                    }
                }
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
            }
        }
    }

    // Color filter for dark mode inversion
    val colorFilter = remember(isDarkMode) {
        if (isDarkMode) {
            val colorMatrix = floatArrayOf(
                -1f,  0f,  0f,  0f, 255f,
                 0f, -1f,  0f,  0f, 255f,
                 0f,  0f, -1f,  0f, 255f,
                 0f,  0f,  0f,  1f,   0f
            )
            ColorFilter.colorMatrix(ColorMatrix(colorMatrix))
        } else {
            null
        }
    }

    val onZoomChangedUpdated by rememberUpdatedState(onZoomChanged)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(pageHeight)
            .background(if (isDarkMode) Color.Black else Color.White)
            .onSizeChanged { size = it }
            .pointerInput(scale) {
                val widthPx = size.width.toFloat()
                val heightPx = size.height.toFloat()
                if (widthPx > 0 && heightPx > 0) {
                    val maxX = (widthPx * (scale - 1f)) / 2f
                    val maxY = (heightPx * (scale - 1f)) / 2f
                    
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isInteracting = true
                        
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val pointerCount = event.changes.filter { it.pressed }.size
                                if (pointerCount >= 2) {
                                    // Multi-finger pinch to zoom
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    
                                    val newScale = (scale * zoomChange).coerceIn(1f, 4f)
                                    val newOffset = if (newScale > 1f) {
                                        val curMaxX = (widthPx * (newScale - 1f)) / 2f
                                        val curMaxY = (heightPx * (newScale - 1f)) / 2f
                                        (offset + panChange).let {
                                            androidx.compose.ui.geometry.Offset(
                                                it.x.coerceIn(-curMaxX, curMaxX),
                                                it.y.coerceIn(-curMaxY, curMaxY)
                                            )
                                        }
                                    } else {
                                        androidx.compose.ui.geometry.Offset.Zero
                                    }
                                    
                                    scale = newScale
                                    offset = newOffset
                                    onZoomChangedUpdated?.invoke(newScale, newOffset)
                                    event.changes.forEach { it.consume() }
                                } else if (pointerCount == 1 && scale > 1f) {
                                    // Single-finger panning when zoomed in
                                    val panChange = event.calculatePan()
                                    if (panChange != androidx.compose.ui.geometry.Offset.Zero) {
                                        val targetX = offset.x + panChange.x
                                        val targetY = offset.y + panChange.y
                                        
                                        val hasReachedLeftEdge = targetX >= maxX && panChange.x > 0
                                        val hasReachedRightEdge = targetX <= -maxX && panChange.x < 0
                                        
                                        if (hasReachedLeftEdge || hasReachedRightEdge) {
                                            // Edge reached: do not consume horizontal drag to allow HorizontalPager snap
                                            val boundedY = targetY.coerceIn(-maxY, maxY)
                                            offset = androidx.compose.ui.geometry.Offset(offset.x, boundedY)
                                            onZoomChangedUpdated?.invoke(scale, offset)
                                        } else {
                                            // Within boundaries: consume drag entirely to block HorizontalPager snap
                                            val boundedX = targetX.coerceIn(-maxX, maxX)
                                            val boundedY = targetY.coerceIn(-maxY, maxY)
                                            offset = androidx.compose.ui.geometry.Offset(boundedX, boundedY)
                                            onZoomChangedUpdated?.invoke(scale, offset)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed } && !canceled)
                        
                        isInteracting = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            LoadingIndicator(color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        } else {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Scale and translate the drawn bitmap dynamically during interactions
                            scaleX = scale / stableScale
                            scaleY = scale / stableScale
                            translationX = offset.x - stableOffset.x * (scale / stableScale)
                            translationY = offset.y - stableOffset.y * (scale / stableScale)
                            clip = true
                        }
                )
            }
        }
    }
}

