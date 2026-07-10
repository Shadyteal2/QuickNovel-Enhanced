package com.lagradost.quicknovel.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.asLiveData

class HomeViewModel : ViewModel() {
    val homeApis: LiveData<List<MainAPI>> = combine(
        Apis.apisFlow,
        Apis.providersActiveFlow,
        Apis.pinnedProvidersFlow,
        Apis.hiddenProvidersFlow
    ) { apis, active, pinned, hidden ->
        val langs = getApiProviderLangSettings()
        apis.filter { api ->
            api.hasMainPage && langs.contains(api.lang) && active.contains(api.name) && api.name !in hidden
        }.sortedWith(
            compareByDescending<MainAPI> { it.name in pinned }.thenBy { it.name }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).asLiveData()

    val latestHistory: MutableLiveData<ResultCached?> = MutableLiveData(null)

    fun updateHistory() {
        ioSafe {
            val keys = getKeys(HISTORY_FOLDER) ?: return@ioSafe
            var latest: ResultCached? = null
            for (k in keys) {
                val res = getKey<ResultCached>(k) ?: continue
                if (latest == null || res.cachedTime > latest.cachedTime) {
                    latest = res
                }
            }
            latestHistory.postValue(latest)
        }
    }

    init {
        updateHistory()
    }
}