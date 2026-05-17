package com.lagradost.quicknovel.db

import androidx.room.*

@Dao
interface RecommendationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<RecommendationCandidateEntity>)

    @Query("SELECT * FROM recommendation_candidates ORDER BY lastFetched DESC LIMIT :limit")
    suspend fun getAllCandidates(limit: Int): List<RecommendationCandidateEntity>

    @Query("SELECT * FROM recommendation_candidates WHERE apiName = :apiName ORDER BY lastFetched DESC")
    suspend fun getCandidatesByApi(apiName: String): List<RecommendationCandidateEntity>

    @Query("DELETE FROM recommendation_candidates WHERE lastFetched < :expiry")
    suspend fun deleteOldCandidates(expiry: Long)

    @Query("DELETE FROM recommendation_candidates")
    suspend fun clearAll()
}
