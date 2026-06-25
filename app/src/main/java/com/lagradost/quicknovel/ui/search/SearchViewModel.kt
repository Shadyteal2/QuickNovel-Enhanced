package com.lagradost.quicknovel.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.APIRepository.Companion.providersActive
import com.lagradost.quicknovel.HomePageList
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.OnGoingSearch
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Apis.Companion.apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.amap
import com.lagradost.quicknovel.providers.WebToEpubMap
import kotlinx.coroutines.*

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<ArrayList<SearchResponse>>?> =
        MutableLiveData(null)
    val searchResponse: LiveData<Resource<ArrayList<SearchResponse>>?> get() = _searchResponse

    private val _currentSearch: MutableLiveData<ArrayList<OnGoingSearch>?> = MutableLiveData(null)
    val currentSearch: LiveData<ArrayList<OnGoingSearch>?> get() = _currentSearch

    val searchHistory: MutableLiveData<List<String>> = MutableLiveData(emptyList())

    init {
        loadHistory()
    }

    fun loadHistory() {
        val mapper = com.lagradost.quicknovel.util.AppUtils.mapper
        val json = com.lagradost.quicknovel.BaseApplication.getKey<String>("GLOBAL_SEARCH_HISTORY", "history", "[]") ?: "[]"
        try {
            val list = mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
            searchHistory.postValue(list)
        } catch (t: Throwable) {
            searchHistory.postValue(emptyList())
        }
    }

    fun addToHistory(query: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        val mapper = com.lagradost.quicknovel.util.AppUtils.mapper
        val current = searchHistory.value.orEmpty().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val limit = current.take(5)
        com.lagradost.quicknovel.BaseApplication.setKey("GLOBAL_SEARCH_HISTORY", "history", mapper.writeValueAsString(limit))
        searchHistory.postValue(limit)
    }

    fun removeFromHistory(query: String) {
        val trimmed = query.trim()
        val mapper = com.lagradost.quicknovel.util.AppUtils.mapper
        val current = searchHistory.value.orEmpty().toMutableList()
        current.remove(trimmed)
        com.lagradost.quicknovel.BaseApplication.setKey("GLOBAL_SEARCH_HISTORY", "history", mapper.writeValueAsString(current))
        searchHistory.postValue(current)
    }

    @Volatile
    var searchCounter = 0

    var lastSearchQuery = ""

    fun clearSearch() {
        searchCounter++
        ongoingSearchJob?.cancel()
        ongoingSearchJob = null
        _searchResponse.value = null
        _currentSearch.value = null
    }

    fun load(card: SearchResponse) {
        loadResult(card.url, card.apiName)
    }

    fun showMetadata(card: SearchResponse) {
        MainActivity.loadPreviewPage(card)
    }

    var ongoingSearchJob : Job? = null

    fun search(query: String) {
        if (query.length <= 1) {
            clearSearch()
            return
        }
        addToHistory(query)
        ongoingSearchJob?.cancel()
        lastSearchQuery = query
        ongoingSearchJob = ioSafe {
            searchCounter++
            val localSearchCounter = searchCounter
            _searchResponse.postValue(Resource.Loading())

            // Thread-safe list collection to prevent concurrent write crashes
            val currentList = java.util.Collections.synchronizedList(ArrayList<OnGoingSearch>())

            _currentSearch.postValue(ArrayList())
            
            // If the query is a supported URL, skip other providers and use WebToEpub directly
            val webToEpubParser = WebToEpubMap.getParserForUrl(query)
            val repos = if (webToEpubParser != null) {
                listOf(Apis.getApiFromName("WebToEpub"))
            } else {
                // Use pre-warmed repo cache from Apis — avoids creating new APIRepository objects on every search
                Apis.getActiveRepositories()
            }

            // Run in local coroutineScope with structured concurrency so child async jobs cancel immediately on parent cancel()
            kotlinx.coroutines.coroutineScope {
                repos.map { a ->
                    async {
                        if (!isActive) return@async
                        val result = a.search(query)
                        if (!isActive) return@async
                        currentList.add(OnGoingSearch(a.name, result))
                        if (localSearchCounter == searchCounter) {
                            // Shallow-copy to ensure rendering thread does not get concurrent modifications
                            _currentSearch.postValue(ArrayList(currentList))
                        }
                    }
                }.forEach { it.await() }
            }
            if(!isActive) return@ioSafe

            _currentSearch.postValue(ArrayList(currentList))

            if (localSearchCounter != searchCounter) return@ioSafe

            val list = ArrayList<SearchResponse>()
            val nestedList =
                currentList.map { it.data }
                    .filterIsInstance<Resource.Success<List<SearchResponse>>>()
                    .map { it.value }

            // I do it this way to move the relevant search results to the top
            var index = 0
            while (true) {
                var added = 0
                for (sublist in nestedList) {
                    if (sublist.size > index) {
                        list.add(sublist[index])
                        added++
                    }
                }
                if (added == 0) break
                index++
            }
            if(!isActive) return@ioSafe

            _searchResponse.postValue(Resource.Success(list))
        }
    }

    fun loadHomepageList(item: HomePageList) {
        SearchFragment.loadHomepageList(this, item)
    }
}