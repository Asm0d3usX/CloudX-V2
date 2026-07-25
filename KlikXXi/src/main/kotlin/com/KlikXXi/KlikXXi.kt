package com.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI

class KlikXXi : MainAPI() {
    override var mainUrl = "https://klikxxi.shop"
    private val mainUrlJson = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private var directUrl: String? = null
    override var name = "KlikXXi"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "year/2026/page/%d/" to "Terbaru",
        "tv/page/%d/" to "TV Series",
        "category/action/page/%d/" to "Action",
        "category/adventure/page/%d/" to "Adventure",
        "category/comedy/page/%d/" to "Comedy",
        "category/cartoon/page/%d/" to "Cartoon",
        "category/crime/page/%d/" to "Crime",
        "category/drama/page/%d/" to "Drama",
        "category/fantasy/page/%d/" to "Fantasy",
        "category/family/page/%d/" to "Family",
        "category/horror/page/%d/" to "Horror",
        "category/mystery/page/%d/" to "Mystery",
        "category/roman/page/%d/" to "Romance",
        "category/science-fiction/page/%d/" to "Science Fiction",
        "category/thriller/page/%d/" to "Thriller",
        "category/war/page/%d/" to "War"
    )

    private suspend fun loadMainUrlIfNeeded() {
        if (directUrl != null) return
        try {
            val response = app.get(mainUrlJson).text
            val json = JSONObject(response)
            val array = json.optJSONArray("klikxxi")
            val newUrl = array?.optString(0)?.removeSuffix("/")

            if (!newUrl.isNullOrBlank()) {
                mainUrl = newUrl
                directUrl = newUrl
            }
        } catch (_: Exception) { }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loadMainUrlIfNeeded()
        val document = app.get("$mainUrl/${request.data.format(page)}").document
        val items = document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("div.content-thumbnail a")?.attr("href") ?: return null)
        val img = selectFirst("div.content-thumbnail img")

        val posterRaw =
            img?.attr("data-lazy-srcset")?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-srcset")?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("srcset")?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                ?: img?.attr("src")?.takeIf { !it.startsWith("data:image") }

        val poster = posterRaw?.let { if (it.startsWith("//")) "https:$it" else it }
        val quality = select("div.gmr-quality-item a").text().trim().replace("-", "")
        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()

        return if (quality.isEmpty()) {
            val episode = select("div.gmr-numbeps span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                addQuality(quality)
                score = Score.from10(ratingText?.toDoubleOrNull())
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        loadMainUrlIfNeeded()
        val document = app.get("$mainUrl?s=$query&post_type[]=post&post_type[]=tv").document
        return document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("div.content-thumbnail a")?.attr("href") ?: return null)
        val img = selectFirst("div.content-thumbnail img")

        val posterRaw =
            img?.attr("data-lazy-srcset")?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("data-srcset")?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("srcset")?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
                ?: img?.attr("src")?.takeIf { !it.startsWith("data:image") }

        val poster = posterRaw?.let { if (it.startsWith("//")) "https:$it" else it }
        val quality = select("div.gmr-quality-item a").text().trim().replace("-", "")
        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()

        return if (quality.isEmpty()) {
            val episode = select("div.gmr-numbeps span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                addQuality(quality)
                score = Score.from10(ratingText?.toDoubleOrNull())
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        loadMainUrlIfNeeded()
        val fetch = app.get(url)
        val doc = fetch.document
        directUrl = getBaseUrl(fetch.url)

        val title = doc.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val img = doc.selectFirst("img.wp-post-image")

        val posterRaw =
            img?.attr("data-lazy-srcset")?.takeIf { it.isNotBlank() }
                ?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            ?: img?.attr("data-lazy-src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("data-srcset")?.takeIf { it.isNotBlank() }
                ?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: img?.attr("srcset")?.takeIf { it.isNotBlank() }
                ?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            ?: img?.attr("src")?.takeIf { !it.startsWith("data:image") }

        val poster = posterRaw?.let {
            if (it.startsWith("//")) "https:$it" else it
        }?.fixImageQuality()

        val description = doc.selectFirst(".entry-content.entry-content-single > p")?.text()?.trim()
        val year = doc.selectFirst("time[itemprop=dateCreated]")?.attr("datetime")?.take(4)?.toIntOrNull()
        val rating = doc.selectFirst(".gmr-rating-item")?.ownText()?.trim()
        val duration = doc.selectFirst(".gmr-duration-item")?.ownText()?.replace(Regex("\\D"), "")?.toIntOrNull()
        val tags = doc.select(".gmr-movie-on a").map { it.text().trim() }
        val actors = doc.select("span[itemprop=director] span[itemprop=name]").map { it.text() }
        val trailer = doc.selectFirst(".gmr-trailer-popup")?.attr("href")
        val isSeries = doc.select("div.gmr-season-block").isNotEmpty()
        val recommendations = doc.select("article.item.col-md-20").mapNotNull { it.toRecommendResult() }

        if (isSeries) {
            val episodes = doc.select("div.gmr-season-block").flatMap { season ->
                val seasonNumber = season.selectFirst(".season-title")
                    ?.text()?.filter { it.isDigit() }?.toIntOrNull()

                season.select("div.gmr-season-episodes a")
                    .filterNot { it.text().contains("Batch", true) }
                    .mapNotNull { ep ->
                        val href = fixUrl(ep.attr("href"))
                        val rawTitle = ep.attr("title").trim()
                        val epNum = Regex("Episode\\s*(\\d+)", RegexOption.IGNORE_CASE)
                            .find(rawTitle)
                            ?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val cleanTitle = epNum?.let { "Episode $it" } ?: "Episode"

                        newEpisode(href) {
                            name = cleanTitle
                            episode = epNum
                            this.season = seasonNumber
                            this.posterUrl = poster
                        }
                    }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                addScore(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            posterUrl = poster
            this.year = year
            plot = description
            this.tags = tags
            addScore(rating)
            addActors(actors)
            this.recommendations = recommendations
            this.duration = duration ?: 0
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadMainUrlIfNeeded()
        val document = app.get(data).document
        val postId = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id")

        if (!postId.isNullOrEmpty()) {
            document.select("div.tab-content-ajax").forEach { tab ->
                val tabId = tab.attr("id")
                if (tabId.isBlank()) return@forEach

                val response = app.post(
                    "$directUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tabId,
                        "post_id" to postId
                    )
                ).document

                response.select("iframe").forEach { frame ->
                    frame.getIframeAttr()?.let { httpsify(it) }?.let { link ->
                        if (link.isNotBlank()) loadExtractor(link, "$directUrl/", subtitleCallback, callback)
                    }
                }
            }
        } else {
            // Fallback jika tidak menggunakan AJAX
            document.select("div.gmr-embed-responsive iframe").forEach { frame ->
                frame.getIframeAttr()?.let { httpsify(it) }?.let { link ->
                    if (link.isNotBlank()) loadExtractor(link, "$directUrl/", subtitleCallback, callback)
                }
            }

            document.select("ul.muvipro-player-tabs li a").forEach { ele ->
                if (!ele.hasClass("active")) {
                    val href = ele.attr("href")
                    if (href.isNotBlank() && !href.startsWith("javascript")) {
                        val iframe = app.get(fixUrl(href)).document
                            .selectFirst("div.gmr-embed-responsive iframe")
                            ?.getIframeAttr()?.let { httpsify(it) }

                        iframe?.let { loadExtractor(it, "$directUrl/", subtitleCallback, callback) }
                    }
                }
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }

    private fun Element?.getIframeAttr(): String? =
        this?.attr("data-litespeed-src").takeIf { !it.isNullOrEmpty() } ?: this?.attr("src")

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.value ?: return this
        return replace(regex, "")
    }

    private fun getBaseUrl(url: String): String =
        URI(url).let { "${it.scheme}://${it.host}" }
}