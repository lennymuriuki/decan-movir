package com.decanmovie.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.data.remote.DesktopHomeCatalogResult
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.PlaybackHistoryItem
import com.decanmovie.desktop.ui.components.AsyncDesktopImage
import com.decanmovie.desktop.ui.components.DesktopMediaRailRow
import com.decanmovie.desktop.ui.theme.DecanDesktopColors

@Composable
fun HomeScreen(
  feed: DesktopHomeCatalogResult?,
  isLoading: Boolean,
  history: List<PlaybackHistoryItem>,
  watchlistIds: Set<String>,
  compactCards: Boolean,
  onRefresh: () -> Unit,
  onMovieClick: (Movie) -> Unit,
  onQuickPlay: (Movie) -> Unit,
  onResumeHistory: (PlaybackHistoryItem) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
) {
  val cardWidth = if (compactCards) 154.dp else 178.dp

  if (isLoading && feed == null) {
    Box(modifier = Modifier.fillMaxSize().testTag("home_screen"), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator(color = DecanDesktopColors.Crimson)
        Text(
          text = "Loading cinema catalog…",
          style = MaterialTheme.typography.titleMedium,
          color = DecanDesktopColors.Mist,
        )
      }
    }
    return
  }

  val resolved = feed ?: return
  var heroIndex by remember(resolved.spotlight) { mutableIntStateOf(0) }
  val featured = resolved.spotlight.getOrNull(heroIndex) ?: resolved.spotlight.firstOrNull()

  val historyAsMovies =
    remember(history) {
      history.take(15).map { h ->
        Movie(
          id = h.movieId,
          title = h.title,
          posterUrl = h.posterUrl,
          backdropUrl = h.posterUrl,
          genre = h.genre.ifBlank { h.mediaType },
          mediaType = h.mediaType,
          subjectId = h.subjectId,
          detailPath = h.detailPath,
          releaseYear = if (h.season > 0 && h.episode > 0) "S%02dE%02d".format(h.season, h.episode) else "Resume",
        )
      }
    }
  val historyById = remember(history) { history.associateBy { it.movieId } }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("home_screen"),
    contentPadding = PaddingValues(bottom = 36.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    if (resolved.isOfflineFallback) {
      item {
        Surface(
          color = DecanDesktopColors.CrimsonContainer.copy(alpha = 0.7f),
          border = BorderStroke(1.dp, DecanDesktopColors.Crimson.copy(alpha = 0.75f)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.WifiOff,
              contentDescription = null,
              tint = DecanDesktopColors.CrimsonBright,
            )
            Text(
              text = "Showing offline cinema snapshot. Reconnect and click Refresh to load live releases.",
              style = MaterialTheme.typography.bodyMedium,
              color = DecanDesktopColors.CrimsonOnContainer,
              modifier = Modifier.weight(1f),
            )
            OutlinedButton(
              onClick = onRefresh,
              border = BorderStroke(1.dp, DecanDesktopColors.CrimsonBright),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Retry")
            }
          }
        }
      }
    }

    if (featured != null) {
      item {
        FeaturedHeroSpotlight(
          featured = featured,
          spotlightItems = resolved.spotlight,
          selectedIndex = heroIndex,
          isInWatchlist = featured.id in watchlistIds,
          onSelectIndex = { heroIndex = it },
          onPlay = { onQuickPlay(featured) },
          onDetails = { onMovieClick(featured) },
          onToggleWatchlist = { onToggleWatchlist(featured) },
          onRefresh = onRefresh,
        )
      }
    }

    if (historyAsMovies.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Continue Watching",
          subtitle = "Pick up right where you left off",
          movies = historyAsMovies,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = { m -> historyById[m.id]?.let(onResumeHistory) ?: onMovieClick(m) },
          onQuickPlay = { m -> historyById[m.id]?.let(onResumeHistory) ?: onQuickPlay(m) },
          onToggleWatchlist = onToggleWatchlist,
          progressLookup = { m -> historyById[m.id]?.progressFraction() },
          subtitleLookup = { m ->
            val h = historyById[m.id]
            if (h != null && h.season > 0 && h.episode > 0) {
              "S%02d • Episode %02d".format(h.season, h.episode)
            } else {
              "Resume playback"
            }
          },
        )
      }
    }

    if (resolved.trending.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Trending Now",
          subtitle = "Most watched releases across Decan Movie",
          movies = resolved.trending,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    if (resolved.popularDramas.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Popular Dramas",
          subtitle = "Opens in TikTok-style Vertical Drama Mode (swipe / wheel episodes)",
          movies = resolved.popularDramas,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    if (resolved.recentlyAdded.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Recently Added",
          subtitle = "Fresh cinema additions ready to stream",
          movies = resolved.recentlyAdded,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    if (resolved.popularMovies.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Popular Movies",
          subtitle = "Feature films in Theater Mode",
          movies = resolved.popularMovies,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    if (resolved.popularSeries.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Popular Series",
          subtitle = "Multi-season television hits",
          movies = resolved.popularSeries,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    if (resolved.popularAnime.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Popular Anime",
          subtitle = "Top ranked anime series & films",
          movies = resolved.popularAnime,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    if (resolved.recommended.isNotEmpty()) {
      item {
        DesktopMediaRailRow(
          title = "Recommended For You",
          subtitle = "Handpicked cinema & series spotlight",
          movies = resolved.recommended,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }

    resolved.rails.take(4).forEach { rail ->
      item {
        DesktopMediaRailRow(
          title = rail.title,
          subtitle = rail.subtitle,
          movies = rail.movies,
          watchlistIds = watchlistIds,
          cardWidth = cardWidth,
          onMovieClick = onMovieClick,
          onQuickPlay = onQuickPlay,
          onToggleWatchlist = onToggleWatchlist,
        )
      }
    }
  }
}

@Composable
private fun FeaturedHeroSpotlight(
  featured: Movie,
  spotlightItems: List<Movie>,
  selectedIndex: Int,
  isInWatchlist: Boolean,
  onSelectIndex: (Int) -> Unit,
  onPlay: () -> Unit,
  onDetails: () -> Unit,
  onToggleWatchlist: () -> Unit,
  onRefresh: () -> Unit,
) {
  val isDrama = remember(featured) { DramaExperienceDetector.isEpisodicDrama(featured) }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 10.dp)
        .height(360.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(DecanDesktopColors.Surface)
  ) {
    AsyncDesktopImage(
      imageUrl = featured.backdropUrl.ifBlank { featured.posterUrl },
      contentDescription = featured.title,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              listOf(
                DecanDesktopColors.ObsidianDeep.copy(alpha = 0.96f),
                DecanDesktopColors.ObsidianDeep.copy(alpha = 0.78f),
                Color.Transparent,
              )
            )
          )
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              listOf(
                Color.Transparent,
                DecanDesktopColors.ObsidianDeep.copy(alpha = 0.85f),
              )
            )
          )
    )

    Row(
      modifier = Modifier.fillMaxSize().padding(28.dp),
      verticalAlignment = Alignment.Bottom,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f).padding(end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(color = DecanDesktopColors.Crimson, shape = RoundedCornerShape(6.dp)) {
            Text(
              text = "FEATURED ${featured.mediaType.uppercase()}",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
              color = Color.White,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          if (isDrama) {
            Surface(
              color = DecanDesktopColors.SurfaceGlassStrong,
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, DecanDesktopColors.CrimsonBright),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Smartphone,
                  contentDescription = null,
                  tint = DecanDesktopColors.CrimsonBright,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "Vertical Drama Player",
                  style = MaterialTheme.typography.labelMedium,
                  color = DecanDesktopColors.Mist,
                )
              }
            }
          }
          if (!featured.imdbRating.isNullOrBlank()) {
            Surface(
              color = Color.Black.copy(alpha = 0.65f),
              shape = RoundedCornerShape(6.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Star,
                  contentDescription = null,
                  tint = DecanDesktopColors.GoldStar,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = featured.imdbRating,
                  style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                )
              }
            }
          }
          Text(
            text = "${featured.releaseYear} • ${featured.genre}",
            style = MaterialTheme.typography.labelMedium,
            color = DecanDesktopColors.Slate,
          )
        }

        Text(
          text = featured.title,
          style = MaterialTheme.typography.displaySmall,
          color = DecanDesktopColors.Mist,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        if (featured.synopsis.isNotBlank()) {
          Text(
            text = featured.synopsis,
            style = MaterialTheme.typography.bodyMedium,
            color = DecanDesktopColors.Mist.copy(alpha = 0.86f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(
          modifier = Modifier.padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.testTag("hero_play_button"),
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(
              text = if (isDrama) "Watch Drama" else "Play Now",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
          }

          OutlinedButton(
            onClick = onDetails,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = DecanDesktopColors.SurfaceGlass,
              contentColor = DecanDesktopColors.Mist,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
          ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Details")
          }

          OutlinedButton(
            onClick = onToggleWatchlist,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = DecanDesktopColors.SurfaceGlass,
              contentColor = DecanDesktopColors.Mist,
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
          ) {
            Icon(
              imageVector = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
              contentDescription = null,
              tint = if (isInWatchlist) DecanDesktopColors.CrimsonBright else DecanDesktopColors.Mist,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isInWatchlist) "Saved" else "Watchlist")
          }
        }
      }

      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          color = DecanDesktopColors.SurfaceGlass,
          shape = CircleShape,
          border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
          modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onRefresh),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Refresh home catalog",
              tint = DecanDesktopColors.Mist,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        if (spotlightItems.size > 1) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            spotlightItems.forEachIndexed { idx, _ ->
              val active = idx == selectedIndex
              Box(
                modifier =
                  Modifier
                    .height(8.dp)
                    .width(if (active) 26.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                      if (active) DecanDesktopColors.Crimson else Color.White.copy(alpha = 0.35f)
                    )
                    .clickable { onSelectIndex(idx) }
              )
            }
          }
        }
      }
    }
  }
}
