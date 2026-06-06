package com.lagradost.quicknovel

import android.content.Context
import androidx.work.*
import com.lagradost.quicknovel.sync.GoogleSyncWorker
import com.lagradost.quicknovel.util.GoogleDriveSyncManager
import java.util.concurrent.TimeUnit

object GoogleSyncWorkHelper {
    private const val WORK_NAME = "com.lagradost.quicknovel.google_sync_work"

    fun scheduleSyncWorker(context: Context) {
        val workManager = WorkManager.getInstance(context)

        val isAutoSync = GoogleDriveSyncManager.isAutoSyncEnabled(context)
        val hasAccount = GoogleDriveSyncManager.getAccountEmail(context) != null

        if (!isAutoSync || !hasAccount) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<GoogleSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
