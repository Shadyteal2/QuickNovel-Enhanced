package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.Jsoup
import com.lagradost.quicknovel.R

class WuxiaClickProvider : MainAPI() {
    override val name = "WuxiaClick"
    override val mainUrl = "https://wuxia.click"
    override val hasMainPage = true
    override val iconId = R.drawable.wuxiaclickicon
    override val iconBackgroundId = R.color.white
    override val iconFullScreen = true

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    private val apiHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept" to "application/json",
        "Accept-Language" to "en-US,en;q=0.9"
    )

    // Data wrappers for search results list response
    private data class WuxiaClickSearchResponse(
        @get:JsonProperty("results") val results: List<WuxiaClickSearchItem>? = null
    )

    private data class WuxiaClickSearchItem(
        @get:JsonProperty("name") val name: String? = null,
        @get:JsonProperty("slug") val slug: String? = null,
        @get:JsonProperty("image") val image: String? = null
    )

    // Data wrappers for Chapters list response
    private data class WuxiaClickChapterItem(
        @get:JsonProperty("title") val title: String? = null,
        @get:JsonProperty("novSlugChapSlug") val novSlugChapSlug: String? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = "$mainUrl/api/search?search=$encodedQuery"
        
        val response = app.get(url, headers = apiHeaders)
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            
        try {
            // Direct API response parsing object wrapper
            val items = mapper.readValue<WuxiaClickSearchResponse>(response.text).results ?: emptyList()
            return items.map { item ->
                val title = item.name ?: ""
                val slug = item.slug ?: ""
                val href = "$mainUrl/novel/$slug"
                val cover = item.image ?: ""
                
                newSearchResponse(name = title, url = href) {
                    this.posterUrl = cover
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WuxiaClick", "Search error", e)
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        
        // Metadata Selection
        val title = document.selectFirst("h5.mantine-Title-root")?.text() 
            ?: document.selectFirst(".mantine-1ra9ysm")?.text() 
            ?: return null

        val synopsis = document.selectFirst("#mantine-w6wvmvx9b-panel-description div.mantine-Text-root")?.text()
            ?: document.selectFirst(".mantine-tpna8b")?.text()

        val author = document.selectFirst(".mantine-ss2azu")?.text()?.substringAfter("By ")

        val slug = url.substringAfterLast("/")
        
        // Instant single fetch for Chapters
        val chapterUrl = "https://wuxiaworld.eu/api/chapters/$slug/"
        val chapterResponse = app.get(chapterUrl, headers = apiHeaders)
        
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            
        val chaptersList = ArrayList<ChapterData>()
        try {
            val items = mapper.readValue<List<WuxiaClickChapterItem>>(chapterResponse.text)
            for (item in items) {
                val cTitle = item.title ?: ""
                val cSlug = item.novSlugChapSlug ?: continue
                val href = "$mainUrl/chapter/$cSlug"
                chaptersList.add(newChapterData(name = cTitle.trim(), url = href))
            }
        } catch (e: Exception) {
            android.util.Log.e("WuxiaClick", "Chapters error", e)
        }

        val posterUrl = document.selectFirst(".mantine-Image-image")?.attr("src") // Better cover selector
        
        return newStreamResponse(url = url, name = title, data = chaptersList) {
            this.posterUrl = posterUrl
            this.synopsis = synopsis
            this.author = author
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val slug = url.substringAfterLast("/")
        val apiUrl = "$mainUrl/api/getchapter/$slug/"
        val response = app.get(apiUrl, headers = apiHeaders)
        
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            
        try {
            val node = mapper.readTree(response.text)
            val text = node.get("text")?.asText() ?: return null
            val list = text.split("\n")
            return list.joinToString("") { 
                val line = it.trim()
                if (line.isEmpty()) {
                    "<div style=\"padding-bottom: 24px;\">&nbsp;</div>"
                } else {
                    "<div style=\"padding-bottom: 24px; line-height: 1.8;\">$line</div>"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WuxiaClick", "loadHtml error", e)
            return null
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val offset = (page - 1) * 18
        val url = "$mainUrl/api/novels/?limit=18&offset=$offset"
        val response = app.get(url, headers = apiHeaders)
        
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            
        try {
            val responseObj = mapper.readValue<WuxiaClickSearchResponse>(response.text)
            val items = responseObj.results ?: emptyList()
            
            val list = items.map { item ->
                val title = item.name ?: ""
                val slug = item.slug ?: ""
                val href = "$mainUrl/novel/$slug"
                val cover = item.image ?: ""
                
                newSearchResponse(name = title, url = href) {
                    this.posterUrl = cover
                }
            }
            return HeadMainPageResponse(url, list)
        } catch (e: Exception) {
            android.util.Log.e("WuxiaClick", "loadMainPage error: ", e)
            android.util.Log.e("WuxiaClick", "MainPage response snippet: ${response.text.take(200)}")
            return HeadMainPageResponse(url, emptyList())
        }
    }
}
