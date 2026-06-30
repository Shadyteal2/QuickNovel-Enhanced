package com.lagradost.quicknovel.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.graphics.Path
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.Matrix
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.asDrawable
import coil3.size.Size
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

// ─── Glance PreferencesKeys ──────────────────────────────

object ContinueReadingWidgetKeys {
    val NOVEL_TITLE = stringPreferencesKey("cr_novel_title")
    val NOVEL_URL = stringPreferencesKey("cr_novel_url")
    val NOVEL_SOURCE = stringPreferencesKey("cr_novel_source")
    val COVER_COMPOSITE_PATH = stringPreferencesKey("cr_cover_composite_path")
    val ACTIVE_INDEX = intPreferencesKey("cr_active_index")

    // Settings
    val SHOW_SETTINGS = booleanPreferencesKey("cr_show_settings")
    val SETTINGS_TAB = stringPreferencesKey("cr_settings_tab") // "Settings", "How To", "Dev"
    val ROTATION_SPEED = stringPreferencesKey("cr_rotation_speed") // "Slow", "Normal", "Fast", "Rapid"
    val BG_THEME = stringPreferencesKey("cr_bg_theme") // "Transparent", "Navy", "Green", "Red", "Blue", "Brown", "Purple"
    val BG_OPACITY = intPreferencesKey("cr_bg_opacity") // 0 to 100
    val LAST_TAP_TIME = longPreferencesKey("cr_last_tap_time")
}

// ─── Parameter Keys ───────────────────────────────────────

val KEY_TAB = ActionParameters.Key<String>("tab")
val KEY_SPEED = ActionParameters.Key<String>("speed")
val KEY_THEME = ActionParameters.Key<String>("theme")
val KEY_OPACITY_DELTA = ActionParameters.Key<Int>("opacity_delta")

// ─── Auto-Rotation Manager ─────────────────────────────────

object WidgetCarouselRotationManager {
    private var job: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRegistered = false

    fun start(context: Context) {
        job?.cancel()

        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            try {
                context.applicationContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        if (intent.action == Intent.ACTION_SCREEN_OFF) {
                            stop()
                        } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                            start(ctx)
                        }
                    }
                }, filter)
                isRegistered = true
            } catch (_: Throwable) {}
        }

        job = scope.launch {
            while (isActive) {
                val delayMs = getRotationSpeedMs(context)
                delay(delayMs)

                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(ContinueReadingWidget::class.java)
                val anyShowingSettings = if (glanceIds.isNotEmpty()) {
                    try {
                        val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, glanceIds.first())
                        state[ContinueReadingWidgetKeys.SHOW_SETTINGS] ?: false
                    } catch (_: Throwable) { false }
                } else false

                if (!anyShowingSettings) {
                    ContinueReadingWidget.updateActiveIndex(context, 1)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun getRotationSpeedMs(context: Context): Long {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(ContinueReadingWidget::class.java)
        val speedStr = if (glanceIds.isNotEmpty()) {
            try {
                val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, glanceIds.first())
                state[ContinueReadingWidgetKeys.ROTATION_SPEED] ?: "Normal"
            } catch (_: Throwable) { "Normal" }
        } else "Normal"

        return when (speedStr) {
            "Slow" -> 8000L
            "Normal" -> 5000L
            "Fast" -> 3000L
            "Rapid" -> 1500L
            else -> 5000L
        }
    }
}

// ─── Widget ────────────────────────────────────────────────

class ContinueReadingWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        private const val COMPOSITE_CACHE_FILE_PREFIX = "cr_widget_composite_"

        // Widget dimensions for composite rendering (matches 4×2 cell at ~160dpi)
        private const val WIDGET_W = 800
        private const val WIDGET_H = 300

        private val animMutex = Mutex()

        // ─── 3D Carousel Composite Canvas Builder ────────────────────
        private suspend fun buildCarouselComposite(
            context: Context,
            historyList: List<ResultCached>,
            activeIndex: Int,
            targetIndex: Int = activeIndex,
            fraction: Float = 0f,
            bgTheme: String,
            opacityFraction: Float
        ): Bitmap {
            val bmp = Bitmap.createBitmap(WIDGET_W, WIDGET_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // 1. Transparent or Colored Background
            val bgColors = mapOf(
                "Navy" to 0xFF0A192F.toInt(),
                "Green" to 0xFF064E3B.toInt(),
                "Red" to 0xFF450A0A.toInt(),
                "Blue" to 0xFF0B3C5D.toInt(),
                "Brown" to 0xFF3E2723.toInt(),
                "Purple" to 0xFF2A0845.toInt()
            )
            if (bgTheme != "Transparent") {
                val baseColor = bgColors[bgTheme] ?: 0xFF09090B.toInt()
                val opacityInt = (opacityFraction * 255f).toInt().coerceIn(0, 255)
                canvas.drawColor((opacityInt shl 24) or (baseColor and 0x00FFFFFF))
            } else {
                canvas.drawColor(0) // Completely Transparent background
            }

            val count = historyList.size
            fun getHistoryIndex(baseIndex: Int, offset: Int): Int {
                var idx = (baseIndex + offset) % count
                if (idx < 0) idx += count
                return idx
            }

            // Load bitmaps in parallel using async coroutines
            val offsets = listOf(-3, -2, 2, -1, 1, 0, 3)
            val indicesToLoad = (offsets.map { getHistoryIndex(activeIndex, it) } + 
                                 offsets.map { getHistoryIndex(targetIndex, it) }).distinct()
            
            val bitmapsMap = withContext(Dispatchers.IO) {
                val deferreds = indicesToLoad.map { idx ->
                    async {
                        idx to fetchSharpCoverBitmap(context, historyList[idx].poster)
                    }
                }
                deferreds.awaitAll().toMap()
            }

            // Interpolate slide offset during transitions
            val isTransition = activeIndex != targetIndex && fraction > 0f
            val direction = if (isTransition) {
                var diff = targetIndex - activeIndex
                if (diff > count / 2) diff -= count
                if (diff < -count / 2) diff += count
                diff.toFloat()
            } else 0f

            // Draw cards in order from outer-most to center (z-order layering)
            for (offset in offsets) {
                if (count <= 1 && offset != 0) continue
                if (count <= 2 && (offset == -2 || offset == 2 || offset == -3 || offset == 3)) continue
                if (count <= 3 && (offset == -3 || offset == 3)) continue

                val histIdx = getHistoryIndex(activeIndex, offset)
                val novel = historyList[histIdx]
                val coverBmp = bitmapsMap[histIdx]

                val currentOffset = offset - fraction * direction
                drawCarouselCard(canvas, novel, coverBmp, currentOffset)
            }

            // Navigation indicators (Chevrons)
            drawChevrons(canvas)

            return bmp
        }

        private val cardParamsMap = mapOf(
            -3f to CardParams(70f, 100f, -20f, -50f, 0.0f),
            -2f to CardParams(90f, 130f, 110f, -40f, 0.40f),
            -1f to CardParams(120f, 175f, 240f, -28f, 0.75f),
            0f to CardParams(155f, 220f, 400f, 0f, 1.0f),
            1f to CardParams(120f, 175f, 560f, 28f, 0.75f),
            2f to CardParams(90f, 130f, 690f, 40f, 0.40f),
            3f to CardParams(70f, 100f, 820f, 50f, 0.0f)
        )

        private fun getInterpolatedParams(offset: Float): CardParams {
            val lower = kotlin.math.floor(offset).coerceIn(-3f, 3f)
            val upper = kotlin.math.ceil(offset).coerceIn(-3f, 3f)
            if (lower == upper) {
                return cardParamsMap[lower] ?: CardParams(120f, 175f, 400f, 0f, 1.0f)
            }
            val lowerParams = cardParamsMap[lower] ?: CardParams(120f, 175f, 400f, 0f, 1.0f)
            val upperParams = cardParamsMap[upper] ?: CardParams(120f, 175f, 400f, 0f, 1.0f)
            val fraction = offset - lower
            return lowerParams.interpolate(upperParams, fraction)
        }

        private fun drawCarouselCard(
            canvas: Canvas,
            novel: ResultCached,
            coverBitmap: Bitmap?,
            offset: Float
        ) {
            val params = getInterpolatedParams(offset)
            val cardW = params.width
            val cardH = params.height
            val cardCenterX = params.centerX
            val rotationY = params.rotationY
            val opacity = params.opacity

            if (opacity <= 0f) return

            val cardCenterY = 150f // Center vertically
            val cardRect = RectF(
                cardCenterX - cardW / 2f,
                cardCenterY - cardH / 2f,
                cardCenterX + cardW / 2f,
                cardCenterY + cardH / 2f
            )

            canvas.save()

            // Apply 3D Y-axis rotation using Camera
            val camera = android.graphics.Camera()
            camera.save()
            camera.setLocation(0f, 0f, -12f)
            camera.rotateY(rotationY)
            val matrix = Matrix()
            camera.getMatrix(matrix)
            camera.restore()

            matrix.preTranslate(-cardCenterX, -cardCenterY)
            matrix.postTranslate(cardCenterX, cardCenterY)
            canvas.concat(matrix)

            // Dynamic drop-shadow
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66000000.toInt()
            }
            val shadowRect = RectF(cardRect.left - 6f, cardRect.top + 6f, cardRect.right + 6f, cardRect.bottom + 12f)
            canvas.drawRoundRect(shadowRect, 14f, 14f, shadowPaint)

            // Draw cover bitmap
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (opacity * 255f).toInt()
            }

            if (coverBitmap != null) {
                val shader = BitmapShader(coverBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val shaderMatrix = Matrix()
                val bmpW = coverBitmap.width.toFloat()
                val bmpH = coverBitmap.height.toFloat()

                val scale = maxOf(cardW / bmpW, cardH / bmpH)
                val dx = cardRect.left + (cardW - bmpW * scale) / 2f
                val dy = cardRect.top + (cardH - bmpH * scale) / 2f

                shaderMatrix.setScale(scale, scale)
                shaderMatrix.postTranslate(dx, dy)
                shader.setLocalMatrix(shaderMatrix)

                paint.shader = shader
                canvas.drawRoundRect(cardRect, 14f, 14f, paint)
                paint.shader = null
            } else {
                // Initial placeholder
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF18181B.toInt()
                    alpha = (opacity * 255f).toInt()
                }
                canvas.drawRoundRect(cardRect, 14f, 14f, placeholderPaint)

                val letter = novel.name.firstOrNull()?.toString()?.uppercase() ?: "B"
                val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x8EFFFFFF.toInt()
                    textSize = cardH * 0.42f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    alpha = (opacity * 255f).toInt()
                }
                val textBounds = Rect()
                letterPaint.getTextBounds(letter, 0, letter.length, textBounds)
                val tx = cardCenterX - textBounds.exactCenterX()
                val ty = cardCenterY - textBounds.exactCenterY()
                canvas.drawText(letter, tx, ty, letterPaint)
            }

            // Outline Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (kotlin.math.abs(offset) < 0.2f) 0x66FF571A.toInt() else 0x26FFFFFF.toInt() // Orange focus border
                style = Paint.Style.STROKE
                strokeWidth = if (kotlin.math.abs(offset) < 0.2f) 2.5f else 1.5f
                alpha = (opacity * 255f).toInt()
            }
            canvas.drawRoundRect(cardRect, 14f, 14f, borderPaint)

            canvas.restore()
        }

        private fun drawChevrons(canvas: Canvas) {
            val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x59FFFFFF.toInt() // 35% translucent white chevrons
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            // Left chevron (<)
            val leftPath = Path().apply {
                moveTo(38f, 140f)
                lineTo(25f, 150f)
                lineTo(38f, 160f)
            }
            canvas.drawPath(leftPath, chevronPaint)

            // Right chevron (>)
            val rightPath = Path().apply {
                moveTo(762f, 140f)
                lineTo(775f, 150f)
                lineTo(762f, 160f)
            }
            canvas.drawPath(rightPath, chevronPaint)
        }

        private data class CardParams(
            val width: Float,
            val height: Float,
            val centerX: Float,
            val rotationY: Float,
            val opacity: Float
        ) {
            fun interpolate(other: CardParams, fraction: Float): CardParams {
                return CardParams(
                    width = width + fraction * (other.width - width),
                    height = height + fraction * (other.height - height),
                    centerX = centerX + fraction * (other.centerX - centerX),
                    rotationY = rotationY + fraction * (other.rotationY - rotationY),
                    opacity = opacity + fraction * (other.opacity - opacity)
                )
            }
        }

        private suspend fun fetchSharpCoverBitmap(context: Context, url: String?): Bitmap? {
            if (url.isNullOrBlank()) return null
            return withContext(Dispatchers.IO) {
                try {
                    val loader = SingletonImageLoader.get(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)  // Software bitmap necessary for Canvas draws
                        .size(Size.ORIGINAL)
                        .build()
                    val result = loader.execute(request)
                    val drawable = (result as? SuccessResult)?.image?.asDrawable(context.resources)
                    (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } catch (t: Throwable) {
                    null
                }
            }
        }

        // ─── Public Action API ────────────────────────────────────

        suspend fun updateActiveIndex(context: Context, offset: Int) {
            withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER) ?: emptyList()
                val historyList = keys.mapNotNull { key -> context.getKey<ResultCached>(key) }
                    .sortedByDescending { it.cachedTime }
                if (historyList.isEmpty()) return@withContext

                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(ContinueReadingWidget::class.java)
                if (glanceIds.isEmpty()) return@withContext

                val id = glanceIds.first()
                val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, id)
                val bgTheme = state[ContinueReadingWidgetKeys.BG_THEME] ?: "Transparent"
                val opacityPercent = state[ContinueReadingWidgetKeys.BG_OPACITY] ?: 80
                val activeIndex = state[ContinueReadingWidgetKeys.ACTIVE_INDEX] ?: 0

                var nextIndex = (activeIndex + offset) % historyList.size
                if (nextIndex < 0) nextIndex += historyList.size

                // Run fast 3-frame transition loop to prevent Glance update lag
                animMutex.withLock {
                    val totalFrames = 3
                    val frameDelay = 60L // Snappy ~180ms slide animation
                    for (frame in 1..totalFrames) {
                        val fraction = frame / totalFrames.toFloat()
                        val composite = buildCarouselComposite(
                            context = context,
                            historyList = historyList,
                            activeIndex = activeIndex,
                            targetIndex = nextIndex,
                            fraction = fraction,
                            bgTheme = bgTheme,
                            opacityFraction = opacityPercent / 100f
                        )

                        saveCompositeAndRefresh(context, id, composite, historyList[nextIndex], nextIndex)
                        delay(frameDelay)
                    }

                    // Save final active index state
                    updateAppWidgetState(context, id) { prefs ->
                        prefs[ContinueReadingWidgetKeys.ACTIVE_INDEX] = nextIndex
                    }
                    ContinueReadingWidget().update(context, id)
                }
            }
        }

        private suspend fun saveCompositeAndRefresh(
            context: Context,
            id: GlanceId,
            composite: Bitmap,
            novel: ResultCached,
            index: Int
        ) {
            // Scale down to 533x200 to prevent TransactionTooLargeException
            val scaledBmp = Bitmap.createScaledBitmap(composite, 533, 200, true)
            val compositeFile = File(context.cacheDir, "${COMPOSITE_CACHE_FILE_PREFIX}${id.hashCode()}.png")
            safeApiCall {
                FileOutputStream(compositeFile).use { out ->
                    scaledBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            updateAppWidgetState(context, id) { prefs ->
                prefs[ContinueReadingWidgetKeys.NOVEL_TITLE] = novel.name
                prefs[ContinueReadingWidgetKeys.NOVEL_URL] = novel.source
                prefs[ContinueReadingWidgetKeys.NOVEL_SOURCE] = novel.apiName
                prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] = if (compositeFile.exists()) compositeFile.absolutePath else ""
            }
            ContinueReadingWidget().update(context, id)
        }

        suspend fun updateWidgetState(
            context: Context,
            historyList: List<ResultCached>,
            activeIndex: Int,
            bgTheme: String = "Transparent",
            opacityFraction: Float = 0.8f
        ) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(ContinueReadingWidget::class.java)

            if (historyList.isEmpty()) {
                for (id in glanceIds) {
                    updateAppWidgetState(context, id) { prefs ->
                        prefs[ContinueReadingWidgetKeys.NOVEL_TITLE] = ""
                        prefs[ContinueReadingWidgetKeys.NOVEL_URL] = ""
                        prefs[ContinueReadingWidgetKeys.NOVEL_SOURCE] = ""
                        prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] = ""
                        prefs[ContinueReadingWidgetKeys.ACTIVE_INDEX] = 0
                    }
                    ContinueReadingWidget().update(context, id)
                }
                return
            }

            for (id in glanceIds) {
                val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, id)
                val resolvedIndex = activeIndex.coerceIn(0, historyList.lastIndex)
                
                val currentBgTheme = state[ContinueReadingWidgetKeys.BG_THEME] ?: bgTheme
                val currentOpacity = state[ContinueReadingWidgetKeys.BG_OPACITY] ?: (opacityFraction * 100f).toInt()
                
                val composite = buildCarouselComposite(context, historyList, resolvedIndex, resolvedIndex, 0f, currentBgTheme, currentOpacity / 100f)
                
                val compositeFile = File(context.cacheDir, "${COMPOSITE_CACHE_FILE_PREFIX}${id.hashCode()}.png")
                safeApiCall {
                    FileOutputStream(compositeFile).use { out ->
                        // Scale down to 533x200 to prevent TransactionTooLargeException
                        val scaledBmp = Bitmap.createScaledBitmap(composite, 533, 200, true)
                        scaledBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }

                val activeNovel = historyList[resolvedIndex]
                updateAppWidgetState(context, id) { prefs ->
                    prefs[ContinueReadingWidgetKeys.NOVEL_TITLE] = activeNovel.name
                    prefs[ContinueReadingWidgetKeys.NOVEL_URL] = activeNovel.source
                    prefs[ContinueReadingWidgetKeys.NOVEL_SOURCE] = activeNovel.apiName
                    prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] = if (compositeFile.exists()) compositeFile.absolutePath else ""
                    prefs[ContinueReadingWidgetKeys.ACTIVE_INDEX] = resolvedIndex
                }
                ContinueReadingWidget().update(context, id)
            }
        }

        suspend fun refreshFromHistory(context: Context) {
            withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER) ?: emptyList()
                val historyList = keys.mapNotNull { key -> context.getKey<ResultCached>(key) }
                    .sortedByDescending { it.cachedTime }

                if (historyList.isEmpty()) {
                    updateWidgetState(context, emptyList(), 0)
                    return@withContext
                }

                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(ContinueReadingWidget::class.java)
                val activeIndex = if (glanceIds.isNotEmpty()) {
                    val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, glanceIds.first())
                    state[ContinueReadingWidgetKeys.ACTIVE_INDEX] ?: 0
                } else 0

                val bgTheme = if (glanceIds.isNotEmpty()) {
                    val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, glanceIds.first())
                    state[ContinueReadingWidgetKeys.BG_THEME] ?: "Transparent"
                } else "Transparent"

                val opacity = if (glanceIds.isNotEmpty()) {
                    val state = ContinueReadingWidget().getAppWidgetState<Preferences>(context, glanceIds.first())
                    state[ContinueReadingWidgetKeys.BG_OPACITY] ?: 80
                } else 80

                updateWidgetState(context, historyList, activeIndex, bgTheme, opacity / 100f)
            }
        }
    }

    // ─── Glance UI ────────────────────────────────────────────

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[ContinueReadingWidgetKeys.NOVEL_TITLE]
            val novelUrl = prefs[ContinueReadingWidgetKeys.NOVEL_URL] ?: ""
            val apiName = prefs[ContinueReadingWidgetKeys.NOVEL_SOURCE] ?: ""
            val compositePath = prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] ?: ""
            val showSettings = prefs[ContinueReadingWidgetKeys.SHOW_SETTINGS] ?: false

            val detailIntent = Intent(context, com.lagradost.quicknovel.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("widgetNovelUrl", novelUrl)
                putExtra("widgetApiName", apiName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val compositeBitmap: Bitmap? = if (compositePath.isNotEmpty()) {
                try { BitmapFactory.decodeFile(compositePath) } catch (t: Throwable) { null }
            } else null

            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (compositeBitmap != null && title != null) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(32.dp)
                            .background(ImageProvider(compositeBitmap)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Precise Card Click Overlays (Uses weights to scale correctly across screens and sizes)
                        // No spacers inside the row to stay safely below container element limit (10).
                        Row(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionRunCallback<RotateLeftTwiceAction>())) {}
                            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionRunCallback<RotateLeftAction>())) {}
                            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity(detailIntent))) {}
                            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionRunCallback<RotateRightAction>())) {}
                            Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionRunCallback<RotateRightTwiceAction>())) {}
                        }

                        // Premium Gear Settings trigger button (only visible when settings are hidden)
                        if (!showSettings) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxSize()
                                    .padding(top = 10.dp, end = 10.dp),
                                contentAlignment = Alignment.TopEnd
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(28.dp)
                                        .background(ColorProvider(day = Color(0x99000000), night = Color(0x99000000))) // Translucent dark circle
                                        .cornerRadius(14.dp)
                                        .clickable(actionRunCallback<OpenSettingsAction>()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_outline_settings_24),
                                        contentDescription = "Settings",
                                        colorFilter = ColorFilter.tint(ColorProvider(day = Color.White, night = Color.White)),
                                        modifier = GlanceModifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Empty placeholder
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(32.dp)
                            .background(
                                ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(0xFF09090B),
                                    night = androidx.compose.ui.graphics.Color(0xFF09090B)
                                )
                            )
                            .clickable(
                                actionStartActivity(
                                    Intent(context, com.lagradost.quicknovel.MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_baseline_menu_book_24),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(
                                    ColorProvider(
                                        day = androidx.compose.ui.graphics.Color(0xFFFF571A),
                                        night = androidx.compose.ui.graphics.Color(0xFFFF571A)
                                    )
                                ),
                                modifier = GlanceModifier.size(36.dp)
                            )
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Text(
                                text = context.getString(R.string.widget_no_history),
                                style = TextStyle(
                                    color = ColorProvider(
                                        day = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
                                        night = androidx.compose.ui.graphics.Color(0xFFAAAAAA)
                                    ),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // Settings Overlay Panel
                if (showSettings) {
                    RenderSettingsOverlay(context, prefs)
                }
            }
        }
    }
}

// ─── Settings Composable Renderers ──────────────────────────

@androidx.compose.runtime.Composable
private fun RenderSettingsOverlay(context: Context, prefs: Preferences) {
    val activeTab = prefs[ContinueReadingWidgetKeys.SETTINGS_TAB] ?: "Settings"
    val opacityPercent = prefs[ContinueReadingWidgetKeys.BG_OPACITY] ?: 80

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(32.dp)
            .background(ColorProvider(day = Color(0xF10E0E11), night = Color(0xF10E0E11))) // Frosted Obsidian dark settings backdrop
            .padding(14.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header Tabs Row (Optimized to stay below 10 elements via padding on tabs)
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabPill("Settings", activeTab == "Settings")
                TabPill("How To", activeTab == "How To", modifier = GlanceModifier.padding(start = 8.dp))
                TabPill("Dev", activeTab == "Dev", modifier = GlanceModifier.padding(start = 8.dp))

                Spacer(modifier = GlanceModifier.defaultWeight())

                // Close Button (X)
                Box(
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(actionRunCallback<CloseSettingsAction>())
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_sharp_clear_24),
                        contentDescription = "Close",
                        colorFilter = ColorFilter.tint(ColorProvider(day = Color.White, night = Color.White))
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(12.dp))

            // Contents Area (Section sub-columns keep child count below 10)
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                when (activeTab) {
                    "Settings" -> {
                        // Section 1: Rotation Speed
                        val rotationSpeed = prefs[ContinueReadingWidgetKeys.ROTATION_SPEED] ?: "Normal"
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            Text(
                                text = "ROTATION SPEED",
                                style = TextStyle(color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Row(modifier = GlanceModifier.fillMaxWidth().height(28.dp)) {
                                SpeedButton("Slow", rotationSpeed == "Slow")
                                SpeedButton("Normal", rotationSpeed == "Normal", modifier = GlanceModifier.padding(start = 4.dp))
                                SpeedButton("Fast", rotationSpeed == "Fast", modifier = GlanceModifier.padding(start = 4.dp))
                                SpeedButton("Rapid", rotationSpeed == "Rapid", modifier = GlanceModifier.padding(start = 4.dp))
                            }
                        }

                        // Section 2: Background Theme
                        val theme = prefs[ContinueReadingWidgetKeys.BG_THEME] ?: "Transparent"
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(
                                text = "BACKGROUND THEME",
                                style = TextStyle(color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Row(modifier = GlanceModifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                                ThemeCircle("Transparent", theme == "Transparent", Color.Transparent)
                                ThemeCircle("Navy", theme == "Navy", Color(0xFF0A192F), modifier = GlanceModifier.padding(start = 8.dp))
                                ThemeCircle("Green", theme == "Green", Color(0xFF064E3B), modifier = GlanceModifier.padding(start = 8.dp))
                                ThemeCircle("Red", theme == "Red", Color(0xFF450A0A), modifier = GlanceModifier.padding(start = 8.dp))
                                ThemeCircle("Blue", theme == "Blue", Color(0xFF0B3C5D), modifier = GlanceModifier.padding(start = 8.dp))
                                ThemeCircle("Brown", theme == "Brown", Color(0xFF3E2723), modifier = GlanceModifier.padding(start = 8.dp))
                                ThemeCircle("Purple", theme == "Purple", Color(0xFF2A0845), modifier = GlanceModifier.padding(start = 8.dp))
                            }
                        }

                        // Section 3: Background Opacity
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text(
                                text = "OPACITY (${opacityPercent}%)",
                                style = TextStyle(color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Row(modifier = GlanceModifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = GlanceModifier
                                        .size(24.dp)
                                        .clickable(actionRunCallback<ChangeOpacityAction>(actionParametersOf(KEY_OPACITY_DELTA to -10))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("-", style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                                }
                                Spacer(modifier = GlanceModifier.width(8.dp))
                                LinearProgressIndicator(
                                    progress = opacityPercent / 100f,
                                    modifier = GlanceModifier.defaultWeight().height(6.dp).cornerRadius(3.dp),
                                    color = ColorProvider(day = Color(0xFFFF571A), night = Color(0xFFFF571A)),
                                    backgroundColor = ColorProvider(day = Color(0x33FFFFFF), night = Color(0x33FFFFFF))
                                )
                                Spacer(modifier = GlanceModifier.width(8.dp))
                                Box(
                                    modifier = GlanceModifier
                                        .size(24.dp)
                                        .clickable(actionRunCallback<ChangeOpacityAction>(actionParametersOf(KEY_OPACITY_DELTA to 10))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("+", style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    "How To" -> {
                        Text(
                            text = "HOW TO USE THE WIDGET",
                            style = TextStyle(color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Text(
                            text = "• Tap the center poster to load the details screen.\n• Tap left/right posters to rotate the carousel manually.\n• Tap the top-right Gear icon to open/close this settings panel.",
                            style = TextStyle(color = ColorProvider(day = Color(0xFFE5E2E1), night = Color(0xFFE5E2E1)), fontSize = 12.sp)
                        )
                    }

                    "Dev" -> {
                        val activeIndex = prefs[ContinueReadingWidgetKeys.ACTIVE_INDEX] ?: 0
                        val coverPath = prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] ?: "None"
                        Text(
                            text = "DIAGNOSTICS",
                            style = TextStyle(color = ColorProvider(day = Color(0xFF8E8E93), night = Color(0xFF8E8E93)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        Text(
                            text = "Active Index: $activeIndex\nCache Composite: ${coverPath.takeLast(35)}",
                            style = TextStyle(color = ColorProvider(day = Color(0xFF00FF88), night = Color(0xFF00FF88)), fontSize = 11.sp)
                        )
                    }
                }
            }

            // Bottom Reset Button
            if (activeTab == "Settings") {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .cornerRadius(15.dp)
                        .background(ColorProvider(day = Color(0x1BFF571A), night = Color(0x1BFF571A)))
                        .clickable(actionRunCallback<ResetSettingsAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset to Default",
                        style = TextStyle(color = ColorProvider(day = Color(0xFFFF571A), night = Color(0xFFFF571A)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TabPill(label: String, selected: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = GlanceModifier
            .height(26.dp)
            .cornerRadius(13.dp)
            .background(ColorProvider(day = if (selected) Color(0x33FF571A) else Color(0x1AFFFFFF), night = if (selected) Color(0x33FF571A) else Color(0x1AFFFFFF)))
            .then(modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(actionRunCallback<SwitchTabAction>(actionParametersOf(KEY_TAB to label))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(day = if (selected) Color(0xFFFF571A) else Color(0xFFC4C7C8), night = if (selected) Color(0xFFFF571A) else Color(0xFFC4C7C8)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun RowScope.SpeedButton(label: String, selected: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .cornerRadius(14.dp)
            .background(ColorProvider(day = if (selected) Color(0xFFFF571A) else Color(0x1AFFFFFF), night = if (selected) Color(0xFFFF571A) else Color(0x1AFFFFFF)))
            .then(modifier)
            .clickable(actionRunCallback<ChangeSpeedAction>(actionParametersOf(KEY_SPEED to label))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(day = if (selected) Color.Black else Color.White, night = if (selected) Color.Black else Color.White),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun ThemeCircle(label: String, selected: Boolean, color: Color, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = GlanceModifier
            .size(32.dp)
            .cornerRadius(16.dp)
            .background(ColorProvider(day = if (selected) Color(0xFFFF571A) else Color.Transparent, night = if (selected) Color(0xFFFF571A) else Color.Transparent))
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .size(26.dp)
                .cornerRadius(13.dp)
                .background(ColorProvider(day = if (label == "Transparent") Color(0x26FFFFFF) else color, night = if (label == "Transparent") Color(0x26FFFFFF) else color))
                .clickable(actionRunCallback<ChangeThemeAction>(actionParametersOf(KEY_THEME to label))),
            contentAlignment = Alignment.Center
        ) {
            if (label == "Transparent") {
                Image(
                    provider = ImageProvider(R.drawable.ic_sharp_clear_24),
                    contentDescription = "Clear",
                    colorFilter = ColorFilter.tint(ColorProvider(day = Color(0x80FFFFFF), night = Color(0x80FFFFFF))),
                    modifier = GlanceModifier.size(14.dp)
                )
            }
        }
    }
}

// ─── Glance Callback Classes ──────────────────────────────

class SwitchTabAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val tab = parameters[KEY_TAB] ?: "Settings"
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ContinueReadingWidgetKeys.SETTINGS_TAB] = tab
        }
        ContinueReadingWidget().update(context, glanceId)
    }
}

class ChangeSpeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val speed = parameters[KEY_SPEED] ?: "Normal"
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ContinueReadingWidgetKeys.ROTATION_SPEED] = speed
        }
        ContinueReadingWidget().update(context, glanceId)
        WidgetCarouselRotationManager.start(context)
    }
}

class ChangeThemeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val theme = parameters[KEY_THEME] ?: "Transparent"
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ContinueReadingWidgetKeys.BG_THEME] = theme
        }
        ContinueReadingWidget().update(context, glanceId)
        ContinueReadingWidget.refreshFromHistory(context)
    }
}

class ChangeOpacityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[KEY_OPACITY_DELTA] ?: 10
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[ContinueReadingWidgetKeys.BG_OPACITY] ?: 80
            val next = (current + delta).coerceIn(0, 100)
            prefs[ContinueReadingWidgetKeys.BG_OPACITY] = next
        }
        ContinueReadingWidget().update(context, glanceId)
        ContinueReadingWidget.refreshFromHistory(context)
    }
}

class ResetSettingsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ContinueReadingWidgetKeys.SETTINGS_TAB] = "Settings"
            prefs[ContinueReadingWidgetKeys.ROTATION_SPEED] = "Normal"
            prefs[ContinueReadingWidgetKeys.BG_THEME] = "Transparent"
            prefs[ContinueReadingWidgetKeys.BG_OPACITY] = 80
            prefs[ContinueReadingWidgetKeys.SHOW_SETTINGS] = false
        }
        ContinueReadingWidget().update(context, glanceId)
        ContinueReadingWidget.refreshFromHistory(context)
    }
}

class OpenSettingsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ContinueReadingWidgetKeys.SHOW_SETTINGS] = true
        }
        ContinueReadingWidget().update(context, glanceId)
    }
}

class CloseSettingsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[ContinueReadingWidgetKeys.SHOW_SETTINGS] = false
        }
        ContinueReadingWidget().update(context, glanceId)
    }
}

class RotateLeftAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ContinueReadingWidget.updateActiveIndex(context, -1)
    }
}

class RotateRightAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ContinueReadingWidget.updateActiveIndex(context, 1)
    }
}

class RotateLeftTwiceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ContinueReadingWidget.updateActiveIndex(context, -2)
    }
}

class RotateRightTwiceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ContinueReadingWidget.updateActiveIndex(context, 2)
    }
}
