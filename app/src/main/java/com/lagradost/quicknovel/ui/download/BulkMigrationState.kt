package com.lagradost.quicknovel.ui.download

import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.db.NovelEntity

data class BulkMigrationItem(
    val sourceNovel: NovelEntity,
    val targetMatch: SearchResponse? = null,
    val allResults: List<SearchResponse> = emptyList(),
    val searchState: MigrationSearchState = MigrationSearchState.Pending
)

sealed class MigrationSearchState {
    object Pending : MigrationSearchState()
    object Loading : MigrationSearchState()
    object Success : MigrationSearchState()
    data class Error(val message: String) : MigrationSearchState()
}
