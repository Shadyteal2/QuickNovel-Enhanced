package com.lagradost.quicknovel.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.BookDownloader2Helper.checkWrite
import com.lagradost.quicknovel.BookDownloader2Helper.requestRW
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DataStore
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.removeKey
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.PREFERENCES_NAME
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.util.BookmarkMigrationManager.MIGRATION_KEY
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.settings.getDefaultDir
import com.lagradost.safefile.SafeFile
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object BackupUtils {
    const val SYNC_BOOKMARKS_KEY = "sync_bookmarks"
    const val SYNC_SETTINGS_KEY = "sync_settings"
    const val SYNC_HISTORY_KEY = "sync_history"

    fun getSyncBookmarks(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SYNC_BOOKMARKS_KEY, true)
    }

    fun getSyncSettings(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SYNC_SETTINGS_KEY, true)
    }

    fun getSyncHistory(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SYNC_HISTORY_KEY, true)
    }

    fun isHistoryKey(key: String): Boolean {
        return key.startsWith("reader_epub_position") || 
               key.startsWith("result_history") || 
               key.contains("history")
    }

    fun isBookmarkKey(key: String): Boolean {
        return key.startsWith("result_bookmarked") || 
               key.startsWith("result_chapter_bookmarked") || 
               key.startsWith("result_bookmarked_state")
    }

    suspend fun generateBackup(context: Context): BackupFile = withContext(Dispatchers.IO) {
        val syncBookmarks = getSyncBookmarks(context)
        val syncSettings = getSyncSettings(context)
        val syncHistory = getSyncHistory(context)

        val allData = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all
        val allSettings = PreferenceManager.getDefaultSharedPreferences(context).all

        val allDataFiltered = withContext(Dispatchers.Default) {
            allData.filterKeys { key ->
                if (isDownloadKey(key)) return@filterKeys false
                val isHistory = isHistoryKey(key)
                val isBookmark = isBookmarkKey(key)
                when {
                    isHistory -> syncHistory
                    isBookmark -> syncBookmarks
                    else -> true
                }
            }
        }

        val allDataSorted = withContext(Dispatchers.Default) {
            BackupVars(
                allDataFiltered.filter { entry -> entry.value is Boolean } as? Map<String, Boolean>,
                allDataFiltered.filter { entry -> entry.value is Int } as? Map<String, Int>,
                allDataFiltered.filter { entry -> entry.value is String } as? Map<String, String>,
                allDataFiltered.filter { entry -> entry.value is Float } as? Map<String, Float>,
                allDataFiltered.filter { entry -> entry.value is Long } as? Map<String, Long>,
                allDataFiltered.filter { entry -> entry.value as? Set<*> != null } as? Map<String, Set<String>>
            )
        }

        val allSettingsFiltered = withContext(Dispatchers.Default) {
            if (syncSettings) {
                allSettings.filterKeys { !isDownloadKey(it) }
            } else if (syncBookmarks) {
                allSettings.filterKeys { key ->
                    key == "download_settings/CUSTOM_CATEGORIES" || key == "download_settings/CATEGORIES_ORDER"
                }
            } else {
                emptyMap()
            }
        }

        val allSettingsSorted = withContext(Dispatchers.Default) {
            BackupVars(
                allSettingsFiltered.filter { entry -> entry.value is Boolean } as? Map<String, Boolean>,
                allSettingsFiltered.filter { entry -> entry.value is Int } as? Map<String, Int>,
                allSettingsFiltered.filter { entry -> entry.value is String } as? Map<String, String>,
                allSettingsFiltered.filter { entry -> entry.value is Float } as? Map<String, Float>,
                allSettingsFiltered.filter { entry -> entry.value is Long } as? Map<String, Long>,
                allSettingsFiltered.filter { entry -> entry.value as? Set<*> != null } as? Map<String, Set<String>>
            )
        }

        val novels = if (syncBookmarks) {
            com.lagradost.quicknovel.db.AppDatabase.getDatabase(context).novelDao().getAll()
                .filter { it.bookmarkType != null && it.bookmarkType != 0 }
        } else {
            emptyList<com.lagradost.quicknovel.db.NovelEntity>()
        }

        val customThemes = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.themes.value
        val contentRules = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.rules.value
        BackupFile(allDataSorted, allSettingsSorted, novels, customThemes, contentRules)
    }

    private var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

    // Kinda hack, but I couldn't think of a better way
    data class BackupVars(
        @JsonProperty("_Bool") val _Bool: Map<String, Boolean>?,
        @JsonProperty("_Int") val _Int: Map<String, Int>?,
        @JsonProperty("_String") val _String: Map<String, String>?,
        @JsonProperty("_Float") val _Float: Map<String, Float>?,
        @JsonProperty("_Long") val _Long: Map<String, Long>?,
        @JsonProperty("_StringSet") val _StringSet: Map<String, Set<String>?>?,
    )

    data class BackupFile(
        @JsonProperty("datastore") val datastore: BackupVars,
        @JsonProperty("settings") val settings: BackupVars,
        @JsonProperty("novels") val novels: List<com.lagradost.quicknovel.db.NovelEntity>? = null,
        @JsonProperty("custom_themes") val customThemes: List<com.lagradost.quicknovel.ui.reader.customization.ReaderTheme>? = null,
        @JsonProperty("content_rules") val contentRules: List<com.lagradost.quicknovel.ui.reader.customization.ContentCleanRule>? = null
    )

    fun setupStream(context: Context, displayName : String, ext : String, subDir : SafeFile?) : Pair<OutputStream?, Uri?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // && subDir?.isDownloadDir() == true
            val cr = context.contentResolver
            val contentUri =
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI
            //val currentMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

            val newFile = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.TITLE, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                //put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }

            val newFileUri = cr.insert(
                contentUri,
                newFile
            ) ?: throw IOException("Error creating file uri")
            val stream = cr.openOutputStream(newFileUri, "w")
                ?: throw IOException("Error opening stream")
            return stream to newFileUri
        } else {
            val fileName = "$displayName.$ext"
            val rFile = subDir?.findFile(fileName)
            if (rFile?.exists() == true) {
                rFile.delete()
            }
            val file =
                subDir?.createFile(fileName)
                    ?: throw IOException("Error creating file")
            if (file.exists() != true) throw IOException("File does not exist")
            val stream = file.openOutputStream()
            return stream to file.uri()
        }
    }

    private fun isDownloadKey(key: String): Boolean {
        // QN-Enhanced: Strictly ignore all download-related meta/content to reduce backup size
        // and prevent ghost downloads from appearing post-restore.
        // QN-Enhanced: Allow custom category definitions to be backed up and restored.
        if (key == "download_settings/CUSTOM_CATEGORIES" || key == "download_settings/CATEGORIES_ORDER") return false

        return key.startsWith("downloads_data/") ||
               key.startsWith("downloads_data") || // Catch folder itself
               key.startsWith("download_settings") ||
               key.startsWith("downloads_size/") ||
               key.startsWith("downloads_total/") ||
               key.startsWith("downloads_offset/") ||
               key.startsWith("downloads_epub_size/") ||
               key.startsWith("downloads_epub_last_access/") ||
               key.startsWith("downloads_sort") || // Catch sorting preferences
               key.contains("download_history") // Catch any history related to downloads
    }

    fun backupToDirectory(context: Context, targetDir: SafeFile) {
        // Run database checkpoint on Room to make sure WAL files are merged before copying
        try {
            com.lagradost.quicknovel.db.AppDatabase.getDatabase(context)
                .openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);")
        } catch (e: Exception) {
            logError(e)
        }

        val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
        val displayName = "neoQN_Backup_${date}"
        val fileName = "$displayName.json"

        val allData = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all
        val allSettings = PreferenceManager.getDefaultSharedPreferences(context).all

        val allDataFiltered = allData.filterKeys { !isDownloadKey(it) }

        val allDataSorted = BackupVars(
            allDataFiltered.filter { it.value is Boolean } as? Map<String, Boolean>,
            allDataFiltered.filter { it.value is Int } as? Map<String, Int>,
            allDataFiltered.filter { it.value is String } as? Map<String, String>,
            allDataFiltered.filter { it.value is Float } as? Map<String, Float>,
            allDataFiltered.filter { it.value is Long } as? Map<String, Long>,
            allDataFiltered.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val allSettingsFiltered = allSettings.filterKeys { !isDownloadKey(it) }

        val allSettingsSorted = BackupVars(
            allSettingsFiltered.filter { it.value is Boolean } as? Map<String, Boolean>,
            allSettingsFiltered.filter { it.value is Int } as? Map<String, Int>,
            allSettingsFiltered.filter { it.value is String } as? Map<String, String>,
            allSettingsFiltered.filter { it.value is Float } as? Map<String, Float>,
            allSettingsFiltered.filter { it.value is Long } as? Map<String, Long>,
            allSettingsFiltered.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val novels = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context).novelDao().getAll()
            .filter { it.bookmarkType != null && it.bookmarkType != 0 }

        val customThemes = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.themes.value
        val contentRules = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.rules.value

        val backupFile = BackupFile(
            allDataSorted,
            allSettingsSorted,
            novels,
            customThemes,
            contentRules
        )

        val rFile = targetDir.findFile(fileName)
        if (rFile?.exists() == true) {
            rFile.delete()
        }
        val file = targetDir.createFile(fileName) ?: throw IOException("Error creating file")
        if (file.exists() != true) throw IOException("File does not exist")
        val stream = file.openOutputStream() ?: throw IOException("Error opening export stream")

        val printStream = PrintWriter(stream)
        printStream.print(mapper.writeValueAsString(backupFile))
        printStream.close()

        pruneOldBackups(targetDir)
    }

    fun pruneOldBackups(targetDir: SafeFile, maxBackupCount: Int = 5) {
        try {
            val files = targetDir.listFiles() ?: return
            val backupFiles = files.filter {
                val name = it.name() ?: ""
                name.startsWith("neoQN_Backup_") && name.endsWith(".json")
            }
            if (backupFiles.size > maxBackupCount) {
                val sorted = backupFiles.sortedBy { it.lastModified() ?: 0L }
                val deleteCount = backupFiles.size - maxBackupCount
                for (i in 0 until deleteCount) {
                    sorted[i].delete()
                }
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun FragmentActivity.backup() {
        thread {
            try {
                if (checkWrite()) {
                    val subDir = getDefaultDir(context = this)
                    val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
                    val displayName = "neoQN_Backup_${date}"

                    val backupFile = kotlinx.coroutines.runBlocking {
                        generateBackup(this@backup)
                    }
                    val (stream, fileUri) = setupStream(this, displayName, "json", subDir)
                    if (stream == null) throw IOException("Error creating export stream")

                    val printStream = PrintWriter(stream)
                    printStream.print(mapper.writeValueAsString(backupFile))
                    printStream.close()

                    showToast(
                        R.string.backup_success,
                        Toast.LENGTH_LONG
                    )

                    // QN-Enhanced: Open Share Panel immediately after backup
                    if (fileUri != null) {
                        runOnUiThread {
                            try {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                startActivity(android.content.Intent.createChooser(shareIntent, "Save or Share Backup"))
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }
                    }

                } else {
                    showToast(getString(R.string.backup_failed), Toast.LENGTH_LONG)
                    requestRW()
                }
            } catch (e: Exception) {
                logError(e)
                try {
                    showToast(
                        getString(R.string.backup_failed_error_format).format(e.toString()),
                        Toast.LENGTH_LONG
                    )
                } catch (e: Exception) {
                    logError(e)
                }
            }
        }
    }

    fun FragmentActivity.setUpBackup() {
        try {
            restoreFileSelector =
                registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    this.let { activity ->
                        uri?.let {
                            try {
                                val input =
                                    activity.contentResolver.openInputStream(uri)
                                        ?: return@registerForActivityResult

                                val restoredValue =
                                    mapper.readValue<BackupFile>(input)

                                thread {
                                    kotlinx.coroutines.runBlocking {
                                        activity.restore(
                                            restoredValue,
                                            restoreSettings = true,
                                            restoreDataStore = true
                                        )
                                    }
                                    activity.runOnUiThread {
                                        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.AlertDialogCustom)
                                            .setTitle(R.string.backup_restored_title)
                                            .setMessage(R.string.backup_restored_message)
                                            .setCancelable(false)
                                            .setPositiveButton(R.string.got_it) { _, _ ->
                                                activity.finishAffinity()
                                            }.show()
                                    }
                                }
                                input.close()
                            } catch (e: Exception) {
                                logError(e)
                                try { // smth can fail in .format
                                    showToast(
                                        getString(R.string.restore_failed_format).format(e.toString())
                                    )
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun FragmentActivity.restorePrompt() {
        runOnUiThread {
            try {
                restoreFileSelector?.launch(
                    arrayOf(
                        "text/plain",
                        "text/str",
                        "text/x-unknown",
                        "application/json",
                        "unknown/unknown",
                        "content/unknown",
                    )
                )
            } catch (e: Exception) {
                showToast(e.message)
                logError(e)
            }
        }
    }

    private fun isDeprecatedKey(key: String): Boolean {
        return key == "living_glass_key" ||
               key == "aura_intensity_key" ||
               key == "aura_speed_key" ||
               key == "aura_palette_key"
    }

    private fun <T> Context.restoreMapFiltered(
        map: Map<String, T>?,
        syncBookmarks: Boolean,
        syncHistory: Boolean,
        isEditingAppSettings: Boolean = false
    ) {
        if (map == null) return
        val editor = DataStore.editor(this, isEditingAppSettings)
        map.forEach { entry ->
            val key = entry.key
            if (!isDownloadKey(key) && !isDeprecatedKey(key)) {
                val isHistory = isHistoryKey(key)
                val isBookmark = isBookmarkKey(key)
                val shouldRestore = when {
                    isHistory -> syncHistory
                    isBookmark -> syncBookmarks
                    else -> true
                }
                if (shouldRestore) {
                    editor.setKeyRaw(key, entry.value)
                }
            }
        }
        editor.apply()
    }

    private fun <T> Context.restoreMap(
        map: Map<String, T>?,
        isEditingAppSettings: Boolean = false
    ) {
        val editor = DataStore.editor(this, isEditingAppSettings)
        map?.forEach {
            if (!isDownloadKey(it.key) && !isDeprecatedKey(it.key)) {
                editor.setKeyRaw(it.key, it.value)
            }
        }
        editor.apply()
    }

    suspend fun Context.restore(
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean
    ) = withContext(Dispatchers.IO) {
        val syncBookmarks = getSyncBookmarks(this@restore)
        val syncSettings = getSyncSettings(this@restore) && restoreSettings
        val syncHistory = getSyncHistory(this@restore)

        if (syncSettings) {
            this@restore.restoreMap(backupFile.settings._Bool, true)
            this@restore.restoreMap(backupFile.settings._Int, true)
            this@restore.restoreMap(backupFile.settings._String, true)
            this@restore.restoreMap(backupFile.settings._Float, true)
            this@restore.restoreMap(backupFile.settings._Long, true)
            this@restore.restoreMap(backupFile.settings._StringSet, true)
        } else if (syncBookmarks) {
            val filterBlock: (String) -> Boolean = { key ->
                key == "download_settings/CUSTOM_CATEGORIES" || key == "download_settings/CATEGORIES_ORDER"
            }
            this@restore.restoreMap(backupFile.settings._Bool?.filterKeys(filterBlock), true)
            this@restore.restoreMap(backupFile.settings._Int?.filterKeys(filterBlock), true)
            this@restore.restoreMap(backupFile.settings._String?.filterKeys(filterBlock), true)
            this@restore.restoreMap(backupFile.settings._Float?.filterKeys(filterBlock), true)
            this@restore.restoreMap(backupFile.settings._Long?.filterKeys(filterBlock), true)
            this@restore.restoreMap(backupFile.settings._StringSet?.filterKeys(filterBlock), true)
        }

        if (restoreDataStore) {
            withContext(Dispatchers.Default) {
                this@restore.restoreMapFiltered(backupFile.datastore._Bool, syncBookmarks, syncHistory)
                this@restore.restoreMapFiltered(backupFile.datastore._Int, syncBookmarks, syncHistory)
                this@restore.restoreMapFiltered(backupFile.datastore._String, syncBookmarks, syncHistory)
                this@restore.restoreMapFiltered(backupFile.datastore._Float, syncBookmarks, syncHistory)
                this@restore.restoreMapFiltered(backupFile.datastore._Long, syncBookmarks, syncHistory)
                this@restore.restoreMapFiltered(backupFile.datastore._StringSet, syncBookmarks, syncHistory)
            }

            if (syncBookmarks) {
                if (backupFile.novels != null && backupFile.novels.isNotEmpty()) {
                    com.lagradost.quicknovel.db.AppDatabase.getDatabase(this@restore).novelDao().insertAll(backupFile.novels)
                } else {
                    this@restore.setKey(MIGRATION_KEY, false)
                }
            }
        }
        backupFile.customThemes?.forEach { theme ->
            com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.saveTheme(this@restore, theme)
        }
        backupFile.contentRules?.let { rules ->
            com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.saveRules(this@restore, rules)
        }
        DataStore.clearCache()
    }

    fun backupToFile(context: Context, file: java.io.File) {
        // Run database checkpoint on Room to make sure WAL files are merged before copying
        try {
            com.lagradost.quicknovel.db.AppDatabase.getDatabase(context)
                .openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);")
        } catch (e: Exception) {
            logError(e)
        }

        val allData = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all
        val allSettings = PreferenceManager.getDefaultSharedPreferences(context).all

        val allDataFiltered = allData.filterKeys { !isDownloadKey(it) }

        val allDataSorted = BackupVars(
            allDataFiltered.filter { it.value is Boolean } as? Map<String, Boolean>,
            allDataFiltered.filter { it.value is Int } as? Map<String, Int>,
            allDataFiltered.filter { it.value is String } as? Map<String, String>,
            allDataFiltered.filter { it.value is Float } as? Map<String, Float>,
            allDataFiltered.filter { it.value is Long } as? Map<String, Long>,
            allDataFiltered.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val allSettingsFiltered = allSettings.filterKeys { !isDownloadKey(it) }

        val allSettingsSorted = BackupVars(
            allSettingsFiltered.filter { it.value is Boolean } as? Map<String, Boolean>,
            allSettingsFiltered.filter { it.value is Int } as? Map<String, Int>,
            allSettingsFiltered.filter { it.value is String } as? Map<String, String>,
            allSettingsFiltered.filter { it.value is Float } as? Map<String, Float>,
            allSettingsFiltered.filter { it.value is Long } as? Map<String, Long>,
            allSettingsFiltered.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
        )

        val novels = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context).novelDao().getAll()
            .filter { it.bookmarkType != null && it.bookmarkType != 0 }

        val customThemes = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.themes.value
        val contentRules = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationStore.rules.value

        val backupFile = BackupFile(
            allDataSorted,
            allSettingsSorted,
            novels,
            customThemes,
            contentRules
        )

        file.outputStream().use { stream ->
            mapper.writeValue(stream, backupFile)
        }
    }

    suspend fun migrateNovel(context: Context, oldId: Int, newNovel: LoadResponse, newApiName: String) = withContext(Dispatchers.IO) {
        val db = com.lagradost.quicknovel.db.AppDatabase.getDatabase(context)
        val dao = db.novelDao()
        val oldEntity = dao.getById(oldId) ?: return@withContext

        val newId = BookDownloader2Helper.generateId(newNovel, newApiName)

        // Clean delete old novel downloaded files (and old entity download meta in DB)
        BookDownloader2Helper.deleteNovel(context, oldEntity.author, oldEntity.name, oldEntity.apiName)

        // Clone/map the new entity
        val newEntity = oldEntity.copy(
            id = newId,
            source = newNovel.url,
            apiName = newApiName,
            author = newNovel.author ?: oldEntity.author,
            posterUrl = newNovel.posterUrl ?: oldEntity.posterUrl,
            rating = newNovel.rating ?: oldEntity.rating,
            peopleVoted = newNovel.peopleVoted ?: oldEntity.peopleVoted,
            views = newNovel.views ?: oldEntity.views,
            synopsis = newNovel.synopsis ?: oldEntity.synopsis,
            tags = newNovel.tags ?: oldEntity.tags,
            // Reset download fields to reflect clean deletion
            downloadStatus = null,
            downloadProgress = null,
            downloadTotal = null,
            filePath = null,
            lastDownloaded = null
        )

        // Insert new entity
        dao.insert(newEntity)

        // Safely remove the old entity from Room database
        dao.deleteById(oldId)

        // Shared preferences notes migration: copy the raw JSON string directly to be fail-safe
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val noteJson = prefs.getString("RESULT_USER_NOTE/$oldId", null)
        if (noteJson != null) {
            DataStore.removeFromCache("RESULT_USER_NOTE/$oldId")
            DataStore.removeFromCache("RESULT_USER_NOTE/$newId")
            prefs.edit().putString("RESULT_USER_NOTE/$newId", noteJson).remove("RESULT_USER_NOTE/$oldId").apply()
        }

        val history = context.getKey<ResultCached>(HISTORY_FOLDER, oldId.toString())
        if (history != null) {
            context.setKey(HISTORY_FOLDER, newId.toString(), history.copy(id = newId, apiName = newApiName, source = newNovel.url))
            context.removeKey(HISTORY_FOLDER, oldId.toString())
        }

        val bookmark = context.getKey<ResultCached>(RESULT_BOOKMARK, oldId.toString())
        if (bookmark != null) {
            context.setKey(RESULT_BOOKMARK, newId.toString(), bookmark.copy(id = newId, apiName = newApiName, source = newNovel.url))
            context.removeKey(RESULT_BOOKMARK, oldId.toString())
        }

        val state = context.getKey<Int>(RESULT_BOOKMARK_STATE, oldId.toString())
        if (state != null) {
            context.setKey(RESULT_BOOKMARK_STATE, newId.toString(), state)
            context.removeKey(RESULT_BOOKMARK_STATE, oldId.toString())
        }
    }

    fun restoreFromFile(context: Context, file: java.io.File): Boolean {
        return try {
            val backupFile = mapper.readValue<BackupFile>(file)
            kotlinx.coroutines.runBlocking {
                context.restore(backupFile, restoreSettings = true, restoreDataStore = true)
            }
            true
        } catch (e: Exception) {
            logError(e)
            false
        }
    }
}