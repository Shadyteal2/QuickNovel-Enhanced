package com.lagradost.quicknovel.ui.neolists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NeoListEntity
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Apis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── NeoListDetailViewModel ───────────────────────────────────────────────────

class NeoListDetailViewModel : ViewModel() {

    private val ctx get() = context ?: throw IllegalStateException("No application context")
    private val dao by lazy { AppDatabase.getDatabase(ctx).neoListDao() }

    // ─── Folder data ──────────────────────────────────────────────────────────

    private val _folder = MutableStateFlow<NeoListEntity?>(null)
    val folder: StateFlow<NeoListEntity?> = _folder.asStateFlow()

    // ─── Resolution states — one entry per novel in the folder ────────────────

    /** Map from novel index to its resolution state. */
    private val _resolutionStates = MutableStateFlow<Map<Int, NovelResolutionState>>(emptyMap())
    val resolutionStates: StateFlow<Map<Int, NovelResolutionState>> = _resolutionStates.asStateFlow()

    // ─── Computed statistics ──────────────────────────────────────────────────

    private val _stats = MutableStateFlow(NeoListStats(0, 0, 0))
    val stats: StateFlow<NeoListStats> = _stats.asStateFlow()

    // ─── Batch migrate state ──────────────────────────────────────────────────

    /**
     * Index of the dead novel currently being presented for migration.
     * null = no batch migration in progress.
     */
    private val _batchMigrateIndex = MutableStateFlow<Int?>(null)
    val batchMigrateIndex: StateFlow<Int?> = _batchMigrateIndex.asStateFlow()

    // ─── Load ─────────────────────────────────────────────────────────────────

    /**
     * Loads the folder and resolves all provider states.
     * Resolution runs on Dispatchers.Default (CPU-bound, no IO needed — just map lookup).
     * Must be called once after the ViewModel is attached to the screen.
     */
    fun load(listId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(listId) ?: return@launch
                _folder.value = entity
                resolveProviders(entity)
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    /**
     * Resolves each novel entry against the currently available providers.
     * Uses Apis.getApiFromNameNull() — O(n_providers) but called once and cached.
     * Emits Pending immediately, then resolves all entries in one batch.
     */
    private suspend fun resolveProviders(entity: NeoListEntity) {
        // Emit Pending for all entries immediately so shimmer shows
        val pendingMap = entity.novels.indices.associateWith { NovelResolutionState.Pending }
        _resolutionStates.value = pendingMap

        // Build a snapshot of available provider names (thread-safe, read-only)
        val availableProviders = Apis.apis.map { it.name }.toHashSet()

        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val resolved = entity.novels.mapIndexed { index, entry ->
                val state: NovelResolutionState = if (entry.apiName in availableProviders) {
                    NovelResolutionState.Resolved
                } else {
                    NovelResolutionState.DeadProvider(
                        savedTitle  = entry.title,
                        savedAuthor = entry.author
                    )
                }
                index to state
            }.toMap()

            _resolutionStates.value = resolved

            // Compute statistics
            val dead = resolved.values.count { it is NovelResolutionState.DeadProvider }
            val total = entity.novels.size
            _stats.value = NeoListStats(
                totalNovels   = total,
                resolvedCount = total - dead,
                deadCount     = dead
            )
        }
    }

    // ─── Batch Migrate ────────────────────────────────────────────────────────

    /**
     * Starts the batch migrate flow by pointing to the first dead novel index.
     * The UI observes [batchMigrateIndex] and navigates to search for each one.
     */
    fun startBatchMigrate() {
        val deadIndex = _resolutionStates.value.entries
            .firstOrNull { it.value is NovelResolutionState.DeadProvider }
            ?.key
        _batchMigrateIndex.value = deadIndex
    }

    /**
     * Advances to the next dead novel in the batch migrate flow.
     * Call this after the user returns from search (whether they migrated or skipped).
     */
    fun advanceBatchMigrate() {
        val current = _batchMigrateIndex.value ?: return
        val states = _resolutionStates.value
        val nextDead = states.entries
            .filter { it.key > current && it.value is NovelResolutionState.DeadProvider }
            .minByOrNull { it.key }
            ?.key
        _batchMigrateIndex.value = nextDead
    }

    fun cancelBatchMigrate() {
        _batchMigrateIndex.value = null
    }

    // ─── Refresh ─────────────────────────────────────────────────────────────

    /** Re-resolves providers (call after user installs a new plugin). */
    fun refresh() {
        val entity = _folder.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            resolveProviders(entity)
        }
    }
}
