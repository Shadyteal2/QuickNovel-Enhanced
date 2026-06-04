package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.newStreamResponse

class WebToEpubAPI : MainAPI() {
    override val mainUrl = "https://github.com/dteviot/WebToEpub"
    override val name = "WebToEpub"
    override val lang = "en"

    override suspend fun search(query: String): List<SearchResponse>? {
        val trimmed = query.trim()
        if (WebToEpubMap.getParserForUrl(trimmed) == null) return null
        val cleanUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }
        val domain = try {
            java.net.URL(cleanUrl).host.lowercase().removePrefix("www.")
        } catch (e: Exception) {
            return null
        }
        
        return listOf(
            SearchResponse(
                name = "Import Novel ($domain)",
                url = cleanUrl,
                posterUrl = null,
                apiName = this.name
            )
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val trimmed = url.trim()
        val parserFile = WebToEpubMap.getParserForUrl(trimmed) 
            ?: throw ErrorLoadingException("No parser found for URL: $url")
            
        val context = BaseApplication.context 
            ?: throw ErrorLoadingException("Context not initialized")

        val cleanUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        val parsed = try {
            WebViewParserEngine.parseMetadata(context, cleanUrl, parserFile)
        } catch (e: Exception) {
            logError(e)
            throw ErrorLoadingException(e.message ?: "Failed parsing metadata via WebToEpub")
        }

        return newStreamResponse(name = parsed.title, url = cleanUrl, data = parsed.chapters) {
            author = parsed.author
            posterUrl = parsed.cover
            synopsis = "Imported web novel via WebToEpub engine."
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val trimmed = url.trim()
        val parserFile = WebToEpubMap.getParserForUrl(trimmed) ?: return null
        val context = BaseApplication.context ?: return null

        val cleanUrl = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            WebViewParserEngine.parseHtml(context, cleanUrl, parserFile)
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
}
