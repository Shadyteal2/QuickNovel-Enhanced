package com.lagradost.quicknovel.widget

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.EPUB_TTS_SET_PITCH
import com.lagradost.quicknovel.EPUB_TTS_SET_SPEED
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.TTSHelper
import com.lagradost.quicknovel.TTSNotificationService
import com.lagradost.quicknovel.TTSNotifications
import com.lagradost.quicknovel.TTSSession
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.stripHtml
import com.lagradost.quicknovel.ui.toUiText
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.ResultCached
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap
import com.lagradost.quicknovel.QuickBook
import com.lagradost.quicknovel.mvvm.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

class TTSForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaSession: MediaSessionCompat? = null

    // Standalone playback properties (Fallback when app is swiped away)
    private var ttsSession: TTSSession? = null
    private var isPlaying = false
    private var activeJob: Job? = null
    private var activeNovel: ResultCached? = null
    private var chapterIndex = 0
    private var ttsLineIndex = 0
    private var ttsLines = listOf<TTSHelper.TTSLine>()

    fun isPlaying() = isPlaying
    fun activeNovelName() = activeNovel?.name
    fun getTtsLines() = ttsLines
    fun getTtsLineIndex() = ttsLineIndex

    var onLineChanged: ((TTSHelper.TTSLine) -> Unit)? = null
    var onStatusChanged: ((TTSHelper.TTSStatus) -> Unit)? = null

    fun bindViewModel(vm: com.lagradost.quicknovel.ReadActivityViewModel) {
        vm.currentTTSStatus = if (isPlaying) TTSHelper.TTSStatus.IsRunning else TTSHelper.TTSStatus.IsPaused
        val currentLine = ttsLines.getOrNull(ttsLineIndex)
        if (currentLine != null) {
            vm.postTTSLine(currentLine)
        }
        this.onLineChanged = { line ->
            vm.postTTSLine(line)
        }
        this.onStatusChanged = { status ->
            vm.postTTSStatus(status)
        }
    }

    fun unbindViewModel() {
        this.onLineChanged = null
        this.onStatusChanged = null
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.lagradost.quicknovel.widget.ACTION_PLAY_PAUSE"
        const val ACTION_PLAY = "com.lagradost.quicknovel.widget.ACTION_PLAY"
        const val ACTION_PAUSE = "com.lagradost.quicknovel.widget.ACTION_PAUSE"
        const val ACTION_STOP = "com.lagradost.quicknovel.widget.ACTION_STOP"
        const val ACTION_NEXT = "com.lagradost.quicknovel.widget.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.lagradost.quicknovel.widget.ACTION_PREVIOUS"
        
        private const val STANDALONE_NOTIFICATION_ID = 133743

        var instance: TTSForegroundService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize MediaSessionCompat
        mediaSession = MediaSessionCompat(this, "QuickNovelTTSWidget").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handleIntentAction(ACTION_PLAY)
                }

                override fun onPause() {
                    handleIntentAction(ACTION_PAUSE)
                }

                override fun onStop() {
                    handleIntentAction(ACTION_STOP)
                }

                override fun onSkipToNext() {
                    handleIntentAction(ACTION_NEXT)
                }

                override fun onSkipToPrevious() {
                    handleIntentAction(ACTION_PREVIOUS)
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleIntentAction(action)
        }
        return START_STICKY
    }

    private fun handleIntentAction(action: String) {
        val vm = TTSNotificationService.viewModel
        if (vm != null) {
            // Case A: ReadActivityViewModel is active - delegate actions to the existing player thread
            when (action) {
                ACTION_PLAY_PAUSE -> vm.pausePlayTTS()
                ACTION_PLAY -> vm.startTTS()
                ACTION_PAUSE -> vm.pauseTTS()
                ACTION_STOP -> vm.stopTTS()
                ACTION_NEXT -> vm.forwardsTTS()
                ACTION_PREVIOUS -> vm.backwardsTTS()
            }
            // Trigger update of Glance widget UI state from VM state
            serviceScope.launch {
                updateWidgetStateFromViewModel()
            }
            return
        }

        // Case B: ReadActivityViewModel is null (swiped away) - run standalone playback
        when (action) {
            ACTION_PLAY_PAUSE -> togglePlayPauseStandalone()
            ACTION_PLAY -> playStandalone()
            ACTION_PAUSE -> pauseStandalone()
            ACTION_STOP -> stopStandalone()
            ACTION_NEXT -> nextChapterStandalone()
            ACTION_PREVIOUS -> previousChapterStandalone()
        }
    }

    internal fun togglePlayPauseStandalone() {
        if (isPlaying) {
            pauseStandalone()
        } else {
            playStandalone()
        }
    }

    internal fun playStandalone() {
        isPlaying = true
        onStatusChanged?.invoke(TTSHelper.TTSStatus.IsRunning)
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            try {
                // 1. Resolve last read novel from history cache
                val novel = withContext(Dispatchers.IO) { getLastReadNovel() } ?: return@launch
                activeNovel = novel

                // 2. Load current chapter details
                chapterIndex = getKey<Int>(EPUB_CURRENT_POSITION, novel.name) ?: 0
                val chapterName = getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, novel.name)
                    ?: "Chapter ${chapterIndex + 1}"

                // 3. Load chapter content text from downloaded file
                val text = withContext(Dispatchers.IO) { loadChapterText(novel, chapterIndex) }
                if (text.isNullOrBlank()) {
                    // Try next chapter or stop if unavailable
                    stopStandalone()
                    return@launch
                }

                // 4. Parse text to TTS lines
                val cleanLines = withContext(Dispatchers.Default) {
                    TTSHelper.ttsParseText(text, chapterIndex)
                }
                if (cleanLines.isEmpty()) {
                    stopStandalone()
                    return@launch
                }
                ttsLines = cleanLines

                // 5. Initialize stand-alone TTS Engine session
                if (ttsSession == null) {
                    ttsSession = TTSSession(this@TTSForegroundService) { command ->
                        // Handle standard session event commands if triggered by OS audio focus listeners
                        when (command) {
                            TTSHelper.TTSActionType.Pause -> pauseStandalone()
                            TTSHelper.TTSActionType.Resume -> playStandalone()
                            TTSHelper.TTSActionType.Stop -> stopStandalone()
                            TTSHelper.TTSActionType.Next -> nextChapterStandalone()
                        }
                        true
                    }
                }

                ttsSession?.register()
                
                // Read preference speed and pitch to set in session
                val speed = getKey<Float>(EPUB_TTS_SET_SPEED, 1.0f) ?: 1.0f
                val pitch = getKey<Float>(EPUB_TTS_SET_PITCH, 1.0f) ?: 1.0f
                ttsSession?.setSpeed(speed)
                ttsSession?.setPitch(pitch)

                // Load novel cover image poster
                val posterBitmap = withContext(Dispatchers.IO) { fetchCoverBitmap(novel.poster) }

                // Start foreground service with persistent media notification
                val notification = buildMediaNotification(novel.name, chapterName, posterBitmap, true)
                startForeground(STANDALONE_NOTIFICATION_ID, notification)

                // Update Glance widget state
                TTSWidget.updateWidgetState(
                    context = this@TTSForegroundService,
                    novelTitle = novel.name,
                    chapterName = chapterName,
                    coverUrl = novel.poster,
                    isPlaying = true,
                    chapterIndex = chapterIndex,
                    totalChapters = novel.totalChapters,
                    author = novel.author
                )

                // 6. Playback loop
                while (isActive && isPlaying && ttsLineIndex < ttsLines.size) {
                    val line = ttsLines[ttsLineIndex]
                    val nextLine = ttsLines.getOrNull(ttsLineIndex + 1)

                    // Track character progress
                    setKey(EPUB_CURRENT_POSITION_SCROLL_CHAR, novel.name, line.startChar)

                    onLineChanged?.invoke(line)

                    val waitFor = ttsSession?.speak(line, nextLine) {
                        !isPlaying
                    }

                    ttsSession?.waitForOr(waitFor, {
                        !isPlaying
                    }) {
                        // Completed line
                    }

                    // Await pause loop duration
                    while (isActive && !isPlaying) {
                        delay(50)
                    }

                    if (isPlaying) {
                        ttsLineIndex++
                    }
                }

                // Move to the next chapter if completed
                if (ttsLineIndex >= ttsLines.size) {
                    chapterIndex++
                    setKey(EPUB_CURRENT_POSITION, novel.name, chapterIndex)
                    ttsLineIndex = 0
                    playStandalone()
                }
            } catch (t: Throwable) {
                logError(t)
                stopStandalone()
            }
        }
    }

    internal fun pauseStandalone() {
        isPlaying = false
        onStatusChanged?.invoke(TTSHelper.TTSStatus.IsPaused)
        ttsSession?.interruptTTS()
        
        val novel = activeNovel ?: return
        val chapterName = getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, novel.name) ?: "Chapter ${chapterIndex + 1}"
        
        serviceScope.launch {
            val posterBitmap = withContext(Dispatchers.IO) { fetchCoverBitmap(novel.poster) }
            val notification = buildMediaNotification(novel.name, chapterName, posterBitmap, false)
            
            // Downgrade to standard notification and keep running
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(STANDALONE_NOTIFICATION_ID, notification)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }

            TTSWidget.updateWidgetState(
                context = this@TTSForegroundService,
                novelTitle = novel.name,
                chapterName = chapterName,
                coverUrl = novel.poster,
                isPlaying = false,
                chapterIndex = chapterIndex,
                totalChapters = novel.totalChapters,
                author = novel.author
            )
        }
    }

    internal fun stopStandalone() {
        isPlaying = false
        onStatusChanged?.invoke(TTSHelper.TTSStatus.IsStopped)
        activeJob?.cancel()
        ttsSession?.interruptTTS()
        ttsSession?.unregister()
        ttsSession?.release()
        ttsSession = null

        val novel = activeNovel
        if (novel != null) {
            val chapterName = getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, novel.name) ?: "Chapter ${chapterIndex + 1}"
            serviceScope.launch {
                TTSWidget.updateWidgetState(
                    context = this@TTSForegroundService,
                    novelTitle = novel.name,
                    chapterName = chapterName,
                    coverUrl = novel.poster,
                    isPlaying = false,
                    chapterIndex = chapterIndex,
                    totalChapters = novel.totalChapters,
                    author = novel.author
                )
            }
        }
        
        stopSelf()
    }

    internal fun nextChapterStandalone() {
        val novel = activeNovel ?: return
        chapterIndex++
        setKey(EPUB_CURRENT_POSITION, novel.name, chapterIndex)
        ttsLineIndex = 0
        playStandalone()
    }

    internal fun previousChapterStandalone() {
        val novel = activeNovel ?: return
        if (chapterIndex > 0) {
            chapterIndex--
            setKey(EPUB_CURRENT_POSITION, novel.name, chapterIndex)
        }
        ttsLineIndex = 0
        playStandalone()
    }

    private fun getLastReadNovel(): ResultCached? {
        val keys = getKeys(HISTORY_FOLDER) ?: return null
        return keys.mapNotNull { key ->
            getKey<ResultCached>(key)
        }.maxByOrNull { it.cachedTime }
    }

    private suspend fun loadChapterText(novel: ResultCached, index: Int): String? {
        val result = safeApiCall {
            val sApi = BookDownloader2Helper.sanitizeFilename(novel.apiName)
            val sAuthor = BookDownloader2Helper.sanitizeFilename(novel.author ?: "")
            val sName = BookDownloader2Helper.sanitizeFilename(novel.name)
            val filepath = filesDir.toString() + BookDownloader2Helper.getFilename(sApi, sAuthor, sName, index)
            val file = File(filepath)
            if (!file.exists()) return@safeApiCall null

            val text = file.readText()
            val firstChar = text.indexOf('\n')
            if (firstChar == -1) return@safeApiCall null

            val title = text.substring(0, firstChar)
            val data = text.substring(firstChar + 1)
            
            // Clean up the loaded HTML for the TTS voice loop
            val html = stripHtml(data, title, index, false)
            org.jsoup.Jsoup.parse(html).text()
        }
        return (result as? Resource.Success)?.value
    }

    private suspend fun fetchCoverBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val loader = SingletonImageLoader.get(this@TTSForegroundService)
                val request = ImageRequest.Builder(this@TTSForegroundService)
                    .data(url)
                    .allowHardware(false) // Must be non-hardware bitmap for notifications
                    .build()
                val result = loader.execute(request)
                val drawable = (result as? SuccessResult)?.image?.asDrawable(resources)
                (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } catch (t: Throwable) {
                null
            }
        }
    }

    private fun buildMediaNotification(
        title: String,
        chapter: String,
        icon: Bitmap?,
        isPlaying: Boolean
    ): Notification {
        // Create tts notification channel
        val channelId = "QuickNovelTTS"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.text_to_speech)
            val descriptionText = getString(R.string.text_to_speech_channel_description)
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_baseline_volume_up_24)
            .setContentTitle(title)
            .setContentText(chapter)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)

        if (icon != null) {
            builder.setLargeIcon(icon)
        }

        // Set up MediaStyle actions
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token).setShowActionsInCompactView(0, 1, 2)
        }
        builder.setStyle(mediaStyle)

        // Notification play controls PendingIntents
        val playPauseIntent = Intent(this, TTSForegroundService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val prevIntent = Intent(this, TTSForegroundService::class.java).apply { action = ACTION_PREVIOUS }
        val nextIntent = Intent(this, TTSForegroundService::class.java).apply { action = ACTION_NEXT }

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pPrev = PendingIntent.getService(this, 1, prevIntent, flag)
        val pPlayPause = PendingIntent.getService(this, 2, playPauseIntent, flag)
        val pNext = PendingIntent.getService(this, 3, nextIntent, flag)

        builder.addAction(R.drawable.ic_baseline_fast_rewind_24, "Rewind", pPrev)
        if (isPlaying) {
            builder.addAction(R.drawable.ic_baseline_pause_24, "Pause", pPlayPause)
        } else {
            builder.addAction(R.drawable.ic_baseline_play_arrow_24, "Play", pPlayPause)
        }
        builder.addAction(R.drawable.ic_baseline_fast_forward_24, "Forward", pNext)

        return builder.build()
    }

    private suspend fun updateWidgetStateFromViewModel() {
        val vm = TTSNotificationService.viewModel ?: return
        val title = vm.book.title()
        
        // Retrieve cached chapter title
        val chapterIndex = vm.currentIndex
        val chapterName = vm.book.getChapterTitle(chapterIndex).asString(this)
        
        val isPlaying = vm.isTTSRunning()

        val coverUrl = (vm.book as? QuickBook)?.data?.poster
        val author = vm.book.author()
        TTSWidget.updateWidgetState(
            context = this,
            novelTitle = title,
            chapterName = chapterName,
            coverUrl = coverUrl,
            isPlaying = isPlaying,
            coverBitmap = vm.book.poster(),
            chapterIndex = chapterIndex,
            totalChapters = vm.book.size(),
            author = author
        )
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        mediaSession?.release()
        activeJob?.cancel()
        ttsSession?.interruptTTS()
        ttsSession?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
