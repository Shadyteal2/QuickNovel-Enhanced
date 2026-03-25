package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import kotlinx.coroutines.*
import com.lagradost.quicknovel.R

class NovelArchiveProvider : MainAPI() {
    override val name = "NovelArchive"
    override val mainUrl = "https://novelarchive.cc"
    override val hasMainPage = true
    override val iconId = R.drawable.novelarchiveicon
    override val iconBackgroundId = R.color.white
    override val iconFullScreen = true

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val content = document.selectFirst("article.reading-content") ?: document.selectFirst(".reading-content")
        val htmlContent = content?.html() ?: return null
        
        // Split by lines and wrap into paragraph with absolute spaced margins
        val list = htmlContent.split("\n")
        return list.joinToString("") { 
            val text = it.trim()
            if (text.isEmpty()) {
                "<div style=\"padding-bottom: 24px;\">&nbsp;</div>"
            } else {
                "<div style=\"padding-bottom: 24px; line-height: 1.8;\">$text</div>"
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val response = app.get("$mainUrl/search?q=$encodedQuery", headers = baseHeaders)
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            
        try {
            val responseObj = mapper.readValue<NovelArchiveSearchResponse>(response.text)
            val items = responseObj.results ?: emptyList()
            return items.map { item ->
                val title = item.title ?: ""
                val slug = item.slug ?: ""
                val genre = item.genre ?: ""
                val href = "$mainUrl/novel/$genre/$slug"
                val cover = "$mainUrl/cover/$genre/$slug"
                
                newSearchResponse(name = title, url = href) {
                    this.posterUrl = cover
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovelArchive", "Search error", e)
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val title = document.selectFirst("h1")?.text() ?: return null

        val synopsis = document.selectFirst(".whitespace-pre-line")?.text()

        // Author & Chapter Count Extraction
        var author: String? = null
        var totalChapters = 50
        val infoRows = document.select("aside .p-8 div.flex")
        for (row in infoRows) {
            val label = row.selectFirst("p, span")?.text() ?: ""
            if (label.contains("Author", ignoreCase = true)) {
                author = row.select("p, span")[1]?.text()
            } else if (label.contains("Chapters", ignoreCase = true)) {
                val countText = row.select("p, span")[1]?.text() ?: ""
                totalChapters = countText.filter { it.isDigit() }.toIntOrNull() ?: 50
            }
        }

        // Failsafe: Parallelize at least 5 pages if sidebar count fails, effectively up to 250 chapters bounding triggers
        val totalPages = Math.max((totalChapters + 49) / 50, 5)
        val chaptersList = ArrayList<ChapterData>()

        kotlinx.coroutines.coroutineScope {
            val jobs = (1..totalPages).map { p ->
                async {
                    val pageUrl = "$url?page=$p&ajax=1"
                    val pageResponse = app.get(pageUrl, headers = baseHeaders)
                    val pageDoc = Jsoup.parse(pageResponse.text)
                    val chapters = pageDoc.select("a.chapter-badge")
                    
                    chapters.mapNotNull { chap ->
                        val href = chap.attr("href") ?: return@mapNotNull null
                        val filename = href.substringAfterLast("/").substringBefore(".txt")
                        val cTitle = if (filename.contains(" - ")) filename.substringAfter(" - ") else filename
                        newChapterData(name = cTitle.trim(), url = fixUrl(href))
                    }
                }
            }
            jobs.awaitAll().flatMap { it }.forEach {
                chaptersList.add(it)
            }
        }

        // Poster URL
        val genre = url.substringAfter("/novel/").substringBefore("/")
        val slug = url.substringAfterLast("/")
        val posterUrl = "$mainUrl/cover/$genre/$slug"

        return newStreamResponse(url = url, name = title, data = chaptersList) {
            this.posterUrl = posterUrl
            this.synopsis = synopsis
            this.author = author
        }
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
        val items = document.select("#novel-grid div.group")
        
        val list = items.mapNotNull {
            val title = it.selectFirst("img")?.attr("alt") ?: it.selectFirst("p.font-bold")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val cover = it.selectFirst("img")?.attr("src")
                
            newSearchResponse(name = title, url = fixUrl(href)) {
                this.posterUrl = fixUrlNull(cover)
            }
        }
        return HeadMainPageResponse(url, list)
    }
}

private data class NovelArchiveSearchResponse(
    @get:JsonProperty("results") val results: List<NovelArchiveSearchItem>?
)

private data class NovelArchiveSearchItem(
    @get:JsonProperty("title") val title: String?,
    @get:JsonProperty("slug") val slug: String?,
    @get:JsonProperty("genre") val genre: String?,
    @get:JsonProperty("chapter_count") val chapterCount: Int?
)
