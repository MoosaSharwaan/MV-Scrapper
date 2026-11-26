package com.mvscrapper.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvscrapper.downloader.ui.AppTheme
import com.mvscrapper.downloader.ui.BrowserScreen
import com.mvscrapper.downloader.ui.HomeScreen
import com.mvscrapper.downloader.ui.LibraryScreen
import com.mvscrapper.downloader.ui.NoticeScreen
import com.mvscrapper.downloader.ui.QueueScreen
import com.mvscrapper.downloader.ui.SettingsScreen
import com.mvscrapper.downloader.ui.extractUrl

class MainActivity : ComponentActivity() {
    /** A link shared into the app; Home picks it up and fetches it. */
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { AppTheme { Root(sharedUrl) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Downloads.pump() // retries anything that was waiting for Wi-Fi or a slot
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { sharedUrl.value = it }
            // Tapping a magnet: link in a browser or another app.
            // Tapping a magnet: link, or opening a .torrent file from Files or a browser download.
            Intent.ACTION_VIEW -> intent.dataString?.let { sharedUrl.value = it }
        }
    }
}

private val TABS = listOf("⌂" to "Home", "◎" to "Browser", "⇩" to "Queue", "▤" to "Library", "⚙" to "Settings")

@Composable
private fun Root(sharedUrl: MutableState<String?>) {
    val accepted by Prefs.noticeAccepted.collectAsStateWithLifecycle()
    if (!accepted) {
        NoticeScreen()
        return
    }
    AskPermissionsOnce()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(sharedUrl.value) { if (sharedUrl.value != null) tab = 0 }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, (glyph, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(glyph, fontSize = 20.sp) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(sharedUrl, onAdded = { tab = 2 })
                1 -> BrowserScreen(sharedUrl)
                2 -> QueueScreen()
                3 -> LibraryScreen()
                else -> SettingsScreen()
            }
        }
    }
}

/** Notifications (Android 13+) for download progress; storage only on Android 8 and 9. */
@Composable
private fun AskPermissionsOnce() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
    }
}
