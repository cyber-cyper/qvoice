package com.riniso.qvoice

import android.app.Application

/**
 * Process entry point. Android always runs this before any activity or the TTS
 * service is created, so [graph] is ready for them. Building the graph does no
 * disk I/O on the main thread (see VoiceStore.seedDeclared).
 */
class QVoiceApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
