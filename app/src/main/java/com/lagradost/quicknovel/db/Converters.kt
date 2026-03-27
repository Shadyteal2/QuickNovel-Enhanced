package com.lagradost.quicknovel.db

import androidx.room.TypeConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class Converters {
    private val mapper = jacksonObjectMapper()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { mapper.writeValueAsString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { mapper.readValue<List<String>>(it) }
    }
}
