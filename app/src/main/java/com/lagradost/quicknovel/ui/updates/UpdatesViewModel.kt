package com.lagradost.quicknovel.ui.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.UpdateItem
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NovelUpdateGroup(
    val novelUrl: String,
    val novelName: String,
    val posterUrl: String?,
    val apiName: String,
    val chapters: List<UpdateItem>,
    var isExpanded: Boolean = false
)

class UpdatesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.updateDao()

    private val _groupedUpdates = MutableStateFlow<Map<String, List<NovelUpdateGroup>>>(emptyMap())
    val groupedUpdates: StateFlow<Map<String, List<NovelUpdateGroup>>> = _groupedUpdates.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        observeUpdates()
    }

    private fun observeUpdates() {
        val threshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        viewModelScope.launch {
            dao.getRecentUpdates(threshold).collectLatest { list ->
                val grouped = withContext(Dispatchers.Default) {
                    groupUpdates(list)
                }
                _groupedUpdates.value = grouped
            }
        }
    }

    private fun groupUpdates(list: List<UpdateItem>): Map<String, List<NovelUpdateGroup>> {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val groupedMap = list.groupBy { item ->
            val date = Date(item.uploadDate)
            val diff = System.currentTimeMillis() - item.uploadDate
            when {
                diff < 24 * 60 * 60 * 1000L -> "Today"
                diff < 48 * 60 * 60 * 1000L -> "Yesterday"
                else -> dateFormat.format(date)
            }
        }

        val resultMap = mutableMapOf<String, List<NovelUpdateGroup>>()
        for ((header, items) in groupedMap) {
            val novelMap = items.groupBy { it.novelUrl }
            val novelGroups = mutableListOf<NovelUpdateGroup>()
            for ((url, novelItems) in novelMap) {
                val sortedItems = novelItems.sortedByDescending { it.chapterIndex }
                val unreadItems = sortedItems.filter { item ->
                    if (item.chapterIndex == -1) return@filter true
                    val key = "${item.novelName}/${item.chapterIndex}"
                    val readAt = BaseApplication.getKey<Long>(
                        EPUB_CURRENT_POSITION_READ_AT,
                        key
                    )
                    readAt == null
                }
                if (unreadItems.isNotEmpty()) {
                    val first = unreadItems.first()
                    novelGroups.add(NovelUpdateGroup(url, first.novelName, first.posterUrl, first.apiName, unreadItems))
                }
            }
            if (novelGroups.isNotEmpty()) {
                resultMap[header] = novelGroups
            }
        }
        return resultMap
    }

    fun toggleGroupExpansion(header: String, groupToToggle: NovelUpdateGroup) {
        val currentMap = _groupedUpdates.value.toMutableMap()
        val groups = currentMap[header]?.toMutableList() ?: return
        val index = groups.indexOfFirst { it.novelUrl == groupToToggle.novelUrl }
        if (index != -1) {
            groups[index] = groupToToggle.copy(isExpanded = !groupToToggle.isExpanded)
            currentMap[header] = groups
            _groupedUpdates.value = currentMap
        }
    }

    fun refreshUpdates() {
        _isRefreshing.value = true
        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.UpdatesSyncWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        val wm = androidx.work.WorkManager.getInstance(getApplication())
        wm.enqueueUniqueWork("UpdatesManualSync", androidx.work.ExistingWorkPolicy.REPLACE, syncRequest)
        
        // We observe the WorkManager status locally or just delay the spinner
        wm.getWorkInfoByIdLiveData(syncRequest.id).observeForever { workInfo ->
            if (workInfo != null && workInfo.state.isFinished) {
                _isRefreshing.value = false
            }
        }
    }

    fun deleteAllUpdates() {
        viewModelScope.launch {
            dao.deleteAllUpdates()
        }
    }

    fun deleteOldUpdates() {
        viewModelScope.launch {
            val thresh = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            dao.deleteOldUpdates(thresh)
        }
    }

    fun getSyncableNovels(): List<ResultCached> {
        val keys = BaseApplication.getKeys(RESULT_BOOKMARK_STATE) ?: emptyList()
        return keys.mapNotNull { key ->
            val id = key.replaceFirst(RESULT_BOOKMARK_STATE, RESULT_BOOKMARK)
            BaseApplication.getKey<ResultCached>(id)
        }.sortedBy { it.name }
    }

    fun updateSyncSettings(novels: List<ResultCached>, checkedItems: BooleanArray) {
        viewModelScope.launch {
            var changed = false
            for (i in novels.indices) {
                if (novels[i].isSyncEnabled != checkedItems[i]) {
                    val updated = novels[i].copy(isSyncEnabled = checkedItems[i])
                    BaseApplication.setKey(RESULT_BOOKMARK, updated.id.toString(), updated)
                    changed = true
                }
            }
            if (changed) {
                val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.lagradost.quicknovel.sync.UpdatesSyncWorker>()
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()
                androidx.work.WorkManager.getInstance(getApplication()).enqueueUniqueWork("UpdatesManualSync", androidx.work.ExistingWorkPolicy.REPLACE, syncRequest)
            }
        }
    }
}
