package com.lagradost.quicknovel.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.TelegramBackupPrefs
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.network.TelegramBackupServiceImpl
import com.lagradost.quicknovel.ui.settings.getBasePath
import com.lagradost.quicknovel.ui.settings.getDefaultDir
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class TelegramBackupWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val backupService = TelegramBackupServiceImpl()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Telegram Cloud Backup"
            val descriptionText = "Notifications for Telegram cloud backups"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("telegram_backup_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notificationId = 1338
        val notification = NotificationCompat.Builder(context, "telegram_backup_channel")
            .setSmallIcon(R.drawable.baseline_save_as_24)
            .setContentTitle("Telegram Cloud Backup")
            .setContentText("Initializing backup pipeline...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(current: Int, total: Int, contentText: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "telegram_backup_channel")
            .setSmallIcon(R.drawable.baseline_save_as_24)
            .setContentTitle("Telegram Cloud Backup")
            .setContentText(contentText)
            .setProgress(total, current, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        notificationManager.notify(1338, notification)
    }

    private fun showFinishedNotification(successCount: Int, totalCount: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "telegram_backup_channel")
            .setSmallIcon(R.drawable.baseline_save_as_24)
            .setContentTitle("Telegram Backup Completed")
            .setContentText("Successfully backed up $successCount of $totalCount novels.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(1338, notification)
    }

    private fun showFailedNotification(errorMessage: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "telegram_backup_channel")
            .setSmallIcon(R.drawable.rderror)
            .setContentTitle("Telegram Backup Failed")
            .setContentText(errorMessage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(1338, notification)
    }

    private fun getResizedThumbByteArray(context: Context, coverSafeFile: SafeFile?, coverIoFile: File?): ByteArray? {
        try {
            val bitmap = when {
                coverSafeFile != null -> {
                    val uri = coverSafeFile.uri() ?: return null
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                coverIoFile != null && coverIoFile.exists() -> {
                    BitmapFactory.decodeFile(coverIoFile.absolutePath)
                }
                else -> null
            } ?: return null

            // Scale bitmap so its maximum dimension is exactly 320px (upscaling if smaller, downscaling if larger)
            val maxDimension = 320
            val width = bitmap.width
            val height = bitmap.height

            val ratio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (ratio > 1f) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt()
            }
            
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val byteArray = outputStream.toByteArray()

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            return byteArray
        } catch (e: Exception) {
            Log.e("TelegramBackupWorker", "Failed to scale thumbnail bitmap", e)
            return null
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        var successCount = 0
        var totalCount = 0

        try {
            // Promote to foreground service immediately to ensure execution survives screen off
            try {
                setForeground(getForegroundInfo())
            } catch (e: Exception) {
                Log.e("TelegramBackupWorker", "Failed to set foreground service", e)
            }

            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val botToken = sharedPrefs.getString(TelegramBackupPrefs.BOT_TOKEN, null)
            val chatId = sharedPrefs.getString(TelegramBackupPrefs.CHAT_ID, null)
            val deleteAfterUpload = sharedPrefs.getBoolean(TelegramBackupPrefs.DELETE_AFTER_UPLOAD, false)
            val compileDownloads = sharedPrefs.getBoolean(TelegramBackupPrefs.COMPILE_DOWNLOADS, false)

            if (botToken.isNullOrBlank() || chatId.isNullOrBlank()) {
                val errorMsg = "Credentials missing. Verify Token and Chat ID under Storage settings."
                showFailedNotification(errorMsg)
                setProgress(
                    Data.Builder()
                        .putInt("progress_current", 0)
                        .putInt("progress_total", 0)
                        .putString("progress_status", errorMsg)
                        .putBoolean("is_active", false)
                        .build()
                )
                return@withContext Result.failure()
            }

            val baseDir = context.getBasePath().first ?: getDefaultDir(context)
            if (baseDir == null || baseDir.exists() != true || baseDir.isDirectory() != true) {
                val errorMsg = "Download directory not found or inaccessible."
                showFailedNotification(errorMsg)
                setProgress(
                    Data.Builder()
                        .putInt("progress_current", 0)
                        .putInt("progress_total", 0)
                        .putString("progress_status", errorMsg)
                        .putBoolean("is_active", false)
                        .build()
                )
                return@withContext Result.failure()
            }

            if (compileDownloads) {
                val db = AppDatabase.getDatabase(context)
                val downloadedNovels = db.novelDao().getFullDownloadedNovels(DownloadState.IsDone.ordinal)
                if (downloadedNovels.isNotEmpty()) {
                    val totalCompile = downloadedNovels.size
                    for ((idx, novel) in downloadedNovels.withIndex()) {
                        val currentNum = idx + 1
                        val compileStatus = "Compiling ($currentNum/$totalCompile): ${novel.name}"
                        
                        updateNotification(currentNum, totalCompile, compileStatus)
                        setProgress(
                            Data.Builder()
                                .putInt("progress_current", idx)
                                .putInt("progress_total", totalCompile)
                                .putString("progress_status", compileStatus)
                                .putBoolean("is_active", true)
                                .build()
                        )

                        try {
                            BookDownloader2Helper.turnToEpub(
                                context = context,
                                author = novel.author,
                                name = novel.name,
                                apiName = novel.apiName,
                                synopsis = novel.synopsis,
                                isSilent = true
                            )
                        } catch (e: Exception) {
                            Log.e("TelegramBackupWorker", "Failed to compile novel ${novel.name}", e)
                        }
                    }
                }
            }

            // Recursive bottom-up search of EPUB files and sibling images using Scoped Storage safe listFiles()
            val epubFiles = mutableListOf<SafeFile>()
            val coverFilesMap = mutableMapOf<SafeFile, SafeFile>()

            fun walk(dir: SafeFile) {
                val children = dir.listFiles() ?: return
                val epubs = children.filter { it.name()?.endsWith(".epub", ignoreCase = true) == true }
                val images = children.filter {
                    val n = it.name()?.lowercase() ?: ""
                    n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg")
                }

                epubs.forEach { epub ->
                    epubFiles.add(epub)
                    val epubNameWithoutExt = epub.name()?.substringBeforeLast(".epub", "")?.lowercase() ?: ""
                    val cover = images.firstOrNull { img ->
                        val imgName = img.name()?.lowercase() ?: ""
                        imgName == "poster.jpg" ||
                        imgName == "cover.jpg" ||
                        imgName == "poster.png" ||
                        imgName == "cover.png" ||
                        imgName == "${epubNameWithoutExt}.jpg" ||
                        imgName == "${epubNameWithoutExt}.png" ||
                        imgName == "${epubNameWithoutExt}.jpeg"
                    }
                    if (cover != null) {
                        coverFilesMap[epub] = cover
                    }
                }

                children.forEach { child ->
                    if (child.isDirectory() == true) {
                        walk(child)
                    }
                }
            }
            walk(baseDir)

            Log.i("TelegramBackup", "Found ${epubFiles.size} EPUB files to backup")

            if (epubFiles.isEmpty()) {
                val msg = "No EPUB files found in downloads directory."
                showFinishedNotification(0, 0)
                setProgress(
                    Data.Builder()
                        .putInt("progress_current", 0)
                        .putInt("progress_total", 0)
                        .putString("progress_status", msg)
                        .putBoolean("is_active", false)
                        .build()
                )
                return@withContext Result.success()
            }

            val db = AppDatabase.getDatabase(context)
            val downloadedNovels = db.novelDao().getDownloadedNovels(DownloadState.IsDone.ordinal)

            // Associate the novels by their sanitized names
            val novelMap = downloadedNovels.associateBy {
                BookDownloader2Helper.sanitizeFilename(it.name).lowercase()
            }

            totalCount = epubFiles.size

            setProgress(
                Data.Builder()
                    .putInt("progress_current", 0)
                    .putInt("progress_total", totalCount)
                    .putString("progress_status", "Starting upload of $totalCount novels...")
                    .putBoolean("is_active", true)
                    .build()
            )

            for ((index, epubFile) in epubFiles.withIndex()) {
                val currentNum = index + 1
                val rawName = epubFile.name() ?: "novel.epub"

                try {
                    val nameWithoutExt = rawName.substringBeforeLast(".epub", "").lowercase()
                    val matchedNovel = novelMap[nameWithoutExt]

                    val statusText = "Uploading ($currentNum/$totalCount): $rawName"
                    updateNotification(currentNum, totalCount, statusText)
                    setProgress(
                        Data.Builder()
                            .putInt("progress_current", index)
                            .putInt("progress_total", totalCount)
                            .putString("progress_status", statusText)
                            .putBoolean("is_active", true)
                            .build()
                    )

                    val caption = if (matchedNovel != null) {
                        buildString {
                            append("<b>Novel:</b> ${matchedNovel.name}\n")
                            if (!matchedNovel.author.isNullOrBlank()) {
                                append("<b>Author:</b> ${matchedNovel.author}\n")
                            }
                            append("<b>Provider:</b> ${matchedNovel.apiName}\n\n")
                            append("Backed up via ⚡ <a href=\"https://github.com/Shadyteal2/QuickNovel-Enhanced\">NeoQN</a> | <a href=\"https://t.me/neoqnnovelreader\">Telegram</a>")
                        }
                    } else {
                        buildString {
                            append("<b>File:</b> $rawName\n\n")
                            append("Backed up via ⚡ <a href=\"https://github.com/Shadyteal2/QuickNovel-Enhanced\">NeoQN</a> | <a href=\"https://t.me/neoqnnovelreader\">Telegram</a>")
                        }
                    }

                    // Dynamic cover search: first check sibling cover from SafeFile walk map
                    val siblingCover = coverFilesMap[epubFile]
                    var thumbBytes: ByteArray? = null

                    if (siblingCover != null) {
                        thumbBytes = getResizedThumbByteArray(context, siblingCover, null)
                    }

                    // Fallback to cache directory poster
                    if (thumbBytes == null && matchedNovel != null) {
                        val sApiName = BookDownloader2Helper.sanitizeFilename(matchedNovel.apiName)
                        val sAuthor = BookDownloader2Helper.sanitizeFilename(matchedNovel.author ?: "")
                        val sName = BookDownloader2Helper.sanitizeFilename(matchedNovel.name)
                        val posterPath = context.filesDir.toString() + BookDownloader2Helper.getFilenameIMG(sApiName, sAuthor, sName)
                        val posterFile = File(posterPath)
                        if (posterFile.exists() && posterFile.length() > 0) {
                            thumbBytes = getResizedThumbByteArray(context, null, posterFile)
                        }
                    }

                    val uploadResult = backupService.uploadDocument(
                        context = context,
                        botToken = botToken,
                        chatId = chatId,
                        documentFile = epubFile,
                        caption = caption,
                        thumbBytes = thumbBytes
                    )

                    when (uploadResult) {
                        is Resource.Success -> {
                            successCount++
                            if (deleteAfterUpload) {
                                try {
                                    epubFile.delete()
                                    Log.i("TelegramBackupWorker", "Deleted local EPUB after upload: $rawName")
                                } catch (t: Throwable) {
                                    Log.e("TelegramBackupWorker", "Failed to delete local EPUB: $rawName", t)
                                }
                            }
                        }
                        is Resource.Failure -> {
                            Log.e("TelegramBackupWorker", "Upload failed for $rawName: ${uploadResult.errorString}")
                        }
                        else -> {}
                    }

                    // Rate-limiting delay to guarantee we don't exceed Telegram's single-chat message frequency limits
                    kotlinx.coroutines.delay(2000L)

                } catch (e: CancellationException) {
                    throw e // Re-throw coroutine cancellation
                } catch (e: Exception) {
                    Log.e("TelegramBackupWorker", "Failed to upload epub in loop: $rawName", e)
                }
            }

            val finalMsg = "Completed: Backed up $successCount of $totalCount novels."
            showFinishedNotification(successCount, totalCount)
            setProgress(
                Data.Builder()
                    .putInt("progress_current", successCount)
                    .putInt("progress_total", totalCount)
                    .putString("progress_status", finalMsg)
                    .putBoolean("is_active", false)
                    .build()
            )

        } catch (t: Throwable) {
            val errorMsg = "Backup interrupted: ${t.localizedMessage}"
            Log.e("TelegramBackupWorker", "Error during backup execution", t)
            showFailedNotification(errorMsg)
            setProgress(
                Data.Builder()
                    .putInt("progress_current", 0)
                    .putInt("progress_total", 0)
                    .putString("progress_status", errorMsg)
                    .putBoolean("is_active", false)
                    .build()
            )
        }

        return@withContext Result.success()
    }
}
