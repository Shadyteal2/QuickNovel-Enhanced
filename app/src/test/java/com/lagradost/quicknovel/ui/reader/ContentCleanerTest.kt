package com.lagradost.quicknovel.ui.reader

import com.lagradost.quicknovel.ui.reader.customization.ContentCleanRule
import com.lagradost.quicknovel.ui.reader.customization.TextReplacement
import com.lagradost.quicknovel.ui.reader.customization.compile
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests to test and demonstrate the HTML cleaning and text replacement engine.
 *
 * This acts as a test script showing how:
 * 1. CSS Selectors (Jsoup) target and strip unwanted HTML elements.
 * 2. String literal replacements remove/change matched words.
 * 3. Pre-compiled Regex replacements handle dynamic/complex patterns.
 *
 * Run this test directly in Android Studio or from CLI via:
 * `./gradlew :app:testDebugUnitTest --tests "com.lagradost.quicknovel.ui.reader.ContentCleanerTest"`
 */
class ContentCleanerTest {

    @Test
    fun testJsoupCssSelectorRemoval() {
        // Arrange: HTML with ads and watermarks we want to remove
        val rawHtml = """
            <html>
                <body>
                    <div class="ads-container">ADVERTISEMENT: Buy more stuff!</div>
                    <h1>Chapter 1: The Beginning</h1>
                    <p>This is the actual interesting novel content.</p>
                    <p class="watermark">Scraped from NovelSite.com - Do not copy</p>
                </body>
            </html>
        """.trimIndent()

        val selectorsToRemove = listOf("div.ads-container", "p.watermark")

        // Act: Parse HTML and remove targeted elements via Jsoup
        val doc = Jsoup.parse(rawHtml)
        for (selector in selectorsToRemove) {
            doc.select(selector).remove()
        }
        val cleanHtml = doc.body().html().trim()

        // Assert: Ads and watermarks are stripped out, only header and content paragraph remain
        val expected = """
            <h1>Chapter 1: The Beginning</h1>
            <p>This is the actual interesting novel content.</p>
        """.trimIndent().replace("\r\n", "\n").trim()

        assertEquals(expected, cleanHtml.replace("\r\n", "\n").trim())
    }

    @Test
    fun testStringLiteralReplacement() {
        // Arrange: Input text with sponsor phrases
        val originalText = "Join our Discord for early updates! Chapter 1 was awesome. Support us on Patreon!"
        
        val replacements = listOf(
            TextReplacement(find = "Join our Discord for early updates! ", replace = ""),
            TextReplacement(find = "Support us on Patreon!", replace = "Thank you for reading!")
        )

        // Act: Apply literal string replacements sequentially
        var text = originalText
        for (replacement in replacements) {
            text = text.replace(replacement.find, replacement.replace)
        }

        // Assert
        val expected = "Chapter 1 was awesome. Thank you for reading!"
        assertEquals(expected, text)
    }

    @Test
    fun testPreCompiledRegexReplacement() {
        // Arrange: Input text with dynamic watermarks and chapter labels
        val originalText = "Chapter 999: The Final Showdown. [NovelSite Watermark 12345] Read at WuxiaWorld"

        val rule = ContentCleanRule(
            id = "test-rule-1",
            providerApiName = "*",
            enabled = true,
            selectorsToRemove = emptyList(),
            replacements = listOf(
                TextReplacement(find = "\\[NovelSite Watermark \\d+\\]", replace = "", isRegex = true),
                TextReplacement(find = "(?i)Read at (WuxiaWorld|Scribblehub)", replace = "[Free Reader]", isRegex = true)
            )
        )

        // Compile the rule (caches compiled Regex objects)
        val compiledRule = rule.compile()

        // Act: Apply compiled Regex replacements
        var text = originalText
        for (replacement in compiledRule.replacements) {
            val regex = replacement.regex
            text = if (replacement.isRegex && regex != null) {
                regex.replace(text, replacement.replace)
            } else {
                text.replace(replacement.find, replacement.replace)
            }
        }
        val cleanText = text.trim().replace(Regex("\\s+"), " ")

        // Assert
        val expected = "Chapter 999: The Final Showdown. [Free Reader]"
        assertEquals(expected, cleanText)
    }
}
