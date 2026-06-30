package com.lagradost.quicknovel.ui.download

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.BackupUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.xdrop.fuzzywuzzy.FuzzySearch

class BulkMigrationViewModel : ViewModel() {

    // List of target providers available
    val allProviders = Apis.apis

    // Selected target providers for alternative search
    val selectedProviders = mutableStateListOf<String>()

    // Queue of items to migrate
    val migrationItems = mutableStateListOf<BulkMigrationItem>()

    // Loading & Progress States
    val isSearching = mutableStateOf(false)
    val isMigrating = mutableStateOf(false)
    val migrationProgress = mutableStateOf("")

    private var searchJob: Job? = null
    private var migrateJob: Job? = null

    // Initialize list of target providers using current active repositories
    fun initializeProviders() {
        selectedProviders.clear()
        val active = APIRepository.providersActive
        selectedProviders.addAll(allProviders.map { it.name }.filter { it in active })
    }

    // Set the list of selected source novels to migrate
    fun setNovelsToMigrate(novels: List<NovelEntity>) {
        migrationItems.clear()
        migrationItems.addAll(novels.map { BulkMigrationItem(sourceNovel = it) })
    }

    // Toggle target provider selection
    fun toggleProvider(name: String) {
        if (selectedProviders.contains(name)) {
            selectedProviders.remove(name)
        } else {
            selectedProviders.add(name)
        }
    }

    // Select all providers
    fun selectAllProviders() {
        selectedProviders.clear()
        selectedProviders.addAll(allProviders.map { it.name })
    }

    // Clear all provider selections
    fun selectNoneProviders() {
        selectedProviders.clear()
    }

    fun updateItem(newItem: BulkMigrationItem) {
        val index = migrationItems.indexOfFirst { it.sourceNovel.id == newItem.sourceNovel.id }
        if (index != -1) {
            migrationItems[index] = newItem
        }
    }

    // Starts alternative provider search for all novels in the queue.
    // To prevent rate-limiting, network congestion, and memory exhaustion,
    // queries are routed through a Semaphore(2) limiting parallel calls on Dispatchers.IO.
    fun startSearch() {
        searchJob?.cancel()
        isSearching.value = true

        searchJob = viewModelScope.launchSafe(Dispatchers.IO) {
            val concurrencyLimit = Semaphore(2)
            val jobs = migrationItems.map { item ->
                viewModelScope.launchSafe(Dispatchers.IO) {
                    concurrencyLimit.withPermit {
                        searchSingleItem(item)
                    }
                }
            }
            jobs.forEach { it.join() }
            isSearching.value = false
        }
    }

    private suspend fun searchSingleItem(item: BulkMigrationItem) {
        val query = item.sourceNovel.name
        val activeProviders = selectedProviders.toList()

        if (activeProviders.isEmpty()) {
            updateItem(item.copy(searchState = MigrationSearchState.Error("No providers selected")))
            return
        }

        updateItem(item.copy(searchState = MigrationSearchState.Loading))

        val allSearchResults = withContext(Dispatchers.IO) {
            activeProviders.map { providerName ->
                async {
                    try {
                        withTimeoutOrNull(6000) { // cap provider query at 6 seconds
                            val repo = Apis.getApiFromName(providerName)
                            val searchResultResource = repo.search(query)
                            if (searchResultResource is Resource.Success) {
                                searchResultResource.value.filter { it.apiName != item.sourceNovel.apiName }
                            } else {
                                emptyList()
                            }
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        if (allSearchResults.isNotEmpty()) {
            // Offload CPU-heavy fuzzy ratios calculations to Dispatchers.Default
            val bestMatch = withContext(Dispatchers.Default) {
                val queryLower = query.lowercase().trim()
                allSearchResults.map { searchRes ->
                    val score = FuzzySearch.ratio(searchRes.name.lowercase(), queryLower)
                    searchRes to score
                }.sortedByDescending { it.second }
                 .firstOrNull()?.first
            }

            updateItem(
                item.copy(
                    allResults = allSearchResults,
                    targetMatch = bestMatch,
                    searchState = MigrationSearchState.Success
                )
            )
        } else {
            updateItem(item.copy(searchState = MigrationSearchState.Error("No matches found")))
        }
    }

    // Run actual database migration for all items in the queue that have a valid target match.
    // Runs sequentially on Dispatchers.IO with robust error boundary catching.
    fun startMigration(context: Context, onComplete: () -> Unit) {
        migrateJob?.cancel()
        isMigrating.value = true

        migrateJob = viewModelScope.launchSafe(Dispatchers.IO) {
            val matchedItems = migrationItems.filter { it.targetMatch != null }
            val total = matchedItems.size

            matchedItems.forEachIndexed { index, item ->
                val currentMatch = item.targetMatch ?: return@forEachIndexed
                val novelName = item.sourceNovel.name
                
                withContext(Dispatchers.Main) {
                    migrationProgress.value = "Migrating '$novelName' (${index + 1}/$total)..."
                }

                val repo = Apis.getApiFromName(currentMatch.apiName)
                val loadResource = repo.load(currentMatch.url)

                if (loadResource is Resource.Success) {
                    val loadedNovel = loadResource.value
                    try {
                        BackupUtils.migrateNovel(context, item.sourceNovel.id, loadedNovel, currentMatch.apiName)
                    } catch (t: Throwable) {
                        com.lagradost.quicknovel.mvvm.logError(t)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                isMigrating.value = false
                onComplete()
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        isSearching.value = false
    }

    fun cancelMigration() {
        migrateJob?.cancel()
        isMigrating.value = false
    }

    override fun onCleared() {
        searchJob?.cancel()
        migrateJob?.cancel()
        super.onCleared()
    }
}
