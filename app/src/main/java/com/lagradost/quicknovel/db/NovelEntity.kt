package com.lagradost.quicknovel.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "novel")
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
    @androidx.room.ColumnInfo(name = "filePath") val filePath: String? = null,
    @androidx.room.ColumnInfo(name = "formatType") val formatType: String? = null,
    @androidx.room.ColumnInfo(name = "hash") val hash: String? = null,
    
    // Bookmark and Download SSOT
    @androidx.room.ColumnInfo(name = "bookmarkType") val bookmarkType: Int? = null,
    @androidx.room.ColumnInfo(name = "downloadStatus") val downloadStatus: Int? = null,
    @androidx.room.ColumnInfo(name = "downloadProgress") val downloadProgress: Long? = null,
    @androidx.room.ColumnInfo(name = "downloadTotal") val downloadTotal: Long? = null
)
