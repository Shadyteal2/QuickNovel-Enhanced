package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import kotlinx.coroutines.*

class LightNovelWorldProvider : MainAPI() {
    override val name = "LightNovelWorld"
    override val mainUrl = "https://lightnovelworld.org"
    override val hasMainPage = true
    override val iconId = R.drawable.light_novel_world
    override val iconBackgroundId = R.color.white
    override val iconFullScreen = true

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-Ch-Ua" to "\"Chromium\";v=\"120\", \"Not_A Brand\";v=\"24\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none"
    )

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val items = document.selectFirst("#chapter-content") ?: document.selectFirst(".chapter-content") ?: document.selectFirst("#chapter-container")
        return items?.html()
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/ranking/?page=$page"
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val items = document.select(".ranking-card")
        
        val list = items.mapNotNull {
            val title = it.selectFirst(".card-title")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a.card-link")?.attr("href") ?: return@mapNotNull null
            val cover = it.selectFirst(".card-cover")?.attr("data-bg-image")
                
            newSearchResponse(name = title, url = fixUrl(href)) {
                this.posterUrl = fixUrlNull(cover)
            }
        }
        return HeadMainPageResponse(url, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isNotBlank() && (query.startsWith("http://") || query.startsWith("https://"))) {
            val name = query.substringAfter("/novel/").substringBefore("/").replace("-", " ").trim()
            return listOf(
                newSearchResponse(name = name.ifBlank { "Direct Link" }, url = query) {
                    this.posterUrl = null
                }
            )
        }

        val apiUrl = "$mainUrl/api/search/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        
        return try {
            val apiResponse = app.get(apiUrl, headers = baseHeaders)
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val responseData = mapper.readValue<LNWSampleResponse>(apiResponse.text)
            
            responseData.novels.map { novel ->
                newSearchResponse(name = novel.title, url = "$mainUrl/novel/${novel.slug}/") {
                    this.posterUrl = novel.cover_path?.let { if (it.startsWith("http")) it else "$mainUrl$it" }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        val title = document.selectFirst(".novel-title")?.text() ?: document.selectFirst("h1")?.text() ?: return null

        val poster = document.selectFirst(".novel-cover")?.attr("src") 
            ?: document.selectFirst(".novel-cover-container img")?.attr("src")
        val synopsis = document.selectFirst(".summary-content")?.text() ?: document.selectFirst(".description-text")?.text()
        
        val author = document.selectFirst(".novel-author")?.text()?.replace("Author:", "")?.trim()

        val chaptersList = ArrayList<ChapterData>()
        val chaptersUrl = if (url.endsWith("/")) "${url}chapters/" else "$url/chapters/"
        
        val firstPageResponse = app.get("$chaptersUrl?page=1", headers = baseHeaders)
        val firstPageDoc = Jsoup.parse(firstPageResponse.text)
        val maxPage = firstPageDoc.select(".pagination-pages option").last()?.text()?.toIntOrNull() ?: 1

        val firstPageChapters = firstPageDoc.select(".chapters-grid .chapter-card, .chapter-card")
        firstPageChapters.forEach {
            val onclick = it.attr("onclick") ?: return@forEach
            val cUrl = onclick.substringAfter("location.href='").substringBefore("'")
            val cName = it.selectFirst(".chapter-title")?.text() ?: it.text()
            if (cUrl.isNotBlank() && cUrl.startsWith("/")) {
                chaptersList.add(newChapterData(name = cName.trim(), url = fixUrl(cUrl.trim())))
            }
        }

        if (maxPage > 1) {
            coroutineScope {
                (2..maxPage).chunked(10).forEach { chunk ->
                    val deferreds = chunk.map { page ->
                        async {
                            val pageUrl = "$chaptersUrl?page=$page"
                            val pageResponse = app.get(pageUrl, headers = baseHeaders)
                            val pageDoc = Jsoup.parse(pageResponse.text)
                            pageDoc.select(".chapters-grid .chapter-card, .chapter-card").mapNotNull {
                                val onclick = it.attr("onclick") ?: return@mapNotNull null
                                val cUrl = onclick.substringAfter("location.href='").substringBefore("'")
                                val cName = it.selectFirst(".chapter-title")?.text() ?: it.text()
                                if (cUrl.isNotBlank() && cUrl.startsWith("/")) {
                                    newChapterData(name = cName.trim(), url = fixUrl(cUrl.trim()))
                                } else null
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

private data class LNWSampleResponse(
    @get:JsonProperty("novels") val novels: List<LNWNovel>
)

private data class LNWNovel(
    @get:JsonProperty("id") val id: Int,
    @get:JsonProperty("title") val title: String,
    @get:JsonProperty("slug") val slug: String,
    @get:JsonProperty("cover_path") val cover_path: String? = null
)
