package com.lagradost.quicknovel.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.extractors.BookImporter
import com.lagradost.quicknovel.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class MetadataExtractionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStr = inputData.getString("uri") ?: return@withContext Result.failure()
        val uri = Uri.parse(uriStr)
        val fileName = inputData.getString("fileName") ?: "Unknown"
        val mimeType = inputData.getString("mimeType")

        try {
            // Quick Hash: first 100KB + file size
            val (quickHash, fullFileBytes, size) = quickHashFile(uri)
            if (quickHash == null) return@withContext Result.failure()

            val dao = AppDatabase.getDatabase(context).novelDao()
            // In a complete implementation, Quick Hash would be stored in the DB.
            // For now, we just compute full hash and check if it exists in the DB.
            val fullHash = hashBytes(fullFileBytes)
            
            val existing = dao.getByHash(fullHash)
            if (existing != null) {
                // Duplicate found
                withContext(Dispatchers.Main) {
                    triggerHaptic(false)
                    try {
                        android.widget.Toast.makeText(context, "Duplicate book skipped.", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        // Ignore UI thread errors if detached
                    }
                }
                return@withContext Result.failure() 
            }

            // Extract Metadata
            val metadata = BookImporter.extractMetadata(context, uri, mimeType, fileName)
            val title = metadata?.title ?: fileName.substringBeforeLast('.')
            val author = metadata?.author ?: "Unknown Author"

            // Save Cover Image
            val ext = fileName.substringAfterLast('.', "")
            val localFileName = "$fullHash.$ext"
            val importDir = File(context.filesDir, "Imports")
            importDir.mkdirs()
            val savedFile = File(importDir, localFileName)
            
            savedFile.writeBytes(fullFileBytes)

            // Inject Cover using built-in IMPORT_SOURCE to route to the correct cache path
            if (metadata?.coverImage != null) {
                val coverFileName = BookDownloader2Helper.getFilenameIMG(IMPORT_SOURCE, author, title)
                File(context.filesDir.toString() + coverFileName).apply {
                    parentFile?.mkdirs()
                    writeBytes(metadata.coverImage)
                }
            }

            // Save to DB
            val entityId = "$IMPORT_SOURCE${author}${title}".hashCode()
            
            dao.insert(
                NovelEntity(
                    id = entityId,
                    source = uri.toString(),
                    name = title,
                    author = author, // Triggers BookDownloader2Helper.getCachedBitmap
                    posterUrl = IMPORT_SOURCE, 
                    rating = 0,
                    peopleVoted = 0,
                    views = 0,
                    synopsis = "Locally imported book.\n\nFile Size: ${android.text.format.Formatter.formatFileSize(context, size)}",
                    tags = arrayListOf(ext.uppercase()),
                    apiName = IMPORT_SOURCE,
                    lastUpdated = System.currentTimeMillis(),
                    lastDownloaded = System.currentTimeMillis(),
                    filePath = savedFile.absolutePath,
                    formatType = ext,
                    hash = fullHash
                )
            )

            withContext(Dispatchers.Main) {
                triggerHaptic(true)
            }
            Result.success()
        } catch (e: Exception) {
            logError(e)
            Result.failure()
        }
    }

    private fun triggerHaptic(success: Boolean) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (success) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                } else {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK))
                }
            } else {
                vibrator.vibrate(if (success) 50L else 150L)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    private fun quickHashFile(uri: Uri): Triple<String?, ByteArray, Long> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Triple(null, ByteArray(0), 0L)
        
        val size = bytes.size.toLong()
        val chunk = if (size > 100 * 1024) bytes.copyOfRange(0, 100 * 1024) else bytes
        val md = MessageDigest.getInstance("MD5")
        md.update(chunk)
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        return Triple("${hash}_$size", bytes, size)
    }

    private fun hashBytes(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
