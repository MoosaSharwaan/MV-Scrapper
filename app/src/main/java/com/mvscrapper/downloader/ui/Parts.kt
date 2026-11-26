package com.mvscrapper.downloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
}

/** Small uppercase label, like the blueprint's mono eyebrows. */
@Composable
fun Eyebrow(text: String, color: Color = Mute, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier, color = color,
        fontSize = 11.sp, letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
    )
}

/** One choosable row; the selected one gets the amber outline. */
@Composable
fun OptionRow(title: String, trailing: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Raised else Color.Transparent)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Amber else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), color = Ink, fontSize = 15.sp)
        Text(trailing, color = Mute, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Video thumbnail with a plain fallback block when there is none. */
@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier, label: String = "") {
    Box(
        modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Raised),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) Text(label, color = Mute, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** Two-line text block used in list rows. */
@Composable
fun RowText(title: String, meta: String, modifier: Modifier = Modifier, metaColor: Color = Mute) {
    Column(modifier) {
        Text(title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(meta, color = metaColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
    }
}
