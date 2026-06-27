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

    @Query("SELECT * FROM novel WHERE name = :name LIMIT 1")
    fun getByName(name: String): NovelEntity?

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0")
    fun getAllBookmarksAsFlow(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0 ORDER BY name COLLATE NOCASE ASC")
    fun getBookmarksSortedAlphabetical(): List<NovelEntity>

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0 ORDER BY name COLLATE NOCASE DESC")
    fun getBookmarksSortedAlphabeticalDesc(): List<NovelEntity>

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0 ORDER BY COALESCE(lastDownloaded, 0) DESC")
    fun getBookmarksSortedLastDownloaded(): List<NovelEntity>

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0 ORDER BY COALESCE(lastDownloaded, 0) ASC")
    fun getBookmarksSortedLastDownloadedAsc(): List<NovelEntity>

    @Query("SELECT * FROM novel WHERE bookmarkType IS NOT NULL AND bookmarkType != 0 AND name LIKE :queryPattern")
    fun getBookmarksFiltered(queryPattern: String): List<NovelEntity>

    @Query("SELECT * FROM novel WHERE bookmarkType = :type")
    fun getBookmarksForCategory(type: Int): List<NovelEntity>

    @Query("UPDATE novel SET bookmarkType = NULL WHERE bookmarkType = :type")
    fun removeCategoryFromNovels(type: Int)

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

    @Query("SELECT id, name, author, apiName, posterUrl FROM novel WHERE downloadStatus = :doneStatus")
    fun getDownloadedNovels(doneStatus: Int): List<NovelBackupInfo>

    @Query("SELECT * FROM novel WHERE downloadStatus = :doneStatus")
    fun getFullDownloadedNovels(doneStatus: Int): List<NovelEntity>
}

data class NovelBackupInfo(
    val id: Int,
    val name: String,
    val author: String?,
    val apiName: String,
    val posterUrl: String?
)
