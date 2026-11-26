package com.mvscrapper.downloader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves the queue and history as one small JSON file, so they survive restarts. */
object Store {
    private fun file(context: Context) = File(context.filesDir, "items.json")

    fun load(context: Context): List<Item> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun save(context: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun toJson(it: Item) = JSONObject().apply {
        put("id", it.id); put("url", it.url); put("title", it.title)
        put("thumbnail", it.thumbnail ?: ""); put("duration", it.durationSec)
        put("state", it.state.name); put("message", it.message)
        put("fileUri", it.fileUri ?: ""); put("fileName", it.fileName ?: "")
        put("size", it.sizeBytes); put("created", it.createdAt); put("folder", it.folder)
        put("spec", JSONObject().apply {
            put("label", it.spec.label); put("format", it.spec.format); put("sort", it.spec.sort)
            put("audio", it.spec.audio); put("container", it.spec.container); put("size", it.spec.sizeBytes)
        })
    }

    private fun fromJson(o: JSONObject): Item {
        val s = o.getJSONObject("spec")
        return Item(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title"),
            thumbnail = o.optString("thumbnail").ifEmpty { null },
            durationSec = o.optInt("duration"),
            spec = Spec(
                label = s.optString("label"), format = s.optString("format"), sort = s.optString("sort"),
                audio = s.optString("audio"), container = s.optString("container", "mp4"), sizeBytes = s.optLong("size"),
            ),
            state = runCatching { State.valueOf(o.optString("state")) }.getOrDefault(State.Waiting),
            message = o.optString("message"),
            fileUri = o.optString("fileUri").ifEmpty { null },
            fileName = o.optString("fileName").ifEmpty { null },
            sizeBytes = o.optLong("size"),
            createdAt = o.optLong("created", System.currentTimeMillis()),
            folder = o.optString("folder"),
        )
    }
}
