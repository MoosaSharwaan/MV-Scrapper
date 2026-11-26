package com.mvscrapper.downloader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mvscrapper.downloader.Downloads

/** A video or audio address caught while a page played it. */
private data class Found(val url: String, val kind: String, val audio: Boolean)

private val FILE_EXT = Regex("""\.(mp4|webm|m4v|mov|mkv|mp3|m4a|aac|flac|ogg|opus|wav)(\?|$)""", RegexOption.IGNORE_CASE)
private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

/**
 * What kind of media an address is, or null when it isn't one. Stream playlists (.m3u8, .mpd)
 * are what most players use; the small pieces they load (.ts, .m4s) are skipped.
 */
private fun mediaKind(url: String): Found? {
    val u = url.substringBefore('#')
    val path = u.substringBefore('?').lowercase()
    if (path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".aac.part")) return null
    return when {
        path.endsWith(".m3u8") -> Found(u, "HLS stream", false)
        path.endsWith(".mpd") -> Found(u, "DASH stream", false)
        else -> FILE_EXT.find(path)?.groupValues?.get(1)?.lowercase()?.let { ext ->
            Found(u, ext.uppercase() + " file", ext in AUDIO_EXT)
        }
    }
}

/**
 * Opens a page in a built-in browser. When the page plays a video, the app sees the real video
 * or stream address in the page's network requests and offers to download it, with the page's
 * cookies and address so the site allows it. DRM-protected video can't be caught this way.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(sharedUrl: MutableState<String?>) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }
    var address by rememberSaveable { mutableStateOf("") }
    var pageUrl by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var showFound by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<Found>() }

    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    // Runs off the main thread: only look, never block or change the request.
                    val hit = request?.url?.toString()?.let { mediaKind(it) }
                    if (hit != null) main.post { if (found.none { it.url == hit.url } && found.size < 40) found.add(0, hit) }
                    return null
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    found.clear()
                    pageUrl = url.orEmpty()
                    address = url.orEmpty()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageTitle = view?.title.orEmpty()
                    canGoBack = view?.canGoBack() == true
                }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { web.stopLoading(); web.destroy() } }
    BackHandler(enabled = canGoBack) { web.goBack() }

    fun go() {
        val text = address.trim()
        if (text.isEmpty()) return
        val url = when {
            text.startsWith("http://") || text.startsWith("https://") -> text
            text.contains('.') && !text.contains(' ') -> "https://$text"
            else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(text, "UTF-8")
        }
        web.loadUrl(url)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Web address or search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { go() }),
            )
            TextButton(onClick = { go() }) { Text("Go") }
        }

        AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())

        // Bottom bar: what was caught, plus the option to let yt-dlp read the page itself.
        Column(Modifier.fillMaxWidth().background(Raised).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        pageUrl.isEmpty() -> "Open a page and play the video"
                        found.isEmpty() -> "Play the video to find it"
                        else -> "${found.size} video${if (found.size == 1) "" else "s"} found"
                    },
                    Modifier.weight(1f), color = if (found.isEmpty()) Mute else Amber, fontSize = 14.sp,
                )
                if (pageUrl.isNotEmpty()) TextButton(onClick = { sharedUrl.value = pageUrl }) { Text("Read page") }
                if (found.isNotEmpty()) Button(onClick = { showFound = !showFound }) { Text(if (showFound) "Hide" else "Download") }
            }
            if (showFound && found.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(found, key = { it.url }) { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.kind, color = Ink, fontSize = 14.sp)
                                Text(f.url, color = Mute, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = {
                                val cookies = CookieManager.getInstance().getCookie(f.url)
                                    ?: CookieManager.getInstance().getCookie(pageUrl).orEmpty()
                                Downloads.enqueueFound(
                                    mediaUrl = f.url,
                                    title = pageTitle.ifBlank { pageUrl },
                                    audio = f.audio,
                                    pageUrl = pageUrl,
                                    cookies = cookies,
                                    userAgent = web.settings.userAgentString,
                                )
                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                            }) { Text("Get") }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
    }
}
