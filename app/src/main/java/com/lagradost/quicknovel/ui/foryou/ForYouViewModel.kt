package com.lagradost.quicknovel.ui.foryou

import android.app.Application
import androidx.lifecycle.*
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.ui.foryou.recommendation.*
import com.lagradost.quicknovel.mvvm.launchSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForYouViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val poolManager = RecommendationPoolManager(context)
    private val engine = RecommendationEngine()
    private val db = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context)

    private val _baseProfile = MutableStateFlow<UserTasteProfile>(UserTasteProfile.EMPTY)
    val baseProfile: StateFlow<UserTasteProfile> = _baseProfile.asStateFlow()

    // Bounded and aggregated UserTasteProfile pipeline combining bookmarks and interactions reactively
    val profile: StateFlow<UserTasteProfile> = combine(
        _baseProfile,
        db.novelDao().getAllBookmarksAsFlow(),
        db.interactionDao().getRecentInteractionsFlow(1000)
    ) { baseProfile, allBookmarks, interactions ->
        // Apply a bounded limit to bookmarks parsing to prevent memory exhaustion (top 200 most recently downloaded/updated/read)
        val bookmarks = allBookmarks
            .sortedByDescending { it.lastDownloaded ?: it.lastUpdated ?: 0L }
            .take(200)

        val urlToTags = mutableMapOf<String, Set<TagCategory>>()
        
        // Add tags from bookmarks
        for (novel in bookmarks) {
            urlToTags[novel.source] = TagNormalizer.normalize(novel.tags) + SynopsisTagExtractor.extractFromTitle(novel.name)
        }
        
        // Query candidates directly to associate tags for interaction matching
        val candidates = db.recommendationDao().getAllCandidates(200)
        for (c in candidates) {
            if (!urlToTags.containsKey(c.url)) {
                urlToTags[c.url] = TagNormalizer.normalize(c.tags) + SynopsisTagExtractor.extractFromTitle(c.name)
            }
        }

        val preferredMap = baseProfile.preferredTags.associateBy { it.tag }.toMutableMap()
        val avoidedMap = baseProfile.avoidedTags.associateBy { it.tag }.toMutableMap()
        
        // 1. Seed from Bookmarks - only boost tags that are already preferred or avoided (do not auto-add new tags to prevent dilution)
        for (novel in bookmarks) {
            val novelTags = urlToTags[novel.source] ?: emptySet()
            for (tag in novelTags) {
                val current = preferredMap[tag]
                if (current != null) {
                    preferredMap[tag] = current.copy(
                        score = (current.score + 0.3f).coerceAtMost(1.0f),
                        confidence = (current.confidence + 0.3f).coerceAtMost(1.0f)
                    )
                }
                avoidedMap.remove(tag)
            }
        }

        // 2. Aggregate from Implicit Interactions
        for (interaction in interactions) {
            val novelTags = urlToTags[interaction.novelUrl] ?: emptySet()
            if (novelTags.isEmpty()) continue
            
            when (interaction.interactionType) {
                "CLICK" -> {
                    for (tag in novelTags) {
                        val current = preferredMap[tag]
                        if (current != null) {
                            preferredMap[tag] = current.copy(
                                score = (current.score + 0.15f).coerceAtMost(1.0f),
                                confidence = (current.confidence + 0.15f).coerceAtMost(1.0f)
                            )
                        } else {
                            preferredMap[tag] = TagAffinity(tag, 0.15f, 0.15f)
                        }
                        avoidedMap.remove(tag)
                    }
                }
                "READ" -> {
                    for (tag in novelTags) {
                        val current = preferredMap[tag]
                        if (current != null) {
                            preferredMap[tag] = current.copy(
                                score = (current.score + 0.4f).coerceAtMost(1.0f)
                            )
                        } else {
                            preferredMap[tag] = TagAffinity(tag, 0.4f, 0.5f)
                        }
                        avoidedMap.remove(tag)
                    }
                }
                "DISMISS" -> {
                    for (tag in novelTags) {
                        val currentAvoided = avoidedMap[tag]
                        if (currentAvoided != null) {
                            avoidedMap[tag] = currentAvoided.copy(
                                score = (currentAvoided.score + 0.3f).coerceAtMost(1.0f),
                                confidence = (currentAvoided.confidence + 0.3f).coerceAtMost(1.0f)
                            )
                        } else {
                            avoidedMap[tag] = TagAffinity(tag, 0.5f, 0.5f)
                        }
                        preferredMap.remove(tag)
                    }
                }
            }
        }

        baseProfile.copy(
            preferredTags = preferredMap.values.toList(),
            avoidedTags = avoidedMap.values.toList(),
            lastUpdated = System.currentTimeMillis()
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserTasteProfile.EMPTY
    )

    val recommendations: StateFlow<List<RecommendationGroup>> = combine(
        profile,
        db.recommendationDao().getAllCandidatesFlow(200),
        db.novelDao().getAllBookmarksAsFlow()
    ) { updatedProfile, candidates, bookmarks ->
        if (!updatedProfile.isWizardComplete) {
            emptyList()
        } else {
            _isLoading.value = true
            try {
                val results = engine.generateRecommendations(updatedProfile, candidates, bookmarks)
                
                val recCount = results.sumOf { it.recommendations.size }
                val indexedCount = candidates.size
                _stats.value = recCount to indexedCount
                
                results
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
                emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val carouselItems: StateFlow<List<Recommendation>> = recommendations
        .map { groups ->
            groups.flatMap { it.recommendations }
                .distinctBy { it.novel.url }
                .sortedByDescending { it.score }
                .take(8)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _stats = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val stats: StateFlow<Pair<Int, Int>> = _stats.asStateFlow()

    private val syncObserver = Observer<Boolean> { syncing ->
        val currentProfile = profile.value
        if (!syncing && currentProfile.isWizardComplete && recommendations.value.isEmpty()) {
            refreshRecommendations()
        }
    }

    init {
        loadProfile()
        
        // Auto-refresh when sync finishes if we have no recommendations yet
        Apis.isSyncing.observeForever(syncObserver)
    }

    override fun onCleared() {
        super.onCleared()
        Apis.isSyncing.removeObserver(syncObserver)
    }

    fun loadProfile() {
        viewModelScope.launchSafe {
            val savedProfile = withContext(Dispatchers.IO) {
                context.getKey<UserTasteProfile>("user_taste_profile") ?: UserTasteProfile.EMPTY
            }
            _baseProfile.value = savedProfile
        }
    }

    fun saveProfile(newProfile: UserTasteProfile) {
        viewModelScope.launchSafe(Dispatchers.IO) {
            context.setKey("user_taste_profile", newProfile)
            _baseProfile.value = newProfile
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launchSafe {
            _isLoading.value = true
            try {
                // If we have no providers and aren't syncing, trigger a one-time sync to recover.
                val pluginsDir = com.lagradost.quicknovel.util.PluginManager.getPluginsDir(context)
                val hasPluginsOnDisk = pluginsDir.listFiles()?.any { it.name.endsWith(".apk") } == true
                if (com.lagradost.quicknovel.util.Apis.apis.isEmpty() &&
                    !hasPluginsOnDisk &&
                    com.lagradost.quicknovel.util.Apis.isSyncing.value != true) {
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.PluginSyncWorker>()
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                        "PluginSyncEmptyState",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        request
                    )
                }

                // Force candidates fetch
                withContext(Dispatchers.IO) {
                    poolManager.fetchNewCandidates()
                }
                
                // Force a trigger in base profile flow to refresh the combined state
                _baseProfile.value = _baseProfile.value.copy(lastUpdated = System.currentTimeMillis())
            } catch (e: Exception) {
                com.lagradost.quicknovel.mvvm.logError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markWizardComplete(diversityScore: Float) {
        val current = _baseProfile.value
        saveProfile(current.copy(isWizardComplete = true, diversityScore = diversityScore))
    }

    fun recordInteraction(novelUrl: String, type: String) {
        viewModelScope.launchSafe(Dispatchers.IO) {
            db.interactionDao().insert(
                com.lagradost.quicknovel.db.ImplicitInteractionEntity(
                    novelUrl = novelUrl,
                    interactionType = type
                )
            )
        }
    }
}
