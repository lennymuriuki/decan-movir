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
    primary = MarqueeGold,
    onPrimary = Color(0xFF281900),
    primaryContainer = MarqueeGoldContainer,
    onPrimaryContainer = MarqueeGoldOnContainer,
    secondary = VelvetCrimson,
    onSecondary = Color.White,
    secondaryContainer = VelvetCrimsonContainer,
    onSecondaryContainer = VelvetCrimsonOnContainer,
    tertiary = ProjectorTeal,
    onTertiary = Color(0xFF00201D),
    tertiaryContainer = ProjectorTealContainer,
    onTertiaryContainer = ProjectorTealOnContainer,
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
    error = VelvetCrimson,
    onError = Color.White,
  )

private val CinemaLightColorScheme =
  lightColorScheme(
    primary = GoldAccentLight,
    onPrimary = Color.White,
    primaryContainer = GoldContainerLight,
    onPrimaryContainer = Color(0xFF311F00),
    secondary = CrimsonAccentLight,
    onSecondary = Color.White,
    secondaryContainer = CrimsonContainerLight,
    onSecondaryContainer = Color(0xFF400010),
    tertiary = Color(0xFF0F6B62),
    onTertiary = Color.White,
    background = CinemaIvory,
    onBackground = CinemaInk,
    surface = CinemaIvory,
    onSurface = CinemaInk,
    surfaceVariant = CinemaPaper,
    onSurfaceVariant = CinemaInkMuted,
    surfaceContainerLow = CinemaPaper,
    surfaceContainer = CinemaPaperHigh,
    outline = Color(0xFFC5BEB0),
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
