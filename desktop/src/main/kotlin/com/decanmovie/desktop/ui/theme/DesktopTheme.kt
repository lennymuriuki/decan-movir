package com.decanmovie.desktop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DecanDesktopColors {
  val Obsidian = Color(0xFF070910)
  val ObsidianDeep = Color(0xFF04050A)
  val Surface = Color(0xFF0E131F)
  val SurfaceElevated = Color(0xFF151D2F)
  val SurfaceGlass = Color(0xCC131B2C)
  val SurfaceGlassStrong = Color(0xE6182237)

  // Elegant Red Cinematic Accents
  val Crimson = Color(0xFFE50914)
  val CrimsonBright = Color(0xFFFF3344)
  val CrimsonContainer = Color(0xFF3E0C14)
  val CrimsonOnContainer = Color(0xFFFFD8DC)

  // Secondary Cinema Highlights
  val SkyAccent = Color(0xFF38BDF8)
  val SkyContainer = Color(0xFF0D334D)
  val GoldStar = Color(0xFFFBBF24)
  val EmeraldOk = Color(0xFF22C55E)

  val Mist = Color(0xFFF3F6FC)
  val Slate = Color(0xFF9AA8C7)
  val Outline = Color(0xFF28344E)
  val OutlineGlass = Color(0x40FFFFFF)
}

private val DesktopDarkColorScheme =
  darkColorScheme(
    primary = DecanDesktopColors.Crimson,
    onPrimary = Color.White,
    primaryContainer = DecanDesktopColors.CrimsonContainer,
    onPrimaryContainer = DecanDesktopColors.CrimsonOnContainer,
    secondary = DecanDesktopColors.SkyAccent,
    onSecondary = Color(0xFF002438),
    secondaryContainer = DecanDesktopColors.SkyContainer,
    onSecondaryContainer = Color(0xFFC8ECFF),
    tertiary = DecanDesktopColors.CrimsonBright,
    onTertiary = Color.White,
    background = DecanDesktopColors.Obsidian,
    onBackground = DecanDesktopColors.Mist,
    surface = DecanDesktopColors.Surface,
    onSurface = DecanDesktopColors.Mist,
    surfaceVariant = DecanDesktopColors.SurfaceElevated,
    onSurfaceVariant = DecanDesktopColors.Slate,
    surfaceContainerLowest = DecanDesktopColors.ObsidianDeep,
    surfaceContainerLow = DecanDesktopColors.Surface,
    surfaceContainer = DecanDesktopColors.SurfaceElevated,
    surfaceContainerHigh = Color(0xFF1D283F),
    outline = DecanDesktopColors.Outline,
    outlineVariant = Color(0xFF1C2538),
    error = Color(0xFFEF4444),
    onError = Color.White,
  )

val DesktopTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
  )

val DesktopShapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun DecanDesktopTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = DesktopDarkColorScheme,
    typography = DesktopTypography,
    shapes = DesktopShapes,
    content = content,
  )
}
