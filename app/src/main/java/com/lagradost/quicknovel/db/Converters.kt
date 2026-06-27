package com.lagradost.quicknovel.db

import androidx.room.TypeConverter
import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.quicknovel.ui.neolists.NeoListNovelEntry

class Converters {
    private val mapper = com.lagradost.quicknovel.util.AppUtils.mapper

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { mapper.writeValueAsString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            try { mapper.readValue(it, object : TypeReference<List<String>>() {}) }
            catch (t: Throwable) { null }
        }
    }

    // ─── NeoList Novel Entry Converters ───────────────────────────────────────

    /**
     * Serializes the novels list for the neolists.novels column.
     * Uses the shared AppUtils.mapper (FAIL_ON_UNKNOWN_PROPERTIES = false).
     */
    @TypeConverter
    fun fromNeoListNovels(novels: List<NeoListNovelEntry>?): String {
        return try { mapper.writeValueAsString(novels ?: emptyList<NeoListNovelEntry>()) }
        catch (t: Throwable) { "[]" }
    }

    /**
     * Deserializes the neolists.novels JSON blob.
     * Returns emptyList() on any parse error — never throws — to prevent DB crashes.
     */
    @TypeConverter
    fun toNeoListNovels(json: String?): List<NeoListNovelEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            mapper.readValue(json, object : TypeReference<List<NeoListNovelEntry>>() {})
        } catch (t: Throwable) { emptyList() }
    }
}
