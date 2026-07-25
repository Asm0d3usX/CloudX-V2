package com.klikxxi

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import java.net.URI

class Hglink : StreamWishExtractor() {
    override val name = "Hglink"
    override val mainUrl = "https://hglink.to"
}