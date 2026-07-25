package com.pencurimovie

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URI

class Pencurimovie : MainAPI() {

    override var mainUrl = "https://ww11.pencurimovie.sbs"
    private val mainUrlJson = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"
    private var directUrl: String? = null
    override var name = "Pencurimovie"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "movies" to "Latest Movies",
        "series" to "TV Series",
        "most-rating" to "Most Rating Movies",
        "top-imdb" to "Top IMDB Movies",
        "country/malaysia" to "Malaysia Movies",
        "country/indonesia" to "Indonesia Movies",
        "country/india" to "India Movies",
        "country/japan" to "Japan Movies",
        "country/thailand" to "Thailand Movies",
        "country/china" to "China Movies",
    )

    private suspend fun loadMainUrlIfNeeded() {
        if (directUrl != null) return
        try {
            val response = app.get(mainUrlJson).text
            val json = JSONObject(response)
            val array = json.optJSONArray("pencurimovie")
            val newUrl = array?.optString(0)?.removeSuffix("/")

            if (!newUrl.isNullOrBlank()) {
                mainUrl = newUrl
                directUrl = newUrl
            }
        } catch (_: Exception) { }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        loadMainUrlIfNeeded()
        val document = app.get("$mainUrl/${request.data}/page/$page", timeout = 50L).document
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
		val a = this.selectFirst("a")
		val title = a?.attr("oldtitle")?.substringBefore("(")?.trim()
			?.ifEmpty { this.selectFirst("h2")?.text()?.trim() } ?: ""
		val href = fixUrl(a?.attr("href").orEmpty())

		// Panggil helper getImageAttr yang sudah difilter
		val img = this.selectFirst("img")
		val rawPoster = img?.getImageAttr()
		val posterUrl = fixUrlNull(rawPoster)

		val quality = this.selectFirst("span.mli-quality, div.jtip-quality")?.text()?.trim()?.replace("-", "") ?: ""
		val epsCount = this.selectFirst("span.mli-eps i")?.text()?.trim()?.toIntOrNull()
		val isSeries = epsCount != null || selectFirst("span.mli-eps") != null

		return if (isSeries) {
			newAnimeSearchResponse(title, href, TvType.TvSeries) {
				this.posterUrl = posterUrl
				addQuality(quality)
				if (epsCount != null) addSub(epsCount)
			}
		} else {
			newMovieSearchResponse(title, href, TvType.Movie) {
				this.posterUrl = posterUrl
				addQuality(quality)
			}
		}
	}

    override suspend fun search(query: String): List<SearchResponse> {
        loadMainUrlIfNeeded()
        val document = app.get("${mainUrl}?s=$query", timeout = 50L).document
        return document.select("div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        loadMainUrlIfNeeded()
        val document = app.get(url, timeout = 50L).document

        val title = document.selectFirst("div.mvic-desc h3")?.text()?.trim().toString().substringBefore("(")
        val poster = document.select("meta[property=og:image]").attr("content").toString()
        val description = document.selectFirst("div.desc p.f-desc")?.text()?.trim()
        val tvtag = if (url.contains("series")) TvType.TvSeries else TvType.Movie
        val trailer = document.select("meta[itemprop=embedUrl]").attr("content") ?: ""
        val genre = document.select("div.mvic-info p:contains(Genre)").select("a").map { it.text() }
        val rating = document.selectFirst("span.imdb-r[itemprop=ratingValue]")?.text()?.toDoubleOrNull()
        val duration = document.selectFirst("span[itemprop=duration]")?.text()?.replace(Regex("\\D"), "")?.toIntOrNull()
        val actors = document.select("div.mvic-info p:contains(Actors)").select("a").map { it.text() }
        val year = document.select("div.mvic-info p:contains(Release)").select("a").text().toIntOrNull()
        val recommendation = document.select("div.mlw-related div.ml-item").mapNotNull { it.toSearchResult() }

        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.tvseason").amap { info ->
                val season = info.select("strong").text().substringAfter("Season").trim().toIntOrNull()
                info.select("div.les-content a").forEach { it ->
                    val name = it.select("a").text().substringAfter("-").trim()
                    val href = it.select("a").attr("href") ?: ""
                    val Rawepisode = it.select("a").text().substringAfter("Episode").substringBefore("-").trim().toIntOrNull()
                    episodes.add(
                        newEpisode(href) {
                            this.episode = Rawepisode
                            this.name = name
                            this.season = season
                            this.posterUrl = poster
                        }
                    )
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations = recommendation
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
            }

        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.tags = genre
                this.year = year
                addTrailer(trailer)
                addActors(actors)
                this.recommendations = recommendation
                this.duration = duration ?: 0
                if (rating != null) addScore(rating.toString(), 10)
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
        document.select("div.movieplay iframe").forEach {
            val href = it.attr("data-src")
            if (href.isNotBlank()) {
                val finalUrl = followRedirect(href)
                loadExtractor(finalUrl, subtitleCallback, callback)
            }
        }
        return true
    }

    suspend fun followRedirect(url: String): String {
        return try {
            val resp = app.get(url, allowRedirects = false)
            val loc = resp.headers["Location"]
            if (!loc.isNullOrBlank()) {
                return if (loc.startsWith("http")) loc else url + loc
            }
            val doc = resp.document
            val meta = doc.selectFirst("meta[http-equiv~=(?i)refresh]")?.attr("content")
            if (!meta.isNullOrBlank()) {
                val u = meta.substringAfter("URL=", "").trim()
                if (u.isNotEmpty()) return if (u.startsWith("http")) u else url + u
            }
            url
        } catch (_: Exception) {
            url
        }
    }
	
	private fun Element.getImageAttr(): String {
		val srcAttr = attr("src").trim()
		val dataOriginal = attr("data-original").trim()
		val dataSrc = attr("data-src").trim()
		val dataLazySrc = attr("data-lazy-src").trim()

		return when {
			// Cek jika src valid dan BUKAN berupa data:image (placeholder/SVG)
			srcAttr.isNotBlank() && !srcAttr.startsWith("data:image") -> srcAttr
			dataOriginal.isNotBlank() && !dataOriginal.startsWith("data:image") -> dataOriginal
			dataSrc.isNotBlank() && !dataSrc.startsWith("data:image") -> dataSrc
			dataLazySrc.isNotBlank() && !dataLazySrc.startsWith("data:image") -> dataLazySrc
			else -> srcAttr
		}
	}
}