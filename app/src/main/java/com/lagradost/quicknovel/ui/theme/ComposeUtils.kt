package com.lagradost.quicknovel.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material3.MaterialTheme
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BaseApplication.Companion.getActivity
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

@Composable
fun rememberShimmerBrush(targetValue: Float = 1000f): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset(translate, translate)
    )
}

private val fileExistenceCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
private val fileExistenceTimestamp = java.util.concurrent.ConcurrentHashMap<String, Long>()

fun checkFileExistsCached(file: java.io.File): Boolean {
    val path = file.absolutePath
    val now = System.currentTimeMillis()
    val cachedValue = fileExistenceCache[path]
    if (cachedValue != null) {
        if (cachedValue == true) {
            return true
        } else {
            val timestamp = fileExistenceTimestamp[path] ?: 0L
            if (now - timestamp < 5000L) {
                return false
            }
        }
    }
    val exists = file.exists() && file.length() > 0L
    fileExistenceCache[path] = exists
    fileExistenceTimestamp[path] = now
    return exists
}

@Composable
fun rememberImageRequest(data: Any?): ImageRequest {
    val context = LocalContext.current
    
    val stableKey = remember(data) {
        when (data) {
            is ResultCached -> data.poster to (data.apiName + data.name)
            is DownloadFragment.DownloadDataLoaded -> data.posterUrl to (data.apiName + data.name)
            is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> data.posterUrl to (data.apiName + data.name)
            is com.lagradost.quicknovel.SearchResponse -> data.posterUrl to (data.apiName + data.name)
            else -> data
        }
    }

    val initialValue = remember(data) {
        when (data) {
            is ResultCached -> data.poster
            is DownloadFragment.DownloadDataLoaded -> data.posterUrl
            is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> data.posterUrl
            is com.lagradost.quicknovel.SearchResponse -> {
                if (data.posterHeaders != null) {
                    UiImage.Image(data.posterUrl ?: "", data.posterHeaders)
                } else {
                    data.posterUrl
                }
            }
            else -> data
        }
    }

    // Asynchronously resolve file existence checks on Dispatchers.IO to prevent main-thread lag
    val resolvedDataState = androidx.compose.runtime.produceState<Any?>(initialValue = initialValue, key1 = stableKey) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val resolved = when (data) {
                is ResultCached -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: data.poster
                }
                is DownloadFragment.DownloadDataLoaded -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: data.posterUrl
                }
                is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            "",
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: data.posterUrl
                }
                is com.lagradost.quicknovel.SearchResponse -> {
                    val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
                    val filesDir = act?.filesDir?.toString()
                    var file: java.io.File? = null
                    if (filesDir != null) {
                        val filePath = BookDownloader2Helper.getFilenameIMG(
                            BookDownloader2Helper.sanitizeFilename(data.apiName),
                            "",
                            BookDownloader2Helper.sanitizeFilename(data.name)
                        )
                        val f = java.io.File(filesDir + filePath)
                        if (f.exists() && f.length() > 0L) {
                            file = f
                        }
                    }
                    file ?: if (data.posterHeaders != null) {
                        UiImage.Image(data.posterUrl ?: "", data.posterHeaders)
                    } else {
                        data.posterUrl
                    }
                }
                else -> data
            }
            if (value != resolved) {
                value = resolved
            }
        }
    }

    val resolvedData = resolvedDataState.value

    val performanceMode = remember(context) {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("performance_mode_enabled", false)
    }

    return remember(resolvedData, performanceMode) {
        val builder = ImageRequest.Builder(context)
        if (performanceMode) {
            builder.crossfade(false)
            builder.allowHardware(true)
            builder.bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
        } else {
            builder.crossfade(200)
        }

        when (resolvedData) {
            is java.io.File -> {
                builder.data(resolvedData)
            }
            is UiImage -> {
                when (resolvedData) {
                    is UiImage.Bitmap -> builder.data(resolvedData.bitmap)
                    is UiImage.Drawable -> builder.data(resolvedData.resId)
                    is UiImage.Image -> {
                        builder.data(resolvedData.url)
                        val headersMap = resolvedData.headers
                        if (!headersMap.isNullOrEmpty()) {
                            builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                                for (entry in headersMap.entries) {
                                    headerBuilder.set(entry.key, entry.value)
                                }
                            }.build())
                        }
                    }
                }
            }
            is String -> {
                val uiImage = img(resolvedData as String)
                when (uiImage) {
                    is UiImage.Bitmap -> builder.data(uiImage.bitmap)
                    is UiImage.Drawable -> builder.data(uiImage.resId)
                    is UiImage.Image -> {
                        builder.data(uiImage.url)
                        val headersMap = uiImage.headers
                        if (!headersMap.isNullOrEmpty()) {
                            builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                                for (entry in headersMap.entries) {
                                    headerBuilder.set(entry.key, entry.value)
                                }
                            }.build())
                        }
                    }
                    else -> builder.data(resolvedData)
                }
            }
            else -> {
                builder.data(resolvedData ?: "")
            }
        }
        builder.build()
    }
}

fun buildImageRequest(context: android.content.Context, data: Any?): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .crossfade(200)

    val resolvedData = when (data) {
        is ResultCached -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            file ?: data.poster
        }
        is DownloadFragment.DownloadDataLoaded -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    BookDownloader2Helper.sanitizeFilename(data.author ?: ""),
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            file ?: data.posterUrl
        }
        is com.lagradost.quicknovel.ui.foryou.recommendation.NovelVector -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    "",
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            file ?: data.posterUrl
        }
        is com.lagradost.quicknovel.SearchResponse -> {
            val act = context.getActivity() ?: com.lagradost.quicknovel.CommonActivity.activity
            val filesDir = act?.filesDir?.toString()
            var file: java.io.File? = null
            if (filesDir != null) {
                val filePath = BookDownloader2Helper.getFilenameIMG(
                    BookDownloader2Helper.sanitizeFilename(data.apiName),
                    "",
                    BookDownloader2Helper.sanitizeFilename(data.name)
                )
                val f = java.io.File(filesDir + filePath)
                if (checkFileExistsCached(f)) {
                    file = f
                }
            }
            
            if (file != null) file else {
                if (data.posterHeaders != null) {
                    UiImage.Image(data.posterUrl ?: "", data.posterHeaders)
                } else {
                    data.posterUrl
                }
            }
        }
        else -> data
    }

    when (resolvedData) {
        is java.io.File -> {
            builder.data(resolvedData)
        }
        is UiImage -> {
            when (resolvedData) {
                is UiImage.Bitmap -> builder.data(resolvedData.bitmap)
                is UiImage.Drawable -> builder.data(resolvedData.resId)
                is UiImage.Image -> {
                    builder.data(resolvedData.url)
                    val headersMap = resolvedData.headers
                    if (!headersMap.isNullOrEmpty()) {
                        builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                            for (entry in headersMap.entries) {
                                headerBuilder.set(entry.key, entry.value)
                            }
                        }.build())
                    }
                }
            }
        }
        is String -> {
            val uiImage = img(resolvedData as String)
            when (uiImage) {
                is UiImage.Bitmap -> builder.data(uiImage.bitmap)
                is UiImage.Drawable -> builder.data(uiImage.resId)
                is UiImage.Image -> {
                    builder.data(uiImage.url)
                    val headersMap = uiImage.headers
                    if (!headersMap.isNullOrEmpty()) {
                        builder.httpHeaders(coil3.network.NetworkHeaders.Builder().also { headerBuilder ->
                            for (entry in headersMap.entries) {
                                headerBuilder.set(entry.key, entry.value)
                            }
                        }.build())
                    }
                }
                else -> builder.data(resolvedData)
            }
        }
        else -> {
            builder.data(resolvedData ?: "")
        }
    }
    return builder.build()
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "expressive_loader")
    
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val morphFactor by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morph"
    )

    Canvas(
        modifier = modifier
            .size(40.dp)
    ) {
        val width = size.width
        val height = size.height
        val center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f)
        val outerRadius = (width.coerceAtMost(height) / 2f) * scale
        val innerRadius = outerRadius * morphFactor
        
        val points = 24
        val path = Path()
        
        val rotationRad = Math.toRadians(rotation.toDouble())
        
        for (i in 0 until points) {
            val angle = i * (2.0 * Math.PI / points) + rotationRad
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val x = (center.x + r * cos(angle)).toFloat()
            val y = (center.y + r * sin(angle)).toFloat()
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        
        drawPath(
            path = path,
            color = color
        )
    }
}

@Composable
fun rememberHighQualityRequest(data: Any?, context: android.content.Context): ImageRequest {
    val baseRequest = rememberImageRequest(data)
    return remember(baseRequest, context) {
        baseRequest.newBuilder(context)
            .allowHardware(true)
            .size(coil3.size.Size.ORIGINAL)
            .crossfade(300)
            .build()
    }
}

suspend fun PointerInputScope.disallowParentIntercept(view: android.view.View) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var isDisallowed = false
        do {
            val event = awaitPointerEvent()
            val dragAmountX = event.changes.sumOf { (it.position.x - it.previousPosition.x).toDouble() }
            val dragAmountY = event.changes.sumOf { (it.position.y - it.previousPosition.y).toDouble() }
            if (kotlin.math.abs(dragAmountX) > kotlin.math.abs(dragAmountY) && kotlin.math.abs(dragAmountX) > 2.0 && !isDisallowed) {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                isDisallowed = true
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
fun rememberHasBackground(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember(context) { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(com.lagradost.quicknovel.R.string.background_image_key), null) }
    val globalFluidBg = remember(settings) { settings.getString("global_fluid_background", "none") ?: "none" }
    return !imageUri.isNullOrBlank() || globalFluidBg != "none"
}

