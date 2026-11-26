package com.mvscrapper.downloader

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A channel or playlist whose new videos are queued automatically. */
data class Subscription(
    val id: String,
    val url: String,
    val title: String,
    val spec: Spec,
    /** Video links already seen, so only new uploads are queued. */
    val seen: List<String>,
    val lastChecked: Long = 0,
    val lastAdded: Int = 0,
)

/**
 * Subscriptions are checked only when the app opens, or when you tap Check now (rule R12).
 * There is no background job, so they cost no battery while the app is closed.
 */
object Subscriptions {
    /** How many of the newest videos to look at on each check. */
    private const val SCAN = 30
    /** Seen links kept per subscription, so the file stays small. */
    private const val MAX_SEEN = 500

    private lateinit var app: Context
    private val _list = MutableStateFlow<List<Subscription>>(emptyList())
    val list: StateFlow<List<Subscription>> = _list
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking

    private fun file() = File(app.filesDir, "subscriptions.json")

    fun init(context: Context) {
        app = context.applicationContext
        _list.value = runCatching {
            val arr = JSONArray(file().readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Subscribes to a playlist or channel. Videos listed now count as seen: only later uploads are queued. */
    fun add(playlist: PlaylistMeta, spec: Spec) {
        if (_list.value.any { it.url == playlist.url }) return
        val sub = Subscription(
            id = UUID.randomUUID().toString(),
            url = playlist.url,
            title = playlist.title,
            spec = spec,
            seen = playlist.entries.map { it.url }.take(MAX_SEEN),
            lastChecked = System.currentTimeMillis(),
        )
        save(_list.value + sub)
    }

    fun remove(id: String) = save(_list.value.filterNot { it.id == id })

    fun isSubscribed(url: String) = _list.value.any { it.url == url }

    /** Looks for new uploads and queues them. Blocking; call off the main thread. Returns how many were queued. */
    fun checkAll(): Int {
        if (_list.value.isEmpty() || _checking.value) return 0
        _checking.value = true
        var total = 0
        try {
            for (sub in _list.value) {
                val result = runCatching { Engine.fetch(sub.url, limit = SCAN) }.getOrNull()
                val playlist = (result as? Fetched.Playlist)?.meta ?: continue
                val fresh = playlist.entries.filter { it.url !in sub.seen }
                if (fresh.isNotEmpty()) {
                    // Oldest first, so the queue keeps upload order.
                    Downloads.enqueueAll(playlist.copy(title = sub.title), fresh.reversed(), sub.spec)
                    total += fresh.size
                }
                val updated = sub.copy(
                    seen = (fresh.map { it.url } + sub.seen).take(MAX_SEEN),
                    lastChecked = System.currentTimeMillis(),
                    lastAdded = fresh.size,
                )
                save(_list.value.map { if (it.id == sub.id) updated else it })
            }
        } finally {
            _checking.value = false
        }
        return total
    }

    @Synchronized
    private fun save(list: List<Subscription>) {
        _list.value = list
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        runCatching { file().writeText(arr.toString()) }
    }

    private fun toJson(s: Subscription) = JSONObject().apply {
        put("id", s.id); put("url", s.url); put("title", s.title)
        put("seen", JSONArray(s.seen)); put("lastChecked", s.lastChecked); put("lastAdded", s.lastAdded)
        put("spec", JSONObject().apply {
            put("label", s.spec.label); put("format", s.spec.format); put("sort", s.spec.sort)
            put("audio", s.spec.audio); put("container", s.spec.container)
        })
    }

    private fun fromJson(o: JSONObject): Subscription {
        val sp = o.getJSONObject("spec")
        val seen = o.optJSONArray("seen")
        return Subscription(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title"),
            spec = Spec(
                label = sp.optString("label"), format = sp.optString("format"), sort = sp.optString("sort"),
                audio = sp.optString("audio"), container = sp.optString("container", "mp4"),
            ),
            seen = seen?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
            lastChecked = o.optLong("lastChecked"),
            lastAdded = o.optInt("lastAdded"),
        )
    }
}
