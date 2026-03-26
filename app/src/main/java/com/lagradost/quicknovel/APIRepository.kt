package com.lagradost.quicknovel

import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.Coroutines.threadSafeListOf
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

// This function is somewhat like preParseHtml
private fun String?.removeAds(): String? {
    if (this.isNullOrBlank()) return null
    return try {
        val document = Jsoup.parse(this)
        //document.select("style").remove() // Style might be good, but is removed in the internal reader
        document.select("small.ads-title").remove()
        document.select("script").remove()
        document.select("iframe").remove()
        document.select(".adsbygoogle").remove()

        // Remove aside https://html.spec.whatwg.org/multipage/sections.html#the-aside-element?
        // https://stackoverflow.com/questions/14384431/html-element-for-ad

        document.html()
    } catch (t : Throwable) {
        logError(t)
        this
    }
}

class APIRepository(val api: MainAPI) {
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L

    companion object {
        var providersActive = ConcurrentHashMap.newKeySet<String>()

        data class SavedLoadResponse(
            val unixTime: Long,
            val response: LoadResponse,
            val hash: Pair<String, String>
        )

        private val cache = threadSafeListOf<SavedLoadResponse>()
        private var cacheIndex: Int = 0
        const val cacheSize = 20

        // 10min cache time should be plenty per session without fucking up anything for the user with outdated data
        const val cacheTimeSec: Int = 60 * 10
    }

    val name: String get() = api.name
    val mainUrl: String get() = api.mainUrl
    val hasReviews: Boolean get() = api.hasReviews
    val rateLimitTime: Long get() = api.rateLimitTime
    val hasMainPage: Boolean get() = api.hasMainPage

    val iconId: Int? get() = api.iconId
    val iconBackgroundId: Int get() = api.iconBackgroundId
    val mainCategories: List<Pair<String, String>> get() = api.mainCategories
    val orderBys: List<Pair<String, String>> get() = api.orderBys
    val tags: List<Pair<String, String>> get() = api.tags


    suspend fun load(url: String, allowCache: Boolean = true): Resource<LoadResponse> {
        return safeApiCall {
            try {
                if (api.hasRateLimit) {
                    api.rateLimitMutex.lock()
                }
                val fixedUrl = api.fixUrl(url)
                val lookingForHash = api.name to fixedUrl

                if (allowCache) {
                    synchronized(cache) {
                        for (item in cache) {
                            // 10 min save
                            if (item.hash == lookingForHash && (unixTime - item.unixTime) < cacheTimeSec) {
                                return@safeApiCall item.response
                            }
                        }
                    }
                }

                api.load(fixedUrl)?.also { response ->
                    // Remove all blank tags as early as possible
                    val add = SavedLoadResponse(unixTime, response, lookingForHash)
                    if (allowCache) {
                        synchronized(cache) {
                            if (cache.size > cacheSize) {
                                cache[cacheIndex] = add // rolling cache
                                cacheIndex = (cacheIndex + 1) % cacheSize
                            } else {
                                cache.add(add)
                            }
                        }
                    }
                } ?: throw ErrorLoadingException("No data")
            } finally {
                if (api.hasRateLimit) {
                    api.rateLimitMutex.unlock()
                }
            }
        }
    }

    private val searchCache = HashMap<String, Pair<Long, List<SearchResponse>>>()
    // Short-lived cache for page-1 main-page results (5 min TTL) — instant re-entry after back press
    private val mainPageCache = ConcurrentHashMap<String, Pair<Long, HeadMainPageResponse>>()
    private val mainPageCacheTtl = 60 * 5

    suspend fun search(query: String): Resource<List<SearchResponse>> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return Resource.Success(emptyList())

        synchronized(searchCache) {
            searchCache[q]?.let { (time, data) ->
                if ((unixTime - time) < cacheTimeSec) {
                    return Resource.Success(data)
                }
            }
        }

        val res = safeApiCall {
            api.search(query) ?: throw ErrorLoadingException("No data")
        }

        if (res is Resource.Success) {
            synchronized(searchCache) {
                searchCache[q] = unixTime to res.value
            }
        }
        return res
    }

    /**
     * Automatically strips adsbygoogle
     * */
    suspend fun loadHtml(url: String): String? {
        return try {
            api.loadHtml(api.fixUrl(url))?.removeAds()
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean = false
    ): Resource<List<UserReview>> {
        return safeApiCall {
            api.loadReviews(url, page, showSpoilers)
        }
    }

    suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): Resource<HeadMainPageResponse> {
        if (page == 1) {
            val key = "${api.name}|$mainCategory|$orderBy|$tag"
            mainPageCache[key]?.let { (time, data) ->
                if ((unixTime - time) < mainPageCacheTtl) return Resource.Success(data)
            }
            val result = safeApiCall { api.loadMainPage(page, mainCategory, orderBy, tag) }
            if (result is Resource.Success) mainPageCache[key] = unixTime to result.value
            return result
        }
        return safeApiCall { api.loadMainPage(page, mainCategory, orderBy, tag) }
    }

}