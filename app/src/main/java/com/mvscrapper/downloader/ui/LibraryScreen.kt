package com.mvscrapper.downloader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvscrapper.downloader.Downloads
import com.mvscrapper.downloader.Item
import com.mvscrapper.downloader.State
import com.mvscrapper.downloader.Storage
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { All("All"), Video("Video"), Audio("Audio") }

/**
 * Finished downloads as a list, newest first. Items stay until you delete them (rule R11).
 * Long-press starts selection, so several can be deleted at once.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by Downloads.items.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<Set<String>?>(null) }
    val selecting = selected.isNotEmpty()

    val done = items.filter { it.state == State.Done }.sortedByDescending { it.createdAt }
    val shown = done.filter {
        (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) &&
            when (filter) {
                Filter.All -> true
                Filter.Video -> !it.spec.isAudio
                Filter.Audio -> it.spec.isAudio
            }
    }

    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        if (selecting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("${selected.size} selected", Modifier.weight(1f))
                TextButton(onClick = {
                    selected = if (selected.size == shown.size) emptySet() else shown.map { it.id }.toSet()
                }) { Text(if (selected.size == shown.size) "Select none" else "Select all") }
            }
        } else {
            ScreenTitle("Library")
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search downloads") },
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }

        if (shown.isEmpty()) {
            Text(
                if (done.isEmpty()) "Finished downloads appear here." else "Nothing matches.",
                color = Mute, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.id }) { item ->
                val isSelected = item.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selecting) selected = toggle(selected, item.id) else open(context, item)
                            },
                            onLongClick = { selected = toggle(selected, item.id) },
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        CheckBox(isSelected)
                        Spacer(Modifier.width(12.dp))
                    }
                    Thumb(item.thumbnail, Modifier.width(88.dp), label = if (item.spec.isAudio) "audio" else "")
                    Spacer(Modifier.width(12.dp))
                    RowText(
                        item.title,
                        dots(formatDuration(item.durationSec), item.spec.label, formatSize(item.sizeBytes)),
                        Modifier.weight(1f),
                    )
                    if (!selecting) RowMenu(
                        onShare = { share(context, item) },
                        onDelete = { confirm = setOf(item.id) },
                    )
                }
                HorizontalDivider(color = Line)
            }
        }

        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { selected = emptySet() }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { confirm = selected }, modifier = Modifier.weight(1f)) { Text("Delete ${selected.size}") }
            }
        }
    }

    confirm?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (ids.size == 1) "Delete this item?" else "Delete ${ids.size} items?") },
            text = { Text("Remove them from the list only, or also delete the files from your phone?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    selected = emptySet()
                    scope.launch {
                        val refused = Downloads.delete(ids, deleteFiles = true)
                        if (refused > 0) {
                            Toast.makeText(context, "$refused file(s) couldn't be deleted. Remove them in your Files app.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Delete files too", color = Danger) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirm = null }) { Text("Cancel") }
                    TextButton(onClick = {
                        confirm = null
                        selected = emptySet()
                        scope.launch { Downloads.delete(ids, deleteFiles = false) }
                    }) { Text("List only") }
                }
            },
        )
    }
}

@Composable
private fun CheckBox(on: Boolean) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier.size(22.dp).border(2.dp, if (on) Amber else Mute, shape).background(if (on) Amber else Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Text("✓", color = Color(0xFF1A1204), fontSize = 14.sp)
    }
}

@Composable
private fun RowMenu(onShare: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("⋮", color = Mute, fontSize = 20.sp) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Share") }, onClick = { open = false; onShare() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; onDelete() })
        }
    }
}

private fun toggle(set: Set<String>, id: String) = if (id in set) set - id else set + id

private fun mimeOf(item: Item) = Storage.mimeOf(item.fileName ?: "", item.spec.isAudio)

/** Opens the file in whatever player the phone uses by default. */
private fun open(context: Context, item: Item) {
    val uri = item.fileUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeOf(item)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open this file.", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "This file can't be opened. It may have been moved or deleted.", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, item: Item) {
    val uri = item.fileUri?.let(Uri::parse) ?: return
    val send = Intent(Intent.ACTION_SEND).setType(mimeOf(item)).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, item.title))
    } catch (_: Exception) {
        Toast.makeText(context, "Couldn't share this file.", Toast.LENGTH_SHORT).show()
    }
}
