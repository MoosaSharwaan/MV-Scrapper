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

/** One video inside a playlist or channel, as listed without fetching each video. */
data class PlaylistEntry(
    val url: String,
    val title: String,
    val durationSec: Int,
    val thumbnail: String?,
)

data class PlaylistMeta(
    val url: String,
    val title: String,
    val site: String,
    val entries: List<PlaylistEntry>,
)

data class TorrentFile(val index: Int, val path: String, val sizeBytes: Long)

/** What a magnet link contains. [metadata] is the .torrent data, kept so peers aren't asked twice. */
class TorrentMeta(
    val url: String,
    val name: String,
    val files: List<TorrentFile>,
    val metadata: ByteArray,
)

/** A link is a single video, a playlist / channel, or a magnet link. */
sealed interface Fetched {
    data class Video(val meta: VideoMeta) : Fetched
    data class Playlist(val meta: PlaylistMeta) : Fetched
    class Torrent(val meta: TorrentMeta) : Fetched
}

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
    /** Sub-folder inside MV Downloader, e.g. the playlist name (rule R9). Empty for single videos. */
    val folder: String = "",
    /** For magnet links: the indexes of the files you chose. */
    val torrentFiles: List<Int> = emptyList(),
    /** Every saved file, for torrents with more than one. [fileUri] is the first. */
    val fileUris: List<String> = emptyList(),
    /** yt-dlp download section, e.g. "*1:00-2:30". Empty = the whole video. */
    val section: String = "",
    /** Save one file per chapter instead of one file. */
    val splitChapters: Boolean = false,
    /** Items queued together from one playlist share a group, shown as one card in the Queue. */
    val group: String = "",
    val groupTitle: String = "",
) {
    val isActive: Boolean get() = state == State.Running || state == State.Processing
    val isTorrent: Boolean get() = url.startsWith("magnet:", ignoreCase = true)
}
