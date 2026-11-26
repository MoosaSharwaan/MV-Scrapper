package com.mvscrapper.downloader

/**
 * One way to download a video. Presets come first in the picker (rule R10),
 * followed by one row per resolution the site offers.
 */
data class Spec(
    val label: String,
    /** yt-dlp `-f` format selector. */
    val format: String,
    /** yt-dlp `-S` sort string, or empty. */
    val sort: String = "",
    /** Audio format for `-x --audio-format`, or empty for video. */
    val audio: String = "",
    /** Container(s) for `--merge-output-format`, e.g. "mp4" or "mp4/mkv". */
    val container: String = "mp4",
    /** Estimated size in bytes, 0 when the site doesn't say. */
    val sizeBytes: Long = 0,
) {
    val isAudio: Boolean get() = audio.isNotEmpty()

    companion object {
        /** Rule R4: the default pick is the best quality up to 1080p. */
        val MP4_1080 = Spec(
            label = "MP4 · best up to 1080p",
            format = "bv*+ba/b",
            sort = "res:1080,vcodec:h264,acodec:aac",
            container = "mp4",
        )
        val MKV_BEST = Spec(label = "MKV · best available", format = "bv*+ba/b", container = "mkv")
        val MP3 = Spec(label = "MP3 audio", format = "ba/b", audio = "mp3")
        val M4A = Spec(label = "M4A (AAC) audio", format = "ba[ext=m4a]/ba/b", audio = "m4a")
        val PRESETS = listOf(MP4_1080, MKV_BEST, MP3, M4A)

        fun height(h: Int, sizeBytes: Long) = Spec(
            label = "${h}p",
            format = "bv*[height<=$h]+ba/b[height<=$h]/b",
            container = "mp4/mkv",
            sizeBytes = sizeBytes,
        )
    }
}

/** What yt-dlp found for a link. */
data class VideoMeta(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Int,
    val site: String,
    /** One entry per available video height, highest first. */
    val heights: List<Spec>,
)

enum class State { Waiting, Running, Processing, Done, Failed, Canceled }

/** A queue entry. Finished entries stay in the list until you delete them (rule R11). */
data class Item(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val durationSec: Int,
    val spec: Spec,
    val state: State = State.Waiting,
    val progress: Float = 0f,
    val etaSec: Long = 0,
    val message: String = "",
    val fileUri: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean get() = state == State.Running || state == State.Processing
}
