package com.lagradost.quicknovel.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.DOWNLOAD_FOLDER
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.ui.download.DownloadFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryMigrationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).novelDao()
            val keys = context.getKeys(DOWNLOAD_FOLDER)

            if (keys.isEmpty()) {
                return@withContext Result.success()
            }

            val novelsToInsert = mutableListOf<NovelEntity>()

            for (key in keys) {
                val data = context.getKey<DownloadFragment.DownloadData>(key) ?: continue
                val idStr = key.removePrefix("$DOWNLOAD_FOLDER/")
                val id = idStr.toIntOrNull() ?: continue

                novelsToInsert.add(
                    NovelEntity(
                        id = id,
                        source = data.source,
                        name = data.name,
                        author = data.author,
                        posterUrl = data.posterUrl,
                        rating = data.rating,
                        peopleVoted = data.peopleVoted,
                        views = data.views,
                        synopsis = data.synopsis,
                        tags = data.tags,
                        apiName = data.apiName,
                        lastUpdated = data.lastUpdated,
                        lastDownloaded = data.lastDownloaded,
                        filePath = null,
                        formatType = null,
                        hash = null
                    )
                )
            }

            if (novelsToInsert.isNotEmpty()) {
                dao.insertAll(novelsToInsert)
                // Cleanup old SharedPreferences to prevent duplicate migrations and free storage
                for (key in keys) {
                    context.removeKey(key)
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
