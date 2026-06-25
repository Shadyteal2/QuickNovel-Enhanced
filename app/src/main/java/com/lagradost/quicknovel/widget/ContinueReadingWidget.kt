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
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
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
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.ReadActivity2
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ─── Glance PreferencesKeys ──────────────────────────────

object ContinueReadingWidgetKeys {
    val NOVEL_TITLE = stringPreferencesKey("cr_novel_title")
    val CHAPTER_NAME = stringPreferencesKey("cr_chapter_name")
    val CHAPTER_INDEX = intPreferencesKey("cr_chapter_index")
    val TOTAL_CHAPTERS = intPreferencesKey("cr_total_chapters")
    val NOVEL_URL = stringPreferencesKey("cr_novel_url")
    val NOVEL_SOURCE = stringPreferencesKey("cr_novel_source")
    val COVER_PATH = stringPreferencesKey("cr_cover_path")
    val COVER_COMPOSITE_PATH = stringPreferencesKey("cr_cover_composite_path")
}

// ─── Widget ────────────────────────────────────────────────

class ContinueReadingWidget : GlanceAppWidget() {

    companion object {
        private const val COVER_CACHE_FILE = "cr_widget_cover.jpg"
        private const val COMPOSITE_CACHE_FILE = "cr_widget_composite.png"

        // Widget dimensions for composite rendering (matches 4×2 cell at ~160dpi)
        private const val WIDGET_W = 800
        private const val WIDGET_H = 300

        // ─── Composite canvas renderer ────────────────────────
        /**
         * Renders the full widget as a single Bitmap using [Canvas] so we
         * get precise control over the cover art quality, the scrim gradient,
         * the progress bar and the text — all at full resolution without any
         * Glance layout constraint hacks.
         *
         * This bitmap is then handed to [ImageProvider] and stretches to fill
         * the widget exactly. No blurriness, no pixelation.
         */
        private fun drawFallbackGradient(canvas: Canvas) {
            val paint = Paint()
            val shader = android.graphics.LinearGradient(
                0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                intArrayOf(0xFF0F172A.toInt(), 0xFF1E293B.toInt()), // Slate 900 to Slate 800
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = shader
            canvas.drawRect(0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(), paint)
        }

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

        private fun buildCompositeBitmap(
            coverBitmap: Bitmap?,
            title: String,
            chapterLabel: String,
            progressFraction: Float, // 0f..1f
            chapterIndex: Int,
            totalChapters: Int,
        ): Bitmap {
            val bmp = Bitmap.createBitmap(WIDGET_W, WIDGET_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // 1. Dark sleek background container (OLED black / deep gray)
            canvas.drawColor(0xFF0C0C0E.toInt())

            // 2. Soft, diffuse radial glow behind the connection wire
            try {
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(
                        600f, 130f, 220f,
                        intArrayOf(0x1F00FF88.toInt(), 0x00000000.toInt()), // 12% mint green glow
                        null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawCircle(600f, 130f, 220f, glowPaint)
            } catch (_: Throwable) {}

            // 3. Top-left Status Pill (Progress level)
            val pillRect = RectF(48f, 32f, 176f, 62f)
            val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x2600FF88.toInt() // 15% green background
            }
            canvas.drawRoundRect(pillRect, 15f, 15f, pillBgPaint)

            val fillW = pillRect.width() * progressFraction.coerceIn(0f, 1f)
            if (fillW > 0f) {
                canvas.save()
                canvas.clipRect(pillRect.left, pillRect.top, pillRect.left + fillW, pillRect.bottom)
                val pillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF00FF88.toInt() // Vibrant green fill
                }
                canvas.drawRoundRect(pillRect, 15f, 15f, pillFillPaint)
                canvas.restore()
            }

            // Progress percent text underneath status pill
            val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8E8E93.toInt()
                textSize = 21f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val progressPercentText = "${(progressFraction * 100f).toInt()}% complete"
            canvas.drawText(progressPercentText, 48f, 90f, pctPaint)

            // 4. Right-aligned Remaining Read Time (Time Left)
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE5E5EA.toInt()
                textSize = 24f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val remainingChapters = (totalChapters - chapterIndex).coerceAtLeast(1)
            val minutesLeft = remainingChapters * 3 // 3 mins estimated per chapter
            val timeLeftText = if (progressFraction >= 1.0f) {
                "Finished"
            } else if (minutesLeft >= 60) {
                "${minutesLeft / 60}h left"
            } else {
                "${minutesLeft}m left"
            }
            val timeTextW = timePaint.measureText(timeLeftText)
            canvas.drawText(timeLeftText, WIDGET_W - 48f - timeTextW, 58f, timePaint)

            // 5. Left Column Text — Title + Chapter label with proper breathing room
            val leftTextMaxW = 280f
            val titleTextSize = 27f
            val titleLineHeight = titleTextSize * 1.3f
            val titleStartY = 126f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = titleTextSize
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            // Count lines title occupies (max 2) so chapter label never overlaps
            val titleLineCount: Int = run {
                val buf = FloatArray(1)
                val fit = titlePaint.breakText(title, true, leftTextMaxW, buf)
                if (fit >= title.length) 1 else 2
            }
            drawWrappedText(canvas, title, titlePaint, 48f, titleStartY, leftTextMaxW, 2)

            // "CHAPTER" micro-label above the chapter name for visual hierarchy
            val microLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x6600FF88.toInt() // 40% mint
                textSize = 16f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                letterSpacing = 0.12f
            }
            val afterTitleY = titleStartY + (titleLineCount - 1) * titleLineHeight
            val microLabelY = afterTitleY + 26f
            canvas.drawText("CHAPTER", 48f, microLabelY, microLabelPaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt() // 80% white — more legible
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT
            }
            // 8px gap below the CHAPTER label
            val chapterLabelY = microLabelY + 8f + subPaint.textSize
            val displayChapter = ellipsizeText(chapterLabel, subPaint, leftTextMaxW)
            canvas.drawText(displayChapter, 48f, chapterLabelY, subPaint)

            // 6. Glowing connection wire curving from the cover art to the right margin
            val linePath = Path().apply {
                moveTo(446f, 110f)
                cubicTo(530f, 110f, 570f, 150f, WIDGET_W - 48f, 150f)
            }
            val lineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x3D00FF88.toInt() // 24% green glow
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(linePath, lineGlowPaint)

            val lineCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00FF88.toInt() // Bright core line
                style = Paint.Style.STROKE
                strokeWidth = 4f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(linePath, lineCorePaint)

            // 7. Center Floating Cover Art Card
            val cardX = 354f
            val cardY = 45f
            val cardW = 92f
            val cardH = 130f
            val cardRect = RectF(cardX, cardY, cardX + cardW, cardY + cardH)

            // Dynamic drop-shadow
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x7F000000.toInt()
            }
            val shadowRect = RectF(cardRect.left - 4f, cardRect.top + 6f, cardRect.right + 4f, cardRect.bottom + 10f)
            canvas.drawRoundRect(shadowRect, 14f, 14f, shadowPaint)

            if (coverBitmap != null) {
                val shader = BitmapShader(coverBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                val matrix = Matrix()
                val scale: Float
                val dx: Float
                val dy: Float
                val bmpW = coverBitmap.width.toFloat()
                val bmpH = coverBitmap.height.toFloat()

                if (bmpW * cardH > cardW * bmpH) {
                    scale = cardH / bmpH
                    dx = cardX + (cardW - bmpW * scale) / 2f
                    dy = cardY
                } else {
                    scale = cardW / bmpW
                    dx = cardX
                    dy = cardY + (cardH - bmpH * scale) / 2f
                }

                matrix.setScale(scale, scale)
                matrix.postTranslate(dx, dy)
                shader.setLocalMatrix(matrix)

                val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                }
                canvas.drawRoundRect(cardRect, 12f, 12f, cardPaint)

                // White subtle border around cover
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x26FFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(cardRect, 12f, 12f, borderPaint)
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x1AFFFFFF.toInt()
                }
                canvas.drawRoundRect(cardRect, 12f, 12f, placeholderPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x1AFFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(cardRect, 12f, 12f, borderPaint)
            }

            // 8. READ NOW pill — anchored to bottom-right corner
            val btnH = 56f
            val btnY = 224f
            val btnLeft = 472f   // wide pill spanning ~35% of width
            val btnRight = 752f
            val btnMidY = btnY + btnH / 2f
            val b3Rect = RectF(btnLeft, btnY, btnRight, btnY + btnH)

            // Subtle warm glow shadow behind pill
            val b3GlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    (btnLeft + btnRight) / 2f, btnMidY, btnH * 1.4f,
                    intArrayOf(0x55FFD54F.toInt(), 0x00000000.toInt()),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                RectF(btnLeft - 12f, btnY - 4f, btnRight + 12f, btnY + btnH + 8f),
                btnH / 2f, btnH / 2f, b3GlowPaint
            )

            // Amber-yellow pill fill
            val b3Bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt() }
            canvas.drawRoundRect(b3Rect, btnH / 2f, btnH / 2f, b3Bg)

            // READ NOW text + play icon, centered in pill
            val btnText = "READ NOW"
            val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0C0C0E.toInt()
                textSize = 25f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val iconSize = 14f
            val textIconGap = 10f
            val tW = btnTextPaint.measureText(btnText)
            val totalW = tW + textIconGap + iconSize
            val startX = (btnLeft + btnRight) / 2f - totalW / 2f
            val textBounds = Rect()
            btnTextPaint.getTextBounds(btnText, 0, btnText.length, textBounds)
            val textY = btnMidY - textBounds.exactCenterY()
            canvas.drawText(btnText, startX, textY, btnTextPaint)

            val iconX = startX + tW + textIconGap
            val iconY = btnMidY - iconSize / 2f
            canvas.drawPath(
                Path().apply {
                    moveTo(iconX, iconY)
                    lineTo(iconX + iconSize, iconY + iconSize / 2f)
                    lineTo(iconX, iconY + iconSize)
                    close()
                },
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0E.toInt(); style = Paint.Style.FILL }
            )

            // 9. Thin progress bar along the very bottom edge (2px strip)
            val pbH = 3f
            val pbY = WIDGET_H - pbH
            canvas.drawRect(0f, pbY, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF.toInt() })
            if (progressFraction > 0f) {
                val fillWidth = WIDGET_W * progressFraction.coerceIn(0f, 1f)
                canvas.drawRect(0f, pbY, fillWidth, WIDGET_H.toFloat(),
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.LinearGradient(
                            0f, 0f, fillWidth, 0f,
                            intArrayOf(0xFF00C853.toInt(), 0xFF00FF88.toInt()),
                            null,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                    })
            }

            // 10. Glass border around the widget
            canvas.drawRect(0f, 0f, WIDGET_W.toFloat(), WIDGET_H.toFloat(),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x1AFFFFFF.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                })

            return bmp
        }

        // ─── Fetch + cache cover at ORIGINAL resolution ────────
        private suspend fun fetchSharpCoverBitmap(context: Context, url: String?): Bitmap? {
            if (url.isNullOrBlank()) return null
            return withContext(Dispatchers.IO) {
                try {
                    val loader = SingletonImageLoader.get(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)  // Must be software bitmap for Canvas drawing
                        .size(Size.ORIGINAL)   // Full resolution — no upscaling artifacts
                        .build()
                    val result = loader.execute(request)
                    val drawable = (result as? SuccessResult)?.image?.asDrawable(context.resources)
                    (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } catch (t: Throwable) {
                    null
                }
            }
        }

        // ─── Public update API ────────────────────────────────────

        /** Called from ReadActivity2 / DownloadViewModel whenever reading state changes. */
        suspend fun updateWidgetState(
            context: Context,
            novelTitle: String,
            chapterName: String,
            chapterIndex: Int,
            totalChapters: Int,
            coverUrl: String?,
            novelUrl: String,
            novelSource: String,
            coverBitmap: Bitmap? = null,
        ) {
            val rawCover = coverBitmap ?: fetchSharpCoverBitmap(context, coverUrl)

            // Cache raw cover separately (for future re-composites without re-fetching)
            val coverFile = File(context.cacheDir, COVER_CACHE_FILE)
            if (rawCover != null) {
                safeApiCall {
                    FileOutputStream(coverFile).use { out ->
                        rawCover.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                }
            }

            val progress = if (totalChapters > 0) chapterIndex.toFloat() / totalChapters.toFloat() else 0f
            val composite = buildCompositeBitmap(rawCover, novelTitle, chapterName, progress, chapterIndex, totalChapters)

            val compositeFile = File(context.cacheDir, COMPOSITE_CACHE_FILE)
            safeApiCall {
                FileOutputStream(compositeFile).use { out ->
                    composite.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(ContinueReadingWidget::class.java)
            for (id in glanceIds) {
                updateAppWidgetState(context, id) { prefs ->
                    prefs[ContinueReadingWidgetKeys.NOVEL_TITLE] = novelTitle
                    prefs[ContinueReadingWidgetKeys.CHAPTER_NAME] = chapterName
                    prefs[ContinueReadingWidgetKeys.CHAPTER_INDEX] = chapterIndex
                    prefs[ContinueReadingWidgetKeys.TOTAL_CHAPTERS] = totalChapters
                    prefs[ContinueReadingWidgetKeys.NOVEL_URL] = novelUrl
                    prefs[ContinueReadingWidgetKeys.NOVEL_SOURCE] = novelSource
                    prefs[ContinueReadingWidgetKeys.COVER_PATH] = if (coverFile.exists()) coverFile.absolutePath else ""
                    prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] = if (compositeFile.exists()) compositeFile.absolutePath else ""
                }
                ContinueReadingWidget().update(context, id)
            }
        }

        /**
         * Self-refresh: reads the last-opened novel from history and pushes a
         * widget update. Called on [android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE].
         */
        suspend fun refreshFromHistory(context: Context) {
            withContext(Dispatchers.IO) {
                val keys = context.getKeys(HISTORY_FOLDER) ?: return@withContext
                val lastNovel = keys.mapNotNull { key -> context.getKey<ResultCached>(key) }
                    .maxByOrNull { it.cachedTime } ?: return@withContext

                val chapterIndex = context.getKey<Int>(EPUB_CURRENT_POSITION, lastNovel.name) ?: 0
                val chapterName = context.getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, lastNovel.name)
                    ?: "Chapter ${chapterIndex + 1}"
                val totalChapters = lastNovel.totalChapters.coerceAtLeast(1)

                updateWidgetState(
                    context = context,
                    novelTitle = lastNovel.name,
                    chapterName = chapterName,
                    chapterIndex = chapterIndex,
                    totalChapters = totalChapters,
                    coverUrl = lastNovel.poster,
                    novelUrl = lastNovel.source,
                    novelSource = lastNovel.apiName,
                )
            }
        }
    }

    // ─── Glance UI ────────────────────────────────────────────

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val title = prefs[ContinueReadingWidgetKeys.NOVEL_TITLE]
            val novelUrl = prefs[ContinueReadingWidgetKeys.NOVEL_URL] ?: ""
            val chapterIndex = prefs[ContinueReadingWidgetKeys.CHAPTER_INDEX] ?: 0
            val compositePath = prefs[ContinueReadingWidgetKeys.COVER_COMPOSITE_PATH] ?: ""

            // ── Intent — tapping anywhere on the widget opens the reader ──
            val readIntent = Intent(context, ReadActivity2::class.java).apply {
                putExtra("novelTitle", title ?: "")
                putExtra("chapterIndex", chapterIndex)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val compositeBitmap: Bitmap? = if (compositePath.isNotEmpty()) {
                try { BitmapFactory.decodeFile(compositePath) } catch (t: Throwable) { null }
            } else null

            if (compositeBitmap != null && title != null) {
                // ── Single-tap target: entire widget opens the reader ─────
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(28.dp)
                        .background(ImageProvider(compositeBitmap))
                        .clickable(actionStartActivity(readIntent)),
                    contentAlignment = Alignment.Center
                ) {}
            } else {
                // ── Placeholder (no history yet) ─────────────────────
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(28.dp)
                        .background(
                            ColorProvider(
                                day = androidx.compose.ui.graphics.Color(0xFF0C0C0E),
                                night = androidx.compose.ui.graphics.Color(0xFF0C0C0E)
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
                                    day = androidx.compose.ui.graphics.Color(0xFF00FF88),
                                    night = androidx.compose.ui.graphics.Color(0xFF00FF88)
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
        }
    }
}
