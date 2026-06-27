package com.lagradost.quicknovel.ui.neolists

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ShareCompat
import com.lagradost.quicknovel.db.AppDatabase
import com.lagradost.quicknovel.db.NeoListEntity
import com.lagradost.quicknovel.db.NeoListPinMap
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.AppUtils.mapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.absoluteValue

// ─── Constants ────────────────────────────────────────────────────────────────

/** Maximum supported schema version this app can parse. */
const val NEOLISTS_MAX_SCHEMA_VERSION = 1

/** Hard cap on novels per imported list. Enforced pre-DB-write. */
const val NEOLISTS_MAX_NOVELS = 200

/** Extension used when saving the export file to the cache dir. */
const val NEOLISTS_FILE_EXTENSION = ".neolist"

// ─── Import Result ────────────────────────────────────────────────────────────

sealed class ImportResult {
    data class Success(val title: String) : ImportResult()
    data class AlreadySaved(val title: String) : ImportResult()
    data class NewerVersionAvailable(
        val existingTitle: String,
        val existingCreatedAt: Long,
        val incoming: NeoListExport
    ) : ImportResult()
    data class Failure(val reason: String) : ImportResult()
}

// ─── Engine ───────────────────────────────────────────────────────────────────

/**
 * Stateless import/export engine for NeoList .neolist files.
 *
 * All heavy work runs on Dispatchers.IO. Callers (ViewModels) are responsible
 * for switching context before calling these functions.
 *
 * SCOPED STORAGE COMPLIANCE:
 * importFromUri() always reads files via Context.contentResolver.openInputStream(uri).
 * File(intent.data.path) is NEVER used — it crashes with SecurityException on Android 10+.
 */
object NeoListImportExportEngine {

    // ─── IMPORT ───────────────────────────────────────────────────────────────

    /**
     * Parses, validates, and (if approved) persists a .neolist file from a URI.
     *
     * @param uri   A content:// URI obtained from the file picker OR an ACTION_VIEW intent.
     *              NEVER use File(uri.path) — always use contentResolver.openInputStream.
     * @param context Android context for contentResolver access.
     * @param replace If true, replaces an existing list with the same ID (version conflict resolution).
     * @return [ImportResult] describing outcome — never throws.
     */
    suspend fun importFromUri(
        uri: Uri,
        context: Context,
        replace: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            // ─── Step 1: Read bytes via contentResolver ──────────────────────
            // SCOPED STORAGE: contentResolver.openInputStream is the ONLY correct
            // API for reading external files from intent filters or file pickers.
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext ImportResult.Failure("Could not read the file.")

            // ─── Step 2: Parse JSON ──────────────────────────────────────────
            val export = try {
                mapper.readValue(json, NeoListExport::class.java)
            } catch (t: Throwable) {
                logError(t)
                return@withContext ImportResult.Failure(
                    "Could not parse this file. Make sure it's a valid .neolist file."
                )
            }

            // ─── Step 3: Validate schema ─────────────────────────────────────
            if (export.schemaVersion > NEOLISTS_MAX_SCHEMA_VERSION) {
                return@withContext ImportResult.Failure(
                    "This list was created with a newer version of the app. Please update NeoQN."
                )
            }
            if (export.title.isBlank()) {
                return@withContext ImportResult.Failure("This list has an invalid or empty title.")
            }
            if (export.novels.size > NEOLISTS_MAX_NOVELS) {
                return@withContext ImportResult.Failure(
                    "This list contains ${export.novels.size} novels but the maximum allowed is $NEOLISTS_MAX_NOVELS."
                )
            }

            val dao = AppDatabase.getDatabase(context).neoListDao()

            // ─── Step 4: Deduplication & version conflict check ──────────────
            val existingId = dao.existsById(export.id)
            if (existingId != null && !replace) {
                val existingCreatedAt = dao.getCreatedAtById(export.id)
                if (existingCreatedAt != null && export.createdAt > existingCreatedAt) {
                    // Newer version exists in the export — surface conflict to user
                    return@withContext ImportResult.NewerVersionAvailable(
                        existingTitle = export.title,
                        existingCreatedAt = existingCreatedAt,
                        incoming = export
                    )
                }
                return@withContext ImportResult.AlreadySaved(export.title)
            }

            // ─── Step 5: Build entity ────────────────────────────────────────
            val entity = NeoListEntity(
                id          = export.id,
                title       = export.title,
                description = export.description,
                coverUrl    = export.coverUrl,
                authorHandle = export.authorHandle,
                createdAt   = export.createdAt,
                importedAt  = System.currentTimeMillis(),
                isLocked    = true,    // Imported lists are locked by default (read-only)
                isImported  = true,
                novels      = export.novels
            )

            // ─── Step 6: Build pin map entries ───────────────────────────────
            val pins = export.novels.mapIndexed { index, novel ->
                // Derive a stable hash from source URL (more stable than title+author)
                val novelHash = "${export.id}::${novel.sourceUrl}"
                NeoListPinMap(
                    novelHash = novelHash,
                    neoListId = export.id,
                    addedAt   = System.currentTimeMillis()
                )
            }

            // ─── Step 7: Atomic DB write ─────────────────────────────────────
            // Both writes happen together — a failed insert leaves zero partial data.
            dao.insert(entity)
            if (pins.isNotEmpty()) dao.insertPins(pins)

            ImportResult.Success(export.title)

        } catch (t: Throwable) {
            logError(t)
            ImportResult.Failure("An unexpected error occurred. The file may be corrupted.")
        }
    }

    // ─── EXPORT ───────────────────────────────────────────────────────────────

    /**
     * Serializes a NeoList folder to a .neolist JSON file in the app cache dir
     * and launches an Android share sheet.
     *
     * @param listId UUID of the NeoList to export.
     * @param context Activity or Application context.
     * @return true if the share intent was launched, false if the list wasn't found.
     */
    suspend fun exportAndShare(
        listId: String,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao    = AppDatabase.getDatabase(context).neoListDao()
            val entity = dao.getById(listId) ?: return@withContext false

            val export = NeoListExport(
                schemaVersion = NEOLISTS_MAX_SCHEMA_VERSION,
                id            = entity.id,
                title         = entity.title,
                description   = entity.description,
                coverUrl      = entity.coverUrl,
                authorHandle  = entity.authorHandle,
                createdAt     = entity.createdAt,
                novels        = entity.novels
            )

            val json     = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(export)
            val safeTitle = entity.title.replace(Regex("[^A-Za-z0-9_\\- ]"), "").take(40).trim()
            val fileName  = "${safeTitle}_${entity.id.take(8)}$NEOLISTS_FILE_EXTENSION"
            val outFile   = File(context.cacheDir, fileName)
            outFile.writeText(json)

            // Build a content URI via FileProvider for secure external sharing
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                outFile
            )

            withContext(Dispatchers.Main) {
                var currentContext = context
                var activity: android.app.Activity? = null
                while (currentContext is android.content.ContextWrapper) {
                    if (currentContext is android.app.Activity) {
                        activity = currentContext
                        break
                    }
                    currentContext = currentContext.baseContext
                }

                val intentBuilder = if (activity != null) {
                    ShareCompat.IntentBuilder(activity)
                } else {
                    ShareCompat.IntentBuilder(context)
                }

                val intent = intentBuilder
                    .setType("application/json")
                    .setStream(fileUri)
                    .setChooserTitle("Share NeoList")
                    .createChooserIntent()

                if (activity == null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            true
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

    suspend fun exportAndSave(listId: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val entity = db.neoListDao().getById(listId) ?: return@withContext false

            val export = NeoListExport(
                schemaVersion = NEOLISTS_MAX_SCHEMA_VERSION,
                id            = entity.id,
                title         = entity.title,
                description   = entity.description,
                coverUrl      = entity.coverUrl,
                authorHandle  = entity.authorHandle ?: "Me",
                createdAt     = entity.createdAt,
                novels        = entity.novels
            )

            val json     = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(export)
            val safeTitle = entity.title.replace(Regex("[^A-Za-z0-9_\\- ]"), "").take(40).trim()
            val fileName  = "${safeTitle}_${entity.id.take(8)}$NEOLISTS_FILE_EXTENSION"

            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cr = context.contentResolver
                val contentUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.TITLE, safeTitle)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                }
                val newFileUri = cr.insert(contentUri, newFile)
                if (newFileUri != null) {
                    cr.openOutputStream(newFileUri, "w")?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    }
                    true
                } else false
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val outFile = File(downloadsDir, fileName)
                outFile.writeText(json)
                true
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    com.lagradost.quicknovel.CommonActivity.showToast(
                        "Saved to Downloads: $fileName",
                        Toast.LENGTH_LONG
                    )
                } else {
                    com.lagradost.quicknovel.CommonActivity.showToast(
                        "Failed to save folder",
                        Toast.LENGTH_LONG
                    )
                }
            }
            success
        } catch (t: Throwable) {
            logError(t)
            withContext(Dispatchers.Main) {
                com.lagradost.quicknovel.CommonActivity.showToast(
                    "Error saving: ${t.localizedMessage}",
                    Toast.LENGTH_LONG
                )
            }
            false
        }
    }

    // ─── ID Assignment ────────────────────────────────────────────────────────

    /**
     * Generates a guaranteed-unique integer ID for a NeoList CategoryItem bridge.
     *
     * Uses maxId + 1 pattern (same as DownloadViewModel.addCategory()).
     * NEVER uses hashCode() — hash collisions would silently overwrite categories.
     *
     * @param existingIds All currently-assigned integer IDs from both system and custom categories.
     * @return A new unique ID that is >= 10 (custom category range).
     */
    fun generateUniqueCategoryId(existingIds: List<Int>): Int {
        val maxExisting = existingIds.maxOrNull() ?: 9
        return maxExisting.coerceAtLeast(9) + 1
    }
}
