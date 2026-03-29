package com.lagradost.quicknovel.ui.foryou

import android.app.Application
import androidx.lifecycle.*
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.ui.foryou.recommendation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForYouViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val poolManager = RecommendationPoolManager(context)
    private val engine = RecommendationEngine()

    private val _profile = MutableLiveData<UserTasteProfile>()
    val profile: LiveData<UserTasteProfile> = _profile

    private val _recommendations = MutableLiveData<List<RecommendationGroup>>()
    val recommendations: LiveData<List<RecommendationGroup>> = _recommendations

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _stats = MutableLiveData<Pair<Int, Int>>()
    val stats: LiveData<Pair<Int, Int>> = _stats

    private val syncObserver = Observer<Boolean> { syncing ->
        val currentProfile = _profile.value
        if (!syncing && currentProfile?.isWizardComplete == true && _recommendations.value.isNullOrEmpty()) {
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
        viewModelScope.launch {
            val savedProfile = withContext(Dispatchers.IO) {
                context.getKey<UserTasteProfile>("user_taste_profile") ?: UserTasteProfile.EMPTY
            }
            _profile.postValue(savedProfile)
            if (savedProfile.isWizardComplete) {
                refreshRecommendations()
            }
        }
    }

    fun saveProfile(newProfile: UserTasteProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            context.setKey("user_taste_profile", newProfile)
            _profile.postValue(newProfile)
            refreshRecommendations()
        }
    }

    fun refreshRecommendations() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                // If we have no providers and aren't syncing, trigger a one-time sync to recover
                if (com.lagradost.quicknovel.util.Apis.apis.isEmpty() && com.lagradost.quicknovel.util.Apis.isSyncing.value != true) {
                    val request = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.PluginSyncWorker>()
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                        "PluginSyncEmptyState",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        request
                    )
                }

                // Ensure pool has some data
                val currentCandidates = poolManager.getCandidates()
                if (currentCandidates.isEmpty()) {
                    poolManager.fetchNewCandidates()
                }
                
                val candidates = poolManager.getCandidates()
                val profile = _profile.value ?: UserTasteProfile.EMPTY
                
                val results = withContext(Dispatchers.Default) {
                    engine.generateRecommendations(profile, candidates)
                }
                _recommendations.postValue(results)
                
                // Update stats
                val recCount = results.sumOf { it.recommendations.size }
                val indexedCount = candidates.size
                _stats.postValue(recCount to indexedCount)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun markWizardComplete() {
        val current = _profile.value ?: UserTasteProfile.EMPTY
        saveProfile(current.copy(isWizardComplete = true))
    }

    fun recordInteraction(novelUrl: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context)
            db.interactionDao().insert(
                com.lagradost.quicknovel.db.ImplicitInteractionEntity(
                    novelUrl = novelUrl,
                    interactionType = type
                )
            )
            // Optionally: Trigger profile update immediately or scheduled
        }
    }
}
