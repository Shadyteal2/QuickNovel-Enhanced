package com.lagradost.quicknovel.extractors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.lagradost.quicknovel.mvvm.logError
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

data class ExtractedMetadata(
    val title: String,
    val author: String?,
    val coverImage: ByteArray?
)

object BookImporter {
    fun extractMetadata(context: Context, uri: Uri, mimeType: String?, fileName: String): ExtractedMetadata? {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return try {
            when {
                extension == "epub" || mimeType?.contains("epub") == true -> extractEpub(context, uri, fileName)
                extension == "pdf" || mimeType?.contains("pdf") == true -> extractPdf(context, uri, fileName)
                extension == "mobi" || extension == "prc" || extension == "azw3" || mimeType?.contains("mobipocket") == true -> extractMobi(context, uri, fileName)
                else -> null
            }
        } catch (e: Exception) {
            logError(e)
            // Fallback for failed extractions
            val title = fileName.substringBeforeLast('.').replace("_", " ").replace("-", " ")
            ExtractedMetadata(title, "Unknown Author", generatePlaceholderCover(context, title))
        }
    }

    private fun extractEpub(context: Context, uri: Uri, fallbackName: String): ExtractedMetadata {
        var title: String? = null
        var author: String? = null
        var coverBytes: ByteArray? = null
        
        val tempFile = java.io.File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.epub")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            java.util.zip.ZipFile(tempFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml")
                var opfPath: String? = null

                if (containerEntry != null) {
                    val content = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                    val matcher = "full-path=\"([^\"]+)\"".toRegex().find(content)
                    opfPath = matcher?.groupValues?.get(1)
                }

                if (opfPath != null) {
                    val opfEntry = zip.getEntry(opfPath)
                    if (opfEntry != null) {
                        val content = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                        title = "<dc:title[^>]*>([^<]+)</dc:title>".toRegex().find(content)?.groupValues?.get(1)
                        author = "<dc:creator[^>]*>([^<]+)</dc:creator>".toRegex().find(content)?.groupValues?.get(1)
                        
                        var coverPath: String? = null
                        val coverId = "name=\"cover\" content=\"([^\"]+)\"".toRegex().find(content)?.groupValues?.get(1)
                        if (coverId != null) {
                            coverPath = "id=\"$coverId\" href=\"([^\"]+)\"".toRegex().find(content)?.groupValues?.get(1)
                        }
                        if (coverPath == null) {
                            coverPath = "properties=\"cover-image\" href=\"([^\"]+)\"".toRegex().find(content)?.groupValues?.get(1)
                        }

                        if (coverPath != null) {
                            val root = opfPath!!.substringBeforeLast('/', "")
                            val resolvedCoverPath = if (root.isNotEmpty()) "$root/$coverPath" else coverPath!!
                            val coverEntry = zip.getEntry(resolvedCoverPath)
                            if (coverEntry != null) {
                                coverBytes = zip.getInputStream(coverEntry).use { it.readBytes() }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }

        val finalTitle = title ?: fallbackName.substringBeforeLast('.')
        return ExtractedMetadata(
            title = finalTitle,
            author = author,
            coverImage = coverBytes ?: generatePlaceholderCover(context, finalTitle)
        )
    }

    private fun extractPdf(context: Context, uri: Uri, fallbackName: String): ExtractedMetadata {
        var coverBytes: ByteArray? = null
        
        try {
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    // Render at high-res width 600
                    val width = 600
                    val height = (width.toFloat() / page.width * page.height).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Fill white background just in case PDF is transparent
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    coverBytes = stream.toByteArray()
                    
                    bitmap.recycle()
                    page.close()
                }
                renderer.close()
                pfd.close()
            }
        } catch (e: Exception) {
            logError(e)
        }

        val finalTitle = fallbackName.substringBeforeLast('.').replace("_", " ").replace("-", " ")
        return ExtractedMetadata(
            title = finalTitle,
            author = "PDF Document",
            coverImage = coverBytes ?: generatePlaceholderCover(context, finalTitle)
        )
    }

    private fun extractMobi(context: Context, uri: Uri, fallbackName: String): ExtractedMetadata {
        // Advanced MOBI extraction via EXTH records requires extensive binary bridging.
        // For standard implementation, fallback to title name generation + placeholder.
        val finalTitle = fallbackName.substringBeforeLast('.').replace("_", " ").replace("-", " ")
        return ExtractedMetadata(
            title = finalTitle,
            author = "MOBI Ebook",
            coverImage = generatePlaceholderCover(context, finalTitle)
        )
    }

    fun generatePlaceholderCover(context: Context, title: String): ByteArray {
        val width = 450
        val height = 650
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        try {
            // Material 3 Secondary Container Color approximation (#EADDFF)
            var bgColor = Color.parseColor("#EADDFF")
            var textColor = Color.parseColor("#21005D") // On-Secondary Container
            
            val typedArray = context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorSecondaryContainer, com.google.android.material.R.attr.colorOnSecondaryContainer))
            bgColor = typedArray.getColor(0, bgColor)
            textColor = typedArray.getColor(1, textColor)
            typedArray.recycle()
            
            canvas.drawColor(bgColor)
            
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            
            // Text wrapping wrapper
            val words = title.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""
            for (word in words) {
                if (paint.measureText("$currentLine $word") < width - 40) {
                    currentLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                } else {
                    lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            
            val startY = (height / 2f) - (lines.size * paint.textSize / 2f)
            for ((index, line) in lines.withIndex()) {
                canvas.drawText(line, width / 2f, startY + (index * paint.textSize * 1.5f), paint)
            }
            
        } catch (e: Exception) {
            logError(e)
            canvas.drawColor(Color.LTGRAY)
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
