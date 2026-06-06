package com.lagradost.quicknovel.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.lagradost.quicknovel.PREFERENCES_NAME
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NovelEntity
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.BackupUtils
import com.lagradost.quicknovel.util.BackupUtils.BackupFile
import com.lagradost.quicknovel.util.BackupUtils.BackupVars
import com.lagradost.quicknovel.util.BackupUtils.restore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object GoogleDriveSyncManager {
    private const val PREFS_NAME = "google_sync_prefs"
    private const val KEY_ACCOUNT_EMAIL = "google_account_email"
    private const val KEY_LAST_SYNCED = "google_last_synced"
    private const val KEY_AUTO_SYNC_ENABLED = "google_auto_sync_enabled"
    private const val BACKUP_FILE_NAME = "neoqn_backup.json"
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata"

    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.appdata"))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun handleSignInResult(context: Context, data: Intent?): String? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            saveAccountEmail(context, account.email)
            account.email
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    fun logout(context: Context, onComplete: () -> Unit) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        GoogleSignIn.getClient(context, gso).signOut().addOnCompleteListener {
            clearAccountData(context)
            onComplete()
        }
    }

    fun getAccountEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_EMAIL, null)
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SYNC_ENABLED, enabled)
            .apply()
    }

    fun getLastSyncedTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNCED, 0L)
    }

    private fun saveAccountEmail(context: Context, email: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_EMAIL, email)
            .apply()
    }

    private fun saveLastSyncedTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNCED, time)
            .apply()
    }

    private fun clearAccountData(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_LAST_SYNCED)
            .remove(KEY_AUTO_SYNC_ENABLED)
            .apply()
    }

    private suspend fun getAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        try {
            // Attempt to retrieve OAuth 2.0 access token
            GoogleAuthUtil.getToken(context, account.account!!, DRIVE_SCOPE)
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    private suspend fun clearAccessToken(context: Context, token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (t: Throwable) {
            logError(t)
        }
    }

    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        var token = getAccessToken(context) ?: return@withContext false

        try {
            var attempt = 1
            var success = false
            while (attempt <= 2 && !success) {
                try {
                    success = executeSyncFlow(context, token)
                } catch (t: Throwable) {
                    if (attempt == 1) {
                        // Clear token cache and try once more in case of token expiration (401/403)
                        clearAccessToken(context, token)
                        token = getAccessToken(context) ?: return@withContext false
                    } else {
                        logError(t)
                    }
                }
                attempt++
            }
            if (success) {
                saveLastSyncedTime(context, System.currentTimeMillis())
            }
            success
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    private suspend fun executeSyncFlow(context: Context, accessToken: String): Boolean {
        // 1. Search for existing backup file in drive appDataFolder
        val fileId = findBackupFileId(accessToken)

        val localBackup = withContext(Dispatchers.IO) {
            // Generate checkpoint for Room WAL files
            try {
                AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);")
            } catch (t: Throwable) {
                logError(t)
            }
            generateLocalBackup(context)
        }

        if (fileId == null) {
            // 2. No backup exists, create one
            val newFileId = createBackupFileMetadata(accessToken) ?: return false
            uploadBackupContent(accessToken, newFileId, localBackup)
        } else {
            // 3. Download remote backup
            val remoteContent = downloadBackupContent(accessToken, fileId)
            // Perform merge logic on Dispatchers.Default
            val mergedBackup = withContext(Dispatchers.Default) {
                val remoteBackup = mapper.readValue(remoteContent, BackupFile::class.java)
                mergeBackups(localBackup, remoteBackup)
            }

            // 4. Upload merged backup to Google Drive
            uploadBackupContent(accessToken, fileId, mergedBackup)

            // 5. Restore merged backup to local SQLite DB & SharedPreferences
            withContext(Dispatchers.IO) {
                context.restore(mergedBackup, restoreSettings = true, restoreDataStore = true)
            }
        }
        return true
    }

    private fun findBackupFileId(accessToken: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=name='${BACKUP_FILE_NAME}'"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to find backup file. HTTP code: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body when finding backup file")
            val node = mapper.readTree(body)
            val files = node.get("files")
            if (files != null && files.isArray && files.size() > 0) {
                return files.get(0).get("id").asText()
            }
        }
        return null
    }

    private fun createBackupFileMetadata(accessToken: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files"
        val metadata = mapOf(
            "name" to BACKUP_FILE_NAME,
            "parents" to listOf("appDataFolder")
        )
        val requestBody = mapper.writeValueAsString(metadata).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to create backup file metadata. HTTP code: ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body when creating backup file metadata")
            val node = mapper.readTree(body)
            return node.get("id")?.asText()
        }
    }

    private fun downloadBackupContent(accessToken: String, fileId: String): String {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download file content from Google Drive. HTTP code: ${response.code}")
            }
            return response.body?.string() ?: throw IOException("Empty response body when downloading backup content")
        }
    }

    private fun uploadBackupContent(accessToken: String, fileId: String, backupFile: BackupFile) {
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val content = mapper.writeValueAsString(backupFile)
        val requestBody = content.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .patch(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to upload file content to Google Drive. HTTP code: ${response.code}")
            }
        }
    }

    private suspend fun generateLocalBackup(context: Context): BackupFile {
        return BackupUtils.generateBackup(context)
    }

    private fun mergeBackups(local: BackupFile, remote: BackupFile): BackupFile {
        // Merge settings and datastore using local preference logic in case of conflicts
        val mergedSettings = BackupVars(
            mergeMap(local.settings._Bool, remote.settings._Bool),
            mergeMap(local.settings._Int, remote.settings._Int),
            mergeMap(local.settings._String, remote.settings._String),
            mergeMap(local.settings._Float, remote.settings._Float),
            mergeMap(local.settings._Long, remote.settings._Long),
            mergeMap(local.settings._StringSet, remote.settings._StringSet)
        )

        val mergedDataStore = BackupVars(
            mergeMap(local.datastore._Bool, remote.datastore._Bool),
            mergeMap(local.datastore._Int, remote.datastore._Int),
            mergeMap(local.datastore._String, remote.datastore._String),
            mergeMap(local.datastore._Float, remote.datastore._Float),
            mergeMap(local.datastore._Long, remote.datastore._Long),
            mergeMap(local.datastore._StringSet, remote.datastore._StringSet)
        )

        // Merge bookmark list by choosing the newer update timestamp
        val localNovels = local.novels ?: emptyList()
        val remoteNovels = remote.novels ?: emptyList()
        val mergedNovelsMap = mutableMapOf<Int, NovelEntity>()

        for (novel in remoteNovels) {
            mergedNovelsMap[novel.id] = novel
        }
        for (novel in localNovels) {
            val existing = mergedNovelsMap[novel.id]
            if (existing == null) {
                mergedNovelsMap[novel.id] = novel
            } else {
                val localTime = novel.lastUpdated ?: 0L
                val remoteTime = existing.lastUpdated ?: 0L
                if (localTime >= remoteTime) {
                    mergedNovelsMap[novel.id] = novel
                }
            }
        }

        return BackupFile(mergedDataStore, mergedSettings, mergedNovelsMap.values.toList())
    }

    private fun <K, V> mergeMap(local: Map<K, V>?, remote: Map<K, V>?): Map<K, V>? {
        if (local == null) return remote
        if (remote == null) return local
        val result = HashMap<K, V>(remote)
        result.putAll(local) // Local keys override remote keys
        return result
    }

    suspend fun restoreFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getAccessToken(context) ?: return@withContext false
        try {
            val fileId = findBackupFileId(token) ?: return@withContext false
            val remoteContent = downloadBackupContent(token, fileId)
            val backupFile = withContext(Dispatchers.Default) {
                mapper.readValue(remoteContent, BackupFile::class.java)
            }
            withContext(Dispatchers.IO) {
                context.restore(backupFile, restoreSettings = true, restoreDataStore = true)
            }
            true
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }
}
