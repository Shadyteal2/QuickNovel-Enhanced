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

        private val mapper = jacksonObjectMapper().apply {
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        private var _viewModel: WeakReference<DownloadViewModel> = WeakReference(null)
        var viewModel: DownloadViewModel?
            get() = _viewModel.get()
            set(value) {
                _viewModel = WeakReference(value)
            }

        private var workNumber: Int = 0
        private val workData: HashMap<Int, Any> = hashMapOf()

        // java.lang.IllegalStateException: Data cannot occupy more than 10240 bytes when serialized
        // This stores the actual data for the WorkManager to use
        private fun insertWork(data: Any): Int {
            synchronized(workData) {
                workNumber += 1
                workData[workNumber] = data
                return workNumber
            }
        }

        private fun popWork(key: Int): Any? {
            synchronized(workData) {
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
                // Fallback to in-memory if too large or fails (less reliable on restart)
                builder.putInt(DATA, insertWork(data))
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

    @WorkerThread
    override suspend fun doWork(): Result {
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
                            else -> popWork(this.workerParams.inputData.getInt(DATA, -1))
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadWork", "Failed to deserialize work data", e)
                        popWork(this.workerParams.inputData.getInt(DATA, -1))
                    }
                } else {
                    popWork(this.workerParams.inputData.getInt(DATA, -1))
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

                    else -> return Result.failure()
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
    }
}