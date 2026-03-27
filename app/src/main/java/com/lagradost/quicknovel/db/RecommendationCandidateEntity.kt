package com.lagradost.quicknovel.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.lagradost.quicknovel.ui.foryou.recommendation.TagCategory

@Entity(tableName = "recommendation_candidates")
data class RecommendationCandidateEntity(
    @PrimaryKey val url: String,
    val name: String,
    val author: String?,
    val posterUrl: String?,
    val rating: Int?,
    val synopsis: String?,
    val tags: List<String>?,
    val apiName: String,
    val lastFetched: Long = System.currentTimeMillis()
)
