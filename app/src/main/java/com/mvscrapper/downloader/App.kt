package com.mvscrapper.downloader

import android.app.Application
import kotlin.concurrent.thread

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Downloads.init(this)
        thread(name = "engine-init") {
            Engine.init(this)
            if (Engine.status.value == Engine.Status.Ready) {
                // Rule R5: update yt-dlp on launch, but only on Wi-Fi. Done before any
                // download starts, so yt-dlp isn't replaced while it is running.
                if (Prefs.autoUpdate.value && Net.isUnmetered(this)) Engine.update(this)
                Downloads.pump() // start anything left waiting from last time
            }
        }
    }
}
