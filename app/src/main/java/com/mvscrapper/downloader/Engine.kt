package com.mvscrapper.downloader

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Thin wrapper around youtubedl-android (yt-dlp + Python + ffmpeg + QuickJS). */
object Engine {
    enum class Status { Starting, Ready, Failed }

    private val _status = MutableStateFlow(Status.Starting)
    private val _version = MutableStateFlow<String?>(null)
    val status: StateFlow<Status> = _status
    val version: StateFlow<String?> = _version
    var initError: String? = null
        private set

    /** Unpacks Python and ffmpeg on first run, which takes a few seconds. Call off the main thread. */
    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            _version.value = YoutubeDL.getInstance().version(context)
            _status.value = Status.Ready
        } catch (t: Throwable) {
            initError = t.message ?: t.javaClass.simpleName
            _status.value = Status.Failed
        }
    }

    /**
     * Reads a link. A single video comes back with its formats; a playlist or channel comes
     * back as a flat list of its videos (fast: each video isn't opened). Blocking.
     * With [single], a video link that also carries a playlist is read as just that video.
     */
    fun fetch(url: String, single: Boolean = false): Fetched {
        val request = YoutubeDLRequest(url)
            .addOption("--dump-single-json")
            .addOption("--flat-playlist")
        if (single) request.addOption("--no-playlist")
        val response = YoutubeDL.getInstance().execute(request, processId = null, redirectErrorStream = false, callback = null)
        val json = try {
            JSONObject(response.out)
        } catch (e: Exception) {
            throw IllegalStateException("ERROR: yt-dlp returned no video information")
        }
        return if (json.str("_type") == "playlist" || json.has("entries")) {
            Fetched.Playlist(toPlaylist(url, json))
        } else {
            Fetched.Video(toVideo(url, json))
        }
    }

    /** Downloads into [dir]. Blocking; throws on failure or cancel. */
    fun download(item: Item, dir: File, onProgress: (Float, Long, String) -> Unit) {
        val spec = item.spec
        val request = YoutubeDLRequest(item.url)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("-f", spec.format)
            // Rule R9: files are named after the title.
            .addOption("-o", File(dir, "%(title).120B.%(ext)s").absolutePath)
        if (spec.sort.isNotEmpty()) request.addOption("-S", spec.sort)
        if (spec.isAudio) {
            request.addOption("-x").addOption("--audio-format", spec.audio)
        } else {
            request.addOption("--merge-output-format", spec.container)
        }
        YoutubeDL.getInstance().execute(
            request,
            processId = item.id,
            redirectErrorStream = false,
            callback = onProgress,
        )
    }

    fun cancel(id: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    /** Updates yt-dlp to the latest stable release. Blocking. Returns a message for the user. */
    fun update(context: Context): String {
        return try {
            val result = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            _version.value = YoutubeDL.getInstance().version(context)
            when (result) {
                YoutubeDL.UpdateStatus.DONE -> "Updated to ${_version.value}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date"
                null -> "Update didn't finish. Try again later."
            }
        } catch (t: Throwable) {
            "Update failed: ${cleanError(t)}"
        }
    }

    private fun toVideo(url: String, json: JSONObject): VideoMeta {
        val formats = json.optJSONArray("formats") ?: JSONArray()
        data class F(val height: Int, val video: Boolean, val audio: Boolean, val size: Long)
        val list = (0 until formats.length()).mapNotNull { i ->
            val f = formats.optJSONObject(i) ?: return@mapNotNull null
            val v = f.optString("vcodec", "none").let { it.isNotEmpty() && it != "none" && it != "null" }
            val a = f.optString("acodec", "none").let { it.isNotEmpty() && it != "none" && it != "null" }
            val size = maxOf(f.optDouble("filesize", 0.0).safeLong(), f.optDouble("filesize_approx", 0.0).safeLong())
            F(f.optDouble("height", 0.0).safeLong().toInt(), v, a, size)
        }
        val bestAudio = list.filter { it.audio && !it.video }.maxOfOrNull { it.size } ?: 0L
        val heights = list
            .filter { it.video && it.height > 0 }
            .groupBy { it.height }
            .map { (h, fs) ->
                val video = fs.maxOf { it.size }
                Spec.height(h, if (video > 0) video + bestAudio else 0L)
            }
            .sortedByDescending { it.label.removeSuffix("p").toIntOrNull() ?: 0 }

        return VideoMeta(
            url = url,
            title = json.str("title").ifBlank { json.str("fulltitle").ifBlank { url } },
            thumbnail = json.str("thumbnail").ifBlank { null },
            durationSec = json.optDouble("duration", 0.0).safeLong().toInt(),
            site = json.str("extractor_key").ifBlank { json.str("extractor") },
            heights = heights,
        )
    }

    private fun toPlaylist(url: String, json: JSONObject): PlaylistMeta {
        val entries = json.optJSONArray("entries") ?: JSONArray()
        val list = (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val link = listOf(e.str("url"), e.str("webpage_url"), e.str("original_url"))
                .firstOrNull { it.startsWith("http") } ?: return@mapNotNull null
            val title = e.str("title").ifBlank { link }
            if (title == "[Private video]" || title == "[Deleted video]") return@mapNotNull null
            val thumbs = e.optJSONArray("thumbnails")
            val thumb = e.str("thumbnail").ifBlank { null }
                ?: thumbs?.optJSONObject(thumbs.length() - 1)?.str("url")?.ifBlank { null }
            PlaylistEntry(link, title, e.optDouble("duration", 0.0).safeLong().toInt(), thumb)
        }
        return PlaylistMeta(
            url = url,
            title = json.str("title").ifBlank { "Playlist" },
            site = json.str("extractor_key").ifBlank { json.str("extractor") },
            entries = list,
        )
    }

    /** optString, but a JSON null reads as empty instead of the text "null". */
    private fun JSONObject.str(key: String): String = optString(key).let { if (it == "null") "" else it }

    private fun Double.safeLong(): Long = if (isNaN() || isInfinite()) 0L else toLong()

    /** yt-dlp errors are long; keep the line that explains what went wrong. */
    fun cleanError(t: Throwable): String {
        val text = t.message ?: return t.javaClass.simpleName
        val line = text.lines().lastOrNull { it.contains("ERROR:") } ?: text.lines().firstOrNull { it.isNotBlank() } ?: text
        val trimmed = line.substringAfter("ERROR:").trim()
        return when {
            trimmed.contains("Unsupported URL", ignoreCase = true) ->
                "No video found on this page. The site may not be supported, or the video is hidden behind a custom player."
            trimmed.contains("DRM", ignoreCase = true) ->
                "This video is DRM-protected and can't be downloaded."
            trimmed.contains("Sign in", ignoreCase = true) || trimmed.contains("login", ignoreCase = true) ->
                "This video needs a sign-in. Cookie import comes in a later version."
            else -> trimmed.take(300)
        }
    }
}
