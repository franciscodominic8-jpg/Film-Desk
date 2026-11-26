package com.filmdesk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object FD {
    val Bg = Color(0xFF0B0B0C)
    val Panel = Color(0xFF111214)
    val Surface = Color(0xFF17181A)
    val Surface2 = Color(0xFF222326)
    val Line = Color(0xFF2E3034)
    val Text = Color(0xFFF1F0EC)
    val Muted = Color(0xFFA3A39E)
    val Amber = Color(0xFFF2A33A)
    val AmberLight = Color(0xFFFFC06A)
    val OnAmber = Color(0xFF1A1206)
    val AmberDim = Color(0xFF2A2113)
    val Mono = FontFamily.Monospace
}

@Composable
fun FilmDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FD.Amber,
            onPrimary = FD.OnAmber,
            secondary = FD.Amber,
            onSecondary = FD.OnAmber,
            background = FD.Bg,
            onBackground = FD.Text,
            surface = FD.Surface,
            onSurface = FD.Text,
            surfaceVariant = FD.Surface2,
            onSurfaceVariant = FD.Muted,
            surfaceContainerHigh = FD.Surface2,
            outline = FD.Line,
            secondaryContainer = FD.AmberDim,
            onSecondaryContainer = FD.Amber,
        ),
        content = content,
    )
}
