package com.pusatmovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PusatmoviePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Pusatmovie())
        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(Ryderjet())
		registerExtractorAPI(Morencius())
		registerExtractorAPI(Luluvdo())
		registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Hglink())
		registerExtractorAPI(Hgcloud())
        registerExtractorAPI(Dhcplay())
        registerExtractorAPI(Streamcasthub())
        registerExtractorAPI(Dm21upns())
    }
}
