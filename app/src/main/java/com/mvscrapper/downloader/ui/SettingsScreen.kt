package com.mvscrapper.downloader.ui

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
        Setting("Save location", "Video: ${Storage.folderLabel(false)}\nAudio: ${Storage.folderLabel(true)}") {}

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
