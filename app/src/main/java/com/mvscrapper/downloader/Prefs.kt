package com.mvscrapper.downloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User settings, saved on the device. Defaults follow the blueprint's rules. */
object Prefs {
    const val MIN_PARALLEL = 1
    const val MAX_PARALLEL = 5

    private lateinit var sp: SharedPreferences

    private val _parallel = MutableStateFlow(2)          // R3
    private val _wifiOnly = MutableStateFlow(true)       // R2
    private val _autoUpdate = MutableStateFlow(true)     // R5
    private val _noticeAccepted = MutableStateFlow(false) // R8

    val parallel: StateFlow<Int> = _parallel
    val wifiOnly: StateFlow<Boolean> = _wifiOnly
    val autoUpdate: StateFlow<Boolean> = _autoUpdate
    val noticeAccepted: StateFlow<Boolean> = _noticeAccepted

    /** An on/off setting saved under [key]. */
    class Flag(private val key: String, default: Boolean) {
        private val _state = MutableStateFlow(default)
        val state: StateFlow<Boolean> = _state
        val on: Boolean get() = _state.value
        fun load() { _state.value = sp.getBoolean(key, _state.value) }
        fun set(v: Boolean) {
            _state.value = v
            sp.edit().putBoolean(key, v).apply()
        }
    }

    // v2 download options, from yt-dlp's post-processing and MeTube's defaults.
    val embedMetadata = Flag("embed_metadata", true)
    val embedThumbnail = Flag("embed_thumbnail", true)
    val embedChapters = Flag("embed_chapters", true)
    val embedSubtitles = Flag("embed_subtitles", false)
    val skipDownloaded = Flag("skip_downloaded", false)
    private val flags = listOf(embedMetadata, embedThumbnail, embedChapters, embedSubtitles, skipDownloaded)

    /** How many videos to list from a playlist or channel: the first N (for channels, the newest). 0 = all. */
    val PLAYLIST_LIMITS = listOf(0, 10, 25, 50, 100, 200)
    private val _playlistLimit = MutableStateFlow(0)
    val playlistLimit: StateFlow<Int> = _playlistLimit

    fun setPlaylistLimit(n: Int) {
        _playlistLimit.value = n
        sp.edit().putInt("playlist_limit", n).apply()
    }

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        flags.forEach { it.load() }
        _playlistLimit.value = sp.getInt("playlist_limit", 0)
        _parallel.value = sp.getInt("parallel", 2).coerceIn(MIN_PARALLEL, MAX_PARALLEL)
        _wifiOnly.value = sp.getBoolean("wifi_only", true)
        _autoUpdate.value = sp.getBoolean("auto_update", true)
        _noticeAccepted.value = sp.getBoolean("notice_accepted", false)
    }

    fun setParallel(n: Int) {
        val v = n.coerceIn(MIN_PARALLEL, MAX_PARALLEL)
        _parallel.value = v
        sp.edit().putInt("parallel", v).apply()
        Downloads.pump() // a higher limit starts waiting items right away
    }

    fun setWifiOnly(on: Boolean) {
        _wifiOnly.value = on
        sp.edit().putBoolean("wifi_only", on).apply()
        Downloads.pump()
    }

    fun setAutoUpdate(on: Boolean) {
        _autoUpdate.value = on
        sp.edit().putBoolean("auto_update", on).apply()
    }

    fun acceptNotice() {
        _noticeAccepted.value = true
        sp.edit().putBoolean("notice_accepted", true).apply()
    }
}
