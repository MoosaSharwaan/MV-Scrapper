package com.mvscrapper.downloader

import android.content.Context
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.torrent_flags_t
import java.io.File

/**
 * Magnet links, through libtorrent4j (MIT). The torrent session runs only while a magnet
 * is being read or downloaded, and a torrent stops sharing as soon as it finishes (rule R13).
 */
object Torrents {
    /** How long to wait for peers to send the torrent's file list. */
    private const val METADATA_TIMEOUT_SEC = 90

    private var session: SessionManager? = null
    private var users = 0
    private val lock = Any()

    fun isMagnet(link: String) = link.trim().startsWith("magnet:", ignoreCase = true)

    private fun acquire(): SessionManager = synchronized(lock) {
        users++
        session ?: SessionManager(false).also { it.start(); session = it }
    }

    /** Stops the session when nothing uses it, so it doesn't run in the background. */
    private fun release() {
        val toStop = synchronized(lock) {
            users = (users - 1).coerceAtLeast(0)
            if (users == 0) session.also { session = null } else null
        }
        toStop?.let { runCatching { it.stop() } }
    }

    /** Asks peers for the torrent's file list. Blocking; can take up to 90 seconds. */
    fun fetch(context: Context, magnet: String): TorrentMeta {
        val s = acquire()
        try {
            val tmp = File(context.cacheDir, "magnet").apply { mkdirs() }
            val bytes = s.fetchMagnet(magnet.trim(), METADATA_TIMEOUT_SEC, tmp)
                ?: throw IllegalStateException(
                    "ERROR: No peer sent the file list within $METADATA_TIMEOUT_SEC seconds. " +
                        "Check that the torrent client on your server is running and sharing this torrent.",
                )
            val ti = TorrentInfo(bytes)
            val fs = ti.files()
            val files = (0 until fs.numFiles()).map { TorrentFile(it, fs.filePath(it), fs.fileSize(it)) }
            return TorrentMeta(url = magnet.trim(), name = ti.name(), files = files, metadata = bytes)
        } finally {
            release()
        }
    }

    /** Reads a .torrent file. No peers needed: the file list is inside it. */
    fun fromTorrentFile(bytes: ByteArray): TorrentMeta {
        val ti = try {
            TorrentInfo(bytes)
        } catch (e: Exception) {
            throw IllegalStateException("ERROR: That isn't a valid .torrent file.")
        }
        val fs = ti.files()
        val files = (0 until fs.numFiles()).map { TorrentFile(it, fs.filePath(it), fs.fileSize(it)) }
        // Queue entries are recognised as torrents by a magnet: link, so build one from the hash.
        val magnet = "magnet:?xt=urn:btih:${ti.infoHash().toHex()}&dn=${java.net.URLEncoder.encode(ti.name(), "UTF-8")}"
        return TorrentMeta(url = magnet, name = ti.name(), files = files, metadata = bytes)
    }

    /** Keeps the file list so a queued or retried download doesn't have to ask peers again. */
    fun saveMetadata(context: Context, id: String, bytes: ByteArray) {
        metaFile(context, id).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    fun deleteMetadata(context: Context, id: String) {
        metaFile(context, id).delete()
    }

    private fun metaFile(context: Context, id: String) = File(context.filesDir, "torrents/$id.torrent")

    /**
     * Downloads the chosen files into [dir]. Blocking until done; throws on error or when
     * [isCanceled] turns true. Partial files stay in [dir], so Retry continues them.
     * Returns the finished files.
     */
    fun download(
        context: Context,
        item: Item,
        dir: File,
        onProgress: (progress: Float, etaSec: Long, info: String) -> Unit,
        isCanceled: () -> Boolean,
    ): List<File> {
        val meta = metaFile(context, item.id)
        if (!meta.exists()) throw IllegalStateException("ERROR: The torrent's file list is missing. Paste the magnet link again.")
        val ti = TorrentInfo(meta)
        val priorities = Array(ti.numFiles()) { if (it in item.torrentFiles) Priority.DEFAULT else Priority.IGNORE }
        val s = acquire()
        try {
            s.download(ti, dir, null, priorities, null, torrent_flags_t())
            while (true) {
                if (isCanceled()) throw InterruptedException("canceled")
                val handle = s.find(ti.infoHash())
                if (handle == null || !handle.isValid) { // the add is asynchronous
                    Thread.sleep(500)
                    continue
                }
                val st = handle.status()
                if (st.errorCode().isError) throw IllegalStateException("ERROR: ${st.errorCode().message}")
                val wanted = st.totalWanted()
                val done = st.totalWantedDone()
                val rate = st.downloadRate().toLong()
                val progress = if (wanted > 0) done.toFloat() / wanted else 0f
                val eta = if (rate > 0) (wanted - done) / rate else 0L
                val peers = st.numPeers()
                val info = if (peers == 0) "Looking for peers" else "$peers peer${if (peers == 1) "" else "s"} · ${rate / 1000} kB/s"
                onProgress(progress, eta, info)
                if (st.isFinished || (wanted > 0 && done >= wanted)) break
                Thread.sleep(1000)
            }
        } finally {
            // Rule R13: stop sharing as soon as the download ends, finished or not.
            runCatching { s.find(ti.infoHash())?.let { if (it.isValid) s.remove(it) } }
            release()
        }
        val fs = ti.files()
        return item.torrentFiles.map { File(fs.filePath(it, dir.absolutePath)) }.filter { it.isFile }
    }
}
