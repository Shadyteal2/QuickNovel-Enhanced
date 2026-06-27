package com.lagradost.quicknovel.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lagradost.quicknovel.ui.neolists.NeoListNovelEntry

/**
 * Persistent storage for a NeoList folder.
 *
 * Design notes:
 * - `novels` is stored as a JSON blob via [Converters.fromNeoListNovels] /
 *   [Converters.toNeoListNovels]. This preserves the export order and enables
 *   complete re-serialization without extra DB queries.
 * - `isLocked` defaults to true for imported lists (read-only by default).
 *   User-created lists are created with isLocked = false.
 * - `createdAt` stores the ORIGINAL author's timestamp from the export JSON.
 * - `importedAt` is set to System.currentTimeMillis() at the moment of import.
 *   For user-created folders, importedAt = null.
 * - `sortOrder` supports future drag-to-reorder functionality.
 */
@Entity(
    tableName = "neolists",
    indices = [
        Index(value = ["isLocked"]),
        Index(value = ["createdAt"])
    ]
)
data class NeoListEntity(
    @PrimaryKey
    val id: String,                          // UUID from the export schema

    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "coverUrl")
    val coverUrl: String? = null,

    @ColumnInfo(name = "authorHandle")
    val authorHandle: String? = null,

    /** Original author's creation timestamp (from export JSON createdAt). */
    val createdAt: Long,

    /**
     * Timestamp of when this list was imported into the app.
     * Null for user-created folders.
     */
    @ColumnInfo(name = "importedAt")
    val importedAt: Long? = null,

    /**
     * When true: this folder is read-only and hidden from the bookmark dialog.
     * Imported lists default to true. Users can unlock via the long-press context menu.
     */
    @ColumnInfo(name = "isLocked")
    val isLocked: Boolean = true,

    /**
     * True for community-imported lists; false for user-created folders.
     * Used to decide whether to show "Imported on [date]" vs "Created on [date]" in the UI.
     */
    @ColumnInfo(name = "isImported")
    val isImported: Boolean = false,

    /** JSON-serialized list of [NeoListNovelEntry] objects via Room TypeConverter. */
    val novels: List<NeoListNovelEntry> = emptyList(),

    /** Manual sort index. Lower = shown first. Used for future drag-to-reorder. */
    @ColumnInfo(name = "sortOrder")
    val sortOrder: Int = 0
)
