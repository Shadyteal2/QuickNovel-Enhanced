package com.lagradost.quicknovel.db

import androidx.room.*

@Entity(tableName = "implicit_interactions")
data class ImplicitInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val novelUrl: String,
    val interactionType: String, // "CLICK", "READ", "DISMISS"
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: String? = null // JSON blob for extra context if needed
)

@Dao
interface ImplicitInteractionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: ImplicitInteractionEntity)

    @Query("SELECT * FROM implicit_interactions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentInteractions(limit: Int): List<ImplicitInteractionEntity>

    @Query("SELECT * FROM implicit_interactions WHERE novelUrl = :url")
    suspend fun getInteractionsForNovel(url: String): List<ImplicitInteractionEntity>

    @Query("DELETE FROM implicit_interactions WHERE timestamp < :expiry")
    suspend fun deleteOldInteractions(expiry: Long)
}
