package com.lagradost.quicknovel.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "novel")
@TypeConverters(StringListConverter::class)
data class NovelEntity(
    @PrimaryKey val id: Int,
    val source: String,
    val name: String,
    val author: String?,
    val posterUrl: String?,
    val rating: Int?,
    val peopleVoted: Int?,
    val views: Int?,
    val synopsis: String?,
    val tags: List<String>?,
    val apiName: String,
    val lastUpdated: Long?,
    val lastDownloaded: Long?,

    // Import Engine Extensions
    val filePath: String? = null,
    val formatType: String? = null,
    val hash: String? = null
)
