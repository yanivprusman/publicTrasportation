package com.automatelinux.pt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PtBlue = Color(0xFF1565C0)
val PtBlueDark = Color(0xFF0D47A1)
val PtBlueLight = Color(0xFF90CAF9)
val WalkGrey = Color(0xFF888888)
val BusGreen = Color(0xFF4CAF50)
val RailBlue = Color(0xFF2196F3)
val TramOrange = Color(0xFFFF5722)
val SubwayPurple = Color(0xFF9C27B0)

val DarkColorScheme = darkColorScheme(
    primary = PtBlue,
    onPrimary = Color.White,
    primaryContainer = PtBlueDark,
    secondary = PtBlueLight,
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCCCCCC)
)

@Composable
fun PTTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
