package com.savefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URI

class Savefilm : MainAPI() {
    override var mainUrl = "https://new13.savefilm21info.com"
	private val mainUrlJson = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private var directUrl: String? = null
    override var name = "Savefilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
		"year/2025/page/%d/" to "Terbaru",
		"tv/page/%d/" to "TV Series",
        "genre/action/page/%d/" to "Action",
		"genre/adventure/page/%d/" to "Adventure",
		"genre/animation/page/%d/" to "Animation",
		"genre/comedy/page/%d/" to "Comedy",
		"genre/crime/page/%d/" to "Crime",
		"genre/drama/page/%d/" to "Drama",
		"genre/horror/page/%d/" to "Horror",
		"genre/mystery/page/%d/" to "Mystery",
		"country/korea/page/%d/" to "Korea",
		"country/japan/page/%d/" to "Japan"
    )
	
	private suspend fun loadMainUrlIfNeeded() {
		if (directUrl != null) return
		val response = app.get(mainUrlJson).text
		val json = JSONObject(response)
		val array = json.optJSONArray("savefilm")
		val newUrl = array?.optString(0)?.removeSuffix("/")

		if (!newUrl.isNullOrBlank()) {
			mainUrl = newUrl
			directUrl = newUrl
		}
	}

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
		loadMainUrlIfNeeded()
        val document = app.get("$mainUrl/${request.data.format(page)}").document
        val items = document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleEl = selectFirst("h2.entry-title > a") ?: return null
        val title = titleEl.text().trim()
        val href = fixUrl(titleEl.attr("href"))
        val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.getImageAttr())?.fixImageQuality()
		val ratingText = selectFirst("div.gmr-rating-item")?.ownText()?.trim()
        val episodeFromSpan = select("div.gmr-numbeps span").text().toIntOrNull()
        val episodeFromTitle = Regex("Episode\\s?([0-9]+)", RegexOption.IGNORE_CASE)
            .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = episodeFromSpan ?: episodeFromTitle

        val quality = select("div.gmr-quality-item a").text().trim().replace("-", "")

        return if (episode != null) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
                addSub(episode)
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
		val a = selectFirst("h2.entry-title > a") ?: return null
		val title = a.text().trim()
		val href = fixUrl(a.attr("href"))
		val poster = fixUrlNull(selectFirst("div.content-thumbnail img")?.getImageAttr())?.fixImageQuality()
		val ratingText = selectFirst(".gmr-rating-item")?.ownText()?.trim()
		val quality = selectFirst(".gmr-quality-item a")?.text()?.trim()

		return newMovieSearchResponse(title, href, TvType.Movie) {
			posterUrl = poster
			this.score = Score.from10(ratingText?.toDoubleOrNull())
			if (!quality.isNullOrBlank()) addQuality(quality)
		}
	}


    override suspend fun load(url: String): LoadResponse {
		loadMainUrlIfNeeded()
        val fetch = app.get(url)
        val document = fetch.document
        directUrl = getBaseUrl(fetch.url)

        val title = document.selectFirst("h1.entry-title")?.text()
            ?.substringBefore("Season")?.substringBefore("Episode")?.trim().orEmpty()
        val poster = fixUrlNull(
            document.selectFirst(".gmr-movie-data img, figure.pull-left img")?.getImageAttr()
        )?.fixImageQuality()
        val tags = document.select(".gmr-moviedata a").map { it.text() }
        val year = document.select(".gmr-moviedata strong:contains(Year:) > a").text().trim().toIntOrNull()
        val description = document.selectFirst("div[itemprop=description] > p, .entry-content-single p")
            ?.text()?.trim()
        val trailer = document.selectFirst(".gmr-trailer-popup")?.attr("href")
        val rating = document.selectFirst("div.gmr-meta-rating span[itemprop=ratingValue]")?.text()?.trim()
        val actors = document.select("span[itemprop=actors] a").map { it.text() }
        val duration = document.selectFirst("span[property=duration]")
            ?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()
        val recommendations = document.select("div.gmr-box-content.gmr-box-archive")
			.mapNotNull { it.toRecommendResult() }
        val episodeNodes = document.select("div.vid-episodes a, div.gmr-listseries a")
        val isSeries = episodeNodes.isNotEmpty()

        return if (isSeries) {
            val episodes = episodeNodes.mapNotNull { eps ->
				val href = fixUrl(eps.attr("href"))
				val name = eps.text()

				val ep = Regex("Eps(\\d+)", RegexOption.IGNORE_CASE).find(name)
					?.groupValues?.getOrNull(1)?.toIntOrNull()
				val season = Regex("S(\\d+)", RegexOption.IGNORE_CASE).find(name)
					?.groupValues?.getOrNull(1)?.toIntOrNull()

				newEpisode(href) {
					this.name = if (ep != null) "Episode $ep" else name
					this.episode = ep
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
		val document = app.get(data).document

		document.select("div.gmr-embed-responsive iframe").forEach { frame ->
			val iframeUrl = frame.getIframeAttr()?.let { httpsify(it) } ?: return@forEach
			loadExtractor(iframeUrl, data, subtitleCallback, callback)
		}

		document.select("ul.muvipro-player-tabs a:not(.active)").forEach { link ->
			val serverUrl = fixUrl(link.attr("href"))
			if (serverUrl != data) {
				val serverDocument = app.get(serverUrl).document
				serverDocument.select("div.gmr-embed-responsive iframe").forEach { frame ->
					val iframeUrl = frame.getIframeAttr()?.let { httpsify(it) } ?: return@forEach
					loadExtractor(iframeUrl, serverUrl, subtitleCallback, callback)
				}
			}
		}

		document.select("ul.gmr-download-list li a").forEach { link ->
			val downloadUrl = link.attr("href")?.trim().orEmpty()
			if (downloadUrl.isNotEmpty()) {
				loadExtractor(downloadUrl, data, subtitleCallback, callback)
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
