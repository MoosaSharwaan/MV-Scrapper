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
    // v3
    val sponsorBlock = Flag("sponsorblock", false)
    val nightly = Flag("ytdlp_nightly", false)
    private val flags = listOf(embedMetadata, embedThumbnail, embedChapters, embedSubtitles, skipDownloaded, sponsorBlock, nightly)

    /** A folder you chose in Settings (a document-tree URI), or null for the default folders. */
    private val _saveFolder = MutableStateFlow<String?>(null)
    val saveFolder: StateFlow<String?> = _saveFolder
    private val _saveFolderName = MutableStateFlow<String?>(null)
    val saveFolderName: StateFlow<String?> = _saveFolderName

    fun setSaveFolder(uri: String?, name: String?) {
        _saveFolder.value = uri
        _saveFolderName.value = name
        sp.edit().putString("save_folder", uri).putString("save_folder_name", name).apply()
    }

    /** File name styles (rule R9 default first). Values are yt-dlp output templates. */
    val NAME_TEMPLATES = listOf(
        "Title" to "%(title).120B.%(ext)s",
        "Uploader - Title" to "%(uploader).40B - %(title).100B.%(ext)s",
        "Date - Title" to "%(upload_date>%Y-%m-%d)s - %(title).100B.%(ext)s",
        "Title [id]" to "%(title).100B [%(id)s].%(ext)s",
    )
    private val _nameTemplate = MutableStateFlow(0)
    val nameTemplate: StateFlow<Int> = _nameTemplate

    fun setNameTemplate(i: Int) {
        _nameTemplate.value = i
        sp.edit().putInt("name_template", i).apply()
    }

    /** Speed limit per download in MB/s. 0 = no limit. Applies to yt-dlp downloads. */
    val SPEED_LIMITS = listOf(0, 1, 2, 5, 10)
    private val _speedLimit = MutableStateFlow(0)
    val speedLimit: StateFlow<Int> = _speedLimit

    fun setSpeedLimit(n: Int) {
        _speedLimit.value = n
        sp.edit().putInt("speed_limit", n).apply()
    }

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
        _speedLimit.value = sp.getInt("speed_limit", 0)
        _saveFolder.value = sp.getString("save_folder", null)
        _saveFolderName.value = sp.getString("save_folder_name", null)
        _nameTemplate.value = sp.getInt("name_template", 0).coerceIn(0, NAME_TEMPLATES.lastIndex)
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
