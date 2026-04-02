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
import com.lagradost.quicknovel.DataStore.getDefaultSharedPrefs
import com.lagradost.quicknovel.DataStore.getSharedPrefs
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.settings.SettingsFragment
import com.lagradost.safefile.SafeFile
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

object BackupUtils {
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
        @JsonProperty("settings") val settings: BackupVars
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
        return key.startsWith("downloads_data/") ||
               key.startsWith("download_settings") ||
               key.startsWith("downloads_size/") ||
               key.startsWith("downloads_total/") ||
               key.startsWith("downloads_offset/") ||
               key.startsWith("downloads_epub_size/") ||
               key.startsWith("downloads_epub_last_access/")
    }

    fun FragmentActivity.backup() {
        try {
            if (checkWrite()) {
                val subDir = SettingsFragment.getDefaultDir(context = this)//getBasePath().first
                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
                val displayName = "neoQN_Backup_${date}"

                val allData = getSharedPrefs().all
                val allSettings = getDefaultSharedPrefs().all

                val allDataFiltered = allData.filterKeys { !isDownloadKey(it) }

                val allDataSorted = BackupVars(
                    allDataFiltered.filter { it.value is Boolean } as? Map<String, Boolean>,
                    allDataFiltered.filter { it.value is Int } as? Map<String, Int>,
                    allDataFiltered.filter { it.value is String } as? Map<String, String>,
                    allDataFiltered.filter { it.value is Float } as? Map<String, Float>,
                    allDataFiltered.filter { it.value is Long } as? Map<String, Long>,
                    allDataFiltered.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
                )

                val allSettingsSorted = BackupVars(
                    allSettings.filter { it.value is Boolean } as? Map<String, Boolean>,
                    allSettings.filter { it.value is Int } as? Map<String, Int>,
                    allSettings.filter { it.value is String } as? Map<String, String>,
                    allSettings.filter { it.value is Float } as? Map<String, Float>,
                    allSettings.filter { it.value is Long } as? Map<String, Long>,
                    allSettings.filter { it.value as? Set<String> != null } as? Map<String, Set<String>>
                )

                val backupFile = BackupFile(
                    allDataSorted,
                    allSettingsSorted
                )
                val (stream, fileUri) = setupStream(this,displayName,"json", subDir)
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

            } else {
                showToast(getString(R.string.backup_failed), Toast.LENGTH_LONG)
                requestRW()
                return
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
                                    activity.restore(
                                        restoredValue,
                                        restoreSettings = true,
                                        restoreDataStore = true
                                    )
                                    activity.runOnUiThread {
                                        activity.recreate()
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

    private fun <T> Context.restoreMap(
        map: Map<String, T>?,
        isEditingAppSettings: Boolean = false
    ) {
        val editor = DataStore.editor(this, isEditingAppSettings)
        map?.forEach {
            // QN-Enhanced: Prevent ANY download metadata from leaking into the 'Downloaded' section post-restore
            if (!isDownloadKey(it.key)) {
                editor.setKeyRaw(it.key, it.value)
            }
        }
        editor.apply()
    }

    fun Context.restore(
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreDataStore: Boolean
    ) {
        if (restoreSettings) {
            restoreMap(backupFile.settings._Bool, true)
            restoreMap(backupFile.settings._Int, true)
            restoreMap(backupFile.settings._String, true)
            restoreMap(backupFile.settings._Float, true)
            restoreMap(backupFile.settings._Long, true)
            restoreMap(backupFile.settings._StringSet, true)
        }

        if (restoreDataStore) {
            restoreMap(backupFile.datastore._Bool)
            restoreMap(backupFile.datastore._Int)
            restoreMap(backupFile.datastore._String)
            restoreMap(backupFile.datastore._Float)
            restoreMap(backupFile.datastore._Long)
            restoreMap(backupFile.datastore._StringSet)
        }
    }
}