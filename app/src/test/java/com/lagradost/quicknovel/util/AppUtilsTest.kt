package com.lagradost.quicknovel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [AppUtils] — JSON utilities and [AppUtils.textToHtmlChapter].
 *
 * All pure JVM — no Android dependencies.
 */
class AppUtilsTest {

    // ── toJson / tryParseJson round-trip ──────────────────────────────────────

    private data class SampleDto(val id: Int, val name: String)

    @Test fun `toJson serializes data class to JSON string`() {
        val json = with(AppUtils) { SampleDto(1, "dune").toJson() }
        assertTrue(json.contains("\"id\":1"))
        assertTrue(json.contains("\"name\":\"dune\""))
    }

    @Test fun `toJson with String input returns the string unchanged`() {
        val raw = "already a string"
        val result = with(AppUtils) { raw.toJson() }
        assertEquals(raw, result)
    }

    @Test fun `tryParseJson returns parsed object on valid JSON`() {
        val json = """{"id":42,"name":"Foundation"}"""
        val result = AppUtils.tryParseJson<SampleDto>(json)
        assertNotNull(result)
        assertEquals(42, result!!.id)
        assertEquals("Foundation", result.name)
    }

    @Test fun `tryParseJson returns null on null input`() {
        val result = AppUtils.tryParseJson<SampleDto>(null)
        assertNull(result)
    }

    @Test fun `tryParseJson returns null on malformed JSON`() {
        val result = AppUtils.tryParseJson<SampleDto>("{not valid json}")
        assertNull(result)
    }

    @Test fun `tryParseJson returns null on empty string`() {
        val result = AppUtils.tryParseJson<SampleDto>("")
        assertNull(result)
    }

    @Test fun `round-trip serialize then deserialize preserves values`() {
        val original = SampleDto(99, "Hyperion")
        val json = with(AppUtils) { original.toJson() }
        val parsed = AppUtils.tryParseJson<SampleDto>(json)
        assertEquals(original, parsed)
    }

    // ── textToHtmlChapter ─────────────────────────────────────────────────────

    /**
     * The function converts raw text chapters (with hard newlines) into an
     * HTML string of `<p>` and `</br>` tags.
     */
    @Test fun `textToHtmlChapter wraps single sentence in paragraph tags`() {
        val input = "This is one sentence."
        val result = with(AppUtils) { input.textToHtmlChapter() }
        assertTrue("Expected <p> tag", result.contains("<p>"))
        assertTrue("Expected </br>", result.contains("</br>"))
    }

    @Test fun `textToHtmlChapter blank input produces only a br tag`() {
        val input = "\n"
        val result = with(AppUtils) { input.textToHtmlChapter() }
        // blank lines become </br>, not <p>
        assertTrue(result.contains("</br>"))
        // should not create empty <p> tags for blank lines
        assertTrue(!result.startsWith("<p>"))
    }

    @Test fun `textToHtmlChapter empty string produces only a br tag`() {
        // split("") → [""]. Blank element → "</br>". So empty never returns truly empty.
        val result = with(AppUtils) { "".textToHtmlChapter() }
        assertEquals("</br>", result)
    }

    @Test fun `textToHtmlChapter handles multiple paragraphs`() {
        val input = "First paragraph.\n\nSecond paragraph."
        val result = with(AppUtils) { input.textToHtmlChapter() }
        // Both paragraphs must be wrapped
        val pCount = result.split("<p>").size - 1
        assertTrue("Expected at least 2 <p> tags, got $pCount", pCount >= 2)
    }

    @Test fun `textToHtmlChapter joins soft-wrapped lowercase continuation`() {
        // The regex matches SPACE + \n between lowercase chars and collapses to a space.
        // Input must have a space before \n for the pattern to fire.
        val input = "this is a soft \nwrapped line"
        val result = with(AppUtils) { input.textToHtmlChapter() }
        // The " \n" between lowercase chars is replaced by " ", joining the phrase.
        assertTrue("Soft-wrap should be joined: $result", result.contains("soft wrapped line"))
    }

    @Test fun `textToHtmlChapter preserves sentence structure`() {
        val input = "First sentence. Second sentence."
        val result = with(AppUtils) { input.textToHtmlChapter() }
        // Content must appear in output
        assertTrue(result.contains("First sentence"))
        assertTrue(result.contains("Second sentence"))
    }

    @Test fun `textToHtmlChapter output always ends with br or is empty`() {
        val input = "Chapter content here."
        val result = with(AppUtils) { input.textToHtmlChapter() }
        if (result.isNotEmpty()) {
            assertTrue("Output should end with </br>", result.trimEnd().endsWith("</br>"))
        }
    }
}
