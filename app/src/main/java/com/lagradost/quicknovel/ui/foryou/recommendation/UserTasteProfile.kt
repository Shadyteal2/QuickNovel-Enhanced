package com.lagradost.quicknovel.ui.foryou.recommendation

import androidx.annotation.Keep

@Keep
data class TagAffinity(
    val tag: TagCategory,
    val score: Float,        // 0.0 to 1.0 (magnitude of interest)
    val confidence: Float,   // 0.0 to 1.0 (how sure we are)
    val lastUsed: Long = System.currentTimeMillis() // For weighted recency
) {
    val weightedScore: Float
        get() {
            // Recency boost: items from the last 24h get a 1.2x boost
            val dayInMs = 24 * 60 * 60 * 1000L
            val recencyMultiplier = if (System.currentTimeMillis() - lastUsed < dayInMs) 1.2f else 1.0f
            return (score * confidence * recencyMultiplier).coerceIn(0f, 1.5f)
        }
}

@Keep
data class UserTasteProfile(
    val preferredTags: List<TagAffinity>,
    val avoidedTags: List<TagAffinity>,
    val diversityScore: Float = 0.5f,
    val sampleSize: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isWizardComplete: Boolean = false
) {
    companion object {
        val EMPTY = UserTasteProfile(
            preferredTags = emptyList(),
            avoidedTags = emptyList(),
            sampleSize = 0,
            isWizardComplete = false
        )
    }

    /**
     * Scores how well a novel's tags matches this profile.
     * Returns 0.0 to 1.0
     */
    fun scoreMatch(novelTags: Set<TagCategory>): Float {
        if (novelTags.isEmpty()) return 0.5f
        if (preferredTags.isEmpty()) return 0.5f

        var totalPositive = 0f
        var totalWeight = 0f

        for (affinity in preferredTags) {
            val weight = affinity.weightedScore
            totalWeight += weight
            if (affinity.tag in novelTags) {
                totalPositive += weight
            }
        }

        // Penalty for avoided tags
        var penalty = 0f
        for (affinity in avoidedTags) {
            if (affinity.tag in novelTags) {
                penalty += affinity.weightedScore * 0.5f
            }
        }

        return if (totalWeight > 0) {
            ((totalPositive - penalty) / totalWeight).coerceIn(0f, 1f)
        } else {
            0.5f
        }
    }
}
