package com.lagradost.quicknovel.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.BitmapImage
import coil3.size.Size
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.theme.buildImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ShareCardEngine {

    suspend fun generateShareCard(
        context: Context,
        image: UiImage?,
        title: String,
        author: String?,
        rating: Int?,
        tags: List<String>?,
        apiName: String = "NeoQN",
        showTitle: Boolean = true,
        showAuthor: Boolean = true,
        showRating: Boolean = true,
        showTags: Boolean = true,
        isPremium: Boolean = false,
        premiumWord: String = "VISION",
        synopsis: String? = null,
        blurRadius: Int = 10,
        focusLeft: Float = 280f,
        focusTop: Float = 50f,
        focusRight: Float = 480f,
        focusBottom: Float = 1550f
    ): Bitmap = withContext(Dispatchers.IO) {
        val width = 1080
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Fetch and draw cover image
        var coverBmp: Bitmap? = null
        if (image != null) {
            try {
                val request = buildImageRequest(context, image)
                val result = SingletonImageLoader.get(context).execute(request)
                val drawable = (result as? SuccessResult)?.image
                val rawBmp = (drawable as? BitmapImage)?.bitmap
                if (rawBmp != null) {
                    coverBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rawBmp.config == Bitmap.Config.HARDWARE) {
                        rawBmp.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        rawBmp
                    }
                }
            } catch (t: Throwable) {
                // Ignore download failures, let fallback handle it
            }
        }

        if (isPremium) {
            // Draw blurred background cover or fallback gradient
            if (coverBmp != null) {
                val blurredBmp = try {
                    BlurTransformation(radius = blurRadius, scale = 0.4f).transform(coverBmp, Size.ORIGINAL)
                } catch (t: Throwable) {
                    coverBmp
                }
                val srcWidth = blurredBmp.width
                val srcHeight = blurredBmp.height
                val scale = Math.max(width.toFloat() / srcWidth, height.toFloat() / srcHeight)
                val scaledWidth = scale * srcWidth
                val scaledHeight = scale * srcHeight
                val left = (width - scaledWidth) / 2f
                val top = (height - scaledHeight) / 2f
                val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                canvas.drawBitmap(blurredBmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                // Apply 25% dark tint overlay for readability
                canvas.drawColor(Color.argb(64, 0, 0, 0))
            } else {
                val fallbackPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"), Color.BLACK),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fallbackPaint)
            }

            // Draw Focus Window (Sharp original image clipped)
            val focusRect = RectF(focusLeft, focusTop, focusRight, focusBottom)
            if (coverBmp != null) {
                canvas.save()
                canvas.clipRect(focusRect)
                val sharpSrcWidth = coverBmp.width
                val sharpSrcHeight = coverBmp.height
                val sharpScale = Math.max(width.toFloat() / sharpSrcWidth, height.toFloat() / sharpSrcHeight)
                val sharpScaledWidth = sharpScale * sharpSrcWidth
                val sharpScaledHeight = sharpScale * sharpSrcHeight
                val sharpLeft = (width - sharpScaledWidth) / 2f
                val sharpTop = (height - sharpScaledHeight) / 2f
                val sharpDestRect = RectF(sharpLeft, sharpTop, sharpLeft + sharpScaledWidth, sharpTop + sharpScaledHeight)
                canvas.drawBitmap(coverBmp, null, sharpDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            }

            // Draw thin white outline Rect over focus boundaries
            val borderPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
                alpha = 180
            }
            canvas.drawRect(focusRect, borderPaint)

            // Draw a small elegant white crosshair at the center of the focus window
            val centerX = focusRect.centerX()
            val centerY = focusRect.centerY()
            val crossPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2f
                isAntiAlias = true
                alpha = 180
            }
            val lineHalfLen = 15f
            canvas.drawLine(centerX - lineHalfLen, centerY, centerX + lineHalfLen, centerY, crossPaint)
            canvas.drawLine(centerX, centerY - lineHalfLen, centerX, centerY + lineHalfLen, crossPaint)

            // Draw custom uppercase word
            val word = if (premiumWord.isBlank()) "VISION" else premiumWord.trim().uppercase()
            val wordPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 130f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                isAntiAlias = true
                letterSpacing = 0.25f
            }
            canvas.drawText(word, 540f, 450f, wordPaint)

            // Draw description / synopsis (clean HTML, limit length)
            val cleanSynopsis = synopsis?.let {
                android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            } ?: "No description available."
            val maxDescLen = 250
            val descText = if (cleanSynopsis.length > maxDescLen) {
                cleanSynopsis.substring(0, maxDescLen).trim() + "..."
            } else {
                cleanSynopsis
            }
            val descPaint = TextPaint().apply {
                color = Color.parseColor("#E5E5E5")
                textSize = 28f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                isAntiAlias = true
                letterSpacing = 0.03f
            }
            val descLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(descText, 0, descText.length, descPaint, 490)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(12f, 1.15f)
                    .setIncludePad(false)
                    .setMaxLines(8)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(descText, descPaint, 490, Layout.Alignment.ALIGN_NORMAL, 1.1f, 12f, false)
            }
            canvas.save()
            canvas.translate(540f, 510f)
            descLayout.draw(canvas)
            canvas.restore()

            // Draw watermark top-left
            val watermarkPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 30f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isAntiAlias = true
                alpha = 180
                letterSpacing = 0.18f
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
            val appLogoSize = 34f
            val logoSpacing = 12f
            val logoRect = RectF(50f, 50f, 50f + appLogoSize, 50f + appLogoSize)
            val appLogoDrawable = androidx.core.content.res.ResourcesCompat.getDrawable(
                context.resources,
                com.lagradost.quicknovel.R.drawable.ic_quicknovel,
                context.theme
            )
            appLogoDrawable?.let { drawable ->
                drawable.setBounds(logoRect.left.toInt(), logoRect.top.toInt(), logoRect.right.toInt(), logoRect.bottom.toInt())
                drawable.draw(canvas)
            }
            canvas.drawText("NEOQN", 50f + appLogoSize + logoSpacing, 50f + 27f, watermarkPaint)

            // Draw Novel Title top-right
            val topTitlePaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 26f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                alpha = 200
                letterSpacing = 0.05f
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
            val maxTitleLen = 30
            val formattedTitle = if (title.length > maxTitleLen) title.substring(0, maxTitleLen) + "..." else title
            canvas.drawText(formattedTitle, 1030f, 76f, topTitlePaint)

            // Draw Bottom Metadata: left (provider), middle (author), right (first tag)
            val bottomMetaPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                isAntiAlias = true
                alpha = 180
                letterSpacing = 0.12f
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
            canvas.drawText(apiName.uppercase(), 50f, 1550f, bottomMetaPaint)

            val bottomAuthorPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                alpha = 180
                letterSpacing = 0.12f
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
            val formattedAuthor = author?.let { "-${it.uppercase()}-" } ?: "-UNKNOWN-"
            canvas.drawText(formattedAuthor, 540f, 1550f, bottomAuthorPaint)

            val bottomTagPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                alpha = 180
                letterSpacing = 0.12f
                setShadowLayer(4f, 1f, 1f, Color.BLACK)
            }
            val firstTag = tags?.firstOrNull()?.uppercase() ?: "NOVEL"
            canvas.drawText(firstTag, 1030f, 1550f, bottomTagPaint)

        } else {
            // 1. Draw solid black background
            canvas.drawColor(Color.BLACK)

            // 2. Fetch and draw cover image (Full-bleed 1600px height)
            if (coverBmp != null) {
                val srcWidth = coverBmp.width
                val srcHeight = coverBmp.height
                val scale = Math.max(width.toFloat() / srcWidth, height.toFloat() / srcHeight)
                val scaledWidth = scale * srcWidth
                val scaledHeight = scale * srcHeight
                val left = (width - scaledWidth) / 2f
                val top = (height - scaledHeight) / 2f
                val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                canvas.drawBitmap(coverBmp, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                // Draw a fallback gradient
                val fallbackPaint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"), Color.BLACK),
                        null, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fallbackPaint)
            }

            // Calculate heights and anchor layout dynamically from the bottom
            var totalDetailsHeight = 0f

            // Tags/Genre height
            val hasTags = showTags && !tags.isNullOrEmpty()
            if (hasTags) {
                totalDetailsHeight += 80f
            }

            // Title bold text height (max 2 lines)
            val hasTitle = showTitle && title.isNotBlank()
            val titlePaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 50f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val titleLayout = if (hasTitle) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, width - 100)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.1f)
                        .setIncludePad(false)
                        .setMaxLines(2)
                        .setEllipsize(android.text.TextUtils.TruncateAt.END)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    StaticLayout(title, titlePaint, width - 100, Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
                }
            } else null

            val titleHeight = titleLayout?.height?.toFloat() ?: 0f
            if (hasTitle) {
                totalDetailsHeight += titleHeight + 20f
            }

            // Author height
            val hasAuthor = showAuthor && !author.isNullOrBlank()
            if (hasAuthor) {
                totalDetailsHeight += 55f
            }

            // Rating height
            val hasRating = showRating && rating != null && rating > 0
            if (hasRating) {
                totalDetailsHeight += 60f
            }

            // 3. Smooth darkening gradient overlay on the bottom portion (dynamic starting point)
            val scrimStart = if (totalDetailsHeight > 0) height * 0.5f else height * 0.85f
            val scrimPaint = Paint().apply {
                shader = LinearGradient(
                    0f, scrimStart, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, Color.argb(120, 10, 14, 23), Color.argb(220, 10, 14, 23), Color.parseColor("#0A0E17")),
                    null, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, scrimStart, width.toFloat(), height.toFloat(), scrimPaint)

            // Determine starting Y coordinate based on total height of active details
            var currentY = 1450f - totalDetailsHeight

            // Render Tags
            if (hasTags && tags != null) {
                val tagBgPaint = Paint().apply {
                    color = Color.argb(25, 139, 92, 246) // Semi-transparent Purple
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val tagBorderPaint = Paint().apply {
                    color = Color.argb(70, 139, 92, 246) // Purple border
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }
                val tagTextPaint = TextPaint().apply {
                    color = Color.parseColor("#D8B4FE") // lavender purple
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                var currentX = 50f
                for (tag in tags.take(4)) {
                    val textWidth = tagTextPaint.measureText(tag)
                    val rect = RectF(currentX, currentY, currentX + textWidth + 36f, currentY + 52f)
                    canvas.drawRoundRect(rect, 26f, 26f, tagBgPaint)
                    canvas.drawRoundRect(rect, 26f, 26f, tagBorderPaint)
                    canvas.drawText(tag, currentX + 18f, currentY + 36f, tagTextPaint)
                    currentX += textWidth + 56f
                    if (currentX > width - 100f) break
                }
                currentY += 80f
            }

            // Render Title
            if (hasTitle && titleLayout != null) {
                canvas.save()
                canvas.translate(50f, currentY)
                titleLayout.draw(canvas)
                canvas.restore()
                currentY += titleHeight + 20f
            }

            // Render Author
            if (hasAuthor && author != null) {
                val authorPaint = TextPaint().apply {
                    color = Color.parseColor("#9E9E9E")
                    textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    isAntiAlias = true
                }
                canvas.drawText(author, 50f, currentY + 30f, authorPaint)
                currentY += 55f
            }

            // Render Rating
            if (hasRating && rating != null) {
                val ratingPaint = TextPaint().apply {
                    color = Color.parseColor("#F59E0B") // Amber gold
                    textSize = 30f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                val ratingText = String.format(java.util.Locale.US, "★ %.1f / 10", rating / 100.0)
                canvas.drawText(ratingText, 50f, currentY + 30f, ratingPaint)
            }

            // 6. Draw the watermark NeoQN logo and branding at top-right
            val brandPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                // Drop shadow for readability on light posters
                setShadowLayer(6f, 2f, 2f, Color.BLACK)
            }
            val brandText = "NeoQN"
            val textWidth = brandPaint.measureText(brandText)
            val logoSize = 40f
            val spacing = 15f
            val totalWidth = logoSize + spacing + textWidth
            val margin = 50f
            val blockX = width - margin - totalWidth
            val logoY = margin + 8f

            val logoRect = RectF(blockX, logoY, blockX + logoSize, logoY + logoSize)
            val appLogoDrawable = androidx.core.content.res.ResourcesCompat.getDrawable(
                context.resources,
                com.lagradost.quicknovel.R.drawable.ic_quicknovel,
                context.theme
            )
            appLogoDrawable?.let { drawable ->
                drawable.setBounds(logoRect.left.toInt(), logoRect.top.toInt(), logoRect.right.toInt(), logoRect.bottom.toInt())
                drawable.draw(canvas)
            }

            // Draw Brand Name
            canvas.drawText(brandText, blockX + logoSize + spacing, logoY + 32f, brandPaint)
        }

        bitmap
    }

    suspend fun saveImageToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val filename = "NeoQN_Share_${System.currentTimeMillis()}.jpg"
        var outputStream: OutputStream? = null
        var imageUri: Uri? = null
        try {
            val contentResolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NeoQN")
                }
                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    outputStream = contentResolver.openOutputStream(imageUri)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/NeoQN"
                val dir = File(imagesDir)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val imageFile = File(dir, filename)
                outputStream = FileOutputStream(imageFile)
                // Trigger scanner
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, imageFile.absolutePath)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                }
                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            }
            if (outputStream != null) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
        } catch (t: Throwable) {
            imageUri = null
        } finally {
            try {
                outputStream?.close()
            } catch (ignored: Throwable) {}
        }
        imageUri
    }

    suspend fun getShareCacheUri(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "shared_images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val file = File(cacheDir, "neoqn_share_temp.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (t: Throwable) {
            null
        }
    }
}
