package com.lagradost.quicknovel.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {
    @Query("SELECT * FROM novel")
    fun getAll(): List<NovelEntity>

    @Query("SELECT * FROM novel")
    fun getAllAsFlow(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novel WHERE id = :id LIMIT 1")
    fun getByIdAsFlow(id: Int): Flow<NovelEntity?>

    @Query("SELECT * FROM novel WHERE id = :id LIMIT 1")
    fun getById(id: Int): NovelEntity?

    @Query("SELECT * FROM novel WHERE hash = :hash LIMIT 1")
    fun getByHash(hash: String): NovelEntity?

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0")
    fun getAllBookmarksAsFlow(): Flow<List<NovelEntity>>

    @Query("UPDATE novel SET bookmarkType = :type WHERE id = :id")
    fun updateBookmarkType(id: Int, type: Int?)

    @Query("UPDATE novel SET downloadStatus = :status, downloadProgress = :progress, downloadTotal = :total WHERE id = :id")
    fun updateDownloadProgress(id: Int, status: Int?, progress: Long?, total: Long?)

    @Query("UPDATE novel SET downloadStatus = NULL, downloadProgress = NULL, downloadTotal = NULL, filePath = NULL, lastDownloaded = NULL WHERE id = :id")
    fun resetDownloadData(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(novels: List<NovelEntity>)

    @Delete
    fun delete(novel: NovelEntity)

    @Query("DELETE FROM novel WHERE id = :id")
    fun deleteById(id: Int)
}
