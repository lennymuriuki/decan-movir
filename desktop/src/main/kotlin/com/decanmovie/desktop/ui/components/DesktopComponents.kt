package com.decanmovie.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Maximize
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.decanmovie.desktop.media.DesktopImageLoader
import com.decanmovie.desktop.model.DesktopDestination
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import kotlinx.coroutines.launch

@Composable
fun AsyncDesktopImage(
  imageUrl: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
) {
  var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }

  LaunchedEffect(imageUrl) {
    bitmap = null
    if (imageUrl.isNotBlank()) {
      bitmap = DesktopImageLoader.loadBitmap(imageUrl)
    }
  }

  val loaded = bitmap
  if (loaded != null) {
    Image(
      bitmap = loaded,
      contentDescription = contentDescription,
      contentScale = contentScale,
      modifier = modifier,
    )
  } else {
    Box(
      modifier =
        modifier.background(
          Brush.linearGradient(
            listOf(
              DecanDesktopColors.SurfaceElevated,
              DecanDesktopColors.CrimsonContainer.copy(alpha = 0.45f),
              DecanDesktopColors.Obsidian,
            )
          )
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Filled.Movie,
        contentDescription = null,
        tint = DecanDesktopColors.Slate.copy(alpha = 0.35f),
        modifier = Modifier.size(32.dp),
      )
    }
  }
}

@Composable
fun WindowScope.DesktopWindowTitleBar(
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onSubmitSearch: () -> Unit,
  onOpenLocalFile: () -> Unit,
  hasUpdateAvailable: Boolean,
  updateTag: String?,
  onClickUpdateBadge: () -> Unit,
  isMaximized: Boolean,
  onMinimize: () -> Unit,
  onToggleMaximize: () -> Unit,
  onClose: () -> Unit,
) {
  WindowDraggableArea {
    Surface(
      color = DecanDesktopColors.ObsidianDeep,
      tonalElevation = 0.dp,
      modifier = Modifier.fillMaxWidth().height(54.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Brand Wordmark
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
          Box(
            modifier =
              Modifier.size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                  Brush.linearGradient(
                    listOf(DecanDesktopColors.Crimson, Color(0xFF9E0B16))
                  )
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Decan Movie",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
          Text(
            text = "DECAN",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = DecanDesktopColors.Mist,
          )
          Text(
            text = "MOVIE",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = DecanDesktopColors.Crimson,
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Global Desktop Search Bar
        Surface(
          color = DecanDesktopColors.SurfaceElevated.copy(alpha = 0.85f),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier = Modifier.weight(1f).height(36.dp).testTag("desktop_top_search"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = "Search",
              tint = DecanDesktopColors.Slate,
              modifier = Modifier.size(18.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (searchQuery.isEmpty()) {
                Text(
                  text = "Search movies, series, anime, K-dramas… (Ctrl+K)",
                  style = MaterialTheme.typography.bodySmall,
                  color = DecanDesktopColors.Slate.copy(alpha = 0.75f),
                  maxLines = 1,
                )
              }
              BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                textStyle =
                  MaterialTheme.typography.bodyMedium.copy(color = DecanDesktopColors.Mist),
                cursorBrush = SolidColor(DecanDesktopColors.Crimson),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                modifier = Modifier.fillMaxWidth(),
              )
            }
            if (searchQuery.isNotEmpty()) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = DecanDesktopColors.Slate,
                modifier = Modifier.size(16.dp).clickable { onSearchQueryChange("") },
              )
            }
          }
        }

        // Open Local Video Button
        Surface(
          color = DecanDesktopColors.SurfaceGlass,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier =
            Modifier.height(34.dp)
              .clip(RoundedCornerShape(8.dp))
              .clickable(onClick = onOpenLocalFile)
              .testTag("desktop_open_local_file_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = "Open local video",
              tint = DecanDesktopColors.Slate,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Open File",
              style = MaterialTheme.typography.labelMedium,
              color = DecanDesktopColors.Mist,
            )
          }
        }

        if (hasUpdateAvailable) {
          Surface(
            color = DecanDesktopColors.CrimsonContainer,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, DecanDesktopColors.Crimson),
            modifier =
              Modifier.height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClickUpdateBadge)
                .testTag("desktop_update_badge"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.SystemUpdateAlt,
                contentDescription = "Update available",
                tint = DecanDesktopColors.CrimsonBright,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Update ${updateTag ?: ""}".trim(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = DecanDesktopColors.CrimsonOnContainer,
              )
            }
          }
        }

        // Native-feeling Windows Caption Controls
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          IconButton(onClick = onMinimize, modifier = Modifier.size(34.dp).testTag("win_min_btn")) {
            Icon(
              imageVector = Icons.Filled.Minimize,
              contentDescription = "Minimize",
              tint = DecanDesktopColors.Mist,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onToggleMaximize,
            modifier = Modifier.size(34.dp).testTag("win_max_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.Maximize,
              contentDescription = if (isMaximized) "Restore" else "Maximize",
              tint = DecanDesktopColors.Mist,
              modifier = Modifier.size(16.dp),
            )
          }
          IconButton(
            onClick = onClose,
            modifier =
              Modifier.size(34.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DecanDesktopColors.Crimson.copy(alpha = 0.14f))
                .testTag("win_close_btn"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close",
              tint = DecanDesktopColors.CrimsonBright,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
fun DesktopSidebarRail(
  currentDestination: DesktopDestination,
  activeDownloadCount: Int,
  watchlistCount: Int,
  hasUpdateAvailable: Boolean,
  onSelectDestination: (DesktopDestination) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = DecanDesktopColors.ObsidianDeep,
    border = BorderStroke(1.dp, DecanDesktopColors.Outline.copy(alpha = 0.55f)),
    modifier = modifier.width(208.dp).fillMaxHeight(),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      DesktopDestination.entries.forEach { dest ->
        val selected = currentDestination == dest
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val bgColor by animateColorAsState(
          when {
            selected -> DecanDesktopColors.Crimson.copy(alpha = 0.22f)
            hovered -> DecanDesktopColors.SurfaceElevated
            else -> Color.Transparent
          }
        )
        val icon: ImageVector =
          when (dest) {
            DesktopDestination.HOME -> Icons.Filled.Home
            DesktopDestination.MOVIES -> Icons.Filled.Movie
            DesktopDestination.SERIES -> Icons.Filled.Tv
            DesktopDestination.ANIME -> Icons.Filled.Whatshot
            DesktopDestination.DRAMAS -> Icons.Filled.Smartphone
            DesktopDestination.SEARCH -> Icons.Filled.Search
            DesktopDestination.WATCHLIST -> Icons.Filled.Bookmark
            DesktopDestination.DOWNLOADS -> Icons.Filled.Download
            DesktopDestination.HISTORY -> Icons.Filled.History
            DesktopDestination.SETTINGS -> Icons.Filled.Settings
          }

        val badgeText =
          when {
            dest == DesktopDestination.DOWNLOADS && activeDownloadCount > 0 ->
              activeDownloadCount.toString()
            dest == DesktopDestination.WATCHLIST && watchlistCount > 0 -> watchlistCount.toString()
            dest == DesktopDestination.SETTINGS && hasUpdateAvailable -> "NEW"
            else -> null
          }

        if (dest == DesktopDestination.WATCHLIST) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "LIBRARY & TOOLS",
            style = MaterialTheme.typography.labelMedium,
            color = DecanDesktopColors.Slate.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }

        Surface(
          color = bgColor,
          shape = RoundedCornerShape(10.dp),
          border =
            if (selected) BorderStroke(1.dp, DecanDesktopColors.Crimson.copy(alpha = 0.7f))
            else null,
          modifier =
            Modifier.fillMaxWidth()
              .height(42.dp)
              .hoverable(interaction)
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectDestination(dest) }
              .testTag("nav_${dest.name.lowercase()}"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = icon,
              contentDescription = dest.label,
              tint = if (selected) DecanDesktopColors.CrimsonBright else DecanDesktopColors.Slate,
              modifier = Modifier.size(19.dp),
            )
            Text(
              text = dest.label,
              style =
                MaterialTheme.typography.titleSmall.copy(
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
              color = if (selected) DecanDesktopColors.Mist else DecanDesktopColors.Slate,
              modifier = Modifier.weight(1f),
            )
            if (badgeText != null) {
              Surface(
                color =
                  if (badgeText == "NEW") DecanDesktopColors.Crimson
                  else DecanDesktopColors.SurfaceElevated,
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = badgeText,
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Surface(
        color = DecanDesktopColors.SurfaceGlass,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DecanDesktopColors.Outline),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.LiveTv,
              contentDescription = null,
              tint = DecanDesktopColors.CrimsonBright,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = "Drama Portrait Mode",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = DecanDesktopColors.Mist,
            )
          }
          Text(
            text = "Dramas launch in TikTok-style vertical swipe playback; Movies, Series & Anime use Theater Player.",
            style = MaterialTheme.typography.bodySmall,
            color = DecanDesktopColors.Slate,
          )
        }
      }
    }
  }
}

@Composable
fun DesktopMediaCard(
  movie: Movie,
  onClick: () -> Unit,
  onQuickPlay: () -> Unit,
  isInWatchlist: Boolean,
  onToggleWatchlist: () -> Unit,
  cardWidth: Dp = 178.dp,
  progressFraction: Float? = null,
  subtitleOverride: String? = null,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val isDrama = remember(movie) { DramaExperienceDetector.isEpisodicDrama(movie) }

  Surface(
    color = DecanDesktopColors.SurfaceElevated.copy(alpha = if (hovered) 0.96f else 0.72f),
    shape = RoundedCornerShape(14.dp),
    border =
      BorderStroke(
        width = if (hovered) 1.5.dp else 1.dp,
        color =
          if (hovered) DecanDesktopColors.Crimson
          else DecanDesktopColors.Outline.copy(alpha = 0.65f),
      ),
    modifier =
      modifier
        .width(cardWidth)
        .hoverable(interaction)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("movie_card_${movie.id}"),
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
        AsyncDesktopImage(
          imageUrl = movie.posterUrl.ifBlank { movie.backdropUrl },
          contentDescription = movie.title,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(
                Brush.verticalGradient(
                  listOf(
                    Color.Black.copy(alpha = 0.15f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.75f),
                  )
                )
              )
        )

        // Top badges (Rank / Drama mode / Rating)
        Row(
          modifier = Modifier.fillMaxWidth().padding(8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          if (movie.rankPosition != null) {
            Surface(color = DecanDesktopColors.Crimson, shape = RoundedCornerShape(6.dp)) {
              Text(
                text = "#${movie.rankPosition}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          } else if (isDrama) {
            Surface(
              color = DecanDesktopColors.Crimson.copy(alpha = 0.9f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = "DRAMA",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          } else {
            Surface(
              color = Color.Black.copy(alpha = 0.65f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Text(
                text = movie.mediaType.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = DecanDesktopColors.Mist,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }

          if (!movie.imdbRating.isNullOrBlank()) {
            Surface(
              color = Color.Black.copy(alpha = 0.72f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = null,
                  tint = DecanDesktopColors.GoldStar,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = movie.imdbRating,
                  style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                )
              }
            }
          }
        }

        // Hover quick actions (Play + Watchlist)
        if (hovered) {
          Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = DecanDesktopColors.Crimson,
              shape = CircleShape,
              modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onQuickPlay),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Filled.PlayArrow,
                  contentDescription = "Play ${movie.title}",
                  tint = Color.White,
                  modifier = Modifier.size(22.dp),
                )
              }
            }
            Surface(
              color = Color.Black.copy(alpha = 0.78f),
              shape = CircleShape,
              border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
              modifier =
                Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onToggleWatchlist),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector =
                    if (isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                  contentDescription = "Toggle Watchlist",
                  tint = if (isInWatchlist) DecanDesktopColors.CrimsonBright else Color.White,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }

        if (progressFraction != null && progressFraction > 0f) {
          LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            color = DecanDesktopColors.Crimson,
            trackColor = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
          )
        }
      }

      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = DecanDesktopColors.Mist,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            subtitleOverride
              ?: "${movie.releaseYear} • ${movie.genre.ifBlank { movie.mediaType }}",
          style = MaterialTheme.typography.bodySmall,
          color = DecanDesktopColors.Slate,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun DesktopMediaRailRow(
  title: String,
  subtitle: String? = null,
  movies: List<Movie>,
  watchlistIds: Set<String>,
  onMovieClick: (Movie) -> Unit,
  onQuickPlay: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  cardWidth: Dp = 176.dp,
  progressLookup: ((Movie) -> Float?)? = null,
  subtitleLookup: ((Movie) -> String?)? = null,
) {
  if (movies.isEmpty()) return
  val scrollState = rememberScrollState()
  val scope = rememberCoroutineScope()

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = DecanDesktopColors.Mist,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = DecanDesktopColors.Slate,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
          color = DecanDesktopColors.SurfaceElevated,
          shape = CircleShape,
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier =
            Modifier.size(32.dp)
              .clip(CircleShape)
              .clickable(enabled = scrollState.value > 0) {
                scope.launch { scrollState.animateScrollTo((scrollState.value - 540).coerceAtLeast(0)) }
              },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = "Scroll left",
              tint = DecanDesktopColors.Mist,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        Surface(
          color = DecanDesktopColors.SurfaceElevated,
          shape = CircleShape,
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          modifier =
            Modifier.size(32.dp)
              .clip(CircleShape)
              .clickable(enabled = scrollState.value < scrollState.maxValue) {
                scope.launch {
                  scrollState.animateScrollTo((scrollState.value + 540).coerceAtMost(scrollState.maxValue))
                }
              },
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Scroll right",
              tint = DecanDesktopColors.Mist,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    Row(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(scrollState)
          .padding(PaddingValues(horizontal = 24.dp)),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      movies.forEach { movie ->
        DesktopMediaCard(
          movie = movie,
          onClick = { onMovieClick(movie) },
          onQuickPlay = { onQuickPlay(movie) },
          isInWatchlist = movie.id in watchlistIds,
          onToggleWatchlist = { onToggleWatchlist(movie) },
          cardWidth = cardWidth,
          progressFraction = progressLookup?.invoke(movie),
          subtitleOverride = subtitleLookup?.invoke(movie),
        )
      }
    }
  }
}
