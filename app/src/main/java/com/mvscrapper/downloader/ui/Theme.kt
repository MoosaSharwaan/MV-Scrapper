package com.mvscrapper.downloader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** True black with an amber accent, as in the blueprint mockups. */
val Amber = Color(0xFFF2B33D)
val Ink = Color(0xFFF1F2F4)
val Mute = Color(0xFF8D929B)
val Line = Color(0xFF24272C)
val Raised = Color(0xFF16181C)
val Danger = Color(0xFFFF8A80)

private val scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1204),
    secondaryContainer = Color(0xFF3A2C12),
    onSecondaryContainer = Amber,
    background = Color.Black,
    onBackground = Ink,
    surface = Color(0xFF07080A),
    onSurface = Ink,
    surfaceVariant = Raised,
    onSurfaceVariant = Mute,
    surfaceContainer = Color(0xFF07080A),
    surfaceContainerHigh = Raised,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
