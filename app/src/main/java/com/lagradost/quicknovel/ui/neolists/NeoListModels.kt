package com.lagradost.quicknovel.ui.neolists

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty

// ─── JSON Export / Import Schema ─────────────────────────────────────────────

/**
 * Root object for the .neolist file format.
 * schemaVersion allows forward-compatible migrations via FAIL_ON_UNKNOWN_PROPERTIES = false.
 */
@Immutable
data class NeoListExport(
    @JsonProperty("schemaVersion") val schemaVersion: Int = 1,
    @JsonProperty("id")            val id: String,
    @JsonProperty("title")         val title: String,
    @JsonProperty("description")   val description: String? = null,
    @JsonProperty("coverUrl")      val coverUrl: String? = null,
    @JsonProperty("authorHandle")  val authorHandle: String? = null,
    @JsonProperty("createdAt")     val createdAt: Long = System.currentTimeMillis(),
    @JsonProperty("novels")        val novels: List<NeoListNovelEntry> = emptyList()
)

/**
 * Lightweight novel stub stored in the export. Contains only metadata needed
 * for display and migration — NOT a full NovelEntity.
 */
@Immutable
data class NeoListNovelEntry(
    @JsonProperty("title")     val title: String,
    @JsonProperty("author")    val author: String? = null,
    @JsonProperty("posterUrl") val posterUrl: String? = null,
    @JsonProperty("apiName")   val apiName: String,
    @JsonProperty("sourceUrl") val sourceUrl: String
)

// ─── Provider Resolution State ────────────────────────────────────────────────

/**
 * Represents whether a NeoList novel entry can be opened in the app.
 * Resolved after checking getApiFromNameOrNull(entry.apiName) on Dispatchers.Default.
 */
sealed class NovelResolutionState {
    /** Not yet checked. Shown as shimmer skeleton. */
    object Pending : NovelResolutionState()

    /** Provider is available — novel can be tapped to open. */
    object Resolved : NovelResolutionState()

    /**
     * Provider is missing or load failed.
     * Surfaces "Provider Unavailable" chip + Migrate button.
     */
    data class DeadProvider(
        val savedTitle: String,
        val savedAuthor: String?
    ) : NovelResolutionState()
}

// ─── Folder Statistics ────────────────────────────────────────────────────────

/** Computed statistics for the NeoListDetailScreen header. */
@Immutable
data class NeoListStats(
    val totalNovels: Int,
    val resolvedCount: Int,
    val deadCount: Int
) {
    val allResolved: Boolean get() = deadCount == 0
}
