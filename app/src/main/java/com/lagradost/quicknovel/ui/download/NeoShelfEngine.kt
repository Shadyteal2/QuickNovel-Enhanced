package com.lagradost.quicknovel.ui.download

import androidx.compose.runtime.Immutable
import com.lagradost.quicknovel.ui.ReadType

object NeoShelfEngine {
    @Immutable
    data class NeoShelf(
        val title: String,
        val items: List<DownloadFragment.DownloadDataLoaded>
    )

    fun compute(
        cards: Collection<DownloadFragment.DownloadDataLoaded>,
        lastAccessMap: Map<Int, Long>
    ): List<NeoShelf> {
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 3600 * 1000L
        val sevenDaysMs = 7 * oneDayMs
        val thirtyDaysMs = 30 * oneDayMs
        val fourteenDaysMs = 14 * oneDayMs
        val ninetyDaysMs = 90 * oneDayMs

        val currentlyReading = mutableListOf<DownloadFragment.DownloadDataLoaded>()
        val almostDone = mutableListOf<DownloadFragment.DownloadDataLoaded>()
        val longAbandoned = mutableListOf<DownloadFragment.DownloadDataLoaded>()
        val completedRecently = mutableListOf<DownloadFragment.DownloadDataLoaded>()
        val worthRevisiting = mutableListOf<DownloadFragment.DownloadDataLoaded>()

        for (card in cards) {
            val lastAccess = lastAccessMap[card.id] ?: 0L
            val bookmark = card.bookmarkType ?: ReadType.NONE.prefValue

            // Combine accesses, downloads, and updates to get a robust "last active" timestamp
            val lastActive = maxOf(lastAccess, card.lastDownloaded ?: 0L, card.lastUpdated ?: 0L)

            // 1. Currently Reading (Reading + accessed in reader within 7 days)
            val isCurrentlyReading = bookmark == ReadType.READING.prefValue && lastAccess > 0 && (now - lastAccess) <= sevenDaysMs
            if (isCurrentlyReading) {
                currentlyReading.add(card)
            }

            // 2. Almost Done (Progress > 80%, not fully read, and not marked completed/dropped/currently reading)
            if (card.downloadedTotal > 0 && bookmark != ReadType.COMPLETED.prefValue && bookmark != ReadType.DROPPED.prefValue && !isCurrentlyReading) {
                val progress = card.readCount.toFloat() / card.downloadedTotal
                if (progress > 0.8f && card.readCount < card.downloadedTotal) {
                    almostDone.add(card)
                }
            }

            // 3. Long Abandoned (Reading + has some history, but no activity in > 30 days)
            if (bookmark == ReadType.READING.prefValue && lastActive > 0L && (now - lastActive) > thirtyDaysMs) {
                longAbandoned.add(card)
            }

            // 4. Completed Recently (Completed + accessed/completed within 14 days)
            if (bookmark == ReadType.COMPLETED.prefValue && lastAccess > 0 && (now - lastAccess) <= fourteenDaysMs) {
                completedRecently.add(card)
            }

            // 5. Worth Revisiting (Completed + no activity in > 90 days)
            if (bookmark == ReadType.COMPLETED.prefValue && lastActive > 0L && (now - lastActive) > ninetyDaysMs) {
                worthRevisiting.add(card)
            }
        }

        return listOf(
            NeoShelf("Currently Reading", currentlyReading.sortedByDescending { lastAccessMap[it.id] ?: 0L }),
            NeoShelf("Almost Done", almostDone.sortedByDescending { it.readCount.toFloat() / it.downloadedTotal }),
            NeoShelf("Long Abandoned", longAbandoned.sortedBy { lastAccessMap[it.id] ?: 0L }),
            NeoShelf("Completed Recently", completedRecently.sortedByDescending { lastAccessMap[it.id] ?: 0L }),
            NeoShelf("Worth Revisiting", worthRevisiting.sortedBy { lastAccessMap[it.id] ?: 0L })
        ).filter { it.items.isNotEmpty() }
    }
}
