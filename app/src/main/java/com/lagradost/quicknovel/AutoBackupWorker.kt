package com.lagradost.quicknovel

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.util.BackupUtils
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AutoBackupWorker(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val uriString = sharedPrefs.getString("auto_backup_path", null)
            if (uriString.isNullOrBlank()) {
                return@withContext Result.failure()
            }

            val targetDir = SafeFile.fromUri(context, Uri.parse(uriString))
            if (targetDir == null || targetDir.exists() != true || targetDir.isDirectory() != true) {
                return@withContext Result.failure()
            }

            BackupUtils.backupToDirectory(context, targetDir)
            Result.success()
        } catch (e: Exception) {
            com.lagradost.quicknovel.mvvm.logError(e)
            Result.retry()
        }
    }
}
