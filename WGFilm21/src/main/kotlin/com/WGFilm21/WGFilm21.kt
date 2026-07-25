package com.wgfilm21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI

class WGFilm21 : MainAPI() {
    override var mainUrl = "https://alternatif3.wgfilm21.net"
    private val mainUrlJson = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private var directUrl: String? = null
    override var name = "WGFilm21"
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
        "category/action/page/%d/" to "Action",
        "category/adventure/page/%d/" to "Adventure",
        "category/anime/page/%d/" to "Anime",
        "category/animation/page/%d/" to "Animation",
        "category/crime/page/%d/" to "Crime",
        "category/mystery/page/%d/" to "Mystery",
        "category/horror/page/%d/" to "Horror",
        "category/romance/page/%d/" to "Romance",
        "category/thriller/page/%d/" to "Thriller",
        "category/war/page/%d/" to "War",
        "country/indonesia/page/%d/" to "Indonesia",
        "country/semi-filipina/page/%d/" to "Philippines",
        "country/china/page/%d/" to "China"
    )

    private suspend fun loadMainUrlIfNeeded() {
        if (directUrl != null) return
        try {
            val response = app.get(mainUrlJson).text
            val json = JSONObject(response)
            val array = json.optJSONArray("wgfilm21")
            val newUrl = array?.optString(0)?.removeSuffix("/")

            if (!newUrl.isNullOrBlank()) {
                mainUrl = newUrl
                directUrl = newUrl
            }
        } catch (_: Exception) { }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loadMainUrlIfNeeded()
        val document = app.get("${mainUrl}/${request.data.format(page)}").document
        val items = document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("h2.entry-title > a") ?: return null
        val title = titleEl.text().trim()
        val href = fixUrl(titleEl.attr("href"))

        // Extraction Poster
        val imgEl = selectFirst("div.content-thumbnail img")
        val rawPoster = imgEl?.getImageAttr()
        val poster = fixUrlNull(rawPoster)?.fixImageQuality()

        // Extraction Rating & Episode
        val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()
        val rating = ratingText?.toFloatOrNull()

        val epsText = selectFirst("div.gmr-numbeps span")?.text()?.trim()
        val eps = epsText?.toIntOrNull()
        
        // Pengecekan tipe TV Series vs Movie
        val isSeries = eps != null || selectFirst("div.gmr-posttype-item")?.text()?.contains("TV", true) == true

        return if (isSeries) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                if (eps != null) addSub(eps)
                if (rating != null) this.score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                if (rating != null) this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        loadMainUrlIfNeeded()
        val fixedQuery = query.replace(" ", "+")
        val searchUrl = "$mainUrl/?s=$fixedQuery&post_type[]=post&post_type[]=tv"
        val document = app.get(searchUrl).document
        return document.select("article.item-infinite.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("h2.entry-title > a")?.attr("href") ?: return null)
        val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.getImageAttr())?.fixImageQuality()
        val rating = selectFirst(".gmr-rating-item")?.ownText()?.trim()?.toFloatOrNull()
        val eps = selectFirst(".gmr-numbeps span")?.text()?.trim()?.toIntOrNull()
        val isSeries = eps != null

        return if (isSeries) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                if (eps != null) {
                    addSub(eps)
                } else {
                    if (rating != null) this.score = Score.from10(rating)
                }
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                if (rating != null) this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        loadMainUrlIfNeeded()
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()
            ?.substringBefore("Season")?.substringBefore("Episode")?.trim().orEmpty()
        val poster = fixUrlNull(document.selectFirst("div.gmr-movie-data figure img")?.getImageAttr()?.fixImageQuality())
        val tags = document.select("div.gmr-movie-on a, div.gmr-moviedata a").map { it.text() }
        val year = document.select("time[itemprop=dateCreated]")
            .attr("datetime").takeIf { it.isNotEmpty() }?.substringBefore("-")?.toIntOrNull()
        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div[itemprop=description] > p, div.gmr-moviedata p")?.text()?.trim()
        val trailer = document.selectFirst("ul.gmr-player-nav a.gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("div.gmr-meta-rating span[itemprop=ratingValue], div.gmr-rating-item")
            ?.text()?.trim()
        val actors = document.select("span[itemprop=director] a, span[itemprop=actors] a").map { it.text() }
        val duration = document.selectFirst("time[property=duration], span[property=duration]")
            ?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()
        val recommendations = document.select("article.item.col-md-20").mapNotNull { it.toRecommendResult() }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.vid-episodes a, div.gmr-listseries a")
            .mapNotNull { eps ->
                val href = fixUrl(eps.attr("href"))
                val name = eps.text().trim()

                val episode = Regex("Eps(\\d+)", RegexOption.IGNORE_CASE).find(name)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
                val season = Regex("S(\\d+)", RegexOption.IGNORE_CASE).find(name)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()

                newEpisode(href) {
                    this.name = if (episode != null) "Episode $episode" else name
                    this.episode = episode
                    this.season = season
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
                addTrailer(trailer)
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
                addTrailer(trailer)
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
        println("⌛ CEK LOAD DIPROSES")
        val document = app.get(data).document
        val id = document.selectFirst("div#muvipro_player_content_id")?.attr("data-id")

        document.select("div.gmr-embed-responsive iframe").forEach { frame ->
            val iframe = frame.getIframeAttr()?.let { httpsify(it) } ?: return@forEach
            println("🎥 [IFRAME] URL ➜ $iframe")
            println("📦 [DATA] ➜ $data")
            loadExtractor(iframe, data, subtitleCallback, callback)
        }

        document.select("ul.muvipro-player-tabs li a[href^=#]").forEach { tab ->
            val tabId = tab.attr("href").removePrefix("#")
            val iframe = document
                .selectFirst("div#$tabId iframe")
                ?.getIframeAttr()
                ?.let { httpsify(it) }

            if (iframe != null) {
                println("🎥 [IFRAME] URL ➜ $iframe")
                println("📦 [DATA] ➜ $data")
                loadExtractor(iframe, data, subtitleCallback, callback)
            }
        }

        if (id != null && document.select("iframe").isEmpty()) {
            document.select("div.tab-content-ajax").forEach { ele ->
                val tabId = ele.attr("id")
                val serverUrl = "$mainUrl/wp-admin/admin-ajax.php"

                val res = app.post(
                    serverUrl,
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tabId,
                        "post_id" to id
                    )
                ).document
                    .selectFirst("iframe")
                    ?.attr("src")
                    ?.let { httpsify(it) }

                if (!res.isNullOrEmpty()) {
                    println("🎥 [RES] URL ➜ $res")
                    println("📦 [DATA] ➜ $data")
                    loadExtractor(res, data, subtitleCallback, callback)
                }
            }
        }

        document.select("ul.gmr-download-list li a").forEach { link ->
            val downloadUrl = link.attr("href")
            if (downloadUrl.isNotBlank()) {
                loadExtractor(downloadUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            hasAttr("data-src") && attr("data-src").isNotBlank() -> attr("abs:data-src")
            hasAttr("data-lazy-src") && attr("data-lazy-src").isNotBlank() -> attr("abs:data-lazy-src")
            hasAttr("src") && attr("src").isNotBlank() -> attr("abs:src")
            else -> attr("abs:src")
        }
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