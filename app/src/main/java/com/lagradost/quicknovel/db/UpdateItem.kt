package com.lagradost.quicknovel.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "update_items")
data class UpdateItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelUrl: String,
    val novelName: String,
    val chapterUrl: String,
    val chapterName: String,
    val uploadDate: Long, // timestamp
    val apiName: String,
    val posterUrl: String? = null,
    val chapterIndex: Int = -1
)
