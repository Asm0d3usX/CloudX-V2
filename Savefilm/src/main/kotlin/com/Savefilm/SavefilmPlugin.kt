package com.savefilm

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SavefilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Savefilm())
        registerExtractorAPI(Dingtezuni())
        registerExtractorAPI(Movearnpre())
        registerExtractorAPI(Mivalyo())
        registerExtractorAPI(Bingezove())
        registerExtractorAPI(Ryderjet())
		registerExtractorAPI(Morencius())
		registerExtractorAPI(Dintezuvio())
        registerExtractorAPI(Ghbrisk())
        registerExtractorAPI(Hglink())
        registerExtractorAPI(Dhcplay())
		registerExtractorAPI(Short())
        registerExtractorAPI(Streamcasthub())
        registerExtractorAPI(Dm21upns())
    }
}
