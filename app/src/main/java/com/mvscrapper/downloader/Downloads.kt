package com.mvscrapper.downloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The download queue. Runs up to [Prefs.parallel] downloads at once (rule R3);
 * the rest wait. Everything is saved, so the queue survives the app being killed.
 */
object Downloads {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val saveLock = Any()
    @Volatile private var ready = false

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    fun init(context: Context) {
        app = context.applicationContext
        _items.value = Store.load(app).map {
            if (it.isActive) it.copy(state = State.Waiting, message = "Resumed after restart") else it
        }
        ready = true
        Net.watch(app) { pump() }
    }

    fun get(id: String): Item? = _items.value.firstOrNull { it.id == id }

    fun enqueue(meta: VideoMeta, spec: Spec, section: String = "", splitChapters: Boolean = false) {
        val item = Item(
            section = section,
            splitChapters = splitChapters,
            folder = if (splitChapters) Storage.folderName(meta.title) else "",
            id = UUID.randomUUID().toString(),
            url = meta.url,
            title = meta.title,
            thumbnail = meta.thumbnail,
            durationSec = meta.durationSec,
            spec = spec,
            sizeBytes = spec.sizeBytes,
        )
        synchronized(lock) { _items.value = _items.value + item }
        persist()
        pump()
    }

    /** Queues every chosen video of a playlist, saved together in a folder named after it. */
    fun enqueueAll(playlist: PlaylistMeta, entries: List<PlaylistEntry>, spec: Spec) {
        val folder = Storage.folderName(playlist.title)
        val now = System.currentTimeMillis()
        val group = if (entries.size > 1) UUID.randomUUID().toString() else ""
        val items = entries.mapIndexed { i, e ->
            Item(
                group = group,
                groupTitle = playlist.title,
                id = UUID.randomUUID().toString(),
                url = e.url,
                title = e.title,
                thumbnail = e.thumbnail,
                durationSec = e.durationSec,
                spec = spec,
                createdAt = now + i, // keeps playlist order in the queue
                folder = folder,
            )
        }
        synchronized(lock) { _items.value = _items.value + items }
        persist()
        pump()
    }

    /** Queues the chosen files of a magnet link as one entry (rule R14: same queue and limits). */
    fun enqueueTorrent(meta: TorrentMeta, chosen: List<TorrentFile>) {
        val id = UUID.randomUUID().toString()
        Torrents.saveMetadata(app, id, meta.metadata)
        val size = chosen.sumOf { it.sizeBytes }
        val item = Item(
            id = id,
            url = meta.url,
            title = meta.name,
            thumbnail = null,
            durationSec = 0,
            spec = Spec(label = "Torrent · ${chosen.size} file${if (chosen.size == 1) "" else "s"}", format = "", sizeBytes = size),
            sizeBytes = size,
            folder = Storage.folderName(meta.name),
            torrentFiles = chosen.map { it.index },
        )
        synchronized(lock) { _items.value = _items.value + item }
        persist()
        pump()
    }

    /** Cancels every unfinished item of a playlist group. */
    fun cancelGroup(group: String) {
        _items.value.filter { it.group == group && (it.isActive || it.state == State.Waiting) }.forEach { cancel(it.id) }
    }

    /** Puts every failed or canceled item of a playlist group back in the queue. */
    fun retryGroup(group: String) {
        _items.value.filter { it.group == group && (it.state == State.Failed || it.state == State.Canceled) }
            .forEach { item -> update(item.id) { it.copy(state = State.Waiting, progress = 0f, etaSec = 0, message = "") } }
        pump()
    }

    fun cancel(id: String) {
        update(id) { if (it.state == State.Done) it else it.copy(state = State.Canceled, message = "Canceled") }
        scope.launch { Engine.cancel(id) }
        pump()
    }

    fun retry(id: String) {
        update(id) { it.copy(state = State.Waiting, progress = 0f, etaSec = 0, message = "") }
        pump()
    }

    /**
     * Removes items. With [deleteFiles], also deletes the saved files.
     * Returns how many files Android refused to delete.
     */
    suspend fun delete(ids: Set<String>, deleteFiles: Boolean): Int = withContext(Dispatchers.IO) {
        var refused = 0
        val targets = _items.value.filter { it.id in ids }
        for (item in targets) {
            if (item.isActive || item.state == State.Waiting) {
                update(item.id, persist = false) { it.copy(state = State.Canceled) }
                Engine.cancel(item.id)
            }
            if (deleteFiles) {
                (item.fileUris + listOfNotNull(item.fileUri)).distinct().forEach { u ->
                    if (!Storage.delete(app, Uri.parse(u))) refused++
                }
            }
            workDir(item.id).deleteRecursively()
            if (item.isTorrent) Torrents.deleteMetadata(app, item.id)
        }
        synchronized(lock) { _items.value = _items.value.filterNot { it.id in ids } }
        persist()
        pump()
        refused
    }

    /** Starts waiting items while there are free slots and the network rule allows it. */
    fun pump() {
        if (!ready || Engine.status.value != Engine.Status.Ready) return
        val toStart = mutableListOf<String>()
        synchronized(lock) {
            val list = _items.value
            var running = list.count { it.isActive }
            val limit = Prefs.parallel.value
            val wifiBlocked = Prefs.wifiOnly.value && !Net.isUnmetered(app)
            if (!wifiBlocked) {
                for (w in list.filter { it.state == State.Waiting }.sortedBy { it.createdAt }) {
                    if (running >= limit) break
                    toStart += w.id
                    running++
                }
            }
            _items.value = list.map {
                when {
                    it.id in toStart -> it.copy(state = State.Running, progress = 0f, message = "")
                    it.state != State.Waiting -> it
                    wifiBlocked -> it.copy(message = "Waiting for Wi-Fi")
                    else -> it.copy(message = "Waiting for a free slot")
                }
            }
        }
        if (toStart.isNotEmpty()) persist()
        toStart.forEach { id -> scope.launch { run(id) } }
        if (_items.value.any { it.isActive }) startService()
    }

    private fun runTorrent(item: Item, dir: File) {
        val id = item.id
        val files = Torrents.download(
            app, item, dir,
            onProgress = { p, eta, info ->
                update(id, persist = false) {
                    if (it.state == State.Canceled) it else it.copy(progress = p, etaSec = eta, message = info)
                }
            },
            isCanceled = { get(id)?.state.let { it == null || it == State.Canceled } },
        )
        if (get(id)?.state != State.Running) return
        if (files.isEmpty()) error("The torrent finished but none of the chosen files were found")
        update(id) { it.copy(state = State.Processing, message = "Saving") }
        val uris = files.map { f ->
            // Keep the torrent's own sub-folders under Download/MV Downloader/<name>.
            val rel = f.parentFile?.relativeTo(dir)?.path.orEmpty()
            val sub = listOf(item.folder, rel).filter { it.isNotEmpty() && it != "." }.joinToString("/")
            Storage.saveDownload(app, f, sub).toString()
        }
        update(id) {
            it.copy(
                state = State.Done, progress = 1f, etaSec = 0,
                fileUri = uris.first(), fileUris = uris, fileName = files.first().name,
                sizeBytes = files.sumOf { f -> f.length() },
                message = "Saved ${files.size} file${if (files.size == 1) "" else "s"} to ${Storage.downloadsLabel(item.folder)}",
            )
        }
        dir.deleteRecursively()
        Torrents.deleteMetadata(app, id)
    }

    private fun run(id: String) {
        val item = get(id) ?: return
        val dir = workDir(id).apply { mkdirs() }
        var skipped = false
        try {
            if (item.isTorrent) {
                runTorrent(item, dir)
                return
            }
            Engine.download(item, dir) { p, eta, line ->
                if (line.contains("already been recorded in the archive")) skipped = true
                val processing = POST_STEPS.any { line.startsWith(it) }
                update(id, persist = false) {
                    if (it.state == State.Canceled) it
                    else it.copy(
                        progress = if (p >= 0) (p / 100f).coerceIn(0f, 1f) else it.progress,
                        etaSec = eta.coerceAtLeast(0),
                        state = if (processing) State.Processing else it.state,
                    )
                }
            }
            if (get(id)?.state != State.Running && get(id)?.state != State.Processing) return
            if (skipped) {
                update(id) { it.copy(state = State.Canceled, message = "Skipped: downloaded before. Turn off \"Skip videos already downloaded\" in Settings to get it again.") }
                return
            }
            update(id) { it.copy(state = State.Processing, message = "Saving") }
            fun finished(d: File) = d.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.contains(".temp.") }
                .orEmpty()
            // Split by chapters: save every chapter file; if the video had no chapters, the whole file.
            val chapters = if (item.splitChapters) finished(File(dir, "chapters")).sortedBy { it.name } else emptyList()
            val files = chapters.ifEmpty {
                listOf(finished(dir).maxByOrNull { it.length() } ?: error("yt-dlp finished but no file was found"))
            }
            val uris = files.map { Storage.save(app, it, item.spec.isAudio, item.folder).toString() }
            update(id) {
                it.copy(
                    state = State.Done, progress = 1f, etaSec = 0,
                    fileUri = uris.first(), fileUris = uris, fileName = files.first().name,
                    sizeBytes = files.sumOf { f -> f.length() },
                    message = if (files.size > 1) "Saved ${files.size} chapters to ${Storage.folderLabel(item.spec.isAudio, item.folder)}"
                    else "Saved to ${Storage.folderLabel(item.spec.isAudio, item.folder)}",
                )
            }
            dir.deleteRecursively()
        } catch (t: Throwable) {
            // A canceled download keeps its partial file, so Retry can continue it.
            if (get(id)?.state != State.Canceled) {
                update(id) { it.copy(state = State.Failed, message = Engine.cleanError(t)) }
            }
        } finally {
            pump()
        }
    }

    private fun update(id: String, persist: Boolean = true, f: (Item) -> Item) {
        synchronized(lock) { _items.value = _items.value.map { if (it.id == id) f(it) else it } }
        if (persist) persist()
    }

    private fun persist() {
        scope.launch { synchronized(saveLock) { Store.save(app, _items.value) } }
    }

    private fun workDir(id: String) = File(app.filesDir, "work/$id")

    private fun startService() {
        if (DownloadService.running) return
        try {
            ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java))
        } catch (_: Throwable) {
            // Android refuses to start a foreground service from the background on 12+.
            // The download still runs while the app process lives.
        }
    }

    /** yt-dlp log prefixes for the steps after the download itself. */
    private val POST_STEPS = listOf("[Merger]", "[ExtractAudio]", "[VideoConvertor]", "[FixupM3u8]", "[FixupM4a]", "[ffmpeg]")
}
