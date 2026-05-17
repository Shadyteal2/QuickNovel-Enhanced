package com.lagradost.quicknovel.ui.reader

import com.lagradost.quicknovel.TTSHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure [ReaderState.reduce] function.
 *
 * These tests run on the JVM (no Android device needed — run via ./gradlew test).
 * Because [reduce] is a plain Kotlin function with zero Android dependencies,
 * there are no Robolectric rules, no mocks, and no coroutine setup required.
 *
 * Each test follows: Arrange → Act → Assert
 */
class ReaderStateTest {

    // ── Baseline state shared across tests ───────────────────────────────────
    private val base = ReaderState()

    // ── SetBookTitle ─────────────────────────────────────────────────────────

    @Test
    fun `SetBookTitle sets bookTitle`() {
        val result = base.reduce(ReaderAction.SetBookTitle("Dune"))
        assertEquals("Dune", result.bookTitle)
    }

    @Test
    fun `SetBookTitle allows null to clear title`() {
        val withTitle = base.copy(bookTitle = "Dune")
        val result = withTitle.reduce(ReaderAction.SetBookTitle(null))
        assertNull(result.bookTitle)
    }

    // ── SetCurrentIndex ──────────────────────────────────────────────────────

    @Test
    fun `SetCurrentIndex updates currentIndex`() {
        val result = base.reduce(ReaderAction.SetCurrentIndex(42))
        assertEquals(42, result.currentIndex)
    }

    @Test
    fun `SetCurrentIndex does not mutate other fields`() {
        val stateWithTitle = base.copy(bookTitle = "Dune")
        val result = stateWithTitle.reduce(ReaderAction.SetCurrentIndex(5))
        assertEquals("Dune", result.bookTitle)   // unaffected
        assertEquals(5, result.currentIndex)
    }

    // ── ToggleTranslation ────────────────────────────────────────────────────

    @Test
    fun `ToggleTranslation activates translation`() {
        val result = base.reduce(ReaderAction.ToggleTranslation(active = true))
        assertTrue(result.isTranslationActive)
    }

    @Test
    fun `ToggleTranslation deactivates translation`() {
        val active = base.copy(isTranslationActive = true)
        val result = active.reduce(ReaderAction.ToggleTranslation(active = false))
        assertFalse(result.isTranslationActive)
    }

    // ── ToggleTTS ────────────────────────────────────────────────────────────

    @Test
    fun `ToggleTTS sets isTTSActive`() {
        val result = base.reduce(ReaderAction.ToggleTTS(active = true))
        assertTrue(result.isTTSActive)
    }

    // ── SetTTSLine ───────────────────────────────────────────────────────────

    @Test
    fun `SetTTSLine updates ttsLine`() {
        val result = base.reduce(ReaderAction.SetTTSLine(line = 7))
        assertEquals(7, result.ttsLine)
    }

    // ── ToggleOriginal ───────────────────────────────────────────────────────

    @Test
    fun `ToggleOriginal sets isShowingOriginal to true`() {
        val result = base.reduce(ReaderAction.ToggleOriginal(showOriginal = true))
        assertTrue(result.isShowingOriginal)
    }

    // ── SwitchVisibility ─────────────────────────────────────────────────────

    @Test
    fun `SwitchVisibility toggles bottomVisibility from false to true`() {
        val result = base.reduce(ReaderAction.SwitchVisibility)
        assertTrue(result.bottomVisibility)
    }

    @Test
    fun `SwitchVisibility toggles bottomVisibility from true to false`() {
        val visible = base.copy(bottomVisibility = true)
        val result = visible.reduce(ReaderAction.SwitchVisibility)
        assertFalse(result.bottomVisibility)
    }

    @Test
    fun `SwitchVisibility twice returns to original state`() {
        val result = base
            .reduce(ReaderAction.SwitchVisibility)
            .reduce(ReaderAction.SwitchVisibility)
        assertEquals(base.bottomVisibility, result.bottomVisibility)
    }

    // ── UpdateTTSStatus ──────────────────────────────────────────────────────

    @Test
    fun `UpdateTTSStatus sets ttsStatus to IsPlaying`() {
        val result = base.reduce(
            ReaderAction.UpdateTTSStatus(TTSHelper.TTSStatus.IsRunning)
        )
        assertEquals(TTSHelper.TTSStatus.IsRunning, result.ttsStatus)
    }

    // ── UpdateChapterData ────────────────────────────────────────────────────

    @Test
    fun `UpdateChapterData stores entry at given index`() {
        val result = base.reduce(
            ReaderAction.UpdateChapterData(index = 3, data = null)
        )
        assertTrue(result.chapterDataMap.containsKey(3))
    }

    @Test
    fun `UpdateChapterData preserves existing entries`() {
        val withEntry = base.reduce(ReaderAction.UpdateChapterData(index = 1, data = null))
        val result = withEntry.reduce(ReaderAction.UpdateChapterData(index = 2, data = null))
        assertTrue(result.chapterDataMap.containsKey(1))
        assertTrue(result.chapterDataMap.containsKey(2))
    }

    // ── ClearChapterData ─────────────────────────────────────────────────────

    @Test
    fun `ClearChapterData empties the chapter map`() {
        val withEntries = base
            .reduce(ReaderAction.UpdateChapterData(index = 0, data = null))
            .reduce(ReaderAction.UpdateChapterData(index = 1, data = null))
        val result = withEntries.reduce(ReaderAction.ClearChapterData)
        assertTrue(result.chapterDataMap.isEmpty())
    }

    // ── Immutability contract ────────────────────────────────────────────────

    @Test
    fun `reduce never mutates the original state`() {
        val original = base.copy(currentIndex = 10)
        original.reduce(ReaderAction.SetCurrentIndex(99))
        // original must be unchanged
        assertEquals(10, original.currentIndex)
    }
}
