package com.lagradost.quicknovel.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.UpdateItem
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.mvvm.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdatesSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.updateDao()
        var newChaptersCount = 0

        // Cleanup updates older than 30 days BEFORE appending
        val threshold = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.deleteOldUpdates(threshold)

        try {
            val keys = getKeys(RESULT_BOOKMARK_STATE)
            for (key in keys ?: emptyList()) {
                val id = key.replaceFirst(RESULT_BOOKMARK_STATE, RESULT_BOOKMARK)
                val cached = getKey<ResultCached>(id) ?: continue
                android.util.Log.d("UpdatesSyncWorker", "Novel: ${cached.name}, isSyncEnabled: ${cached.isSyncEnabled}")
                if (!cached.isSyncEnabled) continue
                
                val api = Apis.getApiFromNameOrNull(cached.apiName) ?: continue
                val resource = api.load(cached.source)
                val response = (resource as? Resource.Success)?.value as? StreamResponse ?: continue

                // Take last 10 chapters
                val lastChapters = response.data.takeLast(10)
                
                for (chapter in lastChapters.reversed()) {
                    if (!dao.exists(chapter.url)) {
                        val item = UpdateItem(
                            novelUrl = cached.source,
                            novelName = cached.name,
                            chapterUrl = chapter.url,
                            chapterName = chapter.name,
                            uploadDate = System.currentTimeMillis(),
                            apiName = cached.apiName,
                            posterUrl = cached.poster,
                            chapterIndex = response.data.indexOf(chapter)
                        )
                        dao.insert(item)
                        newChaptersCount++
                    } else {
                        // Optimised break: if this chapter already exists, skip remaining
                        break
                    }
                }
            }
            if (newChaptersCount > 0) {
                val currentCount = com.lagradost.quicknovel.BaseApplication.Companion.getKey<Int>("NEW_UPDATES_COUNT") ?: 0
                com.lagradost.quicknovel.BaseApplication.Companion.setKey("NEW_UPDATES_COUNT", currentCount + newChaptersCount)
                val intent = android.content.Intent("com.lagradost.quicknovel.UPDATES_REFRESH")
                applicationContext.sendBroadcast(intent)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
