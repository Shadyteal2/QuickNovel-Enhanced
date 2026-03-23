package com.lagradost.quicknovel.db

import androidx.room.TypeConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class StringListConverter {
    private val mapper = jacksonObjectMapper()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return mapper.writeValueAsString(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        return try {
            mapper.readValue<List<String>>(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
