package com.lagradost.quicknovel

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.Voice
import android.text.Spanned
import android.util.Log
import android.widget.Toast
import androidx.activity.result.launch
import androidx.annotation.WorkerThread
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.text.getSpans
import androidx.core.text.toSpanned
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeyClass
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKeyClass
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.lagradost.quicknovel.util.*
import com.lagradost.quicknovel.BookDownloader2Helper.getQuickChapter
import com.lagradost.quicknovel.CommonActivity.TAG
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.TTSHelper.parseTextToSpans
import com.lagradost.quicknovel.TTSHelper.preParseHtml
import com.lagradost.quicknovel.TTSHelper.ttsParseText
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.letInner
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.map
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.mvvm.safeAsync
import com.lagradost.quicknovel.mvvm.throwableToResource
import com.lagradost.quicknovel.ui.OrientationType
import com.lagradost.quicknovel.ui.ReadingType
import com.lagradost.quicknovel.ui.ScrollIndex
import com.lagradost.quicknovel.ui.ScrollVisibilityIndex
import com.lagradost.quicknovel.ui.UiText
import com.lagradost.quicknovel.ui.toScroll
import com.lagradost.quicknovel.ui.toUiText
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.CoilImagesPlugin
import com.lagradost.quicknovel.util.CoilImagesPlugin.CoilStore
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.runOnMainThread
import com.lagradost.safefile.closeQuietly
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageSizeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.ag2s.epublib.domain.EpubBook
import com.lagradost.quicknovel.util.TranslationEngineType
import com.lagradost.quicknovel.util.TranslationRequest
import com.lagradost.quicknovel.util.TranslationEnginesManager
import me.ag2s.epublib.domain.TOCReference
import me.ag2s.epublib.epub.EpubReader
import me.ag2s.epublib.util.zip.AndroidZipFile
import org.commonmark.node.Node
import org.jsoup.Jsoup
import java.io.File
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.math.pow
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

const val DEF_FONT_SIZE: Int = 14
const val DEF_HORIZONTAL_PAD: Int = 20
const val DEF_VERTICAL_PAD: Int = 10

class PreferenceDelegate<T : Any>(
    val key: String, val default: T, private val klass: KClass<T>
) {
    // simple cache to make it not get the key every time it is accessed, however this requires
    // that ONLY this changes the key
    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>) =
        cache ?: getKeyClass(key, klass.java).also { newCache -> cache = newCache } ?: default

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        cache = t
        if (t == null) {
            removeKey(key)
        } else {
            setKeyClass(key, t)
        }
    }
}

class PreferenceDelegateLiveView<T : Any>(
    val key: String,
    val default: T,
    private val klassK: KClass<T>,
    private val _liveData: MutableLiveData<T>,
    private val onChanged: ((T) -> Unit)? = null
) {
    // simple cache to make it not get the key every time it is accessed, however this requires
    // that ONLY this changes the key
    private var cache: T

    init {
        cache = getKeyClass(key, klassK.java) ?: default
        _liveData.postValue(cache)
    }

    operator fun getValue(self: Any?, property: KProperty<*>) = cache

    operator fun setValue(
        self: Any?,
        property: KProperty<*>,
        t: T?
    ) {
        cache = t ?: default
        _liveData.postValue(cache)
        if (t == null) {
            removeKey(key)
        } else {
            setKeyClass(key, t)
        }
    }
}

abstract class AbstractBook {
    open fun resolveUrl(url: String): String {
        return url
    }

    abstract val canReload: Boolean

    abstract fun size(): Int
    abstract fun title(): String
    abstract fun getChapterTitle(index: Int): UiText
    abstract fun getLoadingStatus(index: Int): String?

    @Throws
    open fun loadImage(image: String): ByteArray? {
        return null
    }

    fun loadImageBitmap(image: String): Bitmap? {
        try {
            val data = this.loadImage(image) ?: return null
            return BitmapFactory.decodeByteArray(data, 0, data.size)
        } catch (t: Throwable) {
            logError(t)
            return null
        }
    }

    @WorkerThread
    @Throws
    abstract suspend fun getChapterData(index: Int, reload: Boolean): String

    abstract fun expand(last: String): Boolean

    @WorkerThread
    @Throws
    protected abstract suspend fun posterBytes(): ByteArray?

    private var poster: Bitmap? = null

    init {
        ioSafe {
            poster = posterBytes()?.let { byteArray ->
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            }
        }
    }

    fun poster(): Bitmap? {
        return poster
    }

    abstract fun author(): String?
}

class QuickBook(val data: QuickStreamData) : AbstractBook() {
    override fun resolveUrl(url: String): String {
        return Apis.getApiFromNameNull(data.meta.apiName)?.fixUrl(url) ?: url
    }

    override fun author(): String? {
        return data.meta.author
    }

    override val canReload = true

    override fun size(): Int {
        return data.data.size
    }

    override fun title(): String {
        return data.meta.name
    }

    override fun getChapterTitle(index: Int): UiText {
        return data.data[index].name.toUiText()
    }

    override fun getLoadingStatus(index: Int): String {
        return data.data[index].url
    }

    override suspend fun getChapterData(index: Int, reload: Boolean): String {
        val ctx = context ?: throw ErrorLoadingException("Invalid context")
        return ctx.getQuickChapter(
            data.meta,
            data.data[index],
            index,
            reload
        )?.html ?: throw ErrorLoadingException("Error loading chapter")
    }

    override fun expand(last: String): Boolean {
        try {
            val elements =
                Jsoup.parse(last).allElements.filterNotNull()

            for (element in elements) {
                val href = element.attr("href") ?: continue

                val text =
                    element.ownText().replace(Regex("[\\[\\]().,|{}<>]"), "").trim()
                if (text.equals("next", true) || text.equals(
                        "next chapter",
                        true
                    ) || text.equals("next part", true)
                ) {
                    val name = "Next"
                    data.data.add(ChapterData(name, href, null, null))
                    return true
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
        return false
    }

    override suspend fun posterBytes(): ByteArray? {
        val poster = data.poster
        if (poster != null) {
            try {
                return MainActivity.app.get(poster).okhttpResponse.body.bytes()
            } catch (t: Throwable) {
                logError(t)
            }
        }
        return null
    }
}

class RegularBook(val data: EpubBook) : AbstractBook() {
    init {
        var refs = mutableListOf<TOCReference>()
        data.tableOfContents.tocReferences.forEach { ref ->
            refs.add(ref)
            if (ref.children != null) {
                refs.addAll(ref.children)
            }
        }

        if (refs.size <= 1) {
            val newRefs = mutableListOf<TOCReference>()
            data.spine.spineReferences.forEachIndexed { index, spineRef ->
                if (spineRef.isLinear) {
                    val res = spineRef.resource
                    newRefs.add(TOCReference(res.title ?: "Chapter ${index + 1}", res))
                }
            }
            if (newRefs.isNotEmpty()) {
                refs = newRefs
            }
        }
        data.tableOfContents.tocReferences = refs
    }

    override val canReload = false
    override fun author(): String? {
        val mainAuthor = data.metadata.authors.firstOrNull() ?: return null
        val firstName = mainAuthor.firstname
        val lastName = mainAuthor.lastname ?: return firstName
        if (firstName == null) {
            return lastName
        }
        return "$firstName $lastName"
    }

    override fun loadImage(image: String): ByteArray? {
        return data.resources.resourceMap.values.find { x ->
            x.mediaType.name.contains("image") && image.endsWith(
                x.href
            )
        }?.data
    }

    override fun size(): Int {
        return data.tableOfContents.tocReferences.size
    }

    override fun title(): String {
        return data.title
    }

    override fun getChapterTitle(index: Int): UiText {
        return data.tableOfContents.tocReferences?.get(index)?.title?.toUiText()
            ?: txt(R.string.chapter_format, (index + 1).toString())
    }

    override fun getLoadingStatus(index: Int): String? {
        return null
    }

    override suspend fun getChapterData(index: Int, reload: Boolean): String {
        val start = data.tableOfContents.tocReferences[index].resource
        val startIdx = data.spine.getResourceIndex(start)

        val end = data.tableOfContents.tocReferences.getOrNull(index + 1)?.resource
        var endIdx = data.spine.getResourceIndex(end)
        if (endIdx == -1) {
            endIdx = data.spine.size()
        }
        val builder = StringBuilder()
        for (i in startIdx until endIdx) {
            try//this is for corrupted epubs like from annasarchive
            {
                val ref = data.spine.spineReferences[i]
                // I have no idea, but nonlinear = stop?
                if (!ref.isLinear && i != startIdx) {
                    break
                }
                /*
                    Somewhere in the code, when generating the EPUB of whatever, it changes the root,
                    so it can’t find other resources for some reason. Since the code is already huge,
                    I have no idea where that happens, so I resort to a sketchy fix.
                */
                builder.append(ref.resource.reader.readText().replace(Regex("""src="(?!OEBPS/|http)"""), "src=\"OEBPS/"))
            }
            catch (t: Throwable){
                logError(t)
            }
        }

        return builder.toString()
    }

    override fun expand(last: String): Boolean {
        return false
    }

    override suspend fun posterBytes(): ByteArray? {
        return data.coverImage?.data
    }
}

class MLException(cause: Throwable) : Exception(cause)

data class LiveChapterData(
    val index: Int,
    /** Translated */
    val spans: List<TextSpan>,
    /** Translated */
    val rendered: Spanned,
    /** Non-Translated */
    val originalRendered: Spanned,
    /** Non-Translated */
    val originalSpans: ArrayList<TextSpan>,

    val title: UiText,
    val rawText: String,
    //val ttsLines: List<TTSHelper.TTSLine>
) {
    // tts lines are lazy because not everyone uses tts
    val ttsLines by lazy {
        ttsParseText(rendered.substring(0, rendered.length), index)
    }
}

data class ChapterUpdate(
    val data: ArrayList<SpanDisplay>,
    val seekToDesired: Boolean
)

class ReadActivityViewModel : ViewModel() {
    private var context: Context? = null
    private var loadId: Int = -1
    private var hasInit: Boolean = false
    private var isEpub: Boolean = false
    lateinit var book: AbstractBook
    private lateinit var markwon: Markwon
    private var isInApp: Boolean = true
    private var leftAppAt: ScrollIndex? = null
    private var mlTranslator: Translator? = null
    val isShowingOriginalLive = MutableLiveData<Boolean>(false)
    val isTranslationActiveLive = MutableLiveData<Boolean>(false)
    var isTranslationActive
        get() = isTranslationActiveLive.value ?: false
        set(value) = isTranslationActiveLive.postValue(value)

    fun leftApp() {
        lastChangeIndex?.let { setScrollKeys(it) }
        isInApp = false
        leftAppAt = desiredIndex
    }

    fun resumedApp() {
        val leftAt = leftAppAt
        val resumeAt = desiredIndex

        leftAppAt = null
        isInApp = false

        // if we resume the app and the desired index is different from what we are at currently
        // then we scroll to it
        if (leftAt != null && resumeAt != null && leftAt != resumeAt) {
            scrollToDesired(resumeAt)
        }
    }

    //private lateinit var reducer: MarkwonReducer

    fun canReload(): Boolean {
        return book.canReload
    }


    var mlSettings
        get() = getKey<MLSettings>(EPUB_CURRENT_ML, book.title()) ?: MLSettings("en", "en")
        set(value) = setKey(EPUB_CURRENT_ML, book.title(), value)

    private val _chapterData: MutableLiveData<ChapterUpdate> =
        MutableLiveData<ChapterUpdate>(null)
    val chapter: LiveData<ChapterUpdate> = _chapterData

    private val _loadingStatus = MutableLiveData<Resource<String>>(null)
    val loadingStatus: LiveData<Resource<String>> = _loadingStatus

    private val _translationLoadingStatus = MutableLiveData<Resource<String>>(null)
    val translationLoadingStatus: LiveData<Resource<String>> = _translationLoadingStatus

    private val _aliases = MutableLiveData<Map<String, String>>(emptyMap())
    val aliases: LiveData<Map<String, String>> = _aliases

    private fun loadAliases() {
        val map = getKey<Map<String, String>>(folder = NOVEL_REPLACEMENTS, path = loadId.toString()) ?: emptyMap()
        _aliases.postValue(map)
    }

    fun addAlias(original: String, replacement: String) {
        val current = (_aliases.value ?: emptyMap()).toMutableMap()
        current[original] = replacement
        _aliases.postValue(current)
        setKey(folder = NOVEL_REPLACEMENTS, path = loadId.toString(), value = current)
        // Reload current chapters to apply renames
        reloadChapter(currentIndex)
    }

    fun removeAlias(original: String) {
        val current = (_aliases.value ?: emptyMap()).toMutableMap()
        current.remove(original)
        _aliases.postValue(current)
        setKey(folder = NOVEL_REPLACEMENTS, path = loadId.toString(), value = current)
        // Reload current chapters to apply renames
        reloadChapter(currentIndex)
    }

    private fun applyAliases(text: String): String {
        val map = _aliases.value ?: return text
        if (map.isEmpty()) return text
        
        var result = text
        for ((original, replacement) in map) {
            // Use word boundaries \b to avoid partial matches (e.g. Ash -> Satoshi, but Asher stays Asher)
            val regex = "\\b${Regex.escape(original)}\\b".toRegex(RegexOption.IGNORE_CASE)
            result = regex.replace(result, replacement)
        }
        return result
    }

    fun postLoadingStatus(resource: Resource<String>) {
        _loadingStatus.postValue(resource)
    }

    fun init(id: Int, isEpub: Boolean, context: Context) {
        this.context = context
        this.loadId = id
        this.isEpub = isEpub
        loadAliases()
        
        if (hasInit) return
        hasInit = true
    }

    private val _chaptersTitles: MutableLiveData<List<UiText>> =
        MutableLiveData<List<UiText>>(null)
    val chaptersTitles: LiveData<List<UiText>> = _chaptersTitles

    private val _title: MutableLiveData<String> =
        MutableLiveData<String>(null)
    val title: LiveData<String> = _title

    private val _chapterTile = MutableLiveData<UiText>()
    val chapterTile: LiveData<UiText> get() = _chapterTile

    private var sessionTranslationCancelled = false

    fun stopTranslation() {
        sessionTranslationCancelled = true
        _loadingStatus.postValue(Resource.Failure(null, "Stopped"))
    }

    private val _bottomVisibility: MutableLiveData<Boolean> =
        MutableLiveData<Boolean>(false)
    val bottomVisibility: LiveData<Boolean> = _bottomVisibility

    private val _ttsStatus: MutableLiveData<TTSHelper.TTSStatus> =
        MutableLiveData<TTSHelper.TTSStatus>(TTSHelper.TTSStatus.IsStopped)
    val ttsStatus: LiveData<TTSHelper.TTSStatus> = _ttsStatus

    private val _ttsLine: MutableLiveData<TTSHelper.TTSLine?> =
        MutableLiveData<TTSHelper.TTSLine?>(null)
    val ttsLine: LiveData<TTSHelper.TTSLine?> = _ttsLine


    /*  private val _orientation: MutableLiveData<OrientationType> =
          MutableLiveData<OrientationType>(null)
      val orientation: LiveData<OrientationType> = _orientation

      private val _backgroundColor: MutableLiveData<Int> =
          MutableLiveData<Int>(null)
      val backgroundColor: LiveData<Int> = _backgroundColor

      private val _textColor: MutableLiveData<Int> =
          MutableLiveData<Int>(null)
      val textColor: LiveData<Int> = _textColor

      private val _textSize: MutableLiveData<Int> =
          MutableLiveData<Int>(null)
      val textSize: LiveData<Int> = _textSize*/

    // private val _textFont: MutableLiveData<String> =
    //     MutableLiveData<String>(null)
    // val textFont: LiveData<String> = _textFont

    fun switchVisibility() {
        _bottomVisibility.postValue(!(_bottomVisibility.value ?: false))
    }


    private var chaptersTitlesInternal: ArrayList<UiText> = arrayListOf()

    var desiredIndex: ScrollIndex? = null
    var desiredTTSIndex: ScrollIndex? = null

    private fun updateChapters() {
        for (idx in chaptersTitlesInternal.size until book.size()) {
            chaptersTitlesInternal.add(book.getChapterTitle(idx))
        }
        _chaptersTitles.postValue(chaptersTitlesInternal)
    }

    private val chapterMutex = Mutex()
    private val chapterExpandMutex = Mutex()

    private val requested: HashSet<Int> = hashSetOf()

    private val loading: HashSet<Int> = hashSetOf()
    private val chapterData: HashMap<Int, Resource<LiveChapterData>?> = hashMapOf()
    private val hasExpanded: HashSet<Int> = hashSetOf()

    var currentIndex = Int.MIN_VALUE
        private set

    /** lower padding for preloading current-chapterPaddingBottom*/
    private var chapterPaddingBottom: Int = 1

    /** upper padding, for preloading current+chapterPaddingTop */
    private var chapterPaddingTop: Int = 2

    fun reloadChapter(index: Int) = ioSafe {
        hasExpanded.clear() // will unfuck the rest
        val notify = chapterMutex.withLock {
            chapterData[index] is Resource.Failure
        }
        loadIndividualChapter(index, reload = true, notify = notify)
        updateReadArea(seekToDesired = false)
    }

    fun reTranslateChapter(index: Int) = ioSafe {
        if (!isTranslationActive) return@ioSafe
        hasExpanded.clear() // will unfuck the rest
        val notify = chapterMutex.withLock {
            chapterData[index] is Resource.Failure
        }
        loadIndividualChapter(index, reload = false, reTranslate = true, notify = notify)
        updateReadArea(seekToDesired = false)
    }

    fun reloadChapter() {
        reloadChapter(currentIndex)
    }

    fun refreshChapters() = ioSafe {
        hasExpanded.clear() // will unfuck the rest
        chapterMutex.withLock {
            chapterData.clear()
        }
        _loadingStatus.postValue(Resource.Loading())
        loadIndividualChapter(currentIndex, reload = false, notify = true, postLoading = true)
        updateReadArea(seekToDesired = true)
    }

    private suspend fun updateIndexAsync(
        index: Int,
        notify: Boolean = true,
        postLoading: Boolean = false,
    ) {
        for (idx in index - chapterPaddingBottom..index + chapterPaddingTop) {
            requested += index
            loadIndividualChapter(idx, reload = false, notify = notify, postLoading = postLoading)
        }
    }

    private fun updateIndex(index: Int) {
        var alreadyRequested = false
        for (idx in index - chapterPaddingBottom..index + chapterPaddingTop) {
            if (!requested.contains(index)) {
                alreadyRequested = true
            }
            requested += index
        }

        if (alreadyRequested) return

        ioSafe {
            updateIndexAsync(index)
        }
    }

    private fun chapterIdxToSpanDisplay(index: Int): List<SpanDisplay> {
        return when (val data = chapterData[index]) {
            null -> emptyList()
            is Resource.Loading -> {
                listOf<SpanDisplay>(LoadingSpanned(data.url, index))
            }

            is Resource.Success -> {
                data.value.spans
            }

            is Resource.Failure -> listOf<SpanDisplay>(
                FailedSpanned(
                    reason = data.errorString.toUiText(),
                    index = index,
                    cause = data.cause
                )
            )
        }
    }

    // ChapterLoadSpanned(fromIndex, 0, index, text)
    private fun chapterIdxToSpanDisplayNextButton(index: Int, fromIndex: Int): SpanDisplay? {
        return chapterIdxToSpanDisplayNext(
            index,
            fromIndex
        ) { cIndex, innerIndex, loadIndex, name ->
            ChapterLoadSpanned(cIndex, innerIndex, loadIndex, name)
        }
    }

    private fun chapterIdxToSpanDisplayOverscrollButton(index: Int, fromIndex: Int): SpanDisplay? {
        return chapterIdxToSpanDisplayNext(
            index,
            fromIndex
        ) { cIndex, innerIndex, loadIndex, name ->
            ChapterOverscrollSpanned(cIndex, innerIndex, loadIndex, name)
        }
    }

    private fun chapterIdxToSpanDisplayNext(
        index: Int,
        fromIndex: Int,
        constructor: (Int, Int, Int, UiText) -> SpanDisplay
    ): SpanDisplay? {
        return when (val data = chapterData[index]) {
            is Resource.Loading -> LoadingSpanned(data.url, index)
            is Resource.Failure ->
                FailedSpanned(
                    reason = data.errorString.toUiText(),
                    index = index,
                    cause = data.cause
                )

            else -> chaptersTitlesInternal.getOrNull(index)
                ?.let { text -> constructor(fromIndex, 0, index, text) }
        }
    }

    fun updateReadArea(seekToDesired: Boolean = false) {
        val showOriginal = isShowingOriginalLive.value ?: false
        val cIndex = currentIndex
        val chapters = ArrayList<SpanDisplay>()
        val canReload = this.book.canReload
        
        fun chapterIdxToSpanDisplayToggle(idx: Int): List<SpanDisplay> {
            synchronized(chapterData) {
                return (chapterData[idx]?.letInner { data ->
                    if (showOriginal) {
                        data.originalSpans
                    } else {
                        data.spans
                    }
                } ?: emptyList())
            }
        }

        when (readerType) {
            ReadingType.DEFAULT, ReadingType.INF_SCROLL -> {
                for (idx in cIndex - chapterPaddingBottom..cIndex + chapterPaddingTop) {
                    if (idx < chaptersTitlesInternal.size && idx >= 0)
                        chapters.add(
                            ChapterStartSpanned(
                                idx,
                                0,
                                chaptersTitlesInternal[idx],
                                canReload
                            )
                        )
                    chapters.addAll(chapterIdxToSpanDisplayToggle(idx))
                }
            }

            ReadingType.BTT_SCROLL -> {
                chapterIdxToSpanDisplayNextButton(cIndex - 1, cIndex)?.let {
                    chapters.add(it)
                }

                chaptersTitlesInternal.getOrNull(cIndex)?.let { text ->
                    chapters.add(ChapterStartSpanned(cIndex, 0, text, canReload))
                }

                chapters.addAll(chapterIdxToSpanDisplayToggle(cIndex))

                chapterIdxToSpanDisplayNextButton(cIndex + 1, cIndex)?.let {
                    chapters.add(it)
                }
            }

            ReadingType.OVERSCROLL_SCROLL -> {
                chapterIdxToSpanDisplayOverscrollButton(cIndex - 1, cIndex)?.let {
                    chapters.add(it)
                }

                chaptersTitlesInternal.getOrNull(cIndex)?.let { text ->
                    chapters.add(ChapterStartSpanned(cIndex, 0, text, canReload))
                }

                chapters.addAll(chapterIdxToSpanDisplayToggle(cIndex))

                chapterIdxToSpanDisplayOverscrollButton(cIndex + 1, cIndex)?.let {
                    chapters.add(it)
                }
            }
        }

        _chapterData.postValue(ChapterUpdate(data = chapters, seekToDesired = seekToDesired))
    }

    private fun notifyChapterUpdate(index: Int, seekToDesired: Boolean = false) {
        val cIndex = currentIndex
        if (cIndex - chapterPaddingBottom <= index && index <= cIndex + chapterPaddingTop) {
            updateReadArea(seekToDesired)
        }
    }

    private val markwonMutex = Mutex()

    @WorkerThread
    private suspend fun loadIndividualChapter(
        index: Int,
        reload: Boolean = false,
        notify: Boolean = true,
        reTranslate: Boolean = false,
        postLoading: Boolean = false,
    ) {
        if (index < 0) return

        // set loading and return early if already loading or return cache
        chapterMutex.withLock {
            if (loading.contains(index)) return
            if (!reload && !reTranslate && chapterData.contains(index)) {
                return
            }

            loading += index
            chapterData[index] = Resource.Loading(null)
            if (notify) notifyChapterUpdate(index)
        }

        // we check for out of bounds and if it is out of bounds then try to expand it (Reddit next)
        // we lock it here to prevent duplicate loading when init
        chapterExpandMutex.withLock {
            val preSize = book.size()
            while (index >= book.size()) {
                // will only expand once per session per chapter
                if (hasExpanded.contains(book.size())) break
                hasExpanded += book.size()

                try {
                    // we assume that the text is cached
                    book.expand(book.getChapterData(book.size() - 1, reload = false))
                } catch (t: Throwable) {
                    logError(t)
                }
            }
            if (preSize != book.size()) updateChapters()
        }

        // if we are still out of bounds then return no more chapters
        if (index >= book.size()) {
            chapterMutex.withLock {
                // only push one no more chapters
                if (index == book.size()) {
                    chapterData[index] =
                        Resource.Failure(
                            null,
                            context?.getString(R.string.no_more_chapters) ?: "ERROR"
                        )
                } else {
                    chapterData[index] = null
                }
                loading -= index
                if (notify) notifyChapterUpdate(index)
            }
            return
        }

        // we have verified we are within bounds, then set the loading to the index url
        chapterMutex.withLock {
            chapterData[index] = Resource.Loading(book.getLoadingStatus(index))
            if (notify) notifyChapterUpdate(index)
        }

        // load the data and precalculate everything needed
        try {
            val data = safeApiCall {
                book.getChapterData(index, reload)
            }.map { text ->
                val rawText = applyAliases(preParseHtml(text, authorNotes))
                // val renderedBuilder = SpannableStringBuilder()
                // val lengths : IntArray
                // val nodes : Array<Node>
                val parsed: Node
                var rendered: Spanned
                val originalRendered: Spanned
                val originalSpans: ArrayList<TextSpan>
                var spans: ArrayList<TextSpan>

                markwonMutex.withLock {
                    parsed = markwon.parse(rawText)
                    rendered = markwon.render(parsed)

                    spans = parseTextToSpans(rendered, index)
                    originalSpans = spans
                    originalRendered = rendered

                    val asyncDrawables = rendered.getSpans<AsyncDrawableSpan>()
                    for (async in asyncDrawables) {
                        async.drawable.result =
                            book.loadImageBitmap(async.drawable.destination)?.toDrawable(
                                Resources.getSystem()
                            )
                    }

                    // translation may strip stuff, idk how to solve that in a clean way atm
                    if (isTranslationActive) {
                        translate(
                            rendered,
                            spans
                        ) { (progressChapter, progressInnerIndex, progressInnerTotal) ->
                            val progressText =
                                "${context?.getString(R.string.translating)} ${
                                    book.getChapterTitle(
                                        progressChapter
                                    )
                                } ($progressInnerIndex/$progressInnerTotal)"
                            if (postLoading) {
                                _loadingStatus.postValue(Resource.Loading(progressText))
                            } else {
                                chapterMutex.withLock {
                                    chapterData[index] =
                                        Resource.Loading(progressText)
                                    if (notify) notifyChapterUpdate(index)
                                }
                            }
                        }.let { (mlRender, mlSpans) ->
                            rendered = mlRender
                            spans = mlSpans
                        }
                    }
                }

                LiveChapterData(
                    index = index,
                    rendered = rendered,
                    spans = spans,
                    originalRendered = originalRendered,
                    originalSpans = originalSpans,
                    rawText = rawText,
                    title = book.getChapterTitle(index),
                )
            }

            // set the data and return
            chapterMutex.withLock {
                chapterData[index] = data
            }
        } catch (t: Throwable) {
            // Tasks.await may throw
            chapterMutex.withLock {
                chapterData[index] = throwableToResource(t)
            }
        } finally {
            chapterMutex.withLock {
                loading -= index
                if (notify) notifyChapterUpdate(index)
            }
        }
    }

    private fun hashString(text: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(text)
        val sb = StringBuilder()
        for (b in digest) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    @Throws(MLException::class)
    private suspend fun translate(
        text: Spanned,
        spans: ArrayList<TextSpan>,
        loading: suspend (Triple<Int, Int, Int>) -> Unit
    ): Pair<Spanned, ArrayList<TextSpan>> {
        try {
            val currentSettings = mlSettings
            val appContext = com.lagradost.quicknovel.BaseApplication.context?.applicationContext
            val manager = TranslationEnginesManager
            val engine = appContext?.let { TranslationEnginesManager.getActiveEngine(it) }
            val engineName = engine?.name ?: "offline"
            val modelName = engine?.let { eng -> TranslationEnginesManager.getEngineModel(appContext!!, eng.type) } ?: ""
            val engineId = engine?.type?.value ?: 0
            
            // Clean hash for cache key
            val textToHash = text.toString().trim()
            if (textToHash.isEmpty()) return text to spans
            
            val hash = hashString(textToHash.toByteArray())
            val filePrefix = "ml_${hash}.${currentSettings.from}_to_${currentSettings.to}.${engineId}_${modelName.take(15)}"

            // Read from cache
            appContext?.let { ctx ->
                val dir = File(ctx.filesDir, "translation_cache")
                val cache = File(dir, "$filePrefix.txt")
                if (cache.exists()) {
                    val mlText = cache.readText().toSpanned()
                    return mlText to parseTextToSpans(mlText, spans[0].index)
                }
            }
            
            val builder = StringBuilder()
            val out = ArrayList<TextSpan>()
            
            if (engine != null) {
                val batchSize = engine.recommendedBatchSize
                val maxParallel = engine.maxParallelRequests
                
                val chunks = spans.chunked(batchSize)
                val totalChunks = chunks.size
                var completedChunks = 0
                
                sessionTranslationCancelled = false
                
                coroutineScope {
                    for (parallelGroup in chunks.chunked(maxParallel)) {
                        yield()
                        ensureActive()
                        
                        val deferredResults = parallelGroup.map { batchSpans ->
                            async(Dispatchers.IO) {
                                if (sessionTranslationCancelled) return@async batchSpans to Resource.Failure(null, "Cancelled")
                                
                                val prefersBatching = engine.prefersBatching
                                val isBatch = batchSpans.size > 1 && prefersBatching
                                val sep = if (isBatch) "\n###BATCH_SEP###\n" else ""
                                val batchText = if (isBatch) {
                                    batchSpans.joinToString(sep) { it.text.toString() }
                                } else {
                                    batchSpans.first().text.toString()
                                }
                                
                                val request = TranslationRequest(
                                    text = batchText,
                                    from = currentSettings.from,
                                    to = currentSettings.to,
                                    bridgeText = sep,
                                    systemInstruction = if (engine.supportsSystemInstructions) {
                                        "Translate novel text from ${currentSettings.from} to ${currentSettings.to}. tone: Professional. Preserve exact paragraph count using separators."
                                    } else null
                                )
                                
                                var result: Resource<String>? = null
                                try {
                                    result = withTimeoutOrNull(180_000) {
                                        var retryCount = 0
                                        val maxRetries = 2
                                        var lastCall: Resource<String>? = null
                                        while (retryCount <= maxRetries && isActive) {
                                            lastCall = engine.translate(appContext!!, request)
                                            if (lastCall is Resource.Success) break
                                            if (lastCall is Resource.Failure && lastCall.errorString?.contains("429") == true) {
                                                runOnMainThread { stopTranslation() }
                                                break
                                            }
                                            if (lastCall is Resource.Failure && lastCall.errorString?.contains("503") == true) {
                                                retryCount++
                                                delay(retryCount * 2000L)
                                                continue
                                            }
                                            break
                                        }
                                        lastCall
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Batch failed", e)
                                }
                                batchSpans to (result ?: Resource.Failure(null, "Timeout"))
                            }
                        }
                        
                        val groupResults = deferredResults.awaitAll()
                        for ((batchSpans, result) in groupResults) {
                            if (sessionTranslationCancelled) break
                            
                            if (result is Resource.Success) {
                                val translatedValue = result.value
                                val prefersBatching = engine.prefersBatching
                                val sep = if (prefersBatching) "\n###BATCH_SEP###\n" else ""
                                val wasBatched = translatedValue != null && sep.isNotEmpty() && translatedValue.contains(sep)
                                
                                if (wasBatched && batchSpans.size > 1) {
                                    val translatedTexts = translatedValue?.split(sep) ?: emptyList()
                                    for (j in batchSpans.indices) {
                                        val translatedParagraph = translatedTexts.getOrNull(j)?.trim() ?: batchSpans[j].text.toString()
                                        val start = builder.length
                                        builder.append(translatedParagraph).append("\n\n")
                                        out.add(TextSpan(translatedParagraph.toSpanned(), start, builder.length - 2, batchSpans[j].index, batchSpans[j].innerIndex))
                                    }
                                } else {
                                    val translatedParagraph = translatedValue?.trim() ?: batchSpans[0].text.toString()
                                    val cleanedText = if (sep.isNotEmpty() && translatedParagraph.contains(sep)) translatedParagraph.replace(sep, "\n\n") else translatedParagraph
                                    val start = builder.length
                                    builder.append(cleanedText).append("\n\n")
                                    out.add(TextSpan(cleanedText.toSpanned(), start, builder.length - 2, batchSpans[0].index, batchSpans[0].innerIndex))
                                    
                                    if (batchSpans.size > 1 && !wasBatched) {
                                        for (j in 1 until batchSpans.size) {
                                            val original = batchSpans[j].text.toString()
                                            val s = builder.length
                                            builder.append(original).append("\n\n")
                                            out.add(TextSpan(original.toSpanned(), s, builder.length - 2, batchSpans[j].index, batchSpans[j].innerIndex))
                                        }
                                    }
                                }
                            } else {
                                for (span in batchSpans) {
                                    val original = span.text.toString()
                                    val s = builder.length
                                    builder.append(original).append("\n\n")
                                    out.add(span.copy(text = original.toSpanned()))
                                }
                            }
                        }
                        
                        completedChunks++
                        loading.invoke(Triple(parallelGroup.first().first().index, completedChunks, totalChunks))
                    }
                }
            } else {
                // Offline mode (fallback)
                val translator = mlTranslator
                if (translator != null) {
                    for (i in 0 until spans.size) {
                        currentCoroutineContext().ensureActive()
                        loading.invoke(Triple(spans[i].index, i + 1, spans.size))
                        val translated = try {
                            Tasks.await(translator.translate(spans[i].text.toString()))
                        } catch (t: Throwable) {
                            spans[i].text.toString()
                        }
                        val start = builder.length
                        builder.append(translated).append("\n\n")
                        out.add(TextSpan(translated.toSpanned(), start, builder.length - 2, spans[i].index, spans[i].innerIndex))
                    }
                } else {
                    return text to spans
                }
            }
            
            val resultRawText = builder.toString()
            appContext?.let { ctx ->
                val dir = File(ctx.filesDir, "translation_cache")
                dir.mkdirs()
                File(dir, "$filePrefix.txt").writeText(resultRawText)
            }
            return resultRawText.toSpanned() to out
        } catch (t: Throwable) {
            Log.e(TAG, "Translation error", t)
            return text to spans
        }
    }

    @Throws
    suspend fun requireMLDownload(): Boolean {
        val settings = MLSettings(from = mlFromLanguage, to = mlToLanguage)
        if (settings.isInvalid()) {
            return false
        }
        val modelManager = RemoteModelManager.getInstance()

        for (model in arrayOf(settings.from, settings.to)) {
            if (model == "en") continue

            if (!Tasks.await(
                    modelManager.isModelDownloaded(
                        TranslateRemoteModel.Builder(model).build()
                    )
                )
            ) {
                return true
            }
        }

        return false
    }

    fun applyMLSettings(allowDownload: Boolean) = ioSafe {
        _translationLoadingStatus.postValue(Resource.Loading("")) // Start feedback immediately
        currentCoroutineContext().ensureActive()
        val settings = MLSettings(from = mlFromLanguage, to = mlToLanguage)
        val isDownloadNeeded = if (settings.isValid() && allowDownload) {
            safeAsync { requireMLDownload() } == true
        } else false
        
        if (isDownloadNeeded) {
            _translationLoadingStatus.postValue(Resource.Loading(context?.getString(R.string.download_ml)))
        }
        isTranslationActive = settings.isValid()
        if (isTranslationActive) {
            isShowingOriginalLive.postValue(false)
        }
        initMLFromSettings(settings, allowDownload, isDownloadNeeded)
        reloadMLForAllChapters(true)
        updateReadArea(seekToDesired = false)
        _translationLoadingStatus.postValue(Resource.Success(if (isDownloadNeeded) "Model applied" else ""))
    }

    private suspend fun reloadMLForAllChapters(toTranslationStatus: Boolean = false) {
        val status = if (toTranslationStatus) _translationLoadingStatus else _loadingStatus
        status.postValue(Resource.Loading(context?.getString(R.string.translating)))
        chapterMutex.withLock {
            val cIndex = currentIndex
            val lower = cIndex - chapterPaddingBottom
            val upper = cIndex + chapterPaddingTop

            val keys = chapterData.keys.toTypedArray() // deep copy it to avoid ConcurrentModificationException

            // remove all irrelevant cache so we do not translate outdated shit
            for (key in keys) {
                if (key < lower || key > upper) {
                    chapterData.remove(key)
                }
            }

            // update the rem cache
            for (entry in chapterData.entries) {
                currentCoroutineContext().ensureActive()
                val value = entry.value
                if (value !is Resource.Success) continue
                val success = value.value

                try {
                    translate(
                        success.originalRendered,
                        success.originalSpans
                    ) { (progressChapter, progressInnerIndex, progressInnerTotal) ->
                        // we cannot easily ensureActive here as it is a lambda
                        status.postValue(
                            Resource.Loading(
                                "${context?.getString(R.string.translating)} ${
                                    book.getChapterTitle(
                                        progressChapter
                                    )
                                } ($progressInnerIndex/$progressInnerTotal)"
                            )
                        )
                    }.let { (mlRender, mlSpans) ->
                        entry.setValue(
                            Resource.Success(
                                success.copy(
                                    rendered = mlRender,
                                    spans = mlSpans,
                                )
                            )
                        )
                    }
                } catch (t: Throwable) {
                    entry.setValue(
                        throwableToResource(t)
                    )
                }
            }
        }

        // update what we have read
        updateReadArea()
        //refreshChapters()
    }

    private suspend fun initMLFromSettings(settings: MLSettings, allowDownload: Boolean, isDownloadNeeded: Boolean = false) {
        try {
            mlTranslator?.closeQuietly()
            mlTranslator = null

            if (settings.isInvalid()) {
                mlTranslator?.closeQuietly()
                mlTranslator = null
                mlSettings = settings
                return
            }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(settings.from)
                .setTargetLanguage(settings.to)
                .build()

            val translator = Translation.getClient(options)
            mlTranslator = translator

            if (allowDownload) {
                _translationLoadingStatus.postValue(Resource.Loading("Downloading local model..."))
                try {
                    Tasks.await(
                        translator.downloadModelIfNeeded(), 300L, TimeUnit.SECONDS
                    )
                    // Success will be posted by the caller after translation is done
                } catch (e: Exception) {
                    _translationLoadingStatus.postValue(Resource.Failure(null, e.message ?: "Download failed"))
                    throw e
                }
            }

            mlSettings = settings
        } catch (e: TimeoutException) {
            _translationLoadingStatus.postValue(Resource.Failure(e, "Timeout"))
            mlTranslator?.closeQuietly()
            mlTranslator = null
        } catch (t: Throwable) {
            _translationLoadingStatus.postValue(Resource.Failure(t, t.message ?: "Error"))
            logError(t)
        }
    }


    fun init(intent: Intent?, context: ReadActivity2) = ioSafe {
        _loadingStatus.postValue(Resource.Loading())
        initTTSSession(context)

        val loadedBook = safeApiCall {
            if (intent == null) throw ErrorLoadingException("No intent")

            val data = intent.data ?: throw ErrorLoadingException("Empty intent")
            val isFromEpub = intent.type != "quickstream"

            val epub = if (isFromEpub) {
                val fd = context.contentResolver.openFileDescriptor(data, "r")
                    ?: throw ErrorLoadingException("Unable to open file descriptor")
                val zipFile = AndroidZipFile(fd, "")
                val book = EpubReader().readEpubLazy(zipFile, "utf-8")
                RegularBook(book)
            } else {
                val input = context.contentResolver.openInputStream(data)
                    ?: throw ErrorLoadingException("Empty data")
                QuickBook(DataStore.mapper.readValue(input.reader().readText()))
            }

            if (epub.size() <= 0) {
                throw ErrorLoadingException("Empty book, failed to parse ${intent.type}")
            }
            epub
        }

        when (loadedBook) {
            is Resource.Success -> {
                init(loadedBook.value, context)

                initMLFromSettings(mlSettings, false)

                // cant assume we know a chapter max as it can expand

                val desiredChapterName = getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, book.title())
                val desiredChapterIndex =
                    (0 until book.size()).firstOrNull {
                        loadedBook.value.getChapterTitle(it)
                            .asStringNull(context) == desiredChapterName
                    } ?: getKey<Int>(EPUB_CURRENT_POSITION, book.title()) ?: 0
                val loadedChapterIndex =
                    maxOf(desiredChapterIndex, 0)

                // we the current loaded thing here, but because loadedChapter can be >= book.size (expand) we have to check
                if (loadedChapterIndex < book.size()) {
                    _loadingStatus.postValue(
                        Resource.Loading(
                            book.getLoadingStatus(
                                loadedChapterIndex
                            )
                        )
                    )
                }

                currentIndex = loadedChapterIndex
                updateIndexAsync(loadedChapterIndex, notify = false, postLoading = true)

                if (book.size() <= 0) {
                    _loadingStatus.postValue(
                        Resource.Failure(
                            null,
                            "Invalid chapter data when trying to load chapter $loadedChapterIndex when the book only has ${book.size()} chapters"
                        )
                    )
                    return@ioSafe
                }

                // if we are reading a book that sub/resize for some reason, this will clamp it into the correct range
                if (loadedChapterIndex >= book.size()) {
                    currentIndex = book.size() - 1
                    updateIndexAsync(currentIndex, notify = false)
                    showToast("Resize $loadedChapterIndex -> $currentIndex", Toast.LENGTH_LONG)
                }

                val char = getKey(
                    EPUB_CURRENT_POSITION_SCROLL_CHAR, book.title()
                ) ?: 0

                val innerIndex = innerCharToIndex(currentIndex, char) ?: 0

                // don't update as you want to seek on update
                changeIndex(ScrollIndex(currentIndex, innerIndex, char))

                // notify once because initial load is 3 chapters I don't care about 10 notifications when the user cant see it
                updateReadArea(seekToDesired = true)

                /*_loadingStatus.postValue(
                    Resource.Success(true)
                )*/
            }

            is Resource.Failure -> {
                _loadingStatus.postValue(
                    Resource.Failure(
                        loadedBook.cause,
                        loadedBook.errorString
                    )
                )
            }

            else -> throw NotImplementedError()
        }
    }

    fun init(book: AbstractBook, context: Context) {
        this.book = book
        this.context = context
        _title.postValue(book.title())

        // Ensure loadId is initialized for persistence (Character Aliases, etc.)
        if (book is QuickBook) {
            this.loadId = BookDownloader2Helper.generateId(book.data.meta.apiName, book.data.meta.author, book.data.meta.name)
        } else {
            // For RegularBooks (EPUB), use the title hash as a unique enough identifier
            this.loadId = book.title().hashCode()
        }
        loadAliases()

        updateChapters()
        val imageLoader: ImageLoader = SingletonImageLoader.get(context)

        val coilStore = object : CoilStore {
            override fun load(drawable: AsyncDrawable): ImageRequest {
                val newUrl = drawable.destination.substringAfter("&url=")
                val url =
                    book.resolveUrl(
                        if (newUrl.length > 8) { // we assume that it is not a stub url by length > 8
                            URLDecoder.decode(newUrl)
                        } else {
                            drawable.destination
                        }
                    )

                return ImageRequest.Builder(context)
                    .data(url)
                    .build()
            }

            override fun cancel(disposable: Disposable) {
                disposable.dispose()
            }
        }

        markwon = Markwon.builder(context)
            .usePlugin(HtmlPlugin.create { plugin -> plugin.excludeDefaults(false) })
            .usePlugin(CoilImagesPlugin.create(context, coilStore, imageLoader))
            .usePlugin(object :
                AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.imageSizeResolver(object : ImageSizeResolver() {
                        override fun resolveImageSize(drawable: AsyncDrawable): Rect {
                            return drawable.result.bounds
                        }
                    })
                }
            })
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .build()
        //reducer = MarkwonReducer.directChildren()
    }

    // ========================================  TTS STUFF ========================================

    lateinit var ttsSession: TTSSession

    private fun initTTSSession(context: Context) {
        runOnMainThread {
            ttsSession = TTSSession(context, ::parseAction)
        }
    }

    private var pendingTTSSkip: Int = 0
    private var _currentTTSStatus: TTSHelper.TTSStatus = TTSHelper.TTSStatus.IsStopped
    var currentTTSStatus: TTSHelper.TTSStatus
        get() = _currentTTSStatus
        set(value) = synchronized(this@ReadActivityViewModel) {
            playDummySound()
            if (_currentTTSStatus == TTSHelper.TTSStatus.IsStopped && value == TTSHelper.TTSStatus.IsRunning) {
                startTTSWorker()
            }

            _ttsStatus.postValue(value)
            _currentTTSStatus = value
        }

    fun stopTTS() {
        currentTTSStatus = TTSHelper.TTSStatus.IsStopped
    }

    fun setTTSLanguage(locale: Locale?) {
        ttsSession.setLanguage(locale)
    }


    fun pauseTTS() {
        if (!ttsSession.ttsInitialized()) return
        if (currentTTSStatus == TTSHelper.TTSStatus.IsRunning) {
            currentTTSStatus = TTSHelper.TTSStatus.IsPaused
        }
    }

    fun startTTS() {
        currentTTSStatus = TTSHelper.TTSStatus.IsRunning
    }

    fun forwardsTTS() {
        if (!ttsSession.ttsInitialized()) return
        pendingTTSSkip += 1
    }

    fun backwardsTTS() {
        if (!ttsSession.ttsInitialized()) return
        pendingTTSSkip -= 1
    }

    fun playTTS() {
        currentTTSStatus = TTSHelper.TTSStatus.IsRunning
    }

    fun pausePlayTTS() {
        if (currentTTSStatus == TTSHelper.TTSStatus.IsRunning) {
            currentTTSStatus = TTSHelper.TTSStatus.IsPaused
        } else if (currentTTSStatus == TTSHelper.TTSStatus.IsPaused) {
            currentTTSStatus = TTSHelper.TTSStatus.IsRunning
        }
    }

    fun isTTSRunning(): Boolean {
        return currentTTSStatus == TTSHelper.TTSStatus.IsRunning
    }


    private val ttsThreadMutex = Mutex()

    fun startTTSWorker() = ioSafe {
        TTSNotificationService.start(this@ReadActivityViewModel, context ?: return@ioSafe)
    }

    suspend fun startTTSThread() = coroutineScope {
        try {
            val ttsStartTime = System.currentTimeMillis()
            var ttsEndTime = ttsStartTime + ttsTimer
            val ttsHasTimer = ttsEndTime > ttsStartTime

            val dIndex = desiredTTSIndex ?: desiredIndex ?: return@coroutineScope

            if (ttsThreadMutex.isLocked) return@coroutineScope
            ttsThreadMutex.withLock {
                ttsSession.register()
                ttsSession.setSpeed(ttsSpeed)
                ttsSession.setPitch(ttsPitch)

                var ttsInnerIndex = 0
                var index = dIndex.index

                val startLines = chapterMutex.withLock {
                    chapterData[index].letInner { it.ttsLines }
                } ?: run {
                    index++
                    return@coroutineScope
                }

                val idx = startLines.indexOfFirst { it.startChar >= dIndex.char }
                if (idx != -1) {
                    ttsInnerIndex = idx
                } else {
                    index++
                }

                loadIndividualChapter(index)
                while (isActive && currentTTSStatus != TTSHelper.TTSStatus.IsStopped) {
                    var lines = when (val currentData = chapterMutex.withLock { chapterData[index] }) {
                        null -> {
                            showToast(R.string.got_null_data)
                            break
                        }

                        is Resource.Failure -> {
                            showToast(currentData.errorString)
                            break
                        }

                        is Resource.Loading -> {
                            if (currentTTSStatus == TTSHelper.TTSStatus.IsStopped) break
                            delay(100)
                            continue
                        }

                        is Resource.Success -> {
                            currentData.value.ttsLines
                        }
                    }

                    fun notify() {
                        TTSNotifications.notify(
                            book.title(),
                            chaptersTitlesInternal[index],
                            book.poster(),
                            currentTTSStatus,
                            context
                        )
                    }
                    notify()

                    if (ttsInnerIndex < 0) {
                        ttsInnerIndex += lines.size
                    }

                    updateIndex(index)

                    //preload next chapter
                    viewModelScope.launch(Dispatchers.IO) {
                        val exists = chapterMutex.withLock { chapterData[index + 1] is Resource.Success }
                        if (!exists)
                            loadIndividualChapter(index + 1)
                    }

                    // speak all lines
                    while (ttsInnerIndex < lines.size && ttsInnerIndex >= 0) {
                        ensureActive()

                        // --- SYNC FIX: Re-validate chapter data for translation updates ---
                        val currentChapterData = chapterMutex.withLock { chapterData[index] }
                        if (currentChapterData is Resource.Success && currentChapterData.value.ttsLines !== lines) {
                            val oldLine = lines.getOrNull(ttsInnerIndex)
                            lines = currentChapterData.value.ttsLines
                            if (oldLine != null) {
                                val newIdx = lines.indexOfFirst { it.startChar >= oldLine.startChar }
                                ttsInnerIndex = if (newIdx != -1) newIdx else ttsInnerIndex
                            }
                        }

                        val currentTimeRemaining = ttsEndTime - System.currentTimeMillis()
                        if (ttsHasTimer) {
                            if (currentTimeRemaining < 0) {
                                currentTTSStatus = TTSHelper.TTSStatus.IsStopped
                            } else {
                                ttsTimeRemaining.postValue(currentTimeRemaining)
                            }
                        }

                        if (currentTTSStatus == TTSHelper.TTSStatus.IsStopped) break

                        val line = lines[ttsInnerIndex]
                        val nextLine = lines.getOrNull(ttsInnerIndex + 1)

                        if (!isInApp) {
                            innerCharToIndex(index, line.startChar)?.let {
                                changeIndex(
                                    ScrollIndex(
                                        index,
                                        it,
                                        line.startChar
                                    ), alsoTitle = false
                                )
                            }
                        }

                        // post visual
                        _ttsLine.postValue(line)

                        // wait for next line
                        val waitFor = ttsSession.speak(
                            line,
                            nextLine
                        ) {
                            currentTTSStatus != TTSHelper.TTSStatus.IsRunning || pendingTTSSkip != 0
                        }

                        if (!ttsSession.isValidTTS()) {
                            currentTTSStatus = TTSHelper.TTSStatus.IsStopped
                        }

                        ttsSession.waitForOr(waitFor, {
                            currentTTSStatus != TTSHelper.TTSStatus.IsRunning || pendingTTSSkip != 0
                        }) {
                            notify()
                        }

                        // wait for pause
                        var isPauseDuration = 0L
                        while (currentTTSStatus == TTSHelper.TTSStatus.IsPaused) {
                            isPauseDuration++
                            delay(100)
                        }

                        ttsEndTime += 100L * isPauseDuration

                        if (isPauseDuration > 0) {
                            notify()
                            pendingTTSSkip = 0
                            continue
                        }

                        if (pendingTTSSkip != 0) {
                            ttsInnerIndex += pendingTTSSkip
                            pendingTTSSkip = 0
                        } else {
                            ttsInnerIndex += 1
                        }
                    }

                    if (currentTTSStatus == TTSHelper.TTSStatus.IsStopped) break

                    if (ttsInnerIndex > 0 || lines.isEmpty()) {
                        index++
                        ttsInnerIndex = 0
                    } else if (index > 0) {
                        index--
                    } else {
                        ttsInnerIndex = 0
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
        } catch (t: Throwable) {
            logError(t)
        } finally {
            currentTTSStatus = TTSHelper.TTSStatus.IsStopped
            TTSNotifications.notify(
                book.title(),
                "".toUiText(),
                book.poster(),
                TTSHelper.TTSStatus.IsStopped,
                context
            )
            ttsSession.interruptTTS()
            ttsSession.unregister()
            _ttsLine.postValue(null)
            ttsTimeRemaining.postValue(null)
        }
    }

    fun parseAction(input: TTSHelper.TTSActionType): Boolean {

        // validate that the action makes sense
        if (
            (currentTTSStatus == TTSHelper.TTSStatus.IsPaused && input == TTSHelper.TTSActionType.Pause) ||
            (currentTTSStatus != TTSHelper.TTSStatus.IsPaused && input == TTSHelper.TTSActionType.Resume) ||
            (currentTTSStatus == TTSHelper.TTSStatus.IsStopped && input == TTSHelper.TTSActionType.Stop) ||
            (currentTTSStatus != TTSHelper.TTSStatus.IsRunning && input == TTSHelper.TTSActionType.Next)
        ) {
            return false
        }

        if (!ttsSession.ttsInitialized()) return false

        when (input) {
            TTSHelper.TTSActionType.Pause -> pauseTTS()
            TTSHelper.TTSActionType.Resume -> startTTS()
            TTSHelper.TTSActionType.Stop -> stopTTS()
            TTSHelper.TTSActionType.Next -> forwardsTTS()
        }

        return true
    }

    fun innerCharToIndex(index: Int, char: Int): Int? {
        // the lock is so short it does not matter I *hope*
        return runBlocking {
            chapterMutex.withLock { chapterData[index] }?.letInner { live ->
                // todo binary search, but strip all but TextSpan first
                live.spans.firstOrNull { it.start >= char }?.innerIndex
            }
        }
    }

    /** sets the metadata and global vars used as well as keys */
    private var lastChangeIndex: ScrollIndex? = null
    private var lastScrollMs: Long = 0
    private fun changeIndex(scrollIndex: ScrollIndex, alsoTitle: Boolean = true) {
        if (alsoTitle) {
            _chapterTile.postValue(chaptersTitlesInternal[scrollIndex.index])
        }

        desiredIndex = scrollIndex
        currentIndex = scrollIndex.index

        // the majority of the time is spent on setKey, and because this is called from onscroll
        // this fixes lag
        lastChangeIndex = scrollIndex
        if (System.currentTimeMillis() > lastScrollMs + 200L) {
            lastScrollMs = System.currentTimeMillis()
            setScrollKeys(scrollIndex)
        }
    }

    private fun setScrollKeys(scrollIndex: ScrollIndex) {
        setKey(
            EPUB_CURRENT_POSITION_READ_AT,
            "${book.title()}/${scrollIndex.index}",
            System.currentTimeMillis()
        )

        setKey(
            EPUB_CURRENT_POSITION_SCROLL_CHAR,
            book.title(),
            scrollIndex.char
        )
        setKey(EPUB_CURRENT_POSITION, book.title(), scrollIndex.index)
        context?.let {
            setKey(
                EPUB_CURRENT_POSITION_CHAPTER,
                book.title(),
                book.getChapterTitle(scrollIndex.index).asString(it)
            )
        }
    }

    fun scrollToDesired(scrollIndex: ScrollIndex) {
        changeIndex(scrollIndex)
        updateReadArea(seekToDesired = true)
    }

    fun seekToChapter(index: Int) = ioSafe {
        // sanity check
        if (index < 0 || index >= book.size()) return@ioSafe

        // we wont allow chapter switching and tts at the same time, stop it
        if (currentTTSStatus != TTSHelper.TTSStatus.IsStopped) {
            currentTTSStatus = TTSHelper.TTSStatus.IsStopped
        }

        // set loading
        _loadingStatus.postValue(Resource.Loading())

        // load the chapters
        updateIndexAsync(index, notify = false, postLoading = true)
        // set the keys
        setKey(EPUB_CURRENT_POSITION, book.title(), index)
        setKey(EPUB_CURRENT_POSITION_SCROLL_CHAR, book.title(), 0)

        // set the state
        desiredIndex = ScrollIndex(index, 0, 0)
        currentIndex = index
        desiredTTSIndex = ScrollIndex(index, 0, 0)

        // push the update
        updateReadArea(seekToDesired = true)
        // update the view
        _chapterTile.postValue(chaptersTitlesInternal[index])
        //_loadingStatus.postValue(Resource.Success(true))
    }

    /*private fun changeIndex(index: Int, updateArea: Boolean = true) {
        val realNewIndex = minOf(index, book.size() - 1)
        if (currentIndex == realNewIndex) return
        setKey(EPUB_CURRENT_POSITION, book.title(), realNewIndex)
        currentIndex = realNewIndex
        if (updateArea) updateReadArea()
        _chapterTile.postValue(chaptersTitlesInternal[realNewIndex])
    }*/

    fun onScroll(visibility: ScrollVisibilityIndex?) {
        if (visibility == null) return

        // dynamically increase padding in case of very small chapters with a maximum of 10 chapters
        val first = visibility.firstInMemory.index
        val last = visibility.lastInMemory.index
        chapterPaddingTop = minOf(10, maxOf(chapterPaddingTop, (last - first) + 1))

        val current = currentIndex

        val save = visibility.firstFullyVisible ?: visibility.firstInMemory
        desiredTTSIndex = visibility.firstFullyVisibleUnderLine?.toScroll()
        changeIndex(save.toScroll())

        // update the read area if changed index
        if (current != save.index)
            updateReadArea()

        // load forwards and backwards
        updateIndex(visibility.firstInMemory.index)
        updateIndex(visibility.lastInMemory.index)
    }

    // FUCK ANDROID WITH ALL MY HEART
    // SEE https://stackoverflow.com/questions/45960265/android-o-oreo-8-and-higher-media-buttons-issue WHY
    private fun playDummySound() {
        val act = activity ?: return
        val mMediaPlayer: MediaPlayer = MediaPlayer.create(act, R.raw.dummy_sound_500ms)
        mMediaPlayer.setOnCompletionListener { mMediaPlayer.release() }
        mMediaPlayer.start()
    }

    override fun onCleared() {
        lastChangeIndex?.let { setScrollKeys(it) }
        ttsSession.release()
        stopTranslation()
        mlTranslator?.close()
        mlTranslator = null
        super.onCleared()
    }


    private var readerTypeInternal by PreferenceDelegate(
        EPUB_READER_TYPE,
        ReadingType.DEFAULT.prefValue,
        Int::class
    )

    var readerType
        get() = ReadingType.fromSpinner(readerTypeInternal)
        set(value) {
            readerTypeInternal = value.prefValue
            updateReadArea(seekToDesired = true)
        }


    var scrollWithVolume by PreferenceDelegate(EPUB_SCROLL_VOL, true, Boolean::class)
    var authorNotes by PreferenceDelegate(EPUB_AUTHOR_NOTES, true, Boolean::class)
    var ttsLock by PreferenceDelegate(EPUB_TTS_LOCK, true, Boolean::class)
    //var ttsOSSpeed by PreferenceDelegate(EPUB_TTS_OS_SPEED, true, Boolean::class)

    private var ttsSpeedKey by PreferenceDelegate(EPUB_TTS_SET_SPEED, 1.0f, Float::class)
    val isDictionaryEnabled by PreferenceDelegate(EPUB_DICTIONARY_ENABLED, true, Boolean::class)
    private var ttsPitchKey by PreferenceDelegate(EPUB_TTS_SET_PITCH, 1.0f, Float::class)

    var ttsSpeed: Float
        get() = ttsSpeedKey
        set(value) {
            ttsSession.setSpeed(value)
            ttsSpeedKey = value
        }

    var ttsPitch: Float
        get() = ttsPitchKey
        set(value) {
            ttsSession.setPitch(value)
            ttsPitchKey = value
        }

    fun setTTSVoice(name: String?) {
        setKey(EPUB_VOICE, name)
        ttsSession.setVoice(name)
        ttsSession.interruptTTS()
    }

    private val _ttsUseGoogleLive = MutableLiveData<Boolean>()
    val ttsUseGoogleLive: LiveData<Boolean> get() = _ttsUseGoogleLive
    var ttsUseGoogle: Boolean
        get() = _ttsUseGoogleLive.value ?: false
        set(value) {
            _ttsUseGoogleLive.postValue(value)
            setKey("TTS_USE_GOOGLE", value)
            ttsSession.releaseEngine()
            ttsSession.interruptTTS()
        }


    val textFontLive: MutableLiveData<String> = MutableLiveData(null)
    var textFont by PreferenceDelegateLiveView(EPUB_FONT, "", String::class, textFontLive)
    val textSizeLive: MutableLiveData<Int> = MutableLiveData(null)
    var textSize by PreferenceDelegateLiveView(
        EPUB_TEXT_SIZE,
        DEF_FONT_SIZE,
        Int::class,
        textSizeLive
    )

    val bionicReadingLive: MutableLiveData<Boolean> = MutableLiveData(null)
    var bionicReading by PreferenceDelegateLiveView(
        EPUB_TEXT_BIONIC,
        false,
        Boolean::class,
        bionicReadingLive
    )

    val isTextSelectableLive: MutableLiveData<Boolean> = MutableLiveData(null)
    var isTextSelectable by PreferenceDelegateLiveView(
        EPUB_TEXT_SELECTABLE,
        false,
        Boolean::class,
        isTextSelectableLive
    )

    val orientationLive: MutableLiveData<Int> = MutableLiveData(null)
    var orientation by PreferenceDelegateLiveView(
        EPUB_LOCK_ROTATION,
        OrientationType.DEFAULT.prefValue,
        Int::class, orientationLive
    )

    val textColorLive: MutableLiveData<Int> = MutableLiveData(null)
    var textColor by PreferenceDelegateLiveView(
        EPUB_TEXT_COLOR, "#cccccc".toColorInt(), Int::class, textColorLive
    )

    val textVerticalPaddingLive: MutableLiveData<Float> = MutableLiveData(null)
    var textVerticalPadding by PreferenceDelegateLiveView(
        EPUB_TEXT_VERTICAL_PADDING, 7.5f, Float::class, textVerticalPaddingLive
    )

    val backgroundColorLive: MutableLiveData<Int> = MutableLiveData(null)
    var backgroundColor by PreferenceDelegateLiveView(
        EPUB_BG_COLOR, "#292832".toColorInt(), Int::class, backgroundColorLive
    )

    val showBatteryLive: MutableLiveData<Boolean> = MutableLiveData(null)
    var showBattery by PreferenceDelegateLiveView(
        EPUB_HAS_BATTERY, true, Boolean::class, showBatteryLive
    )

    val showTimeLive: MutableLiveData<Boolean> = MutableLiveData(null)
    var showTime by PreferenceDelegateLiveView(
        EPUB_HAS_TIME, true, Boolean::class, showTimeLive
    )

    val paddingHorizontalLive: MutableLiveData<Int> = MutableLiveData(null)
    var paddingHorizontal by PreferenceDelegateLiveView(
        EPUB_TEXT_PADDING, DEF_HORIZONTAL_PAD, Int::class, paddingHorizontalLive
    )

    val paddingVerticalLive: MutableLiveData<Int> = MutableLiveData(null)
    var paddingVertical by PreferenceDelegateLiveView(
        EPUB_TEXT_PADDING_TOP, DEF_VERTICAL_PAD, Int::class, paddingVerticalLive
    )

    //val time12HLive: MutableLiveData<Boolean> = MutableLiveData(null)
    //var time12H by PreferenceDelegateLiveView(
    //    EPUB_TWELVE_HOUR_TIME, false, Boolean::class, time12HLive
    //)

    val screenAwakeLive: MutableLiveData<Boolean> = MutableLiveData(null)
    var screenAwake by PreferenceDelegateLiveView(
        EPUB_KEEP_SCREEN_ACTIVE, true, Boolean::class, screenAwakeLive
    )

    // in milliseconds
    val ttsTimerLive: MutableLiveData<Long> = MutableLiveData(null)
    var ttsTimer by PreferenceDelegateLiveView(
        EPUB_SLEEP_TIMER, 0, Long::class, ttsTimerLive
    )

    val ttsTimeRemaining: MutableLiveData<Long?> = MutableLiveData(null)

    val mlFromLanguageLive: MutableLiveData<String> = MutableLiveData(null)
    var mlFromLanguage by PreferenceDelegateLiveView(
        EPUB_ML_FROM_LANGUAGE,
        TranslateLanguage.ENGLISH,
        String::class,
        mlFromLanguageLive
    )

    val mlToLanguageLive: MutableLiveData<String> = MutableLiveData(null)
    var mlToLanguage by PreferenceDelegateLiveView(
        EPUB_ML_TO_LANGUAGE,
        TranslateLanguage.ENGLISH,
        String::class,
        mlToLanguageLive
    )

    /* Removed Online Translation preferences */

    /*
   // Moved up to ensure correct initialization order. Having it lower caused a race condition  // where the default 'false' value was loaded before the actual saved preference.
    var mlSettings
        get() = getKey<MLSettings>(EPUB_CURRENT_ML, book.title()) ?: MLSettings("en", "en", false)
        set(value) = setKey(EPUB_CURRENT_ML, book.title(), value)

        */

    data class MLSettings(
        @JsonProperty("from")
        val from: String,
        @JsonProperty("to")
        val to: String,
        // @JsonProperty("useOnlineTranslation")
        // val useOnlineTranslation: Boolean = false
    ) {
        companion object {
            val map = mapOf(
                "af" to "Afrikaans",
                "ar" to "Arabic",
                "be" to "Belarusian",
                "bg" to "Bulgarian",
                "bn" to "Bengali",
                "ca" to "Catalan",
                "cs" to "Czech",
                "cy" to "Welsh",
                "da" to "Danish",
                "de" to "German",
                "el" to "Greek",
                "en" to "English",
                "eo" to "Esperanto",
                "es" to "Spanish",
                "et" to "Estonian",
                "fa" to "Persian",
                "fi" to "Finnish",
                "fr" to "French",
                "ga" to "Irish",
                "gl" to "Galician",
                "gu" to "Gujarati",
                "he" to "Hebrew",
                "hi" to "Hindi",
                "hr" to "Croatian",
                "ht" to "Haitian",
                "hu" to "Hungarian",
                "id" to "Indonesian",
                "is" to "Icelandic",
                "it" to "Italian",
                "ja" to "Japanese",
                "ka" to "Georgian",
                "kn" to "Kannada",
                "ko" to "Korean",
                "lt" to "Lithuanian",
                "lv" to "Latvian",
                "mk" to "Macedonian",
                "mr" to "Marathi",
                "ms" to "Malay",
                "mt" to "Maltese",
                "nl" to "Dutch",
                "no" to "Norwegian",
                "pl" to "Polish",
                "pt" to "Portuguese",
                "ro" to "Romanian",
                "ru" to "Russian",
                "sk" to "Slovak",
                "sl" to "Slovenian",
                "sq" to "Albanian",
                "sv" to "Swedish",
                "sw" to "Swahili",
                "ta" to "Tamil",
                "te" to "Telugu",
                "th" to "Thai",
                "tl" to "Tagalog",
                "tr" to "Turkish",
                "uk" to "Ukrainian",
                "ur" to "Urdu",
                "vi" to "Vietnamese",
                "zh" to "Chinese",
            )

            val list = map.toList()

            fun fromShortToDisplay(from: String): String {
                return map[from] ?: "Unknown"
            }
        }

        val fromDisplay get() = fromShortToDisplay(from)
        val toDisplay get() = fromShortToDisplay(to)

        fun isInvalid(): Boolean = !isValid()

        fun isValid(): Boolean {
            if (from.isBlank() || to.isBlank()) {
                // nonsense
                return false
            }

            val all = TranslateLanguage.getAllLanguages()

            if (!all.contains(to)) {
                // no translation
                return false
            }

            if (!all.contains(from)) {
                // no support for auto yet, see https://developers.google.com/ml-kit/language/identification/android
                return false
            }

            if (from == to) {
                // identity function
                return false
            }

            return true
        }
    }


    //“I don’t know where to put this…
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    data class GoogleTranslationResponse(
        val sentences: List<GoogleSentence>,
        val extra: Any? = null,
        val language: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    data class GoogleSentence(
        val trans: String,
        val orig: String?,
        val translit: String? = null,
        val srcTranslit: String? = null
    )

    /* Removed onlineTranslate function */
}
