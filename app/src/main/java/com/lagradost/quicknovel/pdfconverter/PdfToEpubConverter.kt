package com.lagradost.quicknovel.pdfconverter

import android.content.Context
import android.os.ParcelFileDescriptor
import com.lagradost.quicknovel.mvvm.logError
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.ag2s.epublib.domain.Author
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.epub.EpubWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class PdfToEpubRequest(
    val file: File,
    val title: String,
    val author: String,
    val autoFixParagraphs: Boolean
)

data class PdfToEpubProgress(
    val page: Int,
    val totalPages: Int
)

object PdfToEpubConverter {
    private const val PAGES_PER_CHAPTER = 20

    suspend fun convert(
        context: Context,
        request: PdfToEpubRequest,
        outputFile: File,
        onProgress: suspend (PdfToEpubProgress) -> Unit
    ): File = withContext(Dispatchers.Default) {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }

        val outlines = getOutlines(request.file)

        FileInputStream(request.file).use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                val totalPages = document.numberOfPages.coerceAtLeast(1)

                // Split pages into chapters based on outlines, or fallback to PAGES_PER_CHAPTER chunks
                val chapters = if (outlines.isNotEmpty()) {
                    val sortedOutlines = outlines.sortedBy { it.second }
                    val list = mutableListOf<Pair<String, IntRange>>()
                    for (i in sortedOutlines.indices) {
                        val title = sortedOutlines[i].first
                        val startPage = sortedOutlines[i].second + 1 // 1-based index
                        val endPage = if (i < sortedOutlines.size - 1) {
                            (sortedOutlines[i + 1].second).coerceAtMost(totalPages)
                        } else {
                            totalPages
                        }
                        if (startPage <= endPage) {
                            list.add(title to (startPage..endPage))
                        }
                    }
                    list
                } else {
                    val list = mutableListOf<Pair<String, IntRange>>()
                    var pageIndex = 1
                    var chapterNum = 1
                    while (pageIndex <= totalPages) {
                        val end = (pageIndex + PAGES_PER_CHAPTER - 1).coerceAtMost(totalPages)
                        list.add("Chapter $chapterNum" to (pageIndex..end))
                        pageIndex = end + 1
                        chapterNum++
                    }
                    list
                }

                val book = EpubBook().apply {
                    metadata.addTitle(request.title)
                    metadata.addAuthor(Author(request.author))
                }
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }

                var actualChapterIndex = 1
                for (chapter in chapters) {
                    coroutineContext.ensureActive()
                    val chapterTitle = chapter.first
                    val pageRange = chapter.second

                    val chapterBuilder = StringBuilder()
                    for (pageIndex in pageRange) {
                        coroutineContext.ensureActive()
                        stripper.startPage = pageIndex
                        stripper.endPage = pageIndex

                        val rawText = stripper.getText(document)
                        val cleanedText = cleanExtractedText(rawText, request.autoFixParagraphs)
                        if (cleanedText.isNotBlank()) {
                            chapterBuilder.append(formatChapterHtml(cleanedText))
                        }
                        onProgress(PdfToEpubProgress(pageIndex, totalPages))
                    }

                    if (chapterBuilder.isNotBlank()) {
                        val resource = Resource(
                            createHtmlWrapper(chapterTitle, chapterBuilder.toString())
                                .toByteArray(Charsets.UTF_8),
                            "chapter_$actualChapterIndex.xhtml"
                        )
                        book.addSection(chapterTitle, resource)
                        actualChapterIndex++
                    }
                }

                if (actualChapterIndex == 1) {
                    val resource = Resource(
                        createHtmlWrapper("Chapter 1", "<p>No extractable text was found in this PDF.</p>")
                            .toByteArray(Charsets.UTF_8),
                        "chapter_1.xhtml"
                    )
                    book.addSection("Chapter 1", resource)
                }

                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { outputStream ->
                    EpubWriter().write(book, outputStream)
                }
                outputFile
            }
        }
    }

    private suspend fun getOutlines(file: File): List<Pair<String, Int>> {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val core = PdfiumCoreKt(Dispatchers.Default)
            val doc = core.newDocument(pfd)
            val outlines = doc.getTableOfContents()
            val list = outlines.map { outline ->
                outline.title.orEmpty() to outline.pageIdx.toInt()
            }.filter { it.first.isNotBlank() && it.second >= 0 }
            doc.close()
            pfd.close()
            list
        } catch (t: Throwable) {
            logError(t)
            emptyList()
        }
    }

    private fun formatChapterHtml(text: String): String = buildString {
        text.split("\n\n").forEach { paragraph ->
            val trimmed = paragraph.trim()
            if (trimmed.isNotEmpty()) {
                val lines = trimmed.lines()
                val isHeading = lines.size == 1 &&
                        trimmed.length < 60 &&
                        trimmed.lastOrNull() !in listOf('.', '!', '?')

                if (isHeading) {
                    append("<h2>")
                    append(escapeHtml(trimmed))
                    append("</h2>")
                } else {
                    append("<p>")
                    append(escapeHtml(trimmed).replace("\n", "<br/>"))
                    append("</p>")
                }
            }
        }
    }

    private fun cleanExtractedText(text: String, autoFixParagraphs: Boolean): String = buildString {
        val normalized = text
            .replace('\u0000', ' ')
            .replace(Regex("[\\u0001-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), " ")
            .lines()
            .map { line -> line.trim() }
            .filterNot { line -> line.isBlank() }

        if (!autoFixParagraphs) {
            normalized.forEachIndexed { index, line ->
                append(line)
                if (index < normalized.size - 1) {
                    append("\n\n")
                }
            }
            return@buildString
        }

        var firstParagraph = true
        val current = StringBuilder()
        for (line in normalized) {
            if (current.isEmpty()) {
                current.append(line)
                continue
            }

            val previous = current.lastOrNull()
            val firstChar = line.first()
            val isBulletOrListItem = firstChar.isDigit() || firstChar in "-*•[{("
            val shouldMerge = previous != null && previous !in ".?!:;\"')]" && !firstChar.isUpperCase() && !isBulletOrListItem
            if (shouldMerge) {
                current.append(' ').append(line)
            } else {
                if (!firstParagraph) {
                    append("\n\n")
                }
                append(current)
                current.clear()
                current.append(line)
                firstParagraph = false
            }
        }
        if (current.isNotEmpty()) {
            if (!firstParagraph) {
                append("\n\n")
            }
            append(current)
        }
    }

    private fun createHtmlWrapper(title: String, content: String): String {
        return """
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <meta charset="utf-8"/>
                    <title>${escapeHtml(title)}</title>
                </head>
                <body>
                    $content
                </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String {
        return try {
            org.jsoup.nodes.TextNode(value).outerHtml()
        } catch (t: Throwable) {
            logError(t)
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
    }
}
