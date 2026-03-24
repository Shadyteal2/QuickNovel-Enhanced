package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import kotlinx.coroutines.*
import com.lagradost.quicknovel.R

class LNoRiProvider : MainAPI() {
    override val name = "LNoRi"
    override val mainUrl = "https://lnori.com"
    override val hasMainPage = true
    override val iconId = R.drawable.lnori
    override val iconBackgroundId = R.color.white

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    override suspend fun loadHtml(url: String): String? {
        val anchor = url.substringAfter("#", "")
        val baseUrl = url.substringBefore("#")
        
        val response = app.get(baseUrl, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        
        val section = if (anchor.isNotEmpty()) {
            document.selectFirst("section.chapter#$anchor")
        } else {
            document.selectFirst("section.chapter")
        }
        
        val content = section?.selectFirst("div.main") ?: section
        return content?.html()
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/library"
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val items = document.select("article.card")
        
        val list = items.take(50).mapNotNull {
            val title = it.attr("data-t") ?: return@mapNotNull null
            val href = it.selectFirst("a.stretched-link")?.attr("href") ?: return@mapNotNull null
            val cover = it.selectFirst("img")?.attr("src")
                
            newSearchResponse(name = title, url = fixUrl(href)) {
                this.posterUrl = fixUrlNull(cover)
            }
        }
        return HeadMainPageResponse(url, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/library"
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val items = document.select("article.card")
        
        val queryLower = query.lowercase()
        return items.mapNotNull {
            val title = it.attr("data-t") ?: ""
            val author = it.attr("data-a") ?: ""
            if (title.lowercase().contains(queryLower) || author.lowercase().contains(queryLower)) {
                val href = it.selectFirst("a.stretched-link")?.attr("href") ?: return@mapNotNull null
                val cover = it.selectFirst("img")?.attr("src")
                newSearchResponse(name = title, url = fixUrl(href)) {
                    this.posterUrl = fixUrlNull(cover)
                }
            } else null
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val title = document.selectFirst("h1.s-title")?.text() ?: document.selectFirst("h1")?.text() ?: return null

        val poster = document.selectFirst(".cover-wrap img")?.attr("src") 
        val synopsis = document.selectFirst(".desc-box .description")?.text()
        val author = document.selectFirst(".author")?.text()

        val chaptersList = ArrayList<ChapterData>()
        
        val scriptTags = document.select("script[type=application/ld+json]")
        var bookData: LNoRiBook? = null
        
        for (script in scriptTags) {
            val text = script.data()
            if (text.contains("\"@type\":\"Book\"") && text.contains("\"hasPart\"")) {
                try {
                    val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    bookData = mapper.readValue<LNoRiBook>(text)
                    break
                } catch (e: Exception) {
                    // fall back to DOM
                }
            }
        }

        val volumes = bookData?.hasPart ?: emptyList()
        
        if (volumes.isNotEmpty()) {
            coroutineScope {
                val deferreds = volumes.map { vol ->
                    async {
                        try {
                            val volUrl = vol.url ?: return@async emptyList<ChapterData>()
                            val volResponse = app.get(volUrl, headers = baseHeaders)
                            val volDoc = Jsoup.parse(volResponse.text)
                            val sections = volDoc.select("section.chapter")
                            
                            sections.mapNotNull { sec ->
                                val id = sec.attr("id") ?: return@mapNotNull null
                                val cTitle = sec.selectFirst(".chapter-title")?.text() ?: "Page $id"
                                newChapterData(name = cTitle.trim(), url = "$volUrl#$id")
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
                deferreds.awaitAll().flatten().forEach { chaptersList.add(it) }
            }
        } else {
            // Fallback to reading DOM
            val volumeCards = document.select(".vol-grid .card")
            if (volumeCards.isNotEmpty()) {
                coroutineScope {
                    val deferreds = volumeCards.mapNotNull {
                        val vUrl = it.selectFirst("a.stretched-link")?.attr("href") ?: return@mapNotNull null
                        val fixVUrl = fixUrl(vUrl)
                        async {
                            try {
                                val volResponse = app.get(fixVUrl, headers = baseHeaders)
                                val volDoc = Jsoup.parse(volResponse.text)
                                volDoc.select("section.chapter").mapNotNull { sec ->
                                    val id = sec.attr("id") ?: return@mapNotNull null
                                    val cTitle = sec.selectFirst(".chapter-title")?.text() ?: "Page $id"
                                    newChapterData(name = cTitle.trim(), url = "$fixVUrl#$id")
                                }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                    deferreds.awaitAll().flatten().forEach { chaptersList.add(it) }
                }
            }
        }

        return newStreamResponse(url = url, name = title, data = chaptersList) {
            this.posterUrl = fixUrlNull(poster)
            this.synopsis = synopsis
            this.author = author
        }
    }
}

private data class LNoRiBook(
    @get:JsonProperty("@type") val type: String?,
    @get:JsonProperty("name") val name: String?,
    @get:JsonProperty("hasPart") val hasPart: List<LNoRiVolume>? = null
)

private data class LNoRiVolume(
    @get:JsonProperty("@type") val type: String?,
    @get:JsonProperty("name") val name: String?,
    @get:JsonProperty("url") val url: String?
)
