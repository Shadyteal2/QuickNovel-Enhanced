package com.lagradost.quicknovel.ui.foryou.recommendation

import com.lagradost.quicknovel.db.RecommendationCandidateEntity
import com.lagradost.quicknovel.ui.foryou.recommendation.UserTasteProfile
import kotlin.math.abs

data class NovelVector(
    val url: String,
    val name: String,
    val tags: Set<TagCategory>,
    val rating: Int?,
    val apiName: String,
    val posterUrl: String?
)

data class Recommendation(
    val novel: NovelVector,
    val score: Float,
    val reason: String,
    val type: RecommendationType
)

enum class RecommendationType {
    FOR_YOU,
    TRENDING_IN_GENRE,
    BECAUSE_YOU_READ,
    WILDCARD
}

data class RecommendationGroup(
    val title: String,
    val type: RecommendationType,
    val recommendations: List<Recommendation>
)

class RecommendationEngine {
    
    fun generateRecommendations(
        profile: UserTasteProfile,
        candidates: List<RecommendationCandidateEntity>
    ): List<RecommendationGroup> {
        val vectors = candidates.map { entity ->
            val extractedTags = SynopsisTagExtractor.extractFromTitle(entity.name)
            val providerTags = TagNormalizer.normalize(entity.tags)
            
            NovelVector(
                url = entity.url,
                name = entity.name,
                tags = extractedTags + providerTags,
                rating = entity.rating,
                apiName = entity.apiName,
                posterUrl = entity.posterUrl
            )
        }

        val groups = mutableListOf<RecommendationGroup>()
        val seenUrls = mutableSetOf<String>()

        // 1. FOR YOU (Personalized based on Taste Profile)
        val forYou = vectors.map { vec ->
            Recommendation(
                novel = vec,
                score = profile.scoreMatch(vec.tags),
                reason = "Matched to your interests",
                type = RecommendationType.FOR_YOU
            )
        }.filter { it.score > 0.4f }
         .sortedByDescending { it.score }
         .take(20) // Increased from 10

        if (forYou.isNotEmpty()) {
            groups.add(RecommendationGroup("For You", RecommendationType.FOR_YOU, forYou))
            seenUrls.addAll(forYou.map { it.novel.url })
        }

        // 2. TRENDING IN YOUR TOP GENRES
        val topGenres = profile.preferredTags
            .sortedByDescending { it.weightedScore }
            .take(3) // Increased from 2
            .map { it.tag }

        for (genre in topGenres) {
            val genreRecs = vectors
                .filter { genre in it.tags && it.url !in seenUrls }
                .sortedByDescending { it.rating ?: 0 }
                .take(12) // Increased from 8
                .map { Recommendation(it, 0.8f, "Top in ${genre.displayName}", RecommendationType.TRENDING_IN_GENRE) }
            
            if (genreRecs.size >= 3) {
                groups.add(RecommendationGroup("Top ${genre.displayName}", RecommendationType.TRENDING_IN_GENRE, genreRecs))
                seenUrls.addAll(genreRecs.map { it.novel.url })
            }
        }

        // 3. HIGHEST RATED (Global)
        val highestRated = vectors
            .filter { it.url !in seenUrls && it.rating != null }
            .sortedByDescending { it.rating ?: 0 }
            .take(12)
            .map { Recommendation(it, 0.9f, "High Quality Picks", RecommendationType.TRENDING_IN_GENRE) }
        
        if (highestRated.size >= 4) {
            groups.add(RecommendationGroup("Highest Rated", RecommendationType.TRENDING_IN_GENRE, highestRated))
            seenUrls.addAll(highestRated.map { it.novel.url })
        }

        // 4. NEW ARRIVALS (Freshly Added)
        val newArrivals = vectors
            .filter { it.url !in seenUrls }
            .takeLast(12) // Assuming last added are newest in pool
            .shuffled()
            .map { Recommendation(it, 0.7f, "Freshly indexed", RecommendationType.TRENDING_IN_GENRE) }
        
        if (newArrivals.size >= 4) {
            groups.add(RecommendationGroup("New Arrivals", RecommendationType.TRENDING_IN_GENRE, newArrivals))
            seenUrls.addAll(newArrivals.map { it.novel.url })
        }

        // 5. HIDDEN GEMS (Random but good ratings)
        val hiddenGems = vectors
            .filter { it.url !in seenUrls && (it.rating ?: 0) > 600 }
            .shuffled()
            .take(12)
            .map { Recommendation(it, 0.85f, "Highly rated surprise", RecommendationType.TRENDING_IN_GENRE) }
        
        if (hiddenGems.size >= 4) {
            groups.add(RecommendationGroup("Hidden Gems", RecommendationType.TRENDING_IN_GENRE, hiddenGems))
            seenUrls.addAll(hiddenGems.map { it.novel.url })
        }

        // 6. WILDCARD / DISCOVER
        val wildcard = vectors
            .filter { it.url !in seenUrls }
            .shuffled()
            .take(15)
            .map { Recommendation(it, 1.0f, "Discover Something New", RecommendationType.WILDCARD) }
        
        if (wildcard.isNotEmpty()) {
            groups.add(RecommendationGroup("Discover Something New", RecommendationType.WILDCARD, wildcard))
        }

        return groups
    }
}

private infix fun <T> T.notIn(list: List<T>): Boolean = !list.contains(this)
