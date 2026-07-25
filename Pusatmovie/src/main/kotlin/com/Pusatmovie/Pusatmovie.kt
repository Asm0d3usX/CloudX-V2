package com.pusatmovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI

class Pusatmovie : MainAPI() {
    override var mainUrl = "https://refugepdx.com"
    private val mainUrlJson = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private var directUrl: String? = null
    override var name = "Pusatmovie"
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
        "category/serial-tv/page/%d/" to "TV Series",
        "category/action/page/%d/" to "Action",
        "category/adventure/page/%d/" to "Adventure",
        "category/comedy/page/%d/" to "Comedy",
        "category/crime/page/%d/" to "Crime",
        "category/drama/page/%d/" to "Drama",
        "category/fantasy/page/%d/" to "Fantasy",
        "category/horror/page/%d/" to "Horror",
        "category/mystery/page/%d/" to "Mystery",
        "category/romance/page/%d/" to "Romance",
        "country/china/page/%d/" to "China",
        "country/indonesia/page/%d/" to "Indonesia",
        "country/korea/page/%d/" to "Korea",
        "country/philippines/page/%d/" to "Philippines",
        "country/thailand/page/%d/" to "Thailand"
    )

    private suspend fun loadMainUrlIfNeeded() {
        if (directUrl != null) return
        try {
            val response = app.get(mainUrlJson).text
            val json = JSONObject(response)
            val array = json.optJSONArray("pusatmovie")
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
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("h2.entry-title > a")?.attr("href") ?: return null)
        val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.getImageAttr()?.fixImageQuality())
        val quality = select("div.gmr-quality-item > a, div.gmr-qual > a").text().trim().replace("-", "")
        val episodes = select("div.gmr-numbeps > span").text().toIntOrNull() ?: 0
        val ratingText = this.selectFirst("div.gmr-rating-item")?.ownText()?.trim()

        return if (episodes > 0) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                if (episodes > 0) addSub(episodes)
                if (quality.isNotEmpty()) addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                this.score = Score.from10(ratingText?.toDoubleOrNull())
                if (quality.isNotEmpty()) addQuality(quality)
            }
        }
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        loadMainUrlIfNeeded()
        val document = app.get("$mainUrl?s=$query&post_type[]=post&post_type[]=tv").document
        return document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("h2.entry-title > a")?.attr("href") ?: return null)
        val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.getImageAttr()?.fixImageQuality())
        val quality = select("div.gmr-quality-item > a, div.gmr-qual > a").text().trim().replace("-", "")
        val episodes = select("div.gmr-numbeps > span").text().toIntOrNull() ?: 0
        val ratingText = this.selectFirst("div.gmr-rating-item")?.ownText()?.trim()

        return if (episodes > 0) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                if (episodes > 0) addSub(episodes)
                if (quality.isNotEmpty()) addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                this.score = Score.from10(ratingText?.toDoubleOrNull())
                if (quality.isNotEmpty()) addQuality(quality)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        loadMainUrlIfNeeded()
        val fetch = app.get(url)
        val document = fetch.document
        directUrl = getBaseUrl(fetch.url)

        val title = document.selectFirst("h1.entry-title")?.text()
            ?.substringBefore("Season")?.substringBefore("Episode")?.trim().orEmpty()
        val poster = fixUrlNull(document.selectFirst("div.gmr-movie-data figure img")?.getImageAttr()?.fixImageQuality())
        val tags = document.select("div.gmr-moviedata a").map { it.text() }
        val year = document.select("div.gmr-moviedata strong:contains(Year:) > a")
            .text().trim().toIntOrNull()
        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("div.gmr-meta-rating span[itemprop=ratingValue]")?.text()?.trim()
        val actors = document.select("div.gmr-moviedata span[itemprop=actors] a").map { it.text() }
        val duration = document.selectFirst("div.gmr-moviedata span[property=duration]")?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()
        val recommendations = document.select("article.item.col-md-20").mapNotNull { it.toRecommendResult() }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.vid-episodes a, div.gmr-listseries a")
                .mapNotNull { eps ->
                    val href = fixUrl(eps.attr("href"))
                    val rawTitle = eps.attr("title").takeIf { it.isNotBlank() } ?: eps.text()
                    val cleanTitle = rawTitle.replaceFirst(Regex("(?i)Permalink ke\\s*"), "").trim()

                    val epNum = Regex("Episode\\s*(\\d+)").find(cleanTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: cleanTitle.split(" ").lastOrNull()?.filter { it.isDigit() }?.toIntOrNull()
                    val seasonNum = Regex("Season\\s*(\\d+)").find(cleanTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: cleanTitle.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()

                    val formattedName = buildString {
                        if (epNum != null) append("Episode $epNum")
                    }

                    newEpisode(href) {
                        this.name = formattedName
                        this.episode = epNum
                        this.season = seasonNum
                        this.posterUrl = poster
                    }
                }.filter { it.episode != null }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                addScore(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                trailer?.let { addTrailer(it) }
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                addScore(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                trailer?.let { addTrailer(it) }
            }
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
            // Loop p1 sampai p8 secara bersamaan (paralel)
            document.select("div.tab-content-ajax").amap { ele ->
                val tabId = ele.attr("id")
                if (tabId.isBlank()) return@amap

                // 1. Cek jika server 1/default sudah terisi di HTML awal
                val existingIframe = ele.selectFirst("iframe")?.getIframeAttr()?.let { httpsify(it) }

                if (!existingIframe.isNullOrBlank()) {
                    loadExtractor(existingIframe, "$directUrl/", subtitleCallback, callback)
                } else {
                    // 2. Fetch via AJAX untuk semua tab yang kosong
                    try {
                        val responseDoc = app.post(
                            "$directUrl/wp-admin/admin-ajax.php",
                            data = mapOf(
                                "action" to "muvipro_player_content",
                                "tab" to tabId,
                                "post_id" to postId
                            )
                        ).document

                        val ajaxIframe = responseDoc.selectFirst("iframe")?.getIframeAttr()?.let { httpsify(it) }
                        if (!ajaxIframe.isNullOrBlank()) {
                            loadExtractor(ajaxIframe, "$directUrl/", subtitleCallback, callback)
                        }
                    } catch (_: Exception) { }
                }
            }
        } else {
            // Fallback jika id post tidak ditemukan
            document.select("div.gmr-embed-responsive iframe").forEach { iframe ->
                iframe.getIframeAttr()?.let { httpsify(it) }?.let { url ->
                    if (url.isNotBlank()) loadExtractor(url, "$directUrl/", subtitleCallback, callback)
                }
            }
        }

        // Ambil link ekstraksi dari tombol download (jika ada)
        document.select("ul.gmr-download-list li a").forEach { link ->
            val downloadUrl = link.attr("href")
            if (downloadUrl.isNotBlank()) loadExtractor(downloadUrl, data, subtitleCallback, callback)
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