package com.lagradost.quicknovel.ui.neolists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NeoListEntity
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.download.CategoryItem
import com.lagradost.quicknovel.ui.download.DownloadViewModel
import com.lagradost.quicknovel.util.AppUtils.mapper
import com.fasterxml.jackson.core.type.TypeReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── NeoListsViewModel ────────────────────────────────────────────────────────

class NeoListsViewModel : ViewModel() {

    private val ctx get() = context ?: throw IllegalStateException("No application context")
    private val dao by lazy { AppDatabase.getDatabase(ctx).neoListDao() }

    // ─── Folder list state ────────────────────────────────────────────────────

    /** All NeoList folders, ordered by sortOrder ASC then createdAt DESC. */
    private val _folders = MutableStateFlow<List<NeoListEntity>>(emptyList())
    val folders: StateFlow<List<NeoListEntity>> = _folders.asStateFlow()

    // ─── Import result events (consumed once by UI) ───────────────────────────
    val importResultEvent = MutableSharedFlow<ImportResult>(extraBufferCapacity = 1)

    /** Whether a version conflict dialog should be shown. */
    private val _versionConflict = MutableStateFlow<ImportResult.NewerVersionAvailable?>(null)
    val versionConflict: StateFlow<ImportResult.NewerVersionAvailable?> = _versionConflict.asStateFlow()

    init {
        // Collect Room flow on IO — safe, lifecycle-bound
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllAsFlow().collect { list ->
                _folders.value = list
            }
        }


    }

    // ─── Import ───────────────────────────────────────────────────────────────

    /**
     * Imports a .neolist file from a content:// URI.
     * Uses contentResolver.openInputStream — NEVER File(path).
     */
    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = NeoListImportExportEngine.importFromUri(uri, ctx)
            when (result) {
                is ImportResult.NewerVersionAvailable -> _versionConflict.value = result
                else -> importResultEvent.emit(result)
            }
        }
    }

    /**
     * Called when the user chooses "Replace" in the version conflict dialog.
     * Re-runs import with replace = true.
     */
    fun resolveVersionConflictReplace(incoming: NeoListExport, uri: Uri) {
        _versionConflict.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val result = NeoListImportExportEngine.importFromUri(uri, ctx, replace = true)
            importResultEvent.emit(result)
        }
    }

    fun dismissVersionConflict() {
        _versionConflict.value = null
    }

    // ─── Export / Share ───────────────────────────────────────────────────────

    fun exportList(listId: String, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            NeoListImportExportEngine.exportAndShare(listId, context)
        }
    }

    fun saveList(listId: String, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            NeoListImportExportEngine.exportAndSave(listId, context)
        }
    }

    // ─── Lock / Unlock ────────────────────────────────────────────────────────

    /**
     * Toggles the lock state of a NeoList folder.
     *
     * When unlocking: adds a corresponding CategoryItem to SharedPreferences so the
     * folder appears in the bookmark dialog.
     * When locking: removes that CategoryItem from SharedPreferences.
     *
     * ID assignment uses maxId + 1 (guaranteed unique — same as DownloadViewModel.addCategory()).
     * NEVER uses hashCode().
     */
    fun toggleLock(listId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(listId) ?: return@launch
                val newLocked = !entity.isLocked
                dao.setLocked(listId, newLocked)

                if (!newLocked) {
                    // Unlocking → add a CategoryItem bridge in SharedPreferences
                    addCategoryForNeoList(entity)
                } else {
                    // Locking → remove the CategoryItem bridge
                    removeCategoryForNeoList(entity)
                }

                // Notify all bookmark dialog observers that categories changed
                DownloadViewModel.bookmarkChanged.emit(Unit)

            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    /**
     * Adds a CategoryItem to CUSTOM_CATEGORIES SharedPreferences for the given NeoList.
     * Uses maxId + 1 for guaranteed-unique integer ID (no hashCode collision risk).
     */
    private fun addCategoryForNeoList(entity: NeoListEntity) {
        try {
            val json = getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
            val currentCustom = try {
                mapper.readValue(json, object : TypeReference<List<CategoryItem>>() {})
            } catch (_: Throwable) { emptyList() }

            // Get mapping map from SharedPreferences
            val mapJson = getKey<String>(DOWNLOAD_SETTINGS, "NEOLIST_ID_MAP", "{}") ?: "{}"
            val idMap = try {
                mapper.readValue(mapJson, object : TypeReference<Map<String, Int>>() {})
            } catch (_: Throwable) { emptyMap<String, Int>() }

            var syntheticId = idMap[entity.id]
            if (syntheticId == null) {
                // Generate guaranteed-unique ID using maxId + 1
                val allIds = (DownloadViewModel.systemCategories.map { it.id }) + currentCustom.map { it.id }
                syntheticId = NeoListImportExportEngine.generateUniqueCategoryId(allIds)

                // Save mapping
                val updatedMap = idMap + (entity.id to syntheticId)
                setKey(DOWNLOAD_SETTINGS, "NEOLIST_ID_MAP", mapper.writeValueAsString(updatedMap))
            }

            // Save in NEOLIST_CATEGORY_IDS
            val idsJson = getKey<String>(DOWNLOAD_SETTINGS, "NEOLIST_CATEGORY_IDS", "[]") ?: "[]"
            val idSet = try {
                mapper.readValue(idsJson, object : TypeReference<List<Int>>() {})
            } catch (_: Throwable) { emptyList<Int>() }
            if (syntheticId !in idSet) {
                setKey(DOWNLOAD_SETTINGS, "NEOLIST_CATEGORY_IDS", mapper.writeValueAsString(idSet + syntheticId))
            }

            // Check if category item exists in CUSTOM_CATEGORIES
            if (currentCustom.none { it.id == syntheticId }) {
                val newCategory = CategoryItem(
                    id       = syntheticId,
                    name     = entity.title,
                    isSystem = false,
                    isLocked = false   // Unlocked → visible in bookmark dialog
                )
                val updated = currentCustom + newCategory
                setKey(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", mapper.writeValueAsString(updated))
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    /**
     * Removes the CategoryItem bridge from CUSTOM_CATEGORIES when a NeoList is re-locked.
     */
    private fun removeCategoryForNeoList(entity: NeoListEntity) {
        try {
            val mapJson = getKey<String>(DOWNLOAD_SETTINGS, "NEOLIST_ID_MAP", "{}") ?: "{}"
            val idMap = try {
                mapper.readValue(mapJson, object : TypeReference<Map<String, Int>>() {})
            } catch (_: Throwable) { emptyMap<String, Int>() }

            val categoryId = idMap[entity.id] ?: return

            // Clean up all bookmarked novels mapped to this category ID in novel table
            AppDatabase.getDatabase(ctx).novelDao().removeCategoryFromNovels(categoryId)

            // Remove from NEOLIST_ID_MAP
            val updatedMap = idMap - entity.id
            setKey(DOWNLOAD_SETTINGS, "NEOLIST_ID_MAP", mapper.writeValueAsString(updatedMap))

            // Remove from NEOLIST_CATEGORY_IDS
            val idsJson = getKey<String>(DOWNLOAD_SETTINGS, "NEOLIST_CATEGORY_IDS", "[]") ?: "[]"
            val idSet = try {
                mapper.readValue(idsJson, object : TypeReference<List<Int>>() {})
            } catch (_: Throwable) { emptyList<Int>() }
            setKey(DOWNLOAD_SETTINGS, "NEOLIST_CATEGORY_IDS", mapper.writeValueAsString(idSet - categoryId))

            // Remove entries that match this NeoList's synthetic category ID
            val json = getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
            val currentCustom = try {
                mapper.readValue(json, object : TypeReference<List<CategoryItem>>() {})
            } catch (_: Throwable) { return }
            val updated = currentCustom.filter { it.id != categoryId }
            setKey(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", mapper.writeValueAsString(updated))
        } catch (t: Throwable) {
            logError(t)
        }
    }

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a new empty user NeoList folder.
     */
    fun createNewFolder(title: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uuid = java.util.UUID.randomUUID().toString()
                val entity = NeoListEntity(
                    id = uuid,
                    title = title.trim(),
                    description = description.trim().takeIf { it.isNotBlank() },
                    coverUrl = null,
                    authorHandle = "Me",
                    createdAt = System.currentTimeMillis(),
                    importedAt = null,
                    isLocked = false,     // User-created list is unlocked by default
                    isImported = false,
                    novels = emptyList(),
                    sortOrder = 0
                )
                
                dao.insert(entity)
                addCategoryForNeoList(entity)
                DownloadViewModel.bookmarkChanged.emit(Unit)
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    /**
     * Updates the metadata of an existing user-created folder.
     * Also updates the bridge category name if the folder is unlocked.
     */
    fun updateFolderMetadata(listId: String, title: String, authorHandle: String?, description: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(listId) ?: return@launch
                val updated = entity.copy(
                    title = title.trim(),
                    authorHandle = authorHandle?.trim()?.takeIf { it.isNotBlank() },
                    description = description?.trim()?.takeIf { it.isNotBlank() }
                )
                dao.insert(updated)

                // If folder is unlocked, update its category title in SharedPreferences
                if (!entity.isLocked) {
                    val mapJson = getKey<String>(DOWNLOAD_SETTINGS, "NEOLIST_ID_MAP", "{}") ?: "{}"
                    val idMap = try {
                        mapper.readValue(mapJson, object : TypeReference<Map<String, Int>>() {})
                    } catch (_: Throwable) { emptyMap<String, Int>() }

                    val categoryId = idMap[entity.id]
                    if (categoryId != null) {
                        val json = getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
                        val currentCustom = try {
                            mapper.readValue(json, object : TypeReference<List<CategoryItem>>() {})
                        } catch (_: Throwable) { emptyList() }

                        val updatedCustom = currentCustom.map {
                            if (it.id == categoryId) it.copy(name = updated.title) else it
                        }
                        setKey(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", mapper.writeValueAsString(updatedCustom))
                    }
                }

                DownloadViewModel.bookmarkChanged.emit(Unit)
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }



    // ─── Delete ───────────────────────────────────────────────────────────────

    /**
     * Deletes a folder and its pin map entries.
     * Also removes any CategoryItem bridge if the folder was unlocked.
     */
    fun deleteList(listId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = dao.getById(listId)
                dao.deletePinsForList(listId)
                dao.deleteById(listId)
                // If folder was unlocked, clean up its CategoryItem bridge
                entity?.let { if (!it.isLocked) removeCategoryForNeoList(it) }
                DownloadViewModel.bookmarkChanged.emit(Unit)
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }
}
