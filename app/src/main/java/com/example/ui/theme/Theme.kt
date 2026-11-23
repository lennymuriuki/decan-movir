package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CinemaDarkColorScheme =
  darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF002438),
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlueOnContainer,
    secondary = CobaltBlue,
    onSecondary = Color(0xFF061E3E),
    secondaryContainer = CobaltBlueContainer,
    onSecondaryContainer = CobaltBlueOnContainer,
    tertiary = OceanSky,
    onTertiary = Color.White,
    tertiaryContainer = OceanSkyContainer,
    onTertiaryContainer = OceanSkyOnContainer,
    background = CinemaObsidian,
    onBackground = CinemaMist,
    surface = CinemaSurface,
    onSurface = CinemaMist,
    surfaceVariant = CinemaSurfaceHigh,
    onSurfaceVariant = CinemaSlate,
    surfaceContainerLowest = CinemaObsidianDeep,
    surfaceContainerLow = CinemaSurface,
    surfaceContainer = CinemaSurfaceHigh,
    surfaceContainerHigh = CinemaSurfaceHighest,
    outline = CinemaOutline,
    outlineVariant = CinemaOutlineVariant,
    error = CinemaErrorRed,
    onError = Color.White,
  )

private val CinemaLightColorScheme =
  lightColorScheme(
    primary = SkyBlueAccentLight,
    onPrimary = Color.White,
    primaryContainer = SkyBlueContainerLight,
    onPrimaryContainer = Color(0xFF00253A),
    secondary = CobaltAccentLight,
    onSecondary = Color.White,
    secondaryContainer = CobaltContainerLight,
    onSecondaryContainer = Color(0xFF0B2259),
    tertiary = Color(0xFF0369A1),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC8ECFF),
    onTertiaryContainer = Color(0xFF002438),
    background = CinemaIvory,
    onBackground = CinemaInk,
    surface = CinemaIvory,
    onSurface = CinemaInk,
    surfaceVariant = CinemaPaper,
    onSurfaceVariant = CinemaInkMuted,
    surfaceContainerLow = CinemaPaper,
    surfaceContainer = CinemaPaperHigh,
    outline = Color(0xFFBCCAE0),
  )

val DecanShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun DecanMovieTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) CinemaDarkColorScheme else CinemaLightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
      }
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = DecanTypography,
    shapes = DecanShapes,
    content = content,
  )
}
