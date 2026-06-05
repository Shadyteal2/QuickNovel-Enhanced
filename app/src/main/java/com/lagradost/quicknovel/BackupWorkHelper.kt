package com.lagradost.quicknovel

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.*
import java.util.concurrent.TimeUnit

object BackupWorkHelper {
    private const val WORK_NAME = "com.lagradost.quicknovel.backup_work"

    fun scheduleBackupWorker(context: Context) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val interval = sharedPrefs.getString("auto_backup_interval", "never") ?: "never"
        val path = sharedPrefs.getString("auto_backup_path", null)

        val workManager = WorkManager.getInstance(context)

        if (interval == "never" || path.isNullOrBlank()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val repeatInterval = when (interval) {
            "daily" -> 24L to TimeUnit.HOURS
            "weekly" -> 7L to TimeUnit.DAYS
            "monthly" -> 30L to TimeUnit.DAYS
            else -> {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            repeatInterval.first, repeatInterval.second
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
