package com.lagradost.quicknovel.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NeoListDao {

    // ─── Folder Queries ───────────────────────────────────────────────────────

    /**
     * Reactive stream of ALL folders, ordered by manual sort then newest-first.
     * Used by [NeoListsViewModel] to feed the Folders tab hub grid.
     * Index on `createdAt` ensures this sort is O(log n), not a full scan.
     */
    @Query("SELECT * FROM neolists ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllAsFlow(): Flow<List<NeoListEntity>>

    @Query("SELECT * FROM neolists")
    suspend fun getAllAsList(): List<NeoListEntity>

    /**
     * Reactive stream of UNLOCKED folders only.
     * Used by [ResultViewModel.loadCategories] to build the bookmark dialog list.
     * Index on `isLocked` ensures this filter is O(log n), not a full table scan.
     */
    @Query("SELECT * FROM neolists WHERE isLocked = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun getUnlockedAsFlow(): Flow<List<NeoListEntity>>

    /** Synchronous single fetch by ID — for export and share operations. */
    @Query("SELECT * FROM neolists WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NeoListEntity?

    /**
     * Fast deduplication check before import.
     * Returns the UUID string if found, null if not. Uses LIMIT 1 to short-circuit.
     * Avoids a full entity deserialization just to check existence.
     */
    @Query("SELECT id FROM neolists WHERE id = :id LIMIT 1")
    suspend fun existsById(id: String): String?

    /**
     * Fetch existing entry for version conflict detection.
     * Called during import when existsById() returns non-null.
     */
    @Query("SELECT createdAt FROM neolists WHERE id = :id LIMIT 1")
    suspend fun getCreatedAtById(id: String): Long?

    // ─── Mutation ─────────────────────────────────────────────────────────────

    /**
     * Batch insert for atomic import. OnConflictStrategy.REPLACE handles the
     * "replace older version" case from the version conflict dialog.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NeoListEntity)

    /** Toggle lock state — the core of the long-press context menu Unlock/Lock action. */
    @Query("UPDATE neolists SET isLocked = :locked WHERE id = :id")
    suspend fun setLocked(id: String, locked: Boolean)

    /** Update sort order for manual reordering. */
    @Query("UPDATE neolists SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int)

    /** Delete a folder record. Must be paired with deletePinsForList() in a transaction. */
    @Query("DELETE FROM neolists WHERE id = :id")
    suspend fun deleteById(id: String)

    // ─── Pin Map Queries ──────────────────────────────────────────────────────

    /**
     * Batch insert pin entries. OnConflictStrategy.IGNORE skips duplicates safely.
     * Called during import alongside the entity insert.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPins(pins: List<NeoListPinMap>)

    @Query("DELETE FROM neolist_pin_map WHERE neoListId = :listId")
    suspend fun deletePinsForList(listId: String)

    @Query("DELETE FROM neolist_pin_map WHERE novelHash = :novelHash AND neoListId = :listId")
    suspend fun deletePin(novelHash: String, listId: String)

    /**
     * Count how many NeoLists contain a given novel (by hash).
     * Used for showing "In X folders" badge on novel cards in the library.
     */
    @Query("SELECT COUNT(*) FROM neolist_pin_map WHERE novelHash = :novelHash")
    suspend fun countListsForNovel(novelHash: String): Int

    @Query("SELECT neoListId FROM neolist_pin_map WHERE novelHash = :novelHash")
    suspend fun getListsForNovel(novelHash: String): List<String>
}
