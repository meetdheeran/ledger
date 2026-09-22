package com.meetdheeran.ledger.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One small palette, used everywhere. True black rather than dark grey: the
 * OnePlus 7 is OLED, so black costs no light and makes the numbers the only
 * bright thing on screen, which is the whole point of the app.
 */
object Ink {
    val bg = Color(0xFF000000)
    val surface = Color(0xFF111316)
    val surfaceHigh = Color(0xFF181B1F)
    val line = Color(0xFF23272C)
    val text = Color(0xFFF1F3F5)
    val muted = Color(0xFF8B939C)
    val faint = Color(0xFF5A626A)
    val accent = Color(0xFF5FD9A6)
    val debit = Color(0xFFFF8A7A)
    val credit = Color(0xFF5FD9A6)
    val warn = Color(0xFFE8C06A)
}

private val scheme = darkColorScheme(
    primary = Ink.accent,
    onPrimary = Color(0xFF05130D),
    background = Ink.bg,
    onBackground = Ink.text,
    surface = Ink.surface,
    onSurface = Ink.text,
    surfaceVariant = Ink.surfaceHigh,
    onSurfaceVariant = Ink.muted,
    outline = Ink.line,
    error = Ink.debit
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
