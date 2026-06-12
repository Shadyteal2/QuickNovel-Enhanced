package com.lagradost.quicknovel.ui.reader

import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lagradost.quicknovel.TextSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Page(
    val text: String,
    val startChar: Int,
    val endChar: Int,
    val firstParagraphIndex: Int
)

data class PaginationResult(
    val pages: List<Page>,
    val paragraphToPage: Map<Int, Int>,
    val pageToParagraph: Map<Int, Int>
)

object TextPaginator {

    // ─── Core precision constants ───────────────────────────────────────────────
    // Compose's Text composable renders lines fractionally taller than
    // StaticLayout's integer pixel measurements due to sub-pixel rounding and
    // any residual line-spacing from the theme. A 1.2x buffer absorbs this.
    private const val SAFETY_LINE_MULTIPLIER = 1.2f

    suspend fun paginate(
        text: CharSequence,
        spans: List<TextSpan>,
        paint: TextPaint,
        width: Int,
        height: Int,
        spacingMult: Float = 1.0f,
        spacingAdd: Float = 0.0f
    ): PaginationResult = withContext(Dispatchers.Default) {
        if (text.isEmpty() || width <= 0 || height <= 0) {
            return@withContext PaginationResult(emptyList(), emptyMap(), emptyMap())
        }

        // Measure one accurate line height using FontMetrics (more precise than
        // paint.descent() - paint.ascent() which ignores leading).
        val fm = android.graphics.Paint.FontMetrics()
        paint.getFontMetrics(fm)
        val singleLineHeight = fm.descent - fm.ascent + fm.leading
        val safetyBuffer = singleLineHeight * SAFETY_LINE_MULTIPLIER
        // The effective page height that StaticLayout is allowed to fill.
        val effectiveHeight = (height - safetyBuffer).coerceAtLeast(singleLineHeight)

        val pages = mutableListOf<Page>()
        var remainingText = text
        var currentOffset = 0

        fun buildLayout(source: CharSequence): StaticLayout {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(source, 0, source.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(spacingAdd, spacingMult)
                    .setIncludePad(false)   // Match Compose's includeFontPadding = false
                    .build()
            } else {
                @Suppress("DEPRECATION")
                StaticLayout(
                    source, paint, width,
                    Layout.Alignment.ALIGN_NORMAL,
                    spacingMult, spacingAdd,
                    false
                )
            }
        }

        fun getFirstParagraphIndex(startChar: Int): Int =
            spans.firstOrNull { it.start >= startChar }?.innerIndex
                ?: spans.lastOrNull()?.innerIndex
                ?: 0

        while (remainingText.isNotEmpty()) {
            val layout = buildLayout(remainingText)
            val lineCount = layout.lineCount
            if (lineCount == 0) break

            // ─── Find last line whose bottom edge fits inside effectiveHeight ───
            // We walk forward (O(n) with early exit) rather than binary search
            // because line heights are monotonically increasing — no gain from BS.
            var lastVisibleLine = lineCount - 1   // assume all lines fit
            var allFit = true

            for (i in 0 until lineCount) {
                // getLineBottom already accounts for line spacing
                if (layout.getLineBottom(i).toFloat() > effectiveHeight) {
                    allFit = false
                    // Must fit at least line 0 to avoid infinite loop
                    lastVisibleLine = if (i > 0) i - 1 else 0
                    break
                }
            }

            if (allFit) {
                // Every remaining line fits — this is the final page
                val pageText = remainingText.toString()
                pages.add(
                    Page(
                        text = pageText,
                        startChar = currentOffset,
                        endChar = currentOffset + remainingText.length,
                        firstParagraphIndex = getFirstParagraphIndex(currentOffset)
                    )
                )
                break
            }

            // getLineEnd includes the trailing newline. We split at that boundary
            // so the current page gets the full line. We then skip any leading
            // whitespace so the next page always starts at a word boundary.
            val splitOffset = layout.getLineEnd(lastVisibleLine).coerceIn(1, remainingText.length)

            // Advance past any trailing whitespace/newlines to find the true next-page start
            var nextStart = splitOffset
            while (nextStart < remainingText.length && remainingText[nextStart].isWhitespace()) {
                nextStart++
            }

            val pageText = remainingText.subSequence(0, splitOffset).toString().trimEnd()
            pages.add(
                Page(
                    text = pageText,
                    startChar = currentOffset,
                    endChar = currentOffset + nextStart,   // endChar = where next page starts
                    firstParagraphIndex = getFirstParagraphIndex(currentOffset)
                )
            )
            remainingText = remainingText.subSequence(nextStart, remainingText.length)
            currentOffset += nextStart
        }

        // ─── Build bidirectional paragraph ↔ page maps ──────────────────────────
        val paragraphToPage = mutableMapOf<Int, Int>()
        val pageToParagraph = mutableMapOf<Int, Int>()

        pages.forEachIndexed { pageIndex, page ->
            pageToParagraph[pageIndex] = page.firstParagraphIndex

            spans
                .filter { it.start >= page.startChar && it.start < page.endChar }
                .forEach { paragraphToPage[it.innerIndex] = pageIndex }

            if (!paragraphToPage.containsKey(page.firstParagraphIndex)) {
                paragraphToPage[page.firstParagraphIndex] = pageIndex
            }
        }

        // Fallback: map any unmapped span to the closest page
        spans.forEach { span ->
            if (!paragraphToPage.containsKey(span.innerIndex)) {
                val idx = pages.indexOfFirst { it.startChar <= span.start && span.start < it.endChar }
                paragraphToPage[span.innerIndex] = if (idx != -1) idx else 0
            }
        }

        PaginationResult(pages, paragraphToPage, pageToParagraph)
    }
}
