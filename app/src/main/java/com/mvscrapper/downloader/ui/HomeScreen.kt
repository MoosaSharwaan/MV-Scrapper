package com.mvscrapper.downloader.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvscrapper.downloader.Downloads
import com.mvscrapper.downloader.Engine
import com.mvscrapper.downloader.Fetched
import com.mvscrapper.downloader.PlaylistEntry
import com.mvscrapper.downloader.PlaylistMeta
import com.mvscrapper.downloader.Spec
import com.mvscrapper.downloader.State
import com.mvscrapper.downloader.TorrentFile
import com.mvscrapper.downloader.TorrentMeta
import com.mvscrapper.downloader.Torrents
import com.mvscrapper.downloader.VideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(sharedUrl: MutableState<String?>, onAdded: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val engine by Engine.status.collectAsStateWithLifecycle()

    var url by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<Fetched?>(null) }

    fun fetch(single: Boolean = false) {
        val link = extractUrl(url.trim()) ?: url.trim()
        if (link.isEmpty() || (engine != Engine.Status.Ready && !Torrents.isMagnet(link))) return
        loading = true
        error = null
        meta = null
        scope.launch {
            try {
                meta = withContext(Dispatchers.IO) {
                    if (Torrents.isMagnet(link)) Fetched.Torrent(Torrents.fetch(context, link)) else Engine.fetch(link, single)
                }
            } catch (t: Throwable) {
                error = Engine.cleanError(t)
            } finally {
                loading = false
            }
        }
    }

    /** Reads a .torrent file picked here or opened from another app. */
    fun openTorrent(uri: android.net.Uri) {
        loading = true
        error = null
        meta = null
        scope.launch {
            try {
                meta = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("ERROR: Couldn't read that file.")
                    Fetched.Torrent(Torrents.fromTorrentFile(bytes))
                }
            } catch (t: Throwable) {
                error = Engine.cleanError(t)
            } finally {
                loading = false
            }
        }
    }
    val pickTorrent = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openTorrent(it) }
    }

    // A link shared from another app is fetched as soon as the engine is ready.
    LaunchedEffect(sharedUrl.value, engine) {
        val shared = sharedUrl.value
        if (shared != null && (shared.startsWith("content:") || shared.startsWith("file:"))) {
            sharedUrl.value = null
            openTorrent(android.net.Uri.parse(shared))
        } else if (shared != null && engine == Engine.Status.Ready) {
            url = shared
            sharedUrl.value = null
            fetch()
        }
    }

    when (val found = meta) {
        is Fetched.Video -> {
            InfoContent(
                meta = found.meta,
                onBack = { meta = null },
                onDownload = { spec ->
                    Downloads.enqueue(found.meta, spec)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    meta = null
                    url = ""
                    onAdded()
                },
            )
            return
        }
        is Fetched.Playlist -> {
            PlaylistContent(
                playlist = found.meta,
                // A YouTube "watch?v=…&list=…" link points at one video inside a playlist.
                canPickSingle = Regex("""[?&]v=""").containsMatchIn(found.meta.url),
                onOnlyThis = { fetch(single = true) },
                onBack = { meta = null },
                onDownload = { entries, spec ->
                    Downloads.enqueueAll(found.meta, entries, spec)
                    Toast.makeText(context, "Added ${entries.size} to queue", Toast.LENGTH_SHORT).show()
                    meta = null
                    url = ""
                    onAdded()
                },
            )
            return
        }
        is Fetched.Torrent -> {
            TorrentContent(
                torrent = found.meta,
                onBack = { meta = null },
                onDownload = { files ->
                    Downloads.enqueueTorrent(found.meta, files)
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                    meta = null
                    url = ""
                    onAdded()
                },
            )
            return
        }
        null -> {}
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Eyebrow("Download", color = Amber)
        ScreenTitle("Paste a link")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://… or magnet:…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { fetch() }),
            trailingIcon = {
                TextButton(onClick = { clipboard.getText()?.text?.let { url = extractUrl(it) ?: it } }) { Text("Paste") }
            },
        )
        Button(
            onClick = { fetch() },
            enabled = !loading && url.isNotBlank() && (engine == Engine.Status.Ready || Torrents.isMagnet(url)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (Torrents.isMagnet(url)) "Asking peers for the file list…" else "Fetching video info…")
            } else {
                Text(if (Torrents.isMagnet(url)) "Read magnet link" else "Fetch video info", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        when (engine) {
            Engine.Status.Starting -> Text("Preparing the download engine. The first launch takes a few seconds.", color = Mute, fontSize = 13.sp)
            Engine.Status.Failed -> Text("The download engine didn't start: ${Engine.initError}", color = Danger, fontSize = 13.sp)
            Engine.Status.Ready -> {}
        }
        error?.let { Text(it, color = Danger, fontSize = 14.sp, lineHeight = 20.sp) }
        OutlinedButton(
            onClick = { pickTorrent.launch(arrayOf("application/x-bittorrent", "application/octet-stream", "*/*")) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open a .torrent file") }

        Recent()
    }
}

@Composable
private fun Recent() {
    val items by Downloads.items.collectAsStateWithLifecycle()
    val recent = items.sortedByDescending { it.createdAt }.take(4)
    if (recent.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    Eyebrow("Recent")
    recent.forEach { item ->
        val status = when (item.state) {
            State.Done -> "done"
            State.Failed -> "failed"
            State.Canceled -> "canceled"
            State.Waiting -> "waiting"
            State.Running -> "${(item.progress * 100).toInt()} %"
            State.Processing -> "processing"
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(item.title, dots(item.spec.label, formatDuration(item.durationSec)), Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(status, color = if (item.state == State.Failed) Danger else Mute, fontSize = 13.sp)
        }
    }
}

/** What yt-dlp found: presets first, then each resolution (rule R10). */
@Composable
private fun InfoContent(meta: VideoMeta, onBack: () -> Unit, onDownload: (Spec) -> Unit) {
    var picked by remember(meta) { mutableStateOf(Spec.MP4_1080) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("‹ Back") }
                    Thumb(meta.thumbnail, Modifier.fillMaxWidth())
                    Text(meta.title, color = Ink, fontSize = 20.sp, lineHeight = 26.sp)
                    Text(dots(formatDuration(meta.durationSec), meta.site), color = Mute, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Eyebrow("Presets")
                }
            }
            items(Spec.PRESETS) { spec ->
                OptionRow(spec.label, if (spec.isAudio) "audio" else "", picked == spec) { picked = spec }
            }
            if (meta.heights.isNotEmpty()) {
                item { Eyebrow("Resolution", modifier = Modifier.padding(top = 10.dp)) }
                items(meta.heights) { spec ->
                    OptionRow(spec.label, formatSize(spec.sizeBytes).let { if (it.isEmpty()) "" else "~$it" }, picked == spec) { picked = spec }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Cancel") }
            Button(onClick = { onDownload(picked) }, modifier = Modifier.weight(1f)) {
                val size = formatSize(picked.sizeBytes)
                Text(if (size.isEmpty()) "Download" else "Download · ~$size")
            }
        }
    }
}

/** Every video in a playlist or channel, all ticked. One format applies to all of them. */
@Composable
private fun PlaylistContent(
    playlist: PlaylistMeta,
    canPickSingle: Boolean,
    onOnlyThis: () -> Unit,
    onBack: () -> Unit,
    onDownload: (List<PlaylistEntry>, Spec) -> Unit,
) {
    var picked by remember(playlist) { mutableStateOf(Spec.MP4_1080) }
    var chosen by remember(playlist) { mutableStateOf(playlist.entries.indices.toSet()) }
    val all = chosen.size == playlist.entries.size

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("‹ Back") }
                    Eyebrow(dots("Playlist", playlist.site), color = Amber)
                    Text(playlist.title, color = Ink, fontSize = 20.sp, lineHeight = 26.sp)
                    Text("${playlist.entries.size} videos", color = Mute, fontSize = 13.sp)
                    if (canPickSingle) {
                        OutlinedButton(onClick = onOnlyThis) { Text("Only the video in this link") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Eyebrow("Format for all videos")
                }
            }
            items(Spec.PRESETS) { spec ->
                OptionRow(spec.label, if (spec.isAudio) "audio" else "", picked == spec) { picked = spec }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Videos · ${chosen.size} of ${playlist.entries.size} selected", modifier = Modifier.weight(1f))
                    TextButton(onClick = { chosen = if (all) emptySet() else playlist.entries.indices.toSet() }) {
                        Text(if (all) "Select none" else "Select all")
                    }
                }
            }
            itemsIndexed(playlist.entries) { i, entry ->
                Row(
                    Modifier.fillMaxWidth().clickable { chosen = if (i in chosen) chosen - i else chosen + i }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckMark(i in chosen)
                    Spacer(Modifier.width(12.dp))
                    Thumb(entry.thumbnail, Modifier.width(80.dp))
                    Spacer(Modifier.width(12.dp))
                    RowText(entry.title, dots("${i + 1}", formatDuration(entry.durationSec)), Modifier.weight(1f))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Cancel") }
            Button(
                onClick = { onDownload(playlist.entries.filterIndexed { i, _ -> i in chosen }, picked) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Download ${chosen.size}") }
        }
    }
}

/** The files inside a magnet link. All ticked; untick the ones you don't want. */
@Composable
private fun TorrentContent(torrent: TorrentMeta, onBack: () -> Unit, onDownload: (List<TorrentFile>) -> Unit) {
    var chosen by remember(torrent) { mutableStateOf(torrent.files.map { it.index }.toSet()) }
    val all = chosen.size == torrent.files.size
    val total = torrent.files.filter { it.index in chosen }.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("‹ Back") }
                    Eyebrow("Magnet link · torrent", color = Amber)
                    Text(torrent.name, color = Ink, fontSize = 20.sp, lineHeight = 26.sp)
                    Text(
                        dots("${torrent.files.size} files", formatSize(torrent.files.sumOf { it.sizeBytes })),
                        color = Mute, fontSize = 13.sp,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Files · ${chosen.size} selected", modifier = Modifier.weight(1f))
                        TextButton(onClick = { chosen = if (all) emptySet() else torrent.files.map { it.index }.toSet() }) {
                            Text(if (all) "Select none" else "Select all")
                        }
                    }
                }
            }
            items(torrent.files) { f ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { chosen = if (f.index in chosen) chosen - f.index else chosen + f.index }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckMark(f.index in chosen)
                    Spacer(Modifier.width(12.dp))
                    Text(f.path, Modifier.weight(1f), color = Ink, fontSize = 14.sp, lineHeight = 19.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(formatSize(f.sizeBytes), color = Mute, fontSize = 12.sp)
                }
            }
            item {
                Text(
                    "Saves to Download/MV Downloader. Sharing stops as soon as the download finishes.",
                    color = Mute, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("Cancel") }
            Button(
                onClick = { onDownload(torrent.files.filter { it.index in chosen }) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text(dots("Download ${chosen.size}", formatSize(total))) }
        }
    }
}
