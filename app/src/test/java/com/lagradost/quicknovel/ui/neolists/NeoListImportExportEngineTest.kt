package com.lagradost.quicknovel.ui.neolists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lagradost.quicknovel.util.AppUtils.mapper

class NeoListImportExportEngineTest {

    @Test
    fun testGenerateUniqueCategoryId() {
        // Empty case: should return default 10
        val id1 = NeoListImportExportEngine.generateUniqueCategoryId(emptyList())
        assertEquals(10, id1)

        // Standard case: should return max + 1
        val id2 = NeoListImportExportEngine.generateUniqueCategoryId(listOf(1, 5, 9, 12))
        assertEquals(13, id2)

        // Coerce below 10 case: should return 10
        val id3 = NeoListImportExportEngine.generateUniqueCategoryId(listOf(1, 2, 3))
        assertEquals(10, id3)
    }

    @Test
    fun testJsonSerializationDeserialization() {
        val original = NeoListExport(
            id = "test-uuid",
            title = "Best Fantasy",
            description = "Fabulous picks",
            novels = listOf(
                NeoListNovelEntry(
                    title = "Novel 1",
                    author = "Author 1",
                    apiName = "TestAPI",
                    sourceUrl = "https://example.com/1"
                )
            )
        )

        val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(original)
        assertTrue(json.contains("Best Fantasy"))
        assertTrue(json.contains("test-uuid"))

        val parsed = mapper.readValue(json, NeoListExport::class.java)
        assertEquals(original.id, parsed.id)
        assertEquals(original.title, parsed.title)
        assertEquals(original.novels.size, parsed.novels.size)
        assertEquals(original.novels[0].title, parsed.novels[0].title)
    }
}
