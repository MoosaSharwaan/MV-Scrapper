package com.mvscrapper.downloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mvscrapper.downloader.Downloads
import com.mvscrapper.downloader.Item
import com.mvscrapper.downloader.Prefs
import com.mvscrapper.downloader.State
import kotlinx.coroutines.launch

/** Everything not finished yet. Finished items move to Library. */
@Composable
fun QueueScreen() {
    val items by Downloads.items.collectAsStateWithLifecycle()
    val parallel by Prefs.parallel.collectAsStateWithLifecycle()
    val queue = items.filter { it.state != State.Done }.sortedBy { it.createdAt }
    val active = queue.count { it.isActive }
    val waiting = queue.count { it.state == State.Waiting }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Eyebrow("$active active · $waiting waiting · limit $parallel", color = Amber)
        ScreenTitle("Queue", Modifier.padding(top = 6.dp, bottom = 10.dp))
        if (queue.isEmpty()) {
            Text("Nothing downloading. Paste a link on Home, or share one from your browser.", color = Mute, fontSize = 15.sp)
            return@Column
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(queue, key = { it.id }) { item ->
                QueueRow(item)
                HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun QueueRow(item: Item) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(item.title, item.spec.label, Modifier.weight(1f))
            Text(stateText(item), color = if (item.state == State.Failed) Danger else Mute, fontSize = 13.sp)
        }
        if (item.isActive) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Amber,
                trackColor = Line,
            )
        }
        val detail = dots(if (item.state == State.Running) formatEta(item.etaSec) else "", item.message)
        if (detail.isNotEmpty()) {
            Text(detail, color = if (item.state == State.Failed) Danger else Mute, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (item.state) {
                State.Waiting, State.Running, State.Processing ->
                    TextButton(onClick = { Downloads.cancel(item.id) }) { Text("Cancel") }
                State.Failed, State.Canceled -> {
                    TextButton(onClick = { Downloads.retry(item.id) }) { Text("Retry") }
                    TextButton(onClick = { scope.launch { Downloads.delete(setOf(item.id), deleteFiles = false) } }) {
                        Text("Remove", color = Mute)
                    }
                }
                State.Done -> {}
            }
        }
    }
}

private fun stateText(item: Item) = when (item.state) {
    State.Waiting -> "waiting"
    State.Running -> "${(item.progress * 100).toInt()} %"
    State.Processing -> "processing"
    State.Failed -> "failed"
    State.Canceled -> "canceled"
    State.Done -> "done"
}
