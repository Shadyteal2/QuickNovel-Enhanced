package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.EpubResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newEpubResponse
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.fixUrlNull

class OceanOfPDFProvider : MainAPI() {
    override val name = "OceanOfPDF"
    override val mainUrl = "https://oceanofpdf.com"
    override val hasMainPage = true
    override val usesCloudFlareKiller = true
    override val iconId = com.lagradost.quicknovel.R.drawable.ic_oceanofpdf

    override val mainCategories = listOf(
        "New Releases" to "new-releases"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): com.lagradost.quicknovel.HeadMainPageResponse {
        val category = mainCategory ?: "new-releases"
        val url = if (page <= 1) "$mainUrl/category/$category/" else "$mainUrl/category/$category/page/$page/"
        val response = app.get(url)
        val document = response.document

        val list = document.select(".widget-event").mapNotNull { element ->
             val a = element.selectFirst("a.title-image") ?: element.selectFirst("a")
             val title = a?.attr("title") ?: element.selectFirst(".title a")?.attr("title") ?: element.selectFirst(".title")?.text() ?: "Unknown"
             val href = a?.attr("href") ?: return@mapNotNull null
             val poster = element.selectFirst(".title-image img")?.attr("data-src")?.takeIf { it.isNotBlank() }
                 ?: element.selectFirst(".title-image img")?.attr("src")?.takeIf { it.isNotBlank() }
                 ?: element.selectFirst("img")?.attr("data-src")?.takeIf { it.isNotBlank() }
                 ?: element.selectFirst("img")?.attr("src")

             if (title == "Unknown" || title.isBlank()) return@mapNotNull null

             newSearchResponse(title, href) {
                  this.posterUrl = fixUrlNull(poster)
             }
        }
        return com.lagradost.quicknovel.HeadMainPageResponse(url, list)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val response = app.get(url)
        val document = response.document

        val items = document.select(".widget-event, article, .wp-block-post-title")
        val links = document.select("a").filter { a ->
             val href = a.attr("href")
             href.contains("-download", ignoreCase = true) && !href.contains("/category/", ignoreCase = true)
        }

        // Combine items and direct links layout triggers properly triggers layout triggers streams
        val allItems = items + links

        return allItems.mapNotNull { element ->
             val a = if (element.tagName() == "a") element else element.selectFirst("a") ?: element.selectFirst("h2 a, h3 a, .entry-title a") 
             val title = element.selectFirst(".entry-title, .title a")?.text() 
                 ?: a?.attr("title")?.takeIf { it.isNotBlank() }
                 ?: a?.text()?.takeIf { it.isNotBlank() } 
                 ?: element.selectFirst(".elementor-portfolio-item__title")?.text() 
                 ?: element.text().trim()
                 
             val href = a?.attr("href") ?: element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
             val poster = element.selectFirst(".title-image img")?.attr("data-src")?.takeIf { it.isNotBlank() }
                 ?: element.selectFirst(".title-image img")?.attr("src")?.takeIf { it.isNotBlank() }
                 ?: element.selectFirst("img")?.attr("data-src")?.takeIf { it.isNotBlank() }
                 ?: element.selectFirst("img")?.attr("src")

             if (title.isBlank() || title == "Unknown") return@mapNotNull null

             newSearchResponse(title, href) {
                  this.posterUrl = fixUrlNull(poster)
             }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val document = response.document

        // 1. Scrape Info
        val listItems = document.select("ul li")
        val title = listItems.find { it.text().contains("Full Book Name:", ignoreCase = true) }?.text()?.replace("Full Book Name:", "", ignoreCase = true)?.trim()
            ?: document.selectFirst("h1.entry-title")?.text()
            ?: document.selectFirst("h1")?.text()
            ?: document.selectFirst("h2.entry-title")?.text()
            ?: "Unknown"

        val author = listItems.find { it.text().contains("Author Name:", ignoreCase = true) }?.text()?.replace("Author Name:", "", ignoreCase = true)?.trim()
            ?: document.select("span.byline, .author-name, .entry-meta a").firstOrNull()?.text()
            ?: "Unknown"

        val poster = document.selectFirst(".entry-content img")?.attr("src") 
            ?: document.selectFirst("div.post-content img")?.attr("src")
            ?: document.selectFirst("div.wp-block-image img")?.attr("src")
            ?: document.selectFirst("img")?.attr("src")

        // 2. Fetch Form Data or Direct Links for download
        val forms = document.select("form[action*='Fetching_Resource.php']")
        val formLinks = forms.mapNotNull { form ->
             val id = form.selectFirst("input[name='id']")?.attr("value") ?: return@mapNotNull null
             val filename = form.selectFirst("input[name='filename']")?.attr("value") ?: return@mapNotNull null
             
             DownloadLink(
                  name = if (filename.endsWith(".pdf", ignoreCase = true)) "PDF" else "EPub",
                  url = form.attr("action"),
                  params = mapOf("id" to id, "filename" to filename)
             )
        }

        val directLinks = document.select("a").mapNotNull { a ->
             val href = a.attr("href") ?: return@mapNotNull null
             val cleanHref = href.split("?").first()
             if (cleanHref.endsWith(".pdf", ignoreCase = true) || cleanHref.endsWith(".epub", ignoreCase = true)) {
                  DownloadLink(
                       name = if (cleanHref.endsWith(".pdf", ignoreCase = true)) "PDF" else "EPUB",
                       url = href,
                       params = mapOf()
                  )
             } else null
        }

        val links = if (directLinks.isNotEmpty()) directLinks else formLinks

        return newEpubResponse(title, url, links = links) {
            this.author = author
            this.posterUrl = poster
            this.synopsis = document.select(".entry-content p").firstOrNull()?.text() ?: document.select("div.post-content p").firstOrNull()?.text()
        }
    }
}
