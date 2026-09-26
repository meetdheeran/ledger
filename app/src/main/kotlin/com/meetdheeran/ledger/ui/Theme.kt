package com.meetdheeran.ledger.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Dark surfaces with mint for spending, blue for trends and violet for cards. */
object Ink {
    val bg = Color(0xFF090D13)
    val surface = Color(0xFF131A24)
    val surfaceHigh = Color(0xFF1C2634)
    val line = Color(0xFF2B3848)
    val text = Color(0xFFF1F3F5)
    val muted = Color(0xFFA4B0C0)
    val faint = Color(0xFF8C9BAE)
    val accent = Color(0xFF5FD9A6)
    val debit = Color(0xFFFF8A7A)
    val credit = Color(0xFF5FD9A6)
    val warn = Color(0xFFE8C06A)
    val blue = Color(0xFF82B6FF)
    val purple = Color(0xFFC0A1FF)
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
