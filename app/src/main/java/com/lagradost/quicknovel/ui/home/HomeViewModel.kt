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

class HomeViewModel : ViewModel() {
    val homeApis: LiveData<List<MainAPI>> by lazy {
        MutableLiveData(
            let {
                val langs = getApiProviderLangSettings()
                Apis.apis.filter { api -> api.hasMainPage && (langs.contains(api.lang)) }
            }
        )
    }

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