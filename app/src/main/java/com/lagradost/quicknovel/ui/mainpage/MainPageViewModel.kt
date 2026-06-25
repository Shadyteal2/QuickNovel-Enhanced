package com.lagradost.quicknovel.ui.mainpage

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.map
import com.lagradost.quicknovel.util.Apis
import kotlinx.coroutines.launch
import com.lagradost.quicknovel.util.Coroutines.ioSafe

class MainPageViewModel : ViewModel() {
    lateinit var repo: MainPageRepository
    val api: APIRepository get() = repo.api
    private var hasInit = false
    val searchHistory: MutableLiveData<List<String>> = MutableLiveData(emptyList())

    /*private val searchCards: MutableLiveData<ArrayList<SearchResponse>> by lazy {
        MutableLiveData<ArrayList<SearchResponse>>()
    }*/

    private val infCards: ArrayList<SearchResponse> = arrayListOf()
    private var oldResponse: Resource<SearchResponseList>? = null
    private var searchResponseListQueries: Int = 0

    data class SearchResponseList(
        val items: List<SearchResponse>,
        val pages: Int,
        val id: Int,
    )

    val currentCards: MutableLiveData<Resource<SearchResponseList>> by lazy {
        MutableLiveData<Resource<SearchResponseList>>()
    }

    private val currentPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val currentMainCategory: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(null)
    }
    val currentOrderBy: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(null)
    }
    val currentTag: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(null)
    }

    val loadingMoreItems: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    val currentUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>(null)
    }

    val isInSearch: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    val isStale: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    fun openInBrowser() {
        try {
            val url = currentUrl.value
            if (url != null) {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                activity?.startActivity(i)
            }
        } catch (_: Throwable) {

        }
    }

    fun search(query: String) {
        if (isInSearch.value == false) {
            oldResponse = currentCards.value
        }

        // searchCards.postValue(ArrayList())
        currentCards.postValue(Resource.Loading())
        currentPage.postValue(0)
        isInSearch.postValue(true)
        viewModelScope.launch {
            val res = repo.search(query)
            currentCards.postValue(res.map { x ->
                SearchResponseList(
                    items = x,
                    pages = 1,
                    id = ++searchResponseListQueries
                )
            })
        }
    }

    fun switchToMain() {
        if (isInSearch.value == false) return

        currentCards.postValue(
            oldResponse ?: Resource.Success(
                // this still gives a bug, works works 90% of the time so idc
                SearchResponseList(
                    infCards,
                    ((currentPage.value ?: 0) + 1) + 1,
                    ++searchResponseListQueries,
                )
            )
        )
        oldResponse = null
        isInSearch.postValue(false)
    }

    fun loadHistory(apiName: String) {
        ioSafe {
            val mapper = com.lagradost.quicknovel.util.AppUtils.mapper
            val json = com.lagradost.quicknovel.BaseApplication.getKey<String>("SEARCH_HISTORY", apiName, "[]") ?: "[]"
            try {
                val list = mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
                searchHistory.postValue(list)
            } catch (t: Throwable) {
                searchHistory.postValue(emptyList())
            }
        }
    }

    fun addToHistory(query: String, apiName: String) {
        if (query.isBlank()) return
        val trimmed = query.trim()
        ioSafe {
            val mapper = com.lagradost.quicknovel.util.AppUtils.mapper
            val current = searchHistory.value.orEmpty().toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            val limit = current.take(5)
            try {
                com.lagradost.quicknovel.BaseApplication.setKey("SEARCH_HISTORY", apiName, mapper.writeValueAsString(limit))
                searchHistory.postValue(limit)
            } catch (t: Throwable) {
                // Fail silently
            }
        }
    }

    fun removeFromHistory(query: String, apiName: String) {
        val trimmed = query.trim()
        ioSafe {
            val mapper = com.lagradost.quicknovel.util.AppUtils.mapper
            val current = searchHistory.value.orEmpty().toMutableList()
            current.remove(trimmed)
            try {
                com.lagradost.quicknovel.BaseApplication.setKey("SEARCH_HISTORY", apiName, mapper.writeValueAsString(current))
                searchHistory.postValue(current)
            } catch (t: Throwable) {
                // Fail silently
            }
        }
    }

    fun init(
        apiName: String, mainCategory: Int?,
        orderBy: Int?,
        tag: Int?
    ) {
        if (hasInit) return
        hasInit = true
        loadHistory(apiName)
        repo = MainPageRepository(Apis.getApiFromName(apiName))
        load(
            0,
            mainCategory,
            orderBy,
            tag
        )
    }

    fun setMainCategory(to: Int) {
        currentMainCategory.postValue(to)
        load(0, mainCategory = to, orderBy = currentOrderBy.value, tag = currentOrderBy.value)
    }

    fun setTag(to: Int) {
        currentTag.postValue(to)
        load(0, mainCategory = currentMainCategory.value, orderBy = currentOrderBy.value, tag = to)
    }

    fun setOrderBy(to: Int) {
        currentOrderBy.postValue(to)
        load(0, mainCategory = currentMainCategory.value, orderBy = to, tag = currentTag.value)
    }

    fun load(
        page: Int?,
        mainCategory: Int?,
        orderBy: Int?,
        tag: Int?,
        isUserRefresh: Boolean = false
    ): kotlinx.coroutines.Job {
        currentTag.postValue(tag)
        currentOrderBy.postValue(orderBy)
        currentMainCategory.postValue(mainCategory)

        val cPage = page ?: ((currentPage.value ?: 0) + 1)
        if (cPage == 0) {
            infCards.clear()
            val canUseCache = mainCategory == null && orderBy == null && tag == null && !isUserRefresh
            if (!canUseCache) {
                currentCards.postValue(Resource.Loading())
            }
        }

        isInSearch.postValue(false)
        if (page != 0) {
            loadingMoreItems.postValue(true)
        }
        return viewModelScope.launch {
            val context = com.lagradost.quicknovel.BaseApplication.context
            val canUseCache = cPage == 0 && mainCategory == null && orderBy == null && tag == null && !isUserRefresh
            
            if (canUseCache && context != null) {
                val cached = com.lagradost.quicknovel.util.ProviderCacheManager.loadPage(context, repo.api.name)
                if (cached != null) {
                    infCards.clear()
                    infCards.addAll(cached.items)
                    isStale.postValue(true)
                    currentCards.postValue(
                        Resource.Success(
                            SearchResponseList(
                                infCards,
                                1,
                                ++searchResponseListQueries
                            )
                        )
                    )
                } else {
                    currentCards.postValue(Resource.Loading())
                }
            }

            //val copy = if (cPage == 0) ArrayList() else cards.value
            when (val res = repo.loadMainPage(cPage + 1, mainCategory, orderBy, tag)) {
                is Resource.Success -> {
                    val response = res.value
                    currentUrl.postValue(response.url)
                    if (cPage == 0) {
                        infCards.clear()
                        if (context != null && mainCategory == null && orderBy == null && tag == null) {
                            com.lagradost.quicknovel.util.ProviderCacheManager.savePage(context, repo.api.name, response.list)
                        }
                    }
                    infCards.addAll(response.list)
                    isStale.postValue(false)

                    currentCards.postValue(
                        Resource.Success(
                            SearchResponseList(
                                infCards,
                                cPage + 1,
                                ++searchResponseListQueries
                            )
                        )
                    )
                }

                is Resource.Failure -> {
                    if (!(cPage == 0 && infCards.isNotEmpty() && isStale.value == true)) {
                        val result: Resource<SearchResponseList> = Resource.Failure(
                            res.cause,
                            res.errorString
                        )
                        currentCards.postValue(result)
                    } else {
                        com.lagradost.quicknovel.CommonActivity.showToast("Failed to refresh: ${res.errorString}")
                        isStale.postValue(true)
                    }
                }

                is Resource.Loading -> {
                    //NOTHING
                }
            }

            loadingMoreItems.postValue(false)

            currentPage.postValue(cPage)
        }
    }
}