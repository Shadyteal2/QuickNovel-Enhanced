package com.lagradost.quicknovel.ui.download

import android.content.DialogInterface
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.currentDownloads
import com.lagradost.quicknovel.BookDownloader2.currentDownloadsMutex
import com.lagradost.quicknovel.BookDownloader2.downloadDataRefreshed
import com.lagradost.quicknovel.BookDownloader2.downloadInfoMutex
import com.lagradost.quicknovel.BookDownloader2.downloadProgress
import com.lagradost.quicknovel.BookDownloader2.downloadProgressChanged
import com.lagradost.quicknovel.BookDownloader2.downloadRemoved
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.CURRENT_TAB
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.DownloadFileWorkManager.Companion.viewModel
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromNameOrNull
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.set
import kotlin.coroutines.cancellation.CancellationException

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6
const val LAST_ACCES_SORT = 7
const val REVERSE_LAST_ACCES_SORT = 8
const val LAST_UPDATED_SORT = 9
const val REVERSE_LAST_UPDATED_SORT = 10

const val CHAPTER_SORT = 11
const val REVERSE_CHAPTER_SORT = 12

data class SortingMethod(@StringRes val name: Int, val id: Int, val inverse: Int = id)

data class CategoryItem(
    val id: Int,
    @StringRes val stringRes: Int? = null,
    val name: String,
    val isSystem: Boolean = false
)

class DownloadViewModel : ViewModel() {

    companion object {
        val sortingMethods = arrayOf(
            SortingMethod(R.string.default_sort, DEFAULT_SORT),
            SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
            SortingMethod(
                R.string.recently_updated_sort,
                LAST_UPDATED_SORT,
                REVERSE_LAST_UPDATED_SORT
            ),
            SortingMethod(R.string.alpha_sort, ALPHA_SORT, REVERSE_ALPHA_SORT),
            SortingMethod(R.string.download_sort, DOWNLOADSIZE_SORT, REVERSE_DOWNLOADSIZE_SORT),
            SortingMethod(
                R.string.download_perc, DOWNLOADPRECENTAGE_SORT,
                REVERSE_DOWNLOADPRECENTAGE_SORT
            ),
        )

        val normalSortingMethods = arrayOf(
            SortingMethod(R.string.default_sort, DEFAULT_SORT),
            SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
            SortingMethod(R.string.alpha_sort, ALPHA_SORT, REVERSE_ALPHA_SORT),
        )

        val systemCategories = listOf(
            CategoryItem(ReadType.READING.prefValue, R.string.type_reading, "Reading", true),
            CategoryItem(ReadType.ON_HOLD.prefValue, R.string.type_on_hold, "On-Hold", true),
            CategoryItem(ReadType.PLAN_TO_READ.prefValue, R.string.type_plan_to_read, "Plan to Read", true),
            CategoryItem(ReadType.COMPLETED.prefValue, R.string.type_completed, "Completed", true),
            CategoryItem(ReadType.DROPPED.prefValue, R.string.type_dropped, "Dropped", true),
        )
        val bookmarkChanged = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 0)
    }

    var activeQuery: String = ""
    val _pages: androidx.lifecycle.MutableLiveData<List<Page>> = androidx.lifecycle.MutableLiveData(null)
    val pages: androidx.lifecycle.LiveData<List<Page>> = _pages
    private val cardsDataMutex = kotlinx.coroutines.sync.Mutex()
    private val cardsData: java.util.HashMap<Int, com.lagradost.quicknovel.ui.download.DownloadFragment.DownloadDataLoaded> = hashMapOf()
    private val dao = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context ?: com.lagradost.quicknovel.BaseApplication.context!!).novelDao()

    private fun getSavedCategories(): List<CategoryItem> {
        val json = getKey<String>(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", "[]") ?: "[]"
        return try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val customList = mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<CategoryItem>>() {})
            
            val orderJson = getKey<String>(DOWNLOAD_SETTINGS, "CATEGORIES_ORDER", "[]") ?: "[]"
            val orderList = mapper.readValue(orderJson, object : com.fasterxml.jackson.core.type.TypeReference<List<Int>>() {})

            val allItems = systemCategories + customList
            if (orderList.isNotEmpty()) {
                allItems.sortedBy { orderList.indexOf(it.id).takeIf { idx -> idx >= 0 } ?: Int.MAX_VALUE }
            } else {
                allItems
            }
        } catch (t: Throwable) {
            systemCategories
        }
    }

    private val _readList = MutableStateFlow<List<CategoryItem>>(getSavedCategories())
    val readList: List<CategoryItem> get() = _readList.value

    fun updateCategories(newList: List<CategoryItem>) {
        _readList.value = newList
        val customList = newList.filter { !it.isSystem }
        val orderList = newList.map { it.id }
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        setKey(DOWNLOAD_SETTINGS, "CUSTOM_CATEGORIES", mapper.writeValueAsString(customList))
        setKey(DOWNLOAD_SETTINGS, "CATEGORIES_ORDER", mapper.writeValueAsString(orderList))
        loadAllData(false)
    }

    fun addCategory(name: String) {
        val current = readList
        val maxId = (current.maxByOrNull { it.id }?.id ?: 10).coerceAtLeast(10)
        val newItem = CategoryItem(maxId + 1, null, name, false)
        updateCategories(current + newItem)
    }

    init {
        viewModelScope.launch {
            bookmarkChanged.collect {
                loadAllData(false)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAllAsFlow().collect { novels ->
                cardsDataMutex.withLock {
                    for (novel in novels) {
                        val info = BookDownloader2.downloadProgress[novel.id]
                        val progress = novel.downloadProgress ?: info?.progress ?: 0L
                        val total = novel.downloadTotal ?: info?.total ?: 0L
                        val state = novel.downloadStatus?.let { DownloadState.values().getOrNull(it) } ?: info?.state ?: DownloadState.Nothing
                        
                        cardsData[novel.id] = com.lagradost.quicknovel.ui.download.DownloadFragment.DownloadDataLoaded(
                            source = novel.source,
                            name = novel.name,
                            author = novel.author,
                            posterUrl = novel.posterUrl,
                            rating = novel.rating,
                            peopleVoted = novel.peopleVoted,
                            views = novel.views,
                            synopsis = novel.synopsis,
                            tags = novel.tags,
                            apiName = novel.apiName,
                            readCount = getKey<Int>(DOWNLOAD_EPUB_SIZE, novel.id.toString()) ?: 0,
                            downloadedCount = progress,
                            downloadedTotal = total,
                            ETA = context?.let { ctx -> info?.eta(ctx) } ?: "",
                            state = state,
                            id = novel.id,
                            generating = false,
                            lastUpdated = novel.lastUpdated,
                            lastDownloaded = novel.lastDownloaded,
                            filePath = novel.filePath,
                            formatType = novel.formatType,
                            hash = novel.hash,
                            bookmarkType = novel.bookmarkType
                        )
                    }
                }
                postCards()
            }
        }
    }

    fun deleteCategory(id: Int) {
        updateCategories(readList.filter { it.id != id })
    }

    fun renameCategory(id: Int, newName: String) {
        updateCategories(readList.map { if (it.id == id) it.copy(name = newName) else it })
    }



    var currentTab: MutableLiveData<Int> =
        MutableLiveData<Int>(getKey(DOWNLOAD_SETTINGS, CURRENT_TAB, 0))

    fun switchPage(position: Int) {
        setKey(DOWNLOAD_SETTINGS, CURRENT_TAB, position)
        currentTab.postValue(position)
    }

    fun refreshCard(card: DownloadFragment.DownloadDataLoaded) {
        DownloadFileWorkManager.download(card, context ?: return)
    }

    fun pause(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.addPendingAction(card.id, DownloadActionType.Pause)
    }

    fun resume(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.addPendingAction(card.id, DownloadActionType.Resume)
    }

    fun load(card: ResultCached) {
        loadResult(card.source, card.apiName)
    }

    fun stream(card: ResultCached) {
        BookDownloader2.stream(card)
    }

    fun search(query: String) {
        activeQuery = query.lowercase()
        resortAllData()
    }

    fun readEpub(card: DownloadFragment.DownloadDataLoaded) = ioSafe {
        try {
            cardsDataMutex.withLock {
                cardsData[card.id] = cardsData[card.id]?.copy(generating = true) ?: return@withLock
            }
            postCards()
            BookDownloader2.readEpub(
                card.id,
                card.downloadedCount.toInt(),
                card.author,
                card.name,
                card.apiName,
                card.synopsis,
                openInApp = true
            )
        } finally {
            setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
            cardsDataMutex.withLock {
                val current = cardsData[card.id] ?: return@withLock
                cardsData[card.id] = current.copy(
                    generating = false,
                    readCount = getKey<Int>(DOWNLOAD_EPUB_SIZE, card.id.toString()) ?: 0
                )
            }
            postCards()
        }
    }

    @WorkerThread
    suspend fun refreshInternal() {
        val allValues = cardsDataMutex.withLock {
            cardsData.values
        }

        val values = currentDownloadsMutex.withLock {
            allValues.filter { card ->
                val notImported = !card.isImported && card.apiName != IMPORT_SOURCE_PDF
                val canDownload =
                    card.downloadedTotal <= 0 || (card.downloadedCount * 100 / card.downloadedTotal) > 90
                val notDownloading = !currentDownloads.contains(
                    card.id
                )
                notImported && canDownload && notDownloading
            }
        }

        // Disable automatic downloading of updates to prevent unintentional background activity.
        // Users should manually start downloads for specific novels.
        /*for (card in values) {
            if (card.downloadedTotal <= 0 || (card.downloadedCount * 100 / card.downloadedTotal) > 90) {
                BookDownloader2.downloadWorkThread(card)
            }
        }*/
    }

    fun refresh() {
        DownloadFileWorkManager.refreshAll(this@DownloadViewModel, context ?: return)
    }

    fun refreshReadingProgress(){
        DownloadFileWorkManager.refreshAllReadingProgress(this@DownloadViewModel, context ?: return, currentTab.value ?: 1)
    }

    fun showMetadata(card: DownloadFragment.DownloadDataLoaded) {
        MainActivity.loadPreviewPage(card)
    }

    fun importEpub() {
        MainActivity.importEpub()
    }

    fun showMetadata(card: ResultCached) {
        MainActivity.loadPreviewPage(card)
    }

    fun load(card: DownloadFragment.DownloadDataLoaded) {
        loadResult(card.source, card.apiName)
    }

    fun deleteAlert(card: ResultCached) {
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete(card)
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
        val act = activity ?: return
        val builder: AlertDialog.Builder = AlertDialog.Builder(act)
        builder.setMessage(act.getString(R.string.permanently_delete_format).format(card.name))
            .setTitle(R.string.delete)
            .setPositiveButton(R.string.delete, dialogClickListener)
            .setNegativeButton(R.string.cancel, dialogClickListener)
            .show()
    }

    fun delete(card: ResultCached) {
        ioSafe {
            dao.updateBookmarkType(card.id, null)
            loadAllData(false)
        }
    }

    fun deleteAlert(card: DownloadFragment.DownloadDataLoaded) {
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete(card)
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
        val act = activity ?: return
        val builder: AlertDialog.Builder = AlertDialog.Builder(act)
        builder.setMessage(act.getString(R.string.permanently_delete_format).format(card.name))
            .setTitle(R.string.delete)
            .setPositiveButton(R.string.delete, dialogClickListener)
            .setNegativeButton(R.string.cancel, dialogClickListener)
            .show()
    }

    fun delete(card: DownloadFragment.DownloadDataLoaded) {
        ioSafe {
            BookDownloader2.deleteNovel(card.author, card.name, card.apiName)
            loadAllData(false)
        }
    }

    private fun matchesQuery(x: String): Boolean {
        return activeQuery.isBlank() || FuzzySearch.partialRatio(x.lowercase(), activeQuery) > 50
    }

    private fun sortArray(
        currentArray: ArrayList<DownloadFragment.DownloadDataLoaded>,
    ): List<DownloadFragment.DownloadDataLoaded> {
        val newSortingMethod = getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD) ?: DEFAULT_SORT
        setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, newSortingMethod)

        return when (newSortingMethod) {
            ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray
            }

            REVERSE_ALPHA_SORT -> {
                currentArray.sortByDescending { t -> t.name }
                currentArray
            }

            DOWNLOADSIZE_SORT -> {
                currentArray.sortByDescending { t -> t.downloadedCount }
                currentArray
            }

            REVERSE_DOWNLOADSIZE_SORT -> {
                currentArray.sortBy { t -> t.downloadedCount }
                currentArray
            }

            DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortByDescending { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                currentArray
            }

            REVERSE_DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortBy { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                currentArray
            }

            REVERSE_LAST_ACCES_SORT -> {
                currentArray.sortBy { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }

            LAST_UPDATED_SORT -> {
                if (currentArray.any { it.lastDownloaded == null }) {
                    currentArray.sortByDescending { t ->
                        (getKey<Long>(
                            DOWNLOAD_EPUB_LAST_ACCESS,
                            t.id.toString(),
                            0
                        )!!)
                    }
                }
                currentArray.sortByDescending { it.lastDownloaded ?: 0L }
                currentArray
            }

            REVERSE_LAST_UPDATED_SORT -> {
                if (currentArray.any { it.lastDownloaded == null }) {
                    currentArray.sortByDescending { t ->
                        (getKey<Long>(
                            DOWNLOAD_EPUB_LAST_ACCESS,
                            t.id.toString(),
                            0
                        )!!)
                    }
                }
                currentArray.sortBy { it.lastDownloaded ?: 0L }
                currentArray
            }
            //DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortByDescending { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
        }.filter { matchesQuery(it.name) }
    }

    private fun sortNormalArray(
        currentArray: ArrayList<ResultCached>,
    ): List<ResultCached> {
        val newSortingMethod =
            getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD) ?: DEFAULT_SORT
        setKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD, newSortingMethod)

        return when (newSortingMethod) {
            ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray
            }

            REVERSE_ALPHA_SORT -> {
                currentArray.sortByDescending { t -> t.name }
                currentArray
            }

            REVERSE_LAST_ACCES_SORT -> {
                currentArray.sortBy { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
            // DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortByDescending { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
        }.filter { matchesQuery(it.name) }
    }

    // QN-Enhanced: Optimized background sorting to prevent UI lag with 10k items
    fun resortAllData() = viewModelScope.launch(Dispatchers.Default) {
        val data = _pages.value ?: return@launch
        if (data.isEmpty()) return@launch

        val list = data.mapIndexed { index, page ->
            if (index == 0) {
                val sorted = sortArray(ArrayList(page.unsortedItems.map { (it as DownloadFragment.DownloadDataLoaded).copy() }))
                page.copy(items = sorted, hash = page.title.hashCode() * 31 + sorted.hashCode())
            } else {
                val sorted = sortNormalArray(ArrayList(page.unsortedItems.map { (it as ResultCached).copy() }))
                page.copy(items = sorted, hash = page.title.hashCode() * 31 + sorted.hashCode())
            }
        }
        _pages.postValue(list)
    }

    // QN-Enhanced: Background data loading from Room SSOT
    fun loadAllData(refreshAll: Boolean) = viewModelScope.launch(Dispatchers.Default) {
        if (refreshAll) fetchAllData(false)
        val mapping: HashMap<Int, ArrayList<ResultCached>> = hashMapOf()
        val currentCategories = readList
        for (cat in currentCategories) {
            mapping[cat.id] = arrayListOf()
        }

        // Fetch from Room
        val bookmarks = dao.getAllBookmarksAsFlow().first()
        for (novel in bookmarks) {
            val type = novel.bookmarkType ?: continue
            val cached = ResultCached(
                source = novel.source,
                name = novel.name,
                apiName = novel.apiName,
                id = novel.id,
                author = novel.author,
                poster = novel.posterUrl,
                tags = novel.tags,
                rating = novel.rating,
                totalChapters = novel.downloadTotal?.toInt() ?: 0,
                cachedTime = novel.lastDownloaded ?: 0,
                synopsis = novel.synopsis,
            )
            if (mapping.containsKey(type)) {
                mapping[type]?.add(cached)
            }
        }

        val pages = mutableListOf<Page>()
        
        // Load Downloaded Cards (Index 0)
        val downloadedCards = getDownloadedCards()
        pages.add(downloadedCards)

        for (read in currentCategories) {
            val unsorted = mapping[read.id] ?: arrayListOf()
            val sorted = sortNormalArray(ArrayList(unsorted))
            
            pages.add(
                Page(
                    title = if (read.isSystem && read.stringRes != null) context?.getString(read.stringRes) ?: read.name else read.name,
                    unsortedItems = unsorted,
                    items = sorted,
                    hash = read.id.hashCode() * 31 + sorted.hashCode()
                )
            )
        }
        _pages.postValue(pages)
    }

    private suspend fun getDownloadedCards(): Page = cardsDataMutex.withLock {
        // Filter: Only include in "Downloads" tab (index 0) if it has a download status, is imported, or is active
        val unsorted = ArrayList(cardsData.values.filter { card ->
            card.state != DownloadState.Nothing || 
            card.apiName == com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE || 
            card.apiName == com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
        })
        val sorted = sortArray(ArrayList(unsorted))
        Page(
            title = com.lagradost.quicknovel.ui.ReadType.NONE.name,
            unsortedItems = unsorted,
            items = sorted,
            hash = com.lagradost.quicknovel.ui.ReadType.NONE.prefValue.hashCode() * 31 + sorted.hashCode()
        )
    }


    private suspend fun postCards() {
        val currentPages = _pages.value
        if (currentPages == null) {
            // If they haven't been loaded yet, trigger a full load
            // This prevents the "silent fail" when postCards() is called before loadAllData()
            loadAllData(false)
            return
        }
        val list = CopyOnWriteArrayList(currentPages)
        if (list.isEmpty()) {
            list.add(getDownloadedCards())
        } else {
            list[0] = getDownloadedCards()
        }
        _pages.postValue(list)
    }

    init {
        BookDownloader2.downloadDataChanged += ::progressDataChanged
        BookDownloader2.downloadProgressChanged += ::progressChanged
        BookDownloader2.downloadDataRefreshed += ::downloadDataRefreshed
        BookDownloader2.downloadRemoved += ::downloadRemoved
        //BookDownloader2.readingProgressChanged += :: readingProgressChanged
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader2.downloadProgressChanged -= ::progressChanged
        BookDownloader2.downloadDataChanged -= ::progressDataChanged
        BookDownloader2.downloadDataRefreshed -= ::downloadDataRefreshed
        BookDownloader2.downloadRemoved -= ::downloadRemoved
        //BookDownloader2.readingProgressChanged -= :: readingProgressChanged
    }

    val activeRefreshTabs = mutableSetOf<Int>()
    val isRefreshing = MutableLiveData(false)
    private val _refresh = MutableSharedFlow<Int>(
        extraBufferCapacity = 32
    )
    val refresh = _refresh.asSharedFlow()
    fun setIsLoading(isActive: Boolean, currentTab: Int){
        isRefreshing.postValue(isActive)
        synchronized(activeRefreshTabs){
            if(isActive && !activeRefreshTabs.contains(currentTab))
                activeRefreshTabs.add(currentTab)
            else{
                _refresh.tryEmit(currentTab)
                activeRefreshTabs.remove(currentTab)
            }
        }
    }



    private fun progressChanged(data: Pair<Int, DownloadProgressState>) =
        viewModelScope.launchSafe {
            cardsDataMutex.withLock {
                val (id, state) = data
                val newState = state.eta(context ?: return@launchSafe)
                val current = cardsData[id] ?: return@withLock // Only update if we already know about this card
                
                cardsData[id] = current.copy(
                    downloadedCount = state.progress,
                    downloadedTotal = state.total,
                    state = state.state,
                    ETA = newState,
                    // Occasionally refresh readCount too, though it mostly changes on readEpub
                    readCount = if (state.progress % 10 == 0L) getKey<Int>(DOWNLOAD_EPUB_SIZE, id.toString()) ?: current.readCount else current.readCount
                )
            }
            postCards()
        }

    private fun downloadRemoved(id: Int) = viewModelScope.launchSafe {
        cardsDataMutex.withLock {
            cardsData -= id
        }
        postCards()
    }

    private fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) =
        viewModelScope.launchSafe {
            cardsDataMutex.withLock {
                val (id, value) = data
                cardsData[id] = cardsData[id]?.copy(
                    source = value.source,
                    name = value.name,
                    author = value.author,
                    posterUrl = value.posterUrl,
                    rating = value.rating,
                    peopleVoted = value.peopleVoted,
                    views = value.views,
                    synopsis = value.synopsis,
                    tags = value.tags,
                    apiName = value.apiName,
                    lastUpdated = value.lastUpdated,
                    lastDownloaded = value.lastDownloaded
                ) ?: run {
                    DownloadFragment.DownloadDataLoaded(
                        source = value.source,
                        name = value.name,
                        author = value.author,
                        posterUrl = value.posterUrl,
                        rating = value.rating,
                        peopleVoted = value.peopleVoted,
                        views = value.views,
                        synopsis = value.synopsis,
                        tags = value.tags,
                        apiName = value.apiName,
                        downloadedCount = 0,
                        downloadedTotal = 0,
                        ETA = "",
                        state = DownloadState.Nothing,
                        id = id,
                        generating = false,
                        lastUpdated = value.lastUpdated,
                        lastDownloaded = value.lastDownloaded,
                        readCount = getKey(DOWNLOAD_EPUB_SIZE, id.toString()) ?: 0,
                    )
                }
            }
            postCards()
        }

    suspend fun fetchAllData(postCard: Boolean) {
        downloadInfoMutex.withLock {
            cardsDataMutex.withLock {
                BookDownloader2.downloadData.map { (key, value) ->
                    val info = downloadProgress[key] ?: return@map
                    cardsData[key] = DownloadFragment.DownloadDataLoaded(
                        source = value.source,
                        name = value.name,
                        author = value.author,
                        posterUrl = value.posterUrl,
                        rating = value.rating,
                        peopleVoted = value.peopleVoted,
                        views = value.views,
                        synopsis = value.synopsis,
                        tags = value.tags,
                        apiName = value.apiName,
                        downloadedCount = info.progress,
                        downloadedTotal = info.total,
                        ETA = context?.let { ctx -> info.eta(ctx) } ?: "",
                        state = info.state,
                        id = key,
                        generating = false,
                        lastUpdated = value.lastUpdated,
                        lastDownloaded = value.lastDownloaded,
                        readCount = getKey(DOWNLOAD_EPUB_SIZE, key.toString()) ?: 0,
                    )
                }
            }
            if (postCard) postCards()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun downloadDataRefreshed(_id: Int) = viewModelScope.launchSafe {
        fetchAllData(true)
    }
}
