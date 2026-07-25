package com.klikxxi

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class KlikXXiPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(KlikXXi())
		registerExtractorAPI(Hglink())
    }
}
