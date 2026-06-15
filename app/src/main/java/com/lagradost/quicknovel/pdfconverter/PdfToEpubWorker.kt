package com.lagradost.quicknovel.pdfconverter

import android.content.Context
import android.os.Build
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.extractors.BookImporter
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.settings.getBasePath
import com.lagradost.quicknovel.ui.settings.getDefaultDir
import com.lagradost.safefile.SafeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class PdfToEpubWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.rdload)
            .setContentTitle("Converting PDF")
            .setContentText("Building EPUB in the background...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return@withContext Result.failure()
        val inputFile = File(filePath)
        if (!inputFile.exists()) return@withContext Result.failure()

        val rawTitle = inputData.getString(KEY_TITLE).orEmpty()
        val rawAuthor = inputData.getString(KEY_AUTHOR).orEmpty()
        val autoFixParagraphs = inputData.getBoolean(KEY_AUTO_FIX, true)
        val exportDownloads = inputData.getBoolean(KEY_EXPORT_DOWNLOADS, false)
        val title = BookDownloader2Helper.sanitizeFilename(rawTitle.ifBlank { "Converted PDF" })
        val author = BookDownloader2Helper.sanitizeFilename(rawAuthor.ifBlank { "Unknown Author" })

        try {
            try {
                setForeground(getForegroundInfo())
            } catch (t: Throwable) {
                logError(t)
            }

            val outputDir = File(context.filesDir, "Imports/PdfToEpub").apply { mkdirs() }
            val outputFile = File(outputDir, "${System.currentTimeMillis()}_${title}.epub")
            PdfToEpubConverter.convert(
                context = context,
                request = PdfToEpubRequest(
                    file = inputFile,
                    title = title,
                    author = author,
                    autoFixParagraphs = autoFixParagraphs
                ),
                outputFile = outputFile,
                onProgress = { progress ->
                    setProgress(
                        Data.Builder()
                            .putInt(PROGRESS_PAGE, progress.page)
                            .putInt(PROGRESS_TOTAL, progress.totalPages)
                            .build()
                    )
                }
            )

            val hash = hashFile(outputFile)
            val dao = AppDatabase.getDatabase(context).novelDao()
            if (dao.getByHash(hash) != null) {
                outputFile.delete()
                return@withContext Result.success(Data.Builder().putBoolean(KEY_IS_DUPLICATE, true).build())
            }

            val coverFileName = BookDownloader2Helper.getFilenameIMG(IMPORT_SOURCE, author, title)
            File(context.filesDir.toString() + coverFileName).apply {
                parentFile?.mkdirs()
                writeBytes(BookImporter.generatePlaceholderCover(context, title))
            }

            val now = System.currentTimeMillis()
            val entityId = "$IMPORT_SOURCE$author$title".hashCode()
            dao.insert(
                NovelEntity(
                    id = entityId,
                    source = outputFile.toURI().toString(),
                    name = title,
                    author = author,
                    posterUrl = IMPORT_SOURCE,
                    rating = 0,
                    peopleVoted = 0,
                    views = 0,
                    synopsis = "Converted from a local PDF.\n\nFile Size: ${android.text.format.Formatter.formatFileSize(context, outputFile.length())}",
                    tags = arrayListOf("EPUB", "PDF CONVERTED"),
                    apiName = IMPORT_SOURCE,
                    lastUpdated = now,
                    lastDownloaded = now,
                    filePath = outputFile.absolutePath,
                    formatType = "epub",
                    hash = hash
                )
            )

            // If selected, copy the finalized EPUB to the custom download path set in preferences
            if (exportDownloads) {
                try {
                    val (subDir, _) = context.getBasePath()
                    val targetDir = subDir ?: getDefaultDir(context)
                    if (targetDir != null) {
                        val displayName = "${title}.epub"
                        targetDir.findFile(displayName)?.delete()
                        val exportFile = targetDir.createFileOrThrow(displayName)
                        exportFile.openOutputStream(append = false)?.use { outputStream ->
                            outputFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    logError(t)
                }
            }

            Result.success()
        } catch (t: Throwable) {
            logError(t)
            Result.failure()
        } finally {
            runCatching { inputFile.delete() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "PDF conversion",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun hashFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val KEY_FILE_PATH = "filePath"
        const val KEY_TITLE = "title"
        const val KEY_AUTHOR = "author"
        const val KEY_AUTO_FIX = "autoFix"
        const val KEY_EXPORT_DOWNLOADS = "exportDownloads"
        const val KEY_IS_DUPLICATE = "isDuplicate"
        const val PROGRESS_PAGE = "page"
        const val PROGRESS_TOTAL = "totalPages"
        private const val CHANNEL_ID = "pdf_converter.general"
        private const val NOTIFICATION_ID = 7342
    }
}
