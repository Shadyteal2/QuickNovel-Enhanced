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
    fun getById(id: Int): NovelEntity?

    @Query("SELECT * FROM novel WHERE hash = :hash LIMIT 1")
    fun getByHash(hash: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(novels: List<NovelEntity>)

    @Delete
    fun delete(novel: NovelEntity)

    @Query("DELETE FROM novel WHERE id = :id")
    fun deleteById(id: Int)
}
