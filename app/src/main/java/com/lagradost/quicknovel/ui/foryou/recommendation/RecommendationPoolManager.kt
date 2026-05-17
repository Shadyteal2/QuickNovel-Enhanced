package com.lagradost.quicknovel.ui.foryou.recommendation

import android.content.Context
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.RecommendationCandidateEntity
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the fetching and caching of recommendation candidates from providers.
 */
class RecommendationPoolManager(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.recommendationDao()

    suspend fun fetchNewCandidates() = withContext(Dispatchers.IO) {
        val activeProviders = apis.filter { context.getApiSettings().contains(it.name) }
        
        for (api in activeProviders) {
            if (!api.hasMainPage) continue
            
            // Try fetching from multiple categories (Latest, Trending, Popular, Top, etc.)
            val categories = listOf(null, "0", "1", "2", "3", "4")
            
            for (cat in categories) {
                try {
                    // Fetch up to 2 pages per category across more categories for variety
                    for (page in 1..2) {
                        val response = try { 
                            api.loadMainPage(page, cat, null, null) 
                        } catch (e: Exception) { 
                            null 
                        } ?: break // Stop if page fails
                        
                        if (response.list.isEmpty()) break
                        
                        val candidates = response.list.map { res ->
                            RecommendationCandidateEntity(
                                url = res.url,
                                name = res.name,
                                author = null,
                                posterUrl = res.posterUrl,
                                rating = res.rating,
                                synopsis = null,
                                tags = null,
                                apiName = res.apiName
                            )
                        }
                        dao.insertAll(candidates)
                        
                        // Small throttle between pages
                        kotlinx.coroutines.delay(500)
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }
    }

    suspend fun getCandidates(limit: Int = 200): List<RecommendationCandidateEntity> {
        return dao.getAllCandidates(limit)
    }

    suspend fun clearPool() {
        dao.clearAll()
    }
}
