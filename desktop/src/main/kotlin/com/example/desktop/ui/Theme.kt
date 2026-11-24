package com.example.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CinemaErrorRed
import com.example.ui.theme.CinemaInk
import com.example.ui.theme.CinemaInkMuted
import com.example.ui.theme.CinemaIvory
import com.example.ui.theme.CinemaMist
import com.example.ui.theme.CinemaObsidian
import com.example.ui.theme.CinemaOutline
import com.example.ui.theme.CinemaPaper
import com.example.ui.theme.CinemaPaperHigh
import com.example.ui.theme.CinemaSlate
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceHigh
import com.example.ui.theme.CobaltAccentLight
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueContainer
import com.example.ui.theme.CobaltBlueOnContainer
import com.example.ui.theme.CobaltContainerLight
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SkyBlueAccentLight
import com.example.ui.theme.SkyBlueContainer
import com.example.ui.theme.SkyBlueContainerLight
import com.example.ui.theme.SkyBlueOnContainer

// Same "Cinema Obsidian" / "Sky Blue" palette as the Android app (shared Color.kt).
private val Dark =
  darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF002438),
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlueOnContainer,
    secondary = CobaltBlue,
    onSecondary = Color(0xFF061E3E),
    secondaryContainer = CobaltBlueContainer,
    onSecondaryContainer = CobaltBlueOnContainer,
    background = CinemaObsidian,
    onBackground = CinemaMist,
    surface = CinemaSurface,
    onSurface = CinemaMist,
    surfaceVariant = CinemaSurfaceHigh,
    onSurfaceVariant = CinemaSlate,
    outline = CinemaOutline,
    error = CinemaErrorRed,
  )

private val Light =
  lightColorScheme(
    primary = SkyBlueAccentLight,
    onPrimary = Color.White,
    primaryContainer = SkyBlueContainerLight,
    secondary = CobaltAccentLight,
    secondaryContainer = CobaltContainerLight,
    background = CinemaIvory,
    onBackground = CinemaInk,
    surface = CinemaPaper,
    onSurface = CinemaInk,
    surfaceVariant = CinemaPaperHigh,
    onSurfaceVariant = CinemaInkMuted,
    error = CinemaErrorRed,
  )

@Composable
fun DecanTheme(dark: Boolean, content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
