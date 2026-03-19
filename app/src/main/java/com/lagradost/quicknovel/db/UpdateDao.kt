package com.lagradost.quicknovel.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UpdateDao {
    @Query("SELECT * FROM update_items WHERE uploadDate >= :threshold ORDER BY uploadDate DESC")
    fun getRecentUpdates(threshold: Long): Flow<List<UpdateItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: UpdateItem): Long

    @Query("SELECT EXISTS(SELECT 1 FROM update_items WHERE chapterUrl = :chapterUrl LIMIT 1)")
    suspend fun exists(chapterUrl: String): Boolean

    @Query("DELETE FROM update_items WHERE uploadDate < :threshold")
    suspend fun deleteOldUpdates(threshold: Long)

    @Query("DELETE FROM update_items")
    suspend fun deleteAllUpdates()
}
