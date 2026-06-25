package com.lagradost.quicknovel.util

import android.content.Context
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CachedPage(
    val items: List<SearchResponse>,
    val savedAtMs: Long
)

object ProviderCacheManager {
    private const val CACHE_FOLDER = "provider_mainpage_cache"

    suspend fun savePage(context: Context, apiName: String, items: List<SearchResponse>) = withContext(Dispatchers.IO) {
        try {
            val cachedPage = CachedPage(items, System.currentTimeMillis())
            context.setKey(CACHE_FOLDER, apiName, cachedPage)
        } catch (t: Throwable) {
            // Catch Throwable as mandated by AGENTS.md
        }
    }

    suspend fun loadPage(context: Context, apiName: String): CachedPage? = withContext(Dispatchers.IO) {
        try {
            context.getKey<CachedPage>(CACHE_FOLDER, apiName)
        } catch (t: Throwable) {
            null
        }
    }
}
