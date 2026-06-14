package com.lagradost.quicknovel

import android.content.Context
import android.service.notification.Condition.newId
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.download.DownloadViewModel
import com.lagradost.quicknovel.util.Apis
import java.lang.ref.WeakReference
import android.os.Build
import android.content.pm.ServiceInfo
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import java.io.File
import kotlinx.coroutines.sync.withLock
import com.lagradost.quicknovel.DownloadState

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

// This is needed to fix downloads, as newer android versions pause network connections in the background
class DownloadFileWorkManager(val context: Context, private val workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        const val DATA = "data"
        const val JSON_DATA = "json_data"
        const val TYPE_DATA = "type_data"
        const val ID = "id"

        const val ID_REFRESH_DOWNLOADS = "REFRESH_DOWNLOADS"
        const val ID_REFRESH_READINGPROGRESS = "REFRESH_READINGPROGRESS"
        const val ID_DOWNLOAD = "ID_DOWNLOAD"

        private val mapper = com.lagradost.quicknovel.util.AppUtils.mapper

        private var _viewModel: WeakReference<DownloadViewModel> = WeakReference(null)
        var viewModel: DownloadViewModel?
            get() = _viewModel.get()
            set(value) {
                _viewModel = WeakReference(value)
            }

        private var workNumber: Int = 0
        private val workData: HashMap<Int, Any> = hashMapOf()

        private fun isAppInForeground(context: Context): Boolean {
            return try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val appProcesses = activityManager.runningAppProcesses ?: return false
                appProcesses.any { 
                    it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && 
                    it.processName == context.packageName 
                }
            } catch (e: Exception) {
                false
            }
        }

        // java.lang.IllegalStateException: Data cannot occupy more than 10240 bytes when serialized
        // This stores the actual data for the WorkManager to use
        private fun insertWork(context: Context, data: Any): Int {
            synchronized(workData) {
                workNumber += 1
                try {
                    val file = File(context.cacheDir, "download_work_$workNumber.json")
                    file.writeText(mapper.writeValueAsString(data))
                } catch (e: Exception) {
                    Log.e("DownloadWork", "Failed to write temp work file", e)
                }
                workData[workNumber] = data
                return workNumber
            }
        }

        private fun popWork(context: Context, key: Int, typeName: String? = null): Any? {
            synchronized(workData) {
                val file = File(context.cacheDir, "download_work_$key.json")
                if (file.exists()) {
                    try {
                        val json = file.readText()
                        file.delete()
                        val type = try {
                            if (typeName != null) Class.forName(typeName) else null
                        } catch (e: Exception) {
                            null
                        }
                        return when (type) {
                            DownloadBatch::class.java -> mapper.readValue<DownloadBatch>(json)
                            StreamResponse::class.java -> mapper.readValue<StreamResponse>(json)
                            EpubResponse::class.java -> mapper.readValue<EpubResponse>(json)
                            DownloadFragment.DownloadDataLoaded::class.java -> mapper.readValue<DownloadFragment.DownloadDataLoaded>(json)
                            else -> {
                                // Try parsing sequentially
                                try { mapper.readValue<DownloadBatch>(json) } catch (e: Exception) {
                                    try { mapper.readValue<StreamResponse>(json) } catch (e: Exception) {
                                        try { mapper.readValue<EpubResponse>(json) } catch (e: Exception) {
                                            try { mapper.readValue<DownloadFragment.DownloadDataLoaded>(json) } catch (e: Exception) {
                                                null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadWork", "Failed to read temp work file", e)
                    }
                }
                return workData.remove(key)
            }
        }

        fun refreshAll(from: DownloadViewModel, context: Context) {
            viewModel = from

            (WorkManager.getInstance(context)).enqueueUniqueWork(
                ID_REFRESH_DOWNLOADS,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_REFRESH_DOWNLOADS)
                            .build()
                    )
                    .build()
            )
        }

        fun refreshAllReadingProgress(from: DownloadViewModel, context: Context, currentTab: Int) {
            viewModel = from
            val uniqueWorkName = "${ID_REFRESH_READINGPROGRESS}_$currentTab"
            (WorkManager.getInstance(context)).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_REFRESH_READINGPROGRESS)
                            .putInt(CURRENT_TAB, currentTab)
                            .build()
                    )
                    .build()
            )
        }

        private fun startDownload(data: Any, context: Context, novelId: Int) {
            val builder = Data.Builder()
                .putString(ID, ID_DOWNLOAD)
                .putInt("novelId", novelId)

            var serialized = false
            try {
                val json = mapper.writeValueAsString(data)
                // Limit is 10KB. We check if it's safe to pass via Data bundle.
                if (json.length < 9000) {
                    builder.putString(JSON_DATA, json)
                    builder.putString(TYPE_DATA, data::class.java.name)
                    serialized = true
                }
            } catch (e: Exception) {
                Log.e("DownloadWork", "Failed to serialize work data", e)
            }

            if (!serialized) {
                // Fallback to file/in-memory if too large or fails (less reliable on restart)
                builder.putInt(DATA, insertWork(context, data))
                builder.putString(TYPE_DATA, data::class.java.name)
            }

            (WorkManager.getInstance(context)).enqueueUniqueWork(
                "${ID_DOWNLOAD}_$novelId",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(builder.build())
                    .addTag(ID_DOWNLOAD)
                    .addTag("${ID_DOWNLOAD}_$novelId")
                    .build()
            )
        }

        fun download(
            card: DownloadFragment.DownloadDataLoaded,
            context: Context
        ) {
            startDownload(card, context, card.id)
        }

        fun download(
            load: LoadResponse,
            context: Context,
            novelId: Int,
            indices: List<Int>? = null
        ) {
            if (load.apiName == IMPORT_SOURCE || load.apiName == IMPORT_SOURCE_PDF) {
                return
            }
            if (indices != null && load is StreamResponse) {
                startDownload(DownloadBatch(load, indices), context, novelId)
            } else {
                startDownload(load, context, novelId)
            }
        }
    }

    data class DownloadBatch(
        val load: StreamResponse,
        val indices: List<Int>
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "The download notification channel"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel("epubdownloader.general", name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notificationId = 1337
        val notification = NotificationCompat.Builder(context, "epubdownloader.general")
            .setSmallIcon(R.drawable.rdload)
            .setContentTitle("Downloading Novel")
            .setContentText("Download in progress...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private suspend fun failDownload(novelId: Int) {
        if (novelId == -1) return
        try {
            BookDownloader2.downloadInfoMutex.withLock {
                BookDownloader2.downloadProgress[novelId]?.apply {
                    if (state == DownloadState.IsPending || state == DownloadState.IsDownloading) {
                        state = DownloadState.IsFailed
                        lastUpdatedMs = System.currentTimeMillis()
                        BookDownloader2.downloadProgressChanged.invoke(novelId to this)
                    }
                }
            }
            BookDownloader2.currentDownloadsMutex.withLock {
                BookDownloader2.currentDownloads -= novelId
            }
        } catch (t: Throwable) {
            Log.e("DownloadWork", "Failed to clean up download state for $novelId", t)
        }
    }

    @WorkerThread
    override suspend fun doWork(): Result {
        val novelId = this.workerParams.inputData.getInt("novelId", -1)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QuickNovel:DownloadWakeLock"
        )
        
        try {
            // Promote to foreground service to prevent network/background throttling
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    setForeground(getForegroundInfo())
                } else {
                    if (isAppInForeground(context)) {
                        setForeground(getForegroundInfo())
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("DownloadWork", "Foreground service start not allowed", e)
            } catch (e: SecurityException) {
                Log.e("DownloadWork", "Foreground service security exception", e)
            } catch (e: Exception) {
                Log.e("DownloadWork", "Failed to set worker to foreground", e)
            }
            
            // Acquire wake lock to keep CPU active when screen turns off (30 mins max)
            wakeLock.acquire(30 * 60 * 1000L)
            
            val id = this.workerParams.inputData.getString(ID)
            when (id) {
                ID_DOWNLOAD -> {
                    val jsonData = this.workerParams.inputData.getString(JSON_DATA)
                    val typeData = this.workerParams.inputData.getString(TYPE_DATA)
                    
                    val data: Any? = if (jsonData != null && typeData != null) {
                        try {
                            when (typeData) {
                                DownloadBatch::class.java.name -> mapper.readValue<DownloadBatch>(jsonData)
                                StreamResponse::class.java.name -> mapper.readValue<StreamResponse>(jsonData)
                                EpubResponse::class.java.name -> mapper.readValue<EpubResponse>(jsonData)
                                DownloadFragment.DownloadDataLoaded::class.java.name -> mapper.readValue<DownloadFragment.DownloadDataLoaded>(jsonData)
                                else -> popWork(context, this.workerParams.inputData.getInt(DATA, -1), typeData)
                            }
                        } catch (e: Exception) {
                            Log.e("DownloadWork", "Failed to deserialize work data", e)
                            popWork(context, this.workerParams.inputData.getInt(DATA, -1), typeData)
                        }
                    } else {
                        popWork(context, this.workerParams.inputData.getInt(DATA, -1), typeData)
                    }

                    if (data == null) {
                        failDownload(novelId)
                        return Result.failure()
                    }

                    when (data) {
                        is DownloadBatch -> {
                            BookDownloader2.downloadWorkThread(data.load, Apis.getApiFromName(data.load.apiName), data.indices)
                        }

                        is StreamResponse -> {
                            BookDownloader2.downloadWorkThread(data, Apis.getApiFromName(data.apiName))
                        }

                        is EpubResponse -> {
                            BookDownloader2.downloadWorkThread(data, Apis.getApiFromName(data.apiName))
                        }

                        is DownloadFragment.DownloadDataLoaded -> {
                            if (data.apiName == IMPORT_SOURCE_PDF)
                                BookDownloader2.downloadPDFWorkThread(data.source.toUri(), context)
                            else
                                BookDownloader2.downloadWorkThread(data)
                        }

                        else -> {
                            failDownload(novelId)
                            return Result.failure()
                        }
                    }
                }

                ID_REFRESH_DOWNLOADS -> {
                    viewModel?.refreshInternal()
                }

                ID_REFRESH_READINGPROGRESS ->{
                    val currentTab = this.workerParams.inputData.getInt(CURRENT_TAB, 1)
                    viewModel?.setIsLoading(true, currentTab)
                    BookDownloader2.getOldDataReadingProgress(currentTab)
                    viewModel?.setIsLoading(false, currentTab)
                }

                else -> return Result.failure()
            }
            return Result.success()
        } catch (t: Throwable) {
            Log.e("DownloadWork", "Error executing worker", t)
            failDownload(novelId)
            return Result.failure()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }
}