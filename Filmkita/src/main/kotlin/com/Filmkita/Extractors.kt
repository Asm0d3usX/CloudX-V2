package com.filmkita

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URI
import android.util.Base64

class HlsTereaLayarwibu : ExtractorApi() {
    override val name = "HlsTereaLayarwibu"
    override val mainUrl = "https://hls-terea.layarwibu.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val customUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"

        val headers = mapOf(
            "User-Agent" to customUserAgent,
            "Referer" to url,
            "Origin" to mainUrl,
            "Sec-Ch-Ua" to """"Not;A=Brand";v="8", "Chromium";v="150", "Google Chrome";v="150"""",
            "Sec-Ch-Ua-Mobile" to "?0",
            "Sec-Ch-Ua-Platform" to """"Windows"""",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"
        )

        var masterUrl: String? = null

        // A. Jika URL sudah berakhiran .m3u8 langsung
        if (url.endsWith(".m3u8")) {
            masterUrl = url
        } 
        // B. Dekode Base64 dari path /player2/
        else if (url.contains("/player2/")) {
            val base64String = url.substringAfter("/player2/").substringBefore("?").trim()
            try {
                val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                val decodedUrl = String(decodedBytes, Charsets.UTF_8)

                if (decodedUrl.contains(".m3u8")) {
                    masterUrl = decodedUrl
                }
            } catch (_: Exception) { }
        }

        // C. Fallback via HTTP jika belum dapat masterUrl
        if (masterUrl == null) {
            val html = app.get(
                url = url,
                referer = url,
                headers = headers
            ).text

            val regex = Regex("""https?:\/\/[^\s"'<>]+\/master\.m3u8""")
            masterUrl = regex.find(html)?.value
        }

        val finalMasterUrl = masterUrl ?: return

        // Generate M3U8 Links
        generateM3u8(
            source = name,
            streamUrl = finalMasterUrl,
            referer = url,
            headers = headers
        ).forEach(callback)
    }
}

open class Dingtezuni : ExtractorApi() {
    override val name = "Earnvids"
    override val mainUrl = "https://dingtezuni.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Origin" to mainUrl,
            "User-Agent" to USER_AGENT,
        )

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if (result.contains("var links")) result = result.substringAfter("var links")
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { match ->
            generateM3u8(
                name,
                fixUrl(match.groupValues[1]),
                referer = "$mainUrl/",
                headers = headers
            ).forEach(callback)
        }
    }

    private fun getEmbedUrl(url: String): String = when {
        url.contains("/d/") -> url.replace("/d/", "/v/")
        url.contains("/download/") -> url.replace("/download/", "/v/")
        url.contains("/file/") -> url.replace("/file/", "/v/")
        else -> url.replace("/f/", "/v/")
    }
}

class Movearnpre : Dingtezuni() {
    override var name = "Movearnpre"
    override var mainUrl = "https://movearnpre.com"
}

class Mivalyo : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://mivalyo.com"
}

class Ryderjet : Dingtezuni() {
    override var name = "Ryderjet"
    override var mainUrl = "https://ryderjet.com"
}

class Bingezove : Dingtezuni() {
    override var name = "Earnvids"
    override var mainUrl = "https://bingezove.com"
}