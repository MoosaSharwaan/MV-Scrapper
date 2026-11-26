package com.mvscrapper.downloader.ui

import java.util.Locale

fun formatDuration(sec: Int): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1_000_000.0
    return when {
        mb >= 1000 -> String.format(Locale.US, "%.1f GB", mb / 1000)
        mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.1f MB", mb)
    }
}

fun formatEta(sec: Long): String = when {
    sec <= 0 -> ""
    sec < 60 -> "${sec}s left"
    sec < 3600 -> "${sec / 60} min left"
    else -> "${sec / 3600} h ${(sec % 3600) / 60} min left"
}

/** Pulls the first web link out of shared text, e.g. "Watch this https://…". */
fun extractUrl(text: String?): String? =
    text?.let { Regex("""https?://\S+""").find(it)?.value?.trimEnd(')', ']', '>', '"', '\'', ',', '.') }

/** Joins non-empty parts with a middle dot. */
fun dots(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" · ")
