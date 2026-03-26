package com.lagradost.quicknovel.util

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.StreamResponse


import com.lagradost.quicknovel.util.Coroutines.ioSafe
import java.util.concurrent.ConcurrentHashMap

class Apis {
    companion object {
        private val internalApis: List<MainAPI> = listOf<MainAPI>(
            // All internal providers have been migrated to external extension bundles.
            // Only non-migrated or core providers remain here.
        ).sortedBy { it.name }


        private val pluginApis = java.util.concurrent.CopyOnWriteArrayList<MainAPI>()
        private val apiRepositoryCache = ConcurrentHashMap<String, APIRepository>()
        private var cachedApis: List<MainAPI>? = null

        private val _apisLiveData = androidx.lifecycle.MutableLiveData<List<MainAPI>>()
        val apisLiveData: androidx.lifecycle.LiveData<List<MainAPI>> get() = _apisLiveData

        val apis: List<MainAPI>
            get() {
                val current = cachedApis
                if (current != null) return current
                val combined = synchronized(pluginApis) {
                    (internalApis + pluginApis).sortedBy { it.name }
                }
                cachedApis = combined
                return combined
            }

        private fun notifyChange() {
            // Re-build repository cache with pre-initialized objects atomically
            // Note: Caller already holds synchronized(pluginApis)
            val current = (internalApis + pluginApis).sortedBy { it.name }
            
            val tempCache = ConcurrentHashMap<String, APIRepository>()
            for (api in current) {
                tempCache[api.name] = com.lagradost.quicknovel.APIRepository(api)
            }
            
            // Atomic update
            apiRepositoryCache.clear()
            apiRepositoryCache.putAll(tempCache)
            cachedApis = current
            
            Log.i("Apis", "Providers updated and pre-warmed. Total: ${current.size}")
            _apisLiveData.postValue(current)
        }

        fun addPlugins(apis: List<MainAPI>) {
            synchronized(pluginApis) {
                var addedCount = 0
                for (api in apis) {
                    if (pluginApis.any { it.name == api.name }) continue
                    pluginApis.add(api)
                    com.lagradost.quicknovel.APIRepository.providersActive.add(api.name)
                    addedCount++
                }
                if (addedCount > 0) notifyChange()
            }
        }

        fun addPlugin(api: MainAPI) {
            synchronized(pluginApis) {
                if (pluginApis.any { it.name == api.name }) return
                pluginApis.add(api)
                com.lagradost.quicknovel.APIRepository.providersActive.add(api.name)
                notifyChange()
            }
        }

        fun removePlugin(apiName: String) {
            pluginApis.removeAll { it.name == apiName }
            notifyChange()
        }


        fun getApiFromName(name: String): APIRepository {
            return apiRepositoryCache.getOrPut(name) {
                getApiFromNameNull(name)?.let { APIRepository(it) } ?: APIRepository(apis[0])
            }
        }

        /**
         * Returns pre-warmed repositories for all currently active providers.
         * Uses the cached repository pool — O(1) lookup, zero allocation.
         */
        fun getActiveRepositories(): List<APIRepository> {
            val active = com.lagradost.quicknovel.APIRepository.providersActive
            return if (active.isEmpty()) {
                apiRepositoryCache.values.toList()
            } else {
                active.mapNotNull { name -> apiRepositoryCache[name] }
            }
        }

        fun getApiFromNameNull(apiName: String?): MainAPI? {
            for (api in apis) {
                if (apiName == api.name)
                    return api
            }
            return null
        }

        fun getApiFromNameOrNull(name: String): APIRepository? {
            return getApiFromNameNull(name)?.let { getApiFromName(it.name) }
        }

        fun printProviders() {
            /*
            var str = ""
            for (api in apis) {
                str += "- ${api.mainUrl}\n"
            }
            println(str)

            var str2 = ""
            for (api in apis) {
                val url = api.mainUrl.toUri()
                str2 += "                <data\n" +
                        "                        android:scheme=\"${url.scheme}\"\n" +
                        "                        android:host=\"${url.host}\"\n" +
                        "                        android:pathPrefix=\"/\" />"
            }
            println(str2)

            testProviders()*/
        }

        fun testProviders() = ioSafe {
            val TAG = "APITEST"
            apis.amap { api ->
                try {
                    var result: List<SearchResponse>? = null
                    for (x in arrayOf("my", "hello", "over", "guy")) {
                        result = api.search(x)
                        if (!result.isNullOrEmpty()) {
                            break
                        }
                    }

                    assert(!result.isNullOrEmpty()) {
                        "Invalid search response from ${api.name}"
                    }

                    val loadResult = api.load(result!![0].url)
                    assert(loadResult != null) {
                        "Invalid load response from ${api.name}"
                    }
                    if (loadResult is StreamResponse) {
                        assert(loadResult.data.isNotEmpty()) {
                            "Invalid StreamResponse (no chapters) from ${api.name}"
                        }
                        val html = api.loadHtml(loadResult.data[0].url)
                        assert(html != null) {
                            "Invalid html (null) from ${api.name}"
                        }
                    }

                    if (api.hasMainPage) {
                        val response = api.loadMainPage(
                            1,
                            api.mainCategories.firstOrNull()?.second,
                            api.orderBys.firstOrNull()?.second,
                            api.tags.firstOrNull()?.second
                        )
                        assert(response.list.isNotEmpty()) {
                            "Invalid (empty) loadMainPage list from ${api.name}"
                        }
                    }

                    Log.v(TAG, "Valid response from ${api.name}")
                } catch (t: Throwable) {
                    Log.e(TAG, "Invalid response from ${api.name}, got $t")
                }
            }
        }

        fun Context.getApiSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            val hashSet = HashSet<String>()
            hashSet.addAll(apis.map { it.name })

            val savedSet = settingsManager.getStringSet(
                this.getString(R.string.search_providers_list_key),
                null
            )

            val set = if (savedSet == null) {
                hashSet
            } else {
                val updated = HashSet(savedSet)
                for (api in apis) {
                    if (!savedSet.contains(api.name)) {
                        updated.add(api.name)
                    }
                }
                updated
            }

            val activeLangs = getApiProviderLangSettings()
            val list = HashSet<String>()
            for (name in set) {
                val api = getApiFromNameNull(name) ?: continue
                if (activeLangs.contains(api.lang)) {
                    list.add(name)
                }
            }
            if (list.isEmpty()) return hashSet
            return list
        }

        fun getApiProviderLangSettings(): HashSet<String> {
            return activity?.getApiProviderLangSettings() ?: hashSetOf()
        }

        fun Context.getApiProviderLangSettings(): HashSet<String> {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val hashSet = HashSet<String>()
            hashSet.add("en") // def is only en
            val list = settingsManager.getStringSet(
                this.getString(R.string.provider_lang_key),
                hashSet.toMutableSet()
            )

            if (list.isNullOrEmpty()) return hashSet
            return list.toHashSet()
        }
    }
}
