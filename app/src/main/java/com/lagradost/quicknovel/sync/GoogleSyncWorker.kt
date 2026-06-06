package com.lagradost.quicknovel.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.util.GoogleDriveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!GoogleDriveSyncManager.isAutoSyncEnabled(applicationContext) ||
            GoogleDriveSyncManager.getAccountEmail(applicationContext) == null
        ) {
            return@withContext Result.success()
        }

        val success = GoogleDriveSyncManager.syncNow(applicationContext)
        if (success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
