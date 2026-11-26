package com.mvscrapper.downloader.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvscrapper.downloader.Downloads
import com.mvscrapper.downloader.Engine
import com.mvscrapper.downloader.Prefs
import com.mvscrapper.downloader.Storage
import com.mvscrapper.downloader.Subscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parallel by Prefs.parallel.collectAsStateWithLifecycle()
    val wifiOnly by Prefs.wifiOnly.collectAsStateWithLifecycle()
    val autoUpdate by Prefs.autoUpdate.collectAsStateWithLifecycle()
    val playlistLimit by Prefs.playlistLimit.collectAsStateWithLifecycle()
    val speedLimit by Prefs.speedLimit.collectAsStateWithLifecycle()
    val nameTemplate by Prefs.nameTemplate.collectAsStateWithLifecycle()
    val subs by Subscriptions.list.collectAsStateWithLifecycle()
    val checking by Subscriptions.checking.collectAsStateWithLifecycle()
    val hasCookies by Engine.hasCookies.collectAsStateWithLifecycle()
    val pickCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val problem = withContext(Dispatchers.IO) { Engine.importCookies(context, uri) }
                Toast.makeText(context, problem ?: "Cookies imported", Toast.LENGTH_LONG).show()
            }
        }
    }
    val version by Engine.version.collectAsStateWithLifecycle()
    val engine by Engine.status.collectAsStateWithLifecycle()
    val items by Downloads.items.collectAsStateWithLifecycle()
    val busy = items.any { it.isActive }
    var updating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        ScreenTitle("Settings", Modifier.padding(bottom = 8.dp))

        Setting("Parallel downloads", "Extra items wait in the queue. Phones run cooler with 1 or 2.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { Prefs.setParallel(parallel - 1) }, enabled = parallel > Prefs.MIN_PARALLEL) { Text("−") }
                Text("$parallel", Modifier.width(36.dp), color = Ink, fontSize = 18.sp, fontFamily = FontFamily.Monospace,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton(onClick = { Prefs.setParallel(parallel + 1) }, enabled = parallel < Prefs.MAX_PARALLEL) { Text("+") }
            }
        }
        Setting("Wi-Fi only", "Downloads wait until you're on Wi-Fi.") {
            Switch(checked = wifiOnly, onCheckedChange = { Prefs.setWifiOnly(it) })
        }
        FlagSetting(Prefs.nightly, "Use nightly yt-dlp builds", "Newest site fixes, a day or two before stable. Can be less reliable.")
        Setting("Update yt-dlp on launch", "Only on Wi-Fi. Sites change often; updating is usually the fix.") {
            Switch(checked = autoUpdate, onCheckedChange = { Prefs.setAutoUpdate(it) })
        }
        Setting(
            "yt-dlp version",
            dots(version ?: "unknown", updateMessage ?: if (busy) "Finish or cancel downloads to update" else ""),
        ) {
            OutlinedButton(
                onClick = {
                    updating = true
                    updateMessage = "Checking…"
                    scope.launch {
                        updateMessage = withContext(Dispatchers.IO) { Engine.update(context) }
                        updating = false
                    }
                },
                enabled = !updating && !busy && engine == Engine.Status.Ready,
            ) { Text(if (updating) "Updating…" else "Update") }
        }
        Setting(
            "Save location",
            "Video: ${Storage.folderLabel(false)}\nAudio: ${Storage.folderLabel(true)}\nTorrents: ${Storage.downloadsLabel()}",
        ) {}

        Eyebrow("Download options", modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        FlagSetting(Prefs.embedMetadata, "Embed title and details", "Title, uploader and date are saved inside the file.")
        FlagSetting(Prefs.embedThumbnail, "Embed thumbnail", "Shows as cover art in players and music apps.")
        FlagSetting(Prefs.embedChapters, "Embed chapters", "Video chapters, where the site has them.")
        FlagSetting(Prefs.embedSubtitles, "Embed English subtitles", "When the site offers them. Makes downloads a little slower.")
        FlagSetting(
            Prefs.skipDownloaded, "Skip videos already downloaded",
            "Remembers what you've downloaded and skips it next time, even after you delete the file.",
        )
        FlagSetting(
            Prefs.sponsorBlock, "Cut sponsor segments",
            "YouTube only. Removes sponsor, self-promotion and subscribe reminders, using SponsorBlock.",
        )
        Setting("Speed limit", "Per download. Torrents aren't limited.") {
            OutlinedButton(onClick = {
                val all = Prefs.SPEED_LIMITS
                Prefs.setSpeedLimit(all[(all.indexOf(speedLimit) + 1) % all.size])
            }) { Text(if (speedLimit == 0) "None" else "$speedLimit MB/s") }
        }
        Setting(
            "Cookies",
            if (hasCookies) "Imported. Used for sign-in and age-gated videos."
            else "For videos that need a sign-in. Export cookies.txt from your browser, then import it here.",
        ) {
            if (hasCookies) {
                OutlinedButton(onClick = { Engine.removeCookies() }) { Text("Remove") }
            } else {
                OutlinedButton(onClick = { pickCookies.launch(arrayOf("text/plain", "*/*")) }) { Text("Import") }
            }
        }
        Setting("File names", "How downloaded files are named.") {
            OutlinedButton(onClick = {
                Prefs.setNameTemplate((nameTemplate + 1) % Prefs.NAME_TEMPLATES.size)
            }) { Text(Prefs.NAME_TEMPLATES[nameTemplate].first) }
        }
        Setting("Playlist limit", "How many videos to list from a playlist or channel. For channels, the newest.") {
            OutlinedButton(onClick = {
                val all = Prefs.PLAYLIST_LIMITS
                Prefs.setPlaylistLimit(all[(all.indexOf(playlistLimit) + 1) % all.size])
            }) { Text(if (playlistLimit == 0) "All" else "First $playlistLimit") }
        }

        Eyebrow("Subscriptions", modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        Text(
            "Checked only when the app opens. Subscribe from a playlist or channel on Home.",
            color = Mute, fontSize = 13.sp, lineHeight = 18.sp,
        )
        if (subs.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val added = withContext(Dispatchers.IO) { Subscriptions.checkAll() }
                        Toast.makeText(context, if (added == 0) "No new videos" else "Added $added to the queue", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !checking && engine == Engine.Status.Ready,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(if (checking) "Checking…" else "Check now") }
        }
        subs.forEach { sub ->
            Setting(sub.title, dots(sub.spec.label, if (sub.lastAdded > 0) "${sub.lastAdded} new last check" else "")) {
                OutlinedButton(onClick = { Subscriptions.remove(sub.id) }) { Text("Remove") }
            }
        }

        Text(
            "Download only what you have the right to save. DRM-protected video can't be downloaded. " +
                "Open source under GPL-3.0, built on yt-dlp and youtubedl-android.",
            color = Mute, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun Setting(title: String, detail: String, control: @Composable () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 15.sp)
                if (detail.isNotEmpty()) Text(detail, color = Mute, fontSize = 13.sp, lineHeight = 18.sp)
            }
            control()
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
private fun FlagSetting(flag: Prefs.Flag, title: String, detail: String) {
    val on by flag.state.collectAsStateWithLifecycle()
    Setting(title, detail) {
        Switch(checked = on, onCheckedChange = { flag.set(it) })
    }
}
