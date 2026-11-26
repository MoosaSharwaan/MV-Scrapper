package com.mvscrapper.downloader

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /** Reads title, length and formats for one video. Blocking. */
    fun fetch(url: String): VideoMeta {
        val request = YoutubeDLRequest(url).addOption("--no-playlist")
        val info = YoutubeDL.getInstance().getInfo(request)
        return toMeta(url, info)
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

    private fun toMeta(url: String, info: VideoInfo): VideoMeta {
        val formats = info.formats.orEmpty()
        fun size(f: com.yausername.youtubedl_android.mapper.VideoFormat) =
            maxOf(f.fileSize, f.fileSizeApproximate)

        val bestAudio = formats
            .filter { (it.vcodec ?: "none") == "none" && (it.acodec ?: "none") != "none" }
            .maxOfOrNull { size(it) } ?: 0L

        val heights = formats
            .filter { (it.vcodec ?: "none") != "none" && it.height > 0 }
            .groupBy { it.height }
            .map { (h, list) ->
                val video = list.maxOfOrNull { size(it) } ?: 0L
                Spec.height(h, if (video > 0) video + bestAudio else 0L)
            }
            .sortedByDescending { it.label.removeSuffix("p").toIntOrNull() ?: 0 }

        return VideoMeta(
            url = url,
            title = info.title ?: info.fulltitle ?: url,
            thumbnail = info.thumbnail,
            durationSec = info.duration,
            site = info.extractorKey ?: info.extractor ?: "",
            heights = heights,
        )
    }

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
