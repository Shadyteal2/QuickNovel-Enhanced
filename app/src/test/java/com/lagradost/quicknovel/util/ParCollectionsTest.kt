package com.lagradost.quicknovel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [pmap] and [apmap] parallel collection utilities.
 *
 * Pure JVM — no Android dependencies, no mocks.
 *
 * Key invariant tested: all elements are transformed, but **order is not
 * guaranteed** (concurrent execution). Tests assert set equality only.
 */
class ParCollectionsTest {

    // ── pmap ─────────────────────────────────────────────────────────────────

    @Test fun `pmap on empty list returns empty list`() {
        val result: List<String> = emptyList<Int>().pmap { it.toString() }
        assertTrue(result.isEmpty())
    }

    @Test fun `pmap transforms every element`() {
        val input = listOf(1, 2, 3, 4, 5)
        val result = input.pmap { it * 2 }
        // All elements must be present (order not guaranteed)
        assertEquals(setOf(2, 4, 6, 8, 10), result.toSet())
    }

    @Test fun `pmap preserves result count`() {
        val input = (1..20).toList()
        val result = input.pmap { it.toString() }
        assertEquals(20, result.size)
    }

    @Test fun `pmap with identity transform returns same values`() {
        val input = listOf("a", "b", "c")
        val result = input.pmap { it }
        assertEquals(setOf("a", "b", "c"), result.toSet())
    }

    @Test fun `pmap with single thread numThreads still processes all items`() {
        val input = listOf(10, 20, 30)
        val result = input.pmap(numThreads = 1) { it + 1 }
        assertEquals(setOf(11, 21, 31), result.toSet())
    }

    @Test fun `pmap with expensive mapping handles concurrent execution`() {
        val input = (1..10).toList()
        val result = input.pmap { n ->
            Thread.sleep(5)  // simulate I/O delay
            n * n
        }
        val expected = input.map { it * it }.toSet()
        assertEquals(expected, result.toSet())
    }

    // ── apmap ─────────────────────────────────────────────────────────────────

    @Test fun `apmap on empty list returns empty list`() {
        val result = emptyList<Int>().apmap { it * 2 }
        assertTrue(result.isEmpty())
    }

    @Test fun `apmap transforms every element`() {
        val input = listOf(1, 2, 3)
        val result = input.apmap { it + 10 }
        // apmap preserves order (uses ordered map)
        assertEquals(listOf(11, 12, 13), result)
    }

    @Test fun `apmap with single element`() {
        val result = listOf(42).apmap { it * 2 }
        assertEquals(listOf(84), result)
    }
}
