package com.mvscrapper.downloader.ui

import android.widget.Toast
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
import com.mvscrapper.downloader.Spec
import com.mvscrapper.downloader.State
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
    var meta by remember { mutableStateOf<VideoMeta?>(null) }

    fun fetch() {
        val link = extractUrl(url.trim()) ?: url.trim()
        if (link.isEmpty() || engine != Engine.Status.Ready) return
        loading = true
        error = null
        meta = null
        scope.launch {
            try {
                meta = withContext(Dispatchers.IO) { Engine.fetch(link) }
            } catch (t: Throwable) {
                error = Engine.cleanError(t)
            } finally {
                loading = false
            }
        }
    }

    // A link shared from another app is fetched as soon as the engine is ready.
    LaunchedEffect(sharedUrl.value, engine) {
        val shared = sharedUrl.value
        if (shared != null && engine == Engine.Status.Ready) {
            url = shared
            sharedUrl.value = null
            fetch()
        }
    }

    val found = meta
    if (found != null) {
        InfoContent(
            meta = found,
            onBack = { meta = null },
            onDownload = { spec ->
                Downloads.enqueue(found, spec)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                meta = null
                url = ""
                onAdded()
            },
        )
        return
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
            placeholder = { Text("https://…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { fetch() }),
            trailingIcon = {
                TextButton(onClick = { clipboard.getText()?.text?.let { url = extractUrl(it) ?: it } }) { Text("Paste") }
            },
        )
        Button(
            onClick = { fetch() },
            enabled = !loading && url.isNotBlank() && engine == Engine.Status.Ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Fetching video info…")
            } else {
                Text("Fetch video info", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        when (engine) {
            Engine.Status.Starting -> Text("Preparing the download engine. The first launch takes a few seconds.", color = Mute, fontSize = 13.sp)
            Engine.Status.Failed -> Text("The download engine didn't start: ${Engine.initError}", color = Danger, fontSize = 13.sp)
            Engine.Status.Ready -> {}
        }
        error?.let { Text(it, color = Danger, fontSize = 14.sp, lineHeight = 20.sp) }

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
