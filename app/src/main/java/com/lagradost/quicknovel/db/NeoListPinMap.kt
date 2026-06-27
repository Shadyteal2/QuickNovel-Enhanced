package com.lagradost.quicknovel.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Join-table mapping a novel (by hash) to a NeoList folder (by UUID).
 *
 * We use this alongside the JSON blob in [NeoListEntity.novels] to enable
 * efficient reverse-lookups such as "how many NeoLists contain this novel?"
 * without parsing every JSON blob. The blob handles ordered export reconstruction;
 * this table handles indexed relational queries.
 *
 * `novelHash` is the same ID produced by BookDownloader2Helper.generateId(),
 * which is stable across app restarts for the same source+name combination.
 */
@Entity(
    tableName = "neolist_pin_map",
    primaryKeys = ["novelHash", "neoListId"]
)
data class NeoListPinMap(
    val novelHash: String,               // FK to novel identity (BookDownloader2Helper.generateId)

    val neoListId: String,               // FK to neolists.id (UUID)

    val addedAt: Long = System.currentTimeMillis()
)
