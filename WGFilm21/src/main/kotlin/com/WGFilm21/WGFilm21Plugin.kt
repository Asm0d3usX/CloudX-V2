package com.wgfilm21

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WGFilm21Plugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WGFilm21())

        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Movearnpre())
		registerExtractorAPI(Morencius())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(Ryderjet())
        registerExtractorAPI(Dinisglows())
        registerExtractorAPI(Smoothpre())
        registerExtractorAPI(Dhtpre())
		registerExtractorAPI(Dintezuvio())
		registerExtractorAPI(IDFL())
    }
}
