package com.lagradost.quicknovel.ui.result

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import kotlinx.coroutines.Job
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.downloadProgress
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.PreferenceDelegate
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.RESULT_CHAPTER_FILTER_BOOKMARKED
import com.lagradost.quicknovel.RESULT_CHAPTER_FILTER_DOWNLOADED
import com.lagradost.quicknovel.RESULT_CHAPTER_FILTER_READ
import com.lagradost.quicknovel.RESULT_CHAPTER_FILTER_UNREAD
import com.lagradost.quicknovel.RESULT_CHAPTER_SORT
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.download.CHAPTER_SORT
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.download.LAST_ACCES_SORT
import com.lagradost.quicknovel.ui.download.LAST_UPDATED_SORT
import com.lagradost.quicknovel.ui.download.REVERSE_CHAPTER_SORT
import com.lagradost.quicknovel.ui.download.REVERSE_LAST_ACCES_SORT
import com.lagradost.quicknovel.ui.download.REVERSE_LAST_UPDATED_SORT
import com.lagradost.quicknovel.ui.download.SortingMethod
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.RESULT_CHAPTER_BOOKMARK
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections


class ResultViewModel : ViewModel() {
    companion object {
        val chapterSortingMethods = arrayOf(
            SortingMethod(R.string.chapter_sort, CHAPTER_SORT, REVERSE_CHAPTER_SORT),
            SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
        )
        var sortChapterBy by PreferenceDelegate(RESULT_CHAPTER_SORT, CHAPTER_SORT, Int::class)

        var filterChapterByDownloads by PreferenceDelegate(
            RESULT_CHAPTER_FILTER_DOWNLOADED,
            false,
            Boolean::class
        )
        var filterChapterByBookmarked by PreferenceDelegate(
            RESULT_CHAPTER_FILTER_BOOKMARKED,
            false,
            Boolean::class
        )
        var filterChapterByRead by PreferenceDelegate(
            RESULT_CHAPTER_FILTER_READ,
            true,
            Boolean::class
        )
        var filterChapterByUnread by PreferenceDelegate(
            RESULT_CHAPTER_FILTER_UNREAD,
            true,
            Boolean::class
        )
    }

    val selectedChapters = MutableLiveData<Set<String>>(emptySet())
    val isInSelectionMode = MutableLiveData<Boolean>(false)
    val isBatchDownloading = MutableLiveData<Boolean>(false)

    fun toggleSelection(url: String) {
        val current = selectedChapters.value ?: emptySet()
        if (current.contains(url)) {
            selectedChapters.postValue(current - url)
        } else {
            selectedChapters.postValue(current + url)
        }
    }

    fun selectRange(urls: List<String>) {
        val current = selectedChapters.value ?: emptySet()
        selectedChapters.postValue(current + urls)
    }

    fun selectAll() {
        val streamRes = (loadResponse.value as? Resource.Success)?.value as? StreamResponse ?: return
        selectedChapters.postValue(streamRes.data.map { it.url }.toSet())
    }

    fun clearSelection() {
        selectedChapters.postValue(emptySet())
    }

    fun setSelectionMode(enabled: Boolean) {
        if (!enabled) clearSelection()
        isInSelectionMode.postValue(enabled)
    }

    fun isChapterBookmarked(chapter: ChapterData): Boolean {
        return getKey(RESULT_CHAPTER_BOOKMARK, chapter.url, false) ?: false
    }

    fun setChapterBookmark(chapter: ChapterData, bookmark: Boolean) {
        if (bookmark) {
            setKey(RESULT_CHAPTER_BOOKMARK, chapter.url, true)
        } else {
            removeKey(RESULT_CHAPTER_BOOKMARK, chapter.url)
        }
    }

    fun toggleChapterBookmark(chapter: ChapterData) {
        setChapterBookmark(chapter, !isChapterBookmarked(chapter))
    }

    fun executeBatchMarkRead(read: Boolean) {
        val selected = selectedChapters.value ?: return
        val streamRes = (loadResponse.value as? Resource.Success)?.value as? StreamResponse ?: return
        val ctx = context ?: return
        val editor = com.lagradost.quicknovel.DataStore.editor(ctx)
        val timeToSet = System.currentTimeMillis()
        streamRes.data.filter { selected.contains(it.url) }.forEach { chapterData ->
            val index = chapterIndex(chapterData) ?: return@forEach
            val path = com.lagradost.quicknovel.DataStore.getFolderName(EPUB_CURRENT_POSITION_READ_AT, "${streamRes.name}/$index")
            if (read) {
                editor.setKey(path, timeToSet)
            } else {
                editor.removeKey(path)
            }
        }
        editor.apply()
        chapters.postValue(orderChapters(streamRes.data))
        setSelectionMode(false)
    }

    fun executeBatchBookmark(bookmark: Boolean) {
        val selected = selectedChapters.value ?: return
        val streamRes = (loadResponse.value as? Resource.Success)?.value as? StreamResponse ?: return
        val ctx = context ?: return
        val editor = com.lagradost.quicknovel.DataStore.editor(ctx)
        streamRes.data.filter { selected.contains(it.url) }.forEach { chapterData ->
            val path = com.lagradost.quicknovel.DataStore.getFolderName(RESULT_CHAPTER_BOOKMARK, chapterData.url)
            if (bookmark) {
                editor.setKey(path, true)
            } else {
                editor.removeKey(path)
            }
        }
        editor.apply()
        chapters.postValue(orderChapters(streamRes.data))
        setSelectionMode(false)
    }

    fun executeBatchDownload() {
        val selected = selectedChapters.value ?: return
        val streamRes = (loadResponse.value as? Resource.Success)?.value as? StreamResponse ?: return
        val indices = streamRes.data.mapIndexedNotNull { index: Int, chapter: ChapterData ->
            if (selected.contains(chapter.url)) index else null
        }

        if (indices.isEmpty()) return

        isBatchDownloading.postValue(true)
        viewModelScope.launchSafe {
            val res = loadResponse.value
            if (res is Resource.Success && res.value is StreamResponse) {
                // We'll use a new batch download method in BookDownloader2
                BookDownloader2.download(res.value, context ?: return@launchSafe, indices)
            }
            isBatchDownloading.postValue(false)
            setSelectionMode(false)
        }
    }

    fun reorderChapters() {
        when (val response = this.loadResponse.value) {
            is Resource.Success -> {
                reorderChapters(response.value)
            }

            else -> {}
        }
    }

    fun reorderChapters(response: LoadResponse) {
        when (response) {
            is StreamResponse -> {
                chapters.postValue(orderChapters(response.data))
            }

            else -> chapters.postValue(null)
        }
    }


    private fun orderChapters(list: List<ChapterData>): List<ChapterData> {
        val filterRead = filterChapterByRead
        val filterUnread = filterChapterByUnread
        // val filterBookmarked = filterChapterByBookmarked
        val filterDownloaded = filterChapterByDownloads
        val sort = sortChapterBy
        val state = downloadState.value

        return list.filter { chapter ->
            val read = hasReadChapter(chapter)

            (filterUnread && !read) || (filterRead && read) ||
                    (filterDownloaded && (state != null && state.progress > (chapterIndex(chapter)
                        ?: Int.MAX_VALUE)))
        }.sortedBy { chapter ->
            return@sortedBy when (sort) {
                CHAPTER_SORT -> {
                    chapterIndex(chapter)?.toLong()
                }

                REVERSE_CHAPTER_SORT -> {
                    chapterIndex(chapter)?.toLong()?.unaryMinus()
                }

                LAST_ACCES_SORT -> {
                    getChapterReadTime(chapter) ?: Long.MAX_VALUE
                }

                REVERSE_LAST_ACCES_SORT -> {
                    -(getChapterReadTime(chapter) ?: Long.MAX_VALUE)
                }

                else -> null
            }
        }
    }

    fun clear() {
        loadResponse.postValue(null)
        chapters.postValue(null)
    }

    fun hasReadChapter(chapter: ChapterData): Boolean {
        return getChapterReadTime(chapter) != null
    }

    fun getChapterReadTime(chapter: ChapterData): Long? {
        val streamResponse =
            (load as? StreamResponse) ?: return null
        val index = chapterIndex(chapter) ?: return null
        return getKey<Long>(
            EPUB_CURRENT_POSITION_READ_AT,
            "${streamResponse.name}/$index"
        )
    }

    fun setReadChapter(chapter: ChapterData, value: Boolean): Boolean {
        val streamResponse =
            (load as? StreamResponse) ?: return false
        val index = chapterIndex(chapter) ?: return false

        if (value) {
            setKey(
                EPUB_CURRENT_POSITION_READ_AT,
                "${streamResponse.name}/$index",
                System.currentTimeMillis()
            )
        } else {
            removeKey(
                EPUB_CURRENT_POSITION_READ_AT,
                "${streamResponse.name}/$index",
            )
        }

        return true
    }

    var repo: APIRepository? = null

    var isGetLoaded = false

    var id: MutableLiveData<Int> = MutableLiveData<Int>(-1)
    var readState: MutableLiveData<ReadType> = MutableLiveData<ReadType>(ReadType.NONE)
    val duplicateBookmarkState = MutableLiveData<Int?>(null)

    var apiName : String = ""

    /*This is to detect whether it actually returned to
     the fragment after reading a chapter, and thus update the list of chapters.*/
    var isResume = false

    val currentTabIndex: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    val currentTabPosition: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    private val loadMutex = Mutex()
    private lateinit var load: LoadResponse
    internal var loadId: Int = 0
    private var loadUrl: String = ""
    var hasLoaded: Boolean = false
    val userNote: MutableLiveData<String?> = MutableLiveData(null)
    val isSyncEnabledDisplay: MutableLiveData<Boolean> = MutableLiveData(false)


    val loadResponse: MutableLiveData<Resource<LoadResponse>?> =
        MutableLiveData<Resource<LoadResponse>?>()

    val chapters: MutableLiveData<List<ChapterData>?> =
        MutableLiveData<List<ChapterData>?>()

    val reviews: MutableLiveData<Resource<ArrayList<UserReview>>> by lazy {
        MutableLiveData<Resource<ArrayList<UserReview>>>()
    }
    private var currentReviews: ArrayList<UserReview> = arrayListOf()

    private val reviewPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    private val loadMoreReviewsMutex = Mutex()
    private fun loadMoreReviews(url: String) {
        viewModelScope.launch {
            if (loadMoreReviewsMutex.isLocked) return@launch
            val api = repo ?: return@launch
            loadMoreReviewsMutex.withLock {
                val loadPage = (reviewPage.value ?: 0) + 1
                if (loadPage == 1) {
                    reviews.postValue(Resource.Loading())
                }
                when (val data = api.loadReviews(url, loadPage, false)) {
                    is Resource.Success -> {
                        val moreReviews = data.value
                        currentReviews.addAll(moreReviews)

                        reviews.postValue(Resource.Success(currentReviews))
                        reviewPage.postValue(loadPage)
                    }

                    else -> {}
                }
            }
        }
    }

    fun openInBrowser() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (loadUrl.isBlank()) return@launchSafe
            val i = Intent(Intent.ACTION_VIEW)
            i.data = loadUrl.toUri()
            activity?.startActivity(i)
        }
    }

    fun switchTab(index: Int?, position: Int?) {
        val newPos = index ?: return
        val tabId = position ?: return
        currentTabPosition.postValue(tabId)
        currentTabIndex.postValue(newPos)
        if (tabId == 1 && currentReviews.isEmpty()) {
            loadMoreReviews(verify = false)
        }
        if (tabId == 3) {
            reorderChapters()
        } else {
            // chapters.postValue(null)
        }
    }

    fun readEpub() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            addToHistory()
            val downloadedCount = com.lagradost.quicknovel.BaseApplication.getKey<Int>(com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE, loadId.toString(), 0) ?: 0
            BookDownloader2.readEpub(
                loadId,
                downloadState.value?.progress?.toInt() ?: downloadedCount,
                load.author,
                load.name,
                apiName,
                load.synopsis
            )
        }
    }

    private var cachedChapters: HashMap<ChapterData, Int> = hashMapOf()

    fun chapterIndex(chapter: ChapterData): Int? {
        return cachedChapters[chapter]
    }

    private fun reCacheChapters() {
        val streamResponse = (load as? StreamResponse)
        if (streamResponse == null) {
            cachedChapters = hashMapOf()
            return
        }
        val out = hashMapOf<ChapterData, Int>()
        streamResponse.data.mapIndexed { index, chapterData ->
            out[chapterData] = index
        }
        cachedChapters = out
    }

    fun streamRead(chapter: ChapterData? = null) = ioSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@ioSafe
            addToHistory()

            chapter?.let {
                // TODO BETTER STORE
                val streamResponse =
                    ((loadResponse.value as? Resource.Success)?.value as? StreamResponse)
                        ?: return@let
                val index = chapterIndex(chapter)
                if (index != null && index >= 0) {
                    setReadChapter(chapter, true)
                    setKey(EPUB_CURRENT_POSITION, streamResponse.name, index)
                    setKey(
                        EPUB_CURRENT_POSITION_CHAPTER,
                        streamResponse.name,
                        streamResponse.data[index].name
                    )
                    setKey(
                        EPUB_CURRENT_POSITION_SCROLL_CHAR, streamResponse.name, 0,
                    )
                }
            }

            BookDownloader2.stream(load, apiName)
        }
    }

    /** paused => resume,
     *  downloading => pause,
     *  done / pending => nothing,
     *  else => download
     * */
    fun downloadOrPause() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe

            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsDownloading -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Pause
                        )

                        DownloadState.IsDone, DownloadState.IsPending -> {

                        }

                        else -> BookDownloader2.download(load, context ?: return@launchSafe)
                    }
                } ?: run {
                    BookDownloader2.download(load, context ?: return@launchSafe)
                }
            }
        }
    }

    fun pause() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe

            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsDownloading -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Pause
                        )

                        else -> {

                        }
                    }
                }
            }
        }
    }

    fun stop() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe

            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.Nothing, DownloadState.IsDone, DownloadState.IsStopped, DownloadState.IsFailed -> {

                        }

                        else -> {
                            BookDownloader2.addPendingAction(
                                loadId,
                                DownloadActionType.Stop
                            )
                        }
                    }
                }
            }
        }
    }

    fun downloadFrom(start: Int?) = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            val api = repo ?: return@launchSafe
            BookDownloader2.downloadInfoMutex.withLock {
                BookDownloader2.changeDownloadStart(load, api, start)
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsPending -> {

                        }

                        // DownloadState.IsDone
                        else -> BookDownloader2.download(load, context ?: return@launchSafe)
                    }
                } ?: run {
                    BookDownloader2.download(load, context ?: return@launchSafe)
                }
            }
        }
    }

    fun download() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsDone, DownloadState.IsPending -> {

                        }

                        else -> BookDownloader2.download(load, context ?: return@launchSafe)
                    }
                } ?: run {
                    BookDownloader2.download(load, context ?: return@launchSafe)
                }
            }
        }
    }


    private fun addToHistory() = viewModelScope.launchSafe {
        // we wont add it to history from cache
        if (!isGetLoaded) return@launchSafe
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            setKey(
                HISTORY_FOLDER, loadId.toString(), ResultCached(
                    loadUrl,
                    load.name,
                    apiName,
                    loadId,
                    load.author,
                    load.posterUrl,
                    load.tags,
                    load.rating,
                    (load as? StreamResponse)?.data?.size ?: 1,
                    System.currentTimeMillis(),
                    synopsis = load.synopsis
                )
            )
        }
    }

// requireContext().setKey(DOWNLOAD_TOTAL, localId.toString(), res.data .size)
// loadReviews()

    fun isInReviews(): Boolean {
        return currentTabIndex.value == 1
    }

    fun deleteAlert() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val act = activity ?: return@launch
            val builder: AlertDialog.Builder = AlertDialog.Builder(act)
            builder.setMessage(act.getString(R.string.permanently_delete_format).format(load.name))
                .setTitle(R.string.delete)
                .setPositiveButton(R.string.delete, dialogClickListener)
                .setNegativeButton(R.string.cancel, dialogClickListener)
                .show()
        }
    }

    fun delete() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            BookDownloader2.deleteNovel(load.author, load.name, apiName)
        }
    }

    private fun updateBookmarkData() {
        // dont update data if preview because that data is from cache
        if (!isGetLoaded && getKey<ResultCached>(RESULT_BOOKMARK, loadId.toString()) != null) {
            return
        }
        val totalChapters = (load as? StreamResponse)?.data?.size ?: 1
        val currentCached = getKey<ResultCached>(RESULT_BOOKMARK, loadId.toString())
        val isSyncEnabled = currentCached?.isSyncEnabled ?: false
        isSyncEnabledDisplay.postValue(isSyncEnabled)

        setKey(
            RESULT_BOOKMARK, loadId.toString(), ResultCached(
                loadUrl,
                load.name,
                apiName,
                loadId,
                load.author,
                load.posterUrl,
                load.tags,
                load.rating,
                totalChapters,
                System.currentTimeMillis(),
                synopsis = load.synopsis,
                isSyncEnabled = isSyncEnabled
            )
        )
    }

    fun getNote(): String? = userNote.value

    private var updateNoteJob: Job? = null
    fun updateNote(note: String?) {
        userNote.postValue(note)
        viewModelScope.launch {
            if (note.isNullOrBlank()) {
                removeKey("RESULT_USER_NOTE", loadId.toString())
            } else {
                setKey("RESULT_USER_NOTE", loadId.toString(), NoteWrapper(note))
            }
            loadMutex.withLock {
                if (!hasLoaded) return@launch
                updateBookmarkData()
                checkDuplicates()
                addToHistory()
            }
        }
    }
    private fun String.normalize(): String {
        return this.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
    }

    private fun findDuplicateState(name: String, author: String?): Int? {
        val cleanName = name.normalize()
        if (cleanName.isEmpty()) return null
        val cleanAuthor = author?.normalize()

        val bookmarkedKeys = getKeys(RESULT_BOOKMARK) ?: return null
        for (key in bookmarkedKeys) {
            val idStr = key.substringAfter("/")
            if (idStr == loadId.toString()) continue // Skip current provider

            val cached = getKey<ResultCached>(RESULT_BOOKMARK, idStr)
            if (cached != null) {
                val cachedName = cached.name.normalize()
                val cachedAuthor = cached.author?.normalize()

                if (cachedName == cleanName && (cleanAuthor.isNullOrEmpty() || cachedAuthor == cleanAuthor)) {
                    val state = getKey<Int>(RESULT_BOOKMARK_STATE, idStr) ?: -1
                    if (state != -1) return state
                }
            }
        }
        return null
    }

    private fun checkDuplicates() {
        val novel = (loadResponse.value as? Resource.Success)?.value ?: return
        duplicateBookmarkState.postValue(findDuplicateState(novel.name, novel.author))
    }

    fun bookmark(state: Int) = viewModelScope.launch {
        if (state != -1) { // -1 is Unbookmark
            // 1. Check current ID (Standard flow)
            val currentState = getKey<Int>(folder = RESULT_BOOKMARK_STATE, path = loadId.toString()) ?: -1
            if (currentState != -1 && currentState != state) {
                showToast(R.string.already_in_library)
            }

            // 2. Synchronous robust duplicate check (Cross-provider)
            val novel = (loadResponse.value as? Resource.Success)?.value
            if (novel != null) {
                val duplicate = findDuplicateState(novel.name, novel.author)
                if (duplicate != null && currentState == -1) {
                    showToast(R.string.already_in_library)
                    // Trigger UI to show where it is
                    duplicateBookmarkState.postValue(duplicate)
                    return@launch
                }
            }
        }

        loadMutex.withLock {
            if (!hasLoaded) return@launch
            setKey(
                RESULT_BOOKMARK_STATE, loadId.toString(), state
            )
            updateBookmarkData()
            readState.postValue(ReadType.fromSpinner(state))

            // SSOT: Sync with Room Database
            val context = context ?: return@withLock
            ioSafe {
                val dao = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context).novelDao()
                if (state == -1) {
                    dao.updateBookmarkType(loadId, null)
                } else {
                    val existing = dao.getById(loadId)
                    if (existing != null) {
                        dao.updateBookmarkType(loadId, state)
                    } else {
                        dao.insert(
                            com.lagradost.quicknovel.db.NovelEntity(
                                id = loadId,
                                source = loadUrl,
                                name = load.name,
                                author = load.author,
                                posterUrl = load.posterUrl,
                                rating = load.rating,
                                peopleVoted = (load as? StreamResponse)?.peopleVoted,
                                views = (load as? StreamResponse)?.views,
                                synopsis = load.synopsis,
                                tags = load.tags,
                                apiName = apiName,
                                lastUpdated = null,
                                lastDownloaded = null,
                                bookmarkType = state
                            )
                        )
                    }
                }
            }

            com.lagradost.quicknovel.ui.download.DownloadViewModel.bookmarkChanged.emit(Unit)
        }
    }

    fun toggleSyncEnabled() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            val currentCached = getKey<ResultCached>(RESULT_BOOKMARK, loadId.toString()) ?: return@launch
            val isSyncEnabled = !currentCached.isSyncEnabled
            isSyncEnabledDisplay.postValue(isSyncEnabled)
            setKey(
                RESULT_BOOKMARK, loadId.toString(), currentCached.copy(isSyncEnabled = isSyncEnabled)
            )
        }
    }

    fun share() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, load.name)
            i.putExtra(Intent.EXTRA_TEXT, loadUrl)
            activity?.startActivity(Intent.createChooser(i, load.name))
        }
    }

    fun loadMoreReviews(verify: Boolean = true) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            if (verify && currentTabIndex.value == 0) return@launch
            loadMoreReviews(loadUrl)
        }
    }

    init {
        // BookDownloader2.downloadDataChanged += ::progressDataChanged
        BookDownloader2.downloadProgressChanged += ::progressChanged
        BookDownloader2.downloadRemoved += ::downloadRemoved
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader2.downloadProgressChanged -= ::progressChanged
        //BookDownloader2.downloadDataChanged -= ::progressDataChanged
        BookDownloader2.downloadRemoved -= ::downloadRemoved
    }

    val downloadState: MutableLiveData<DownloadProgressState> by lazy {
        MutableLiveData<DownloadProgressState>(null)
    }
    private var downloadStateValue: DownloadProgressState? = null

    fun setDownloadState(state: DownloadProgressState) {
        downloadStateValue = state
        downloadState.postValue(state)
    }

    private fun progressChanged(data: Pair<Int, DownloadProgressState>) =
        viewModelScope.launch {
            val (id, state) = data
            loadMutex.withLock {
                if (!hasLoaded || id != loadId) return@launch
                setDownloadState(state)
            }
        }

    /*fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) =
        viewModelScope.launch {
            val (id, downloadData) = data
            loadMutex.withLock {
                if (!hasLoaded || id != loadId) return@launch

            }
        }*/

    private fun downloadRemoved(id: Int) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded || id != loadId) return@launch
            insertZeroData()
        }
    }

    private fun insertZeroData() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            BookDownloader2.downloadInfoMutex.withLock {
                val current = downloadProgress[loadId]
                if (current != null) {
                    setDownloadState(current)
                } else {
                    BookDownloader2Helper.downloadInfo(
                        context,
                        load.author,
                        load.name,
                        load.apiName
                    )?.let { info ->
                        val new = DownloadProgressState(
                            state = DownloadState.Nothing,
                            progress = info.progress,
                            total = info.total,
                            downloaded = info.downloaded,
                            lastUpdatedMs = System.currentTimeMillis(),
                            etaMs = null
                        )
                        downloadProgress[loadId] = new
                        setDownloadState(new)
                    } ?: run {
                        val new = DownloadProgressState(
                            state = DownloadState.Nothing,
                            progress = 0,
                            total = (load as? StreamResponse)?.data?.size?.toLong() ?: 1,
                            downloaded = 0,
                            lastUpdatedMs = System.currentTimeMillis(),
                            etaMs = null
                        )
                        setDownloadState(new)
                    }
                }
            }
        }
    }

    fun initState(card: ResultCached) = viewModelScope.launch {
        isGetLoaded = false
        loadMutex.withLock {
            this@ResultViewModel.apiName = card.apiName
            repo = Apis.getApiFromNameOrNull(card.apiName)
            loadUrl = card.source

            val data = StreamResponse(
                url = card.source,
                name = card.name,
                data = listOf(),
                author = card.author,
                posterUrl = card.poster,
                rating = card.rating,
                synopsis = card.synopsis,
                tags = card.tags,
                apiName = card.apiName
            )

            load = data
            loadResponse.postValue(Resource.Success(data))
            setState(card.id)
        }
    }

    private fun setState(tid: Int) {
        loadId = tid
        id.postValue(tid)

        readState.postValue(
            ReadType.fromSpinner(
                getKey(
                    RESULT_BOOKMARK_STATE, tid.toString()
                )
            )
        )

        setKey(
            DOWNLOAD_EPUB_LAST_ACCESS, tid.toString(), System.currentTimeMillis()
        )
        reCacheChapters()

        val savedWrapper = getKey<NoteWrapper>("RESULT_USER_NOTE", tid.toString())
        val note = savedWrapper?.note
        userNote.value = note

        updateBookmarkData()
        checkDuplicates()

        hasLoaded = true

        // insert a download progress if not found
        insertZeroData()
    }

    fun initState(card: DownloadFragment.DownloadDataLoaded) = viewModelScope.launch {
        isGetLoaded = false
        loadResponse.postValue(Resource.Loading(card.source))

        loadMutex.withLock {
            this@ResultViewModel.apiName = card.apiName
            repo = Apis.getApiFromName(card.apiName)
            loadUrl = card.source

            val data = StreamResponse(
                url = card.source,
                name = card.name,
                data = listOf(),
                author = card.author,
                posterUrl = card.posterUrl,
                rating = card.rating,
                synopsis = card.synopsis,
                tags = card.tags,
                apiName = card.apiName
            )
            load = data
            loadResponse.postValue(Resource.Success(data))
            setState(card.id)
        }
    }

    fun initState(apiName: String, url: String) = viewModelScope.launch {
        isGetLoaded = true
        loadResponse.postValue(Resource.Loading(url))

        loadMutex.withLock {
            this@ResultViewModel.apiName = apiName
            repo = Apis.getApiFromNameOrNull(apiName)
            loadUrl = url
        }

        val data = repo?.load(url)
        loadMutex.withLock {
            loadResponse.postValue(data) // Post data FIRST
            when (data) {
                is Resource.Success -> {
                    val res = data.value
                    load = res
                    loadUrl = res.url
                    val tid = generateId(res, apiName)
                    setState(tid) // Now checkDuplicates will see the data
                }
                else -> {}
            }
        }
    }
}

data class NoteWrapper(val note: String? = "")