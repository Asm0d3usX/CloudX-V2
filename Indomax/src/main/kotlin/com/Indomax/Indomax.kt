package com.indomax

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI

class Indomax : MainAPI() {
    override var mainUrl = "https://idmxl.ink"
    private val mainUrlJson = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private var directUrl: String? = null
    override var name = "Indomax"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "category/box-office/page/%d/" to "Box Office",
        "category/serial-tv/page/%d/" to "TV Series",
        "category/action/page/%d/" to "Action",
        "category/adventure/page/%d/" to "Adventure",
        "category/animation/page/%d/" to "Animation",
        "category/anime/page/%d/" to "Anime",
        "category/comedy/page/%d/" to "Comedy",
        "category/donghua/page/%d/" to "Donghua",
        "category/thriller/page/%d/" to "Thriller",
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
            val array = json.optJSONArray("indomax")
            val newUrl = array?.optString(0)?.removeSuffix("/")

            if (!newUrl.isNullOrBlank()) {
                mainUrl = newUrl
                directUrl = newUrl
            }
        } catch (_: Exception) { }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loadMainUrlIfNeeded()
        val doc = app.get("$mainUrl/${request.data.format(page)}").document
        val items = doc.select("article.item-infinite").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title a")?.text()?.trim() ?: return null
        val href = fixUrl(selectFirst("h2.entry-title a")?.attr("href") ?: return null)
        val poster = fixUrlNull(selectFirst("img.wp-post-image")?.getImageAttr())?.fixImageQuality()

        val quality = selectFirst(".gmr-quality-item a")?.text()?.trim().orEmpty()
        val rating = selectFirst(".gmr-rating-item")?.ownText()?.toFloatOrNull()

        val isSeries = selectFirst(".gmr-numbeps span") != null
        val eps = selectFirst(".gmr-numbeps span")?.text()?.toIntOrNull()

        return if (isSeries) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                if (quality.isNotEmpty()) addQuality(quality)
                if (eps != null) {
                    addSub(eps)
                } else {
                    if (rating != null) this.score = Score.from10(rating)
                }               
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                if (quality.isNotEmpty()) addQuality(quality)
                if (rating != null) this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        loadMainUrlIfNeeded()
        val doc = app.get("$mainUrl/?s=$query&post_type[]=post&post_type[]=tv").document
        return doc.select("article.item-infinite").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = selectFirst("h2.entry-title > a")?.attr("href") ?: return null
        val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.attr("src"))?.fixImageQuality()
        val quality = select("div.gmr-qual, div.gmr-quality-item > a")
            .text().trim().replace("-", "")
        val ratingText = this.selectFirst("div.gmr-rating-item")?.ownText()?.trim()
        return if (quality.isEmpty()) {
            val episode = Regex("Episode\\s?([0-9]+)").find(title)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: select("div.gmr-numbeps > span").text().toIntOrNull()

            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                posterUrl = poster
                addQuality(quality)
                this.score = Score.from10(ratingText?.toDoubleOrNull())
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        loadMainUrlIfNeeded()
        val fetch = app.get(url)
        val doc = fetch.document
        directUrl = getBaseUrl(fetch.url)

        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim().orEmpty()
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content"))
        val description = doc.selectFirst("div[itemprop=description] p")?.text()?.trim()
        val tags = doc.select("div.gmr-moviedata a").map { it.text() }
        val year = doc.select("div.gmr-moviedata strong:contains(Release:) + a")
            .text().trim().toIntOrNull()
        val trailer = doc.selectFirst("a.gmr-trailer-popup")?.attr("href")
        val trailerIframe = doc.selectFirst(".gmr-embed-responsive iframe")?.attr("src")
        val actors = doc.select("span[itemprop=actor] a, span[itemprop=actors] a")
            .map { it.text() }
        val rating = doc.selectFirst("div.gmr-rating-bar span")
            ?.attr("style")
            ?.substringAfter("width:", "")
            ?.substringBefore("%", "")
            ?.trim()
            ?.toDoubleOrNull()
            ?.div(10)
        val duration = doc.selectFirst("div.gmr-moviedata:contains(Duration:) ")
            ?.text()?.filter { it.isDigit() }?.toIntOrNull()
        val recommendations = doc.select("article.item.col-md-20").mapNotNull { it.toRecommendResult() }

        return if (tvType == TvType.TvSeries) {
            val episodes = doc.select("div.vid-episodes a, div.gmr-listseries a")
                .mapNotNull { eps ->
                    val href = fixUrl(eps.attr("href"))
                    val rawTitle = eps.attr("title").takeIf { it.isNotBlank() } ?: eps.text()
                    val cleanTitle = rawTitle.replaceFirst(Regex("(?i)Permalink ke\\s*"), "").trim()

                    val epNum = Regex("Episode\\s*(\\d+)").find(cleanTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: cleanTitle.split(" ").lastOrNull()?.filter { it.isDigit() }?.toIntOrNull()

                    val formattedName = epNum?.let { "Episode $it" } ?: cleanTitle

                    newEpisode(href) {
                        this.name = formattedName
                        this.episode = epNum
                        this.posterUrl = poster
                    }
                }.filter { it.episode != null }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                posterUrl = poster
                plot = description
                this.tags = tags
                this.year = year
                if (rating != null) this.score = Score.from10(rating)
                addActors(actors)
                this.recommendations = recommendations
                this.duration = duration ?: 0
                addTrailer(trailerIframe)
            }

        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                posterUrl = poster
                plot = description
                this.tags = tags
                this.year = year
                if (rating != null) this.score = Score.from10(rating)
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
        val doc = app.get(data).document

        // 1. Ambil iframe aktif langsung dari halaman utama
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            iframe.getIframeAttr()?.let { httpsify(it) }?.let { url ->
                if (url.isNotBlank()) loadExtractor(url, "$directUrl/", subtitleCallback, callback)
            }
        }

        // 2. Loop ke tab server lain (lewati yang active)
        doc.select("ul.muvipro-player-tabs li a").forEach { server ->
            if (!server.hasClass("active")) {
                val serverHref = server.attr("href")
                if (serverHref.isNotBlank() && !serverHref.startsWith("javascript")) {
                    val serverUrl = fixUrl(serverHref)
                    val iframe = app.get(serverUrl).document
                        .selectFirst("div.gmr-embed-responsive iframe")
                        ?.getIframeAttr()
                        ?.let { httpsify(it) }

                    if (!iframe.isNullOrEmpty()) {
                        loadExtractor(iframe, "$directUrl/", subtitleCallback, callback)
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