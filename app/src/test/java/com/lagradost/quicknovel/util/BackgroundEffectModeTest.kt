package com.lagradost.quicknovel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [BackgroundEffectMode.from] and [BackgroundEffectState].
 *
 * No Android dependencies — runs entirely on JVM.
 *
 * Notable behavior documented here:
 * - Unrecognised strings fall back to NOIR (not NONE). See the
 *   "unknown string returns NOIR" test for the deliberate asymmetry.
 * - Legacy mode values ("classic", "grain", "film", "dream") map to NONE at
 *   display-label time but are stored separately as enum members.
 */
class BackgroundEffectModeTest {

    // ── Null / empty ──────────────────────────────────────────────────────────

    @Test fun `from null returns NONE`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from(null))

    @Test fun `from empty string returns NONE`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from(""))

    // ── Known valid values ────────────────────────────────────────────────────

    @Test fun `from none returns NONE`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from("none"))

    @Test fun `from noir returns NOIR`() =
        assertEquals(BackgroundEffectMode.NOIR, BackgroundEffectMode.from("noir"))

    @Test fun `from sepia returns SEPIA`() =
        assertEquals(BackgroundEffectMode.SEPIA, BackgroundEffectMode.from("sepia"))

    @Test fun `from blockify returns BLOCKIFY`() =
        assertEquals(BackgroundEffectMode.BLOCKIFY, BackgroundEffectMode.from("blockify"))

    @Test fun `from threshold returns THRESHOLD`() =
        assertEquals(BackgroundEffectMode.THRESHOLD, BackgroundEffectMode.from("threshold"))

    @Test fun `from edge_detection returns EDGE_DETECTION`() =
        assertEquals(BackgroundEffectMode.EDGE_DETECTION, BackgroundEffectMode.from("edge_detection"))

    @Test fun `from dots returns DOTS`() =
        assertEquals(BackgroundEffectMode.DOTS, BackgroundEffectMode.from("dots"))

    @Test fun `from dithering returns DITHERING`() =
        assertEquals(BackgroundEffectMode.DITHERING, BackgroundEffectMode.from("dithering"))

    @Test fun `from voronoi returns VORONOI`() =
        assertEquals(BackgroundEffectMode.VORONOI, BackgroundEffectMode.from("voronoi"))

    // ── Legacy modes (stored but map to NONE) ─────────────────────────────────

    @Test fun `from classic returns NONE (soft fallback for legacy mode)`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from("classic"))

    @Test fun `from grain returns NONE`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from("grain"))

    @Test fun `from film returns NONE`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from("film"))

    @Test fun `from dream returns NONE`() =
        assertEquals(BackgroundEffectMode.NONE, BackgroundEffectMode.from("dream"))

    // ── Unknown ───────────────────────────────────────────────────────────────

    /**
     * Documented intentional behavior: unknown strings fall back to NOIR,
     * not NONE. This means a downgraded app receiving a future mode string
     * shows the noir style rather than no style. Capture this contract here.
     */
    @Test fun `from unknown string returns NOIR deliberately`() =
        assertEquals(BackgroundEffectMode.NOIR, BackgroundEffectMode.from("futuristic_mode"))

    @Test fun `from whitespace only returns NOIR`() =
        assertEquals(BackgroundEffectMode.NOIR, BackgroundEffectMode.from("  "))

    // ── Round-trip: value → from → same enum ─────────────────────────────────

    @Test fun `round trip NOIR value`() =
        assertEquals(BackgroundEffectMode.NOIR, BackgroundEffectMode.from(BackgroundEffectMode.NOIR.value))

    @Test fun `round trip SEPIA value`() =
        assertEquals(BackgroundEffectMode.SEPIA, BackgroundEffectMode.from(BackgroundEffectMode.SEPIA.value))

    @Test fun `round trip VORONOI value`() =
        assertEquals(BackgroundEffectMode.VORONOI, BackgroundEffectMode.from(BackgroundEffectMode.VORONOI.value))

    // ── activeModes ───────────────────────────────────────────────────────────

    @Test fun `activeModes contains NONE as first element`() {
        val modes = BackgroundEffectMode.activeModes()
        assertEquals(BackgroundEffectMode.NONE, modes.first())
    }

    @Test fun `activeModes does not contain legacy classic grain film dream`() {
        val modes = BackgroundEffectMode.activeModes()
        val legacy = setOf("classic", "grain", "film", "dream")
        assertTrue(modes.none { it.value in legacy })
    }

    @Test fun `activeModes contains 9 entries`() =
        assertEquals(9, BackgroundEffectMode.activeModes().size)

    // ── BackgroundEffectState defaults ────────────────────────────────────────

    @Test fun `BackgroundEffectState equality by value`() {
        val a = BackgroundEffectState(BackgroundEffectMode.NOIR, 5, 30, 10, 20)
        val b = BackgroundEffectState(BackgroundEffectMode.NOIR, 5, 30, 10, 20)
        assertEquals(a, b)
    }

    @Test fun `BackgroundEffectState copy changes only specified fields`() {
        val base = BackgroundEffectState(BackgroundEffectMode.NONE, 0, 0, 0, 0)
        val modified = base.copy(mode = BackgroundEffectMode.SEPIA, blur = 8)
        assertEquals(BackgroundEffectMode.SEPIA, modified.mode)
        assertEquals(8, modified.blur)
        assertEquals(0, modified.dim)
        assertEquals(0, modified.grain)
        assertEquals(0, modified.vignette)
    }

    @Test fun `BackgroundEffectState is not equal when modes differ`() {
        val a = BackgroundEffectState(BackgroundEffectMode.NOIR, 0, 0, 0, 0)
        val b = BackgroundEffectState(BackgroundEffectMode.SEPIA, 0, 0, 0, 0)
        assertNotEquals(a, b)
    }
}
