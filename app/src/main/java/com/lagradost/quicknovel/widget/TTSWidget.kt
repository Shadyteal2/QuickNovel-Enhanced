package com.lagradost.quicknovel.widget

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
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
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.ReadActivity2
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.TTSNotificationService
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object TTSWidgetKeys {
    val NOVEL_TITLE = stringPreferencesKey("novel_title")
    val CHAPTER_NAME = stringPreferencesKey("chapter_name")
    val COVER_PATH = stringPreferencesKey("cover_path")
    val COVER_COMPOSITE_PATH = stringPreferencesKey("cover_composite_path")
    val IS_PLAYING = booleanPreferencesKey("is_playing")
    val NOVEL_URL = stringPreferencesKey("novel_url")
    val CHAPTER_INDEX = intPreferencesKey("chapter_index")
    val TOTAL_CHAPTERS = intPreferencesKey("total_chapters")
    val AUTHOR = stringPreferencesKey("author")
}

class TTSWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        private const val COVER_CACHE_FILE = "tts_widget_cover.jpg"
        private const val COMPOSITE_CACHE_FILE = "tts_widget_composite.png"

        // Widget dimensions for composite rendering (matches 4×2 cell at ~160dpi)
        private const val WIDGET_W = 800
        private const val WIDGET_H = 300

        private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val limit = (maxWidth - paint.measureText("…")).coerceAtLeast(0f)
            val measuredWidth = FloatArray(1)
            val count = paint.breakText(text, true, limit, measuredWidth)
            return text.take(count) + "…"
        }

        private fun drawWrappedText(canvas: Canvas, text: String, paint: Paint, x: Float, y: Float, maxWidth: Float, maxLines: Int) {
            var currentY = y
            var remainingText = text
            var line = 0
            while (remainingText.isNotEmpty() && line < maxLines) {
                if (line == maxLines - 1) {
                    val ellipsized = ellipsizeText(remainingText, paint, maxWidth)
                    canvas.drawText(ellipsized, x, currentY, paint)
                    break
                }
                val measuredWidth = FloatArray(1)
                val count = paint.breakText(remainingText, true, maxWidth, measuredWidth)
                if (count == 0) break
                
                var breakIndex = count
                if (count < remainingText.length) {
                    val spaceIndex = remainingText.lastIndexOf(' ', count)
                    if (spaceIndex > count / 2) {
                        breakIndex = spaceIndex + 1
                    }
                }
                
                val lineText = remainingText.substring(0, breakIndex).trimEnd()
                canvas.drawText(lineText, x, currentY, paint)
                remainingText = remainingText.substring(breakIndex).trimStart()
                currentY += paint.textSize * 1.25f
                line++
            }
        }

        // ─── Composite canvas renderer ────────────────────────
        private fun buildCompositeBitmap(
            coverBitmap: Bitmap?,
            title: String,
            author: String?,
            chapterLabel: String,
            isPlaying: Boolean,
            chapterIndex: Int,
            totalChapters: Int,
        ): Bitmap {
            val bmp = Bitmap.createBitmap(WIDGET_W, WIDGET_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Clip the canvas to rounded corners first (32dp corner radius -> 80px)
            val clipPath = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat()),
                    80f, 80f,
                    Path.Direction.CW
                )
            }
            canvas.clipPath(clipPath)

            // 1. Sleek glass background (OLED black base)
            canvas.drawColor(0xFF131313.toInt())

            // 2. Draw scaled cover art as background at 20% opacity (alpha = 51)
            if (coverBitmap != null) {
                val matrix = Matrix()
                val scale: Float
                val dx: Float
                val dy: Float
                val bmpW = coverBitmap.width.toFloat()
                val bmpH = coverBitmap.height.toFloat()

                if (bmpW * WIDGET_H > WIDGET_W * bmpH) {
                    scale = WIDGET_H / bmpH
                    dx = (WIDGET_W - bmpW * scale) / 2f
                    dy = 0f
                } else {
                    scale = WIDGET_W / bmpW
                    dx = 0f
                    dy = (WIDGET_H - bmpH * scale) / 2f
                }

                matrix.setScale(scale, scale)
                matrix.postTranslate(dx, dy)

                val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = 51 // 20% opacity
                }
                canvas.drawBitmap(coverBitmap, matrix, coverPaint)
            }

            // 3. Vertical gradient to fade cover background towards the top
            val verticalGradient = android.graphics.LinearGradient(
                0f, WIDGET_H.toFloat(), 0f, 0f,
                intArrayOf(
                    0xFF131313.toInt(),
                    0xCC131313.toInt(),
                    0x00131313.toInt()
                ),
                floatArrayOf(0.0f, 0.6f, 1.0f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(
                0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = verticalGradient }
            )

            // 4. Diagonal glass container gradient
            val diagonalGradient = android.graphics.LinearGradient(
                0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                0xB3131313.toInt(),
                0xE6131313.toInt(),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(
                0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = diagonalGradient }
            )

            // 5. Left column - Cover Art Thumbnail
            val thumbRect = RectF(32f, 30f, 202f, 270f)
            val thumbRadius = 24f

            // Thumbnail shadow
            val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66000000.toInt()
            }
            val thumbShadowRect = RectF(thumbRect.left - 2f, thumbRect.top + 4f, thumbRect.right + 2f, thumbRect.bottom + 6f)
            canvas.drawRoundRect(thumbShadowRect, thumbRadius, thumbRadius, thumbShadowPaint)

            if (coverBitmap != null) {
                val shader = BitmapShader(coverBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val matrix = Matrix()
                val scale: Float
                val dx: Float
                val dy: Float
                val bmpW = coverBitmap.width.toFloat()
                val bmpH = coverBitmap.height.toFloat()
                val cardW = thumbRect.width()
                val cardH = thumbRect.height()

                if (bmpW * cardH > cardW * bmpH) {
                    scale = cardH / bmpH
                    dx = thumbRect.left + (cardW - bmpW * scale) / 2f
                    dy = thumbRect.top
                } else {
                    scale = cardW / bmpW
                    dx = thumbRect.left
                    dy = thumbRect.top + (cardH - bmpH * scale) / 2f
                }

                matrix.setScale(scale, scale)
                matrix.postTranslate(dx, dy)
                shader.setLocalMatrix(matrix)

                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                }
                canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, cardPaint)
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x1AFFFFFF.toInt()
                }
                canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, placeholderPaint)

                // Draw simple book outline icon in placeholder
                val centerX = thumbRect.centerX()
                val centerY = thumbRect.centerY()
                val bookPath = Path().apply {
                    val w = 24f
                    val h = 18f
                    moveTo(centerX, centerY + h)
                    quadTo(centerX - w/2f, centerY + h - 5f, centerX - w, centerY + h)
                    lineTo(centerX - w, centerY - h)
                    quadTo(centerX - w/2f, centerY - h - 5f, centerX, centerY - h)
                    lineTo(centerX, centerY + h)
                    
                    moveTo(centerX, centerY + h)
                    quadTo(centerX + w/2f, centerY + h - 5f, centerX + w, centerY + h)
                    lineTo(centerX + w, centerY - h)
                    quadTo(centerX + w/2f, centerY - h - 5f, centerX, centerY - h)
                    lineTo(centerX, centerY + h)
                }
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x4DFFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawPath(bookPath, iconPaint)
            }

            // Thumbnail border
            val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x26FFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbBorderPaint)

            // 6. Right column - Text and Progress Information
            val pbLeft = 232f
            val pbRight = 768f
            val textMaxW = pbRight - pbLeft

            // Title
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 34f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val displayTitle = ellipsizeText(title, titlePaint, textMaxW)
            canvas.drawText(displayTitle, pbLeft, 68f, titlePaint)

            // Author
            val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFC4C7C8.toInt()
                textSize = 24f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            }
            val displayAuthor = ellipsizeText(author ?: "", authorPaint, textMaxW)
            canvas.drawText(displayAuthor, pbLeft, 108f, authorPaint)

            // Progress Bar Track
            val progressFraction = if (totalChapters > 0) chapterIndex.toFloat() / totalChapters.toFloat() else 0f
            val pbY = 144f
            val pbH = 6f
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x26FFFFFF.toInt() // 15% opacity white
            }
            canvas.drawRoundRect(RectF(pbLeft, pbY, pbRight, pbY + pbH), 3f, 3f, trackPaint)

            // Progress Bar Fill (Sunset Orange)
            if (progressFraction > 0f) {
                val fillWidth = textMaxW * progressFraction.coerceIn(0f, 1f)
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFF571A.toInt() // Sunset Orange
                }
                canvas.drawRoundRect(RectF(pbLeft, pbY, pbLeft + fillWidth, pbY + pbH), 3f, 3f, fillPaint)
            }

            // Progress labels
            val pctText = "${(progressFraction * 100f).toInt()}%"
            val remainingChapters = (totalChapters - chapterIndex).coerceAtLeast(1)
            val minutesLeft = remainingChapters * 3
            val timeLeftText = if (progressFraction >= 1.0f) {
                "Finished"
            } else if (minutesLeft >= 60) {
                "${minutesLeft / 60}h left"
            } else {
                "${minutesLeft}m left"
            }

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99C4C7C8.toInt() // 60% opacity muted gray
                textSize = 19f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
            }
            canvas.drawText(pctText, pbLeft, 184f, labelPaint)

            val timeTextW = labelPaint.measureText(timeLeftText)
            canvas.drawText(timeLeftText, pbRight - timeTextW, 184f, labelPaint)

            // 7. Media Control and Reader Buttons
            val controlCenterY = 238f
            val prevCenterX = 272.5f
            val playCenterX = 387.5f
            val nextCenterX = 502.5f
            val readerCenterX = 727.5f

            // Play/Pause button background & active glow
            val playRadius = 36f
            if (isPlaying) {
                try {
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.Shader.TileMode.CLAMP.let { mode ->
                            android.graphics.RadialGradient(
                                playCenterX, controlCenterY, 60f,
                                intArrayOf(0x33FF571A.toInt(), 0x00000000.toInt()),
                                null,
                                mode
                            )
                        }
                    }
                    canvas.drawCircle(playCenterX, controlCenterY, 60f, glowPaint)
                } catch (_: Throwable) {}
            }

            val playBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x1AFFFFFF.toInt() // 10% opacity white
            }
            canvas.drawCircle(playCenterX, controlCenterY, playRadius, playBgPaint)

            val playBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x26FFFFFF.toInt() // 15% opacity white
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(playCenterX, controlCenterY, playRadius, playBorderPaint)

            // Play/Pause icon (White)
            val playIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            if (isPlaying) {
                val barW = 6f
                val barH = 20f
                val gap = 6f
                canvas.drawRect(playCenterX - barW - gap/2f, controlCenterY - barH/2f, playCenterX - gap/2f, controlCenterY + barH/2f, playIconPaint)
                canvas.drawRect(playCenterX + gap/2f, controlCenterY - barH/2f, playCenterX + barW + gap/2f, controlCenterY + barH/2f, playIconPaint)
            } else {
                val triSize = 12f
                val playPath = Path().apply {
                    moveTo(playCenterX - triSize/1.5f, controlCenterY - triSize)
                    lineTo(playCenterX + triSize, controlCenterY)
                    lineTo(playCenterX - triSize/1.5f, controlCenterY + triSize)
                    close()
                }
                canvas.drawPath(playPath, playIconPaint)
            }

            // Skip previous outline icon
            val prevIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xB3FFFFFF.toInt() // 70% opacity white
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val prevPath = Path().apply {
                moveTo(prevCenterX - 8f, controlCenterY - 10f)
                lineTo(prevCenterX - 8f, controlCenterY + 10f)
                moveTo(prevCenterX + 8f, controlCenterY - 10f)
                lineTo(prevCenterX - 4f, controlCenterY)
                lineTo(prevCenterX + 8f, controlCenterY + 10f)
                close()
            }
            canvas.drawPath(prevPath, prevIconPaint)

            // Skip next outline icon
            val nextIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xB3FFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val nextPath = Path().apply {
                moveTo(nextCenterX + 8f, controlCenterY - 10f)
                lineTo(nextCenterX + 8f, controlCenterY + 10f)
                moveTo(nextCenterX - 8f, controlCenterY - 10f)
                lineTo(nextCenterX + 4f, controlCenterY)
                lineTo(nextCenterX - 8f, controlCenterY + 10f)
                close()
            }
            canvas.drawPath(nextPath, nextIconPaint)

            // Reader Pill background
            val pillRect = RectF(638f, 211f, 768f, 265f)
            val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
            }
            canvas.drawRoundRect(pillRect, 27f, 27f, pillBgPaint)

            // Reader Pill Icon (Book)
            val pillIconCenterX = 670f
            val bookPath = Path().apply {
                val w = 11f
                val h = 8f
                moveTo(pillIconCenterX, controlCenterY + h)
                quadTo(pillIconCenterX - w/2f, controlCenterY + h - 2f, pillIconCenterX - w, controlCenterY + h)
                lineTo(pillIconCenterX - w, controlCenterY - h)
                quadTo(pillIconCenterX - w/2f, controlCenterY - h - 2f, pillIconCenterX, controlCenterY - h)
                lineTo(pillIconCenterX, controlCenterY + h)
                
                moveTo(pillIconCenterX, controlCenterY + h)
                quadTo(pillIconCenterX + w/2f, controlCenterY + h - 2f, pillIconCenterX + w, controlCenterY + h)
                lineTo(pillIconCenterX + w, controlCenterY - h)
                quadTo(pillIconCenterX + w/2f, controlCenterY - h - 2f, pillIconCenterX, controlCenterY - h)
                lineTo(pillIconCenterX, controlCenterY + h)
            }
            val pillIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF131313.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(bookPath, pillIconPaint)

            // Reader Pill Text ("READ")
            val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF131313.toInt()
                textSize = 20f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            canvas.drawText("READ", 698f, 245f, pillTextPaint)

            // 8. Outer Rim Light Border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                shader = android.graphics.LinearGradient(
                    0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                    0x3DFFFFFF.toInt(),
                    0x00FFFFFF.toInt(),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                RectF(1.25f, 1.25f, WIDGET_W.toFloat() - 1.25f, WIDGET_H.toFloat() - 1.25f),
                80f, 80f,
                borderPaint
            )

            return bmp
        }

        private suspend fun fetchCoverBitmap(context: Context, url: String?): Bitmap? {
            if (url.isNullOrBlank()) return null
            return withContext(Dispatchers.IO) {
                try {
                    val loader = SingletonImageLoader.get(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
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

        suspend fun updateWidgetState(
            context: Context,
            novelTitle: String,
            chapterName: String,
            coverUrl: String?,
            isPlaying: Boolean,
            coverBitmap: Bitmap? = null,
            novelUrl: String? = null,
            chapterIndex: Int? = null,
            totalChapters: Int? = null,
            author: String? = null
        ) {
            val rawCover = coverBitmap ?: fetchCoverBitmap(context, coverUrl)

            val coverFile = File(context.cacheDir, COVER_CACHE_FILE)
            if (rawCover != null) {
                safeApiCall {
                    FileOutputStream(coverFile).use { out ->
                        rawCover.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }
            }

            val actualChapterIndex = chapterIndex ?: withContext(Dispatchers.IO) {
                context.getKey<Int>(EPUB_CURRENT_POSITION, novelTitle) ?: 0
            }
            val actualTotalChapters = totalChapters ?: withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER)
                val cached = keys?.mapNotNull { key -> context.getKey<ResultCached>(key) }
                    ?.firstOrNull { it.name == novelTitle }
                cached?.totalChapters ?: 100
            }
            val actualNovelUrl = novelUrl ?: withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER)
                val cached = keys?.mapNotNull { key -> context.getKey<ResultCached>(key) }
                    ?.firstOrNull { it.name == novelTitle }
                cached?.source ?: ""
            }
            val actualAuthor = author ?: withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER)
                val cached = keys?.mapNotNull { key -> context.getKey<ResultCached>(key) }
                    ?.firstOrNull { it.name == novelTitle }
                cached?.author ?: ""
            }

            val composite = buildCompositeBitmap(rawCover, novelTitle, actualAuthor, chapterName, isPlaying, actualChapterIndex, actualTotalChapters)

            val compositeFile = File(context.cacheDir, COMPOSITE_CACHE_FILE)
            safeApiCall {
                FileOutputStream(compositeFile).use { out ->
                    composite.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TTSWidget::class.java)
            for (id in glanceIds) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[TTSWidgetKeys.NOVEL_TITLE] = novelTitle
                    prefs[TTSWidgetKeys.CHAPTER_NAME] = chapterName
                    prefs[TTSWidgetKeys.IS_PLAYING] = isPlaying
                    prefs[TTSWidgetKeys.COVER_PATH] = if (coverFile.exists()) coverFile.absolutePath else ""
                    prefs[TTSWidgetKeys.COVER_COMPOSITE_PATH] = if (compositeFile.exists()) compositeFile.absolutePath else ""
                    prefs[TTSWidgetKeys.NOVEL_URL] = actualNovelUrl
                    prefs[TTSWidgetKeys.CHAPTER_INDEX] = actualChapterIndex
                    prefs[TTSWidgetKeys.TOTAL_CHAPTERS] = actualTotalChapters
                    prefs[TTSWidgetKeys.AUTHOR] = actualAuthor
                }
                TTSWidget().update(context, id)
            }
        }

        suspend fun updateAll(context: Context) {
            withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER) ?: return@withContext
                val lastNovel = keys.mapNotNull { key ->
                    context.getKey<ResultCached>(key)
                }.maxByOrNull { it.cachedTime } ?: return@withContext

                val chapterIndex = context.getKey<Int>(EPUB_CURRENT_POSITION, lastNovel.name) ?: 0
                val chapterName = context.getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, lastNovel.name)
                    ?: "Chapter ${chapterIndex + 1}"
                
                val isPlaying = TTSNotificationService.viewModel?.isTTSRunning() == true ||
                        TTSForegroundService.instance?.isPlaying() == true

                updateWidgetState(
                    context = context,
                    novelTitle = lastNovel.name,
                    chapterName = chapterName,
                    coverUrl = lastNovel.poster,
                    isPlaying = isPlaying,
                    coverBitmap = null,
                    novelUrl = lastNovel.source,
                    chapterIndex = chapterIndex,
                    totalChapters = lastNovel.totalChapters,
                    author = lastNovel.author
                )
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[TTSWidgetKeys.NOVEL_TITLE]
            val author = prefs[TTSWidgetKeys.AUTHOR] ?: ""
            val novelUrl = prefs[TTSWidgetKeys.NOVEL_URL] ?: ""
            val chapterIndex = prefs[TTSWidgetKeys.CHAPTER_INDEX] ?: 0
            val compositePath = prefs[TTSWidgetKeys.COVER_COMPOSITE_PATH] ?: ""

            val launchIntent = Intent(context, ReadActivity2::class.java).apply {
                putExtra("novelUrl", novelUrl)
                putExtra("novelTitle", title ?: "")
                putExtra("chapterIndex", chapterIndex)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val compositeBitmap: Bitmap? = if (compositePath.isNotEmpty()) {
                try { BitmapFactory.decodeFile(compositePath) } catch (t: Throwable) { null }
            } else null

            if (compositeBitmap != null && title != null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(32.dp)
                        .background(ImageProvider(compositeBitmap))
                        .clickable(actionStartActivity(launchIntent)),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Row(
                        modifier = GlanceModifier
                            .padding(start = 93.dp, end = 13.dp, bottom = 7.dp)
                            .height(36.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Controls Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(32.dp)
                                    .clickable(actionRunCallback<RewindAction>())
                            ) {}
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            Box(
                                modifier = GlanceModifier
                                    .size(36.dp)
                                    .clickable(actionRunCallback<PlayPauseAction>())
                            ) {}
                            Spacer(modifier = GlanceModifier.width(12.dp))
                            Box(
                                modifier = GlanceModifier
                                    .size(32.dp)
                                    .clickable(actionRunCallback<ForwardAction>())
                            ) {}
                        }

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        // Reader Pill click target
                        Box(
                            modifier = GlanceModifier
                                .width(52.dp)
                                .height(36.dp)
                                .clickable(actionStartActivity(launchIntent))
                        ) {}
                    }
                }
            } else {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(32.dp)
                        .background(
                            ColorProvider(
                                day = androidx.compose.ui.graphics.Color(0xFF131313),
                                night = androidx.compose.ui.graphics.Color(0xFF131313)
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
                                    day = androidx.compose.ui.graphics.Color(0xFFE5E2E1),
                                    night = androidx.compose.ui.graphics.Color(0xFFE5E2E1)
                                ),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
