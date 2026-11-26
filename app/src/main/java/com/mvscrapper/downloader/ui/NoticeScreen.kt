package com.mvscrapper.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvscrapper.downloader.Prefs
import com.mvscrapper.downloader.Storage

/** First launch (rule R8): the legal line plus the two defaults that matter most. */
@Composable
fun NoticeScreen() {
    val wifiOnly by Prefs.wifiOnly.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Eyebrow("Welcome", color = Amber)
        ScreenTitle("Before you start")
        Text(
            "This app downloads videos you have the right to save. Follow each site's terms and your local " +
                "copyright law. It can't download DRM-protected video, such as Netflix or Disney+.",
            color = Mute, fontSize = 15.sp, lineHeight = 22.sp,
        )
        Text(
            "Videos are saved to ${Storage.folderLabel(false)}, audio to ${Storage.folderLabel(true)}.",
            color = Mute, fontSize = 15.sp, lineHeight = 22.sp,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Download only on Wi-Fi", color = Ink, fontSize = 15.sp)
                Text("You can change this later in Settings.", color = Mute, fontSize = 13.sp)
            }
            Switch(checked = wifiOnly, onCheckedChange = { Prefs.setWifiOnly(it) })
        }
        Spacer(Modifier.padding(top = 8.dp))
        Button(onClick = { Prefs.acceptNotice() }, modifier = Modifier.fillMaxWidth()) {
            Text("I understand, continue", modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}
