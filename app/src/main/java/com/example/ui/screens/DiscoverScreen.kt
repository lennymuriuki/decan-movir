package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.ui.components.HeroSpotlightCard
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.MoviePosterImage
import com.example.ui.viewmodel.DiscoverUiState
import com.example.ui.viewmodel.MediaFilter
import com.example.ui.viewmodel.UpdateUiState

@Composable
fun DiscoverScreen(
  uiState: DiscoverUiState,
  updateState: UpdateUiState,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  onRefresh: () -> Unit,
  onSelectMediaFilter: (MediaFilter) -> Unit,
  onSelectGenre: (String) -> Unit,
  onOpenMovie: (Movie) -> Unit,
  onPlayMovie: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  onLoadMore: () -> Unit,
  onOpenDownloads: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (uiState.isLoading && uiState.allMovies.isEmpty()) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
          text = "Curating Decan Cinema…",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    return
  }

  val mediaFiltered =
    when (uiState.selectedMediaFilter) {
      MediaFilter.ALL -> uiState.allMovies
      MediaFilter.MOVIES ->
        uiState.allMovies.filter {
          !it.mediaType.equals("Series", ignoreCase = true) &&
            !it.mediaType.equals("Anime", ignoreCase = true)
        }
      MediaFilter.SERIES ->
        uiState.allMovies.filter { it.mediaType.equals("Series", ignoreCase = true) }
      MediaFilter.ANIME ->
        (uiState.anime +
            uiState.allMovies.filter {
              it.mediaType.equals("Anime", ignoreCase = true) ||
                it.genre.contains("Animation", ignoreCase = true) ||
                it.genre.contains("Anime", ignoreCase = true)
            })
          .distinctBy { it.id }
      MediaFilter.RANKINGS ->
        (uiState.rankings.ifEmpty { uiState.allMovies.take(20) }).distinctBy { it.id }
    }

  val isRankingsMode = uiState.selectedMediaFilter == MediaFilter.RANKINGS
  val isGridMode =
    !isRankingsMode &&
      (uiState.selectedGenre != "All" || uiState.selectedMediaFilter != MediaFilter.ALL)

  val filteredMovies =
    if (uiState.selectedGenre == "All") {
      mediaFiltered
    } else {
      val localGenre =
        mediaFiltered.filter { it.genre.contains(uiState.selectedGenre, ignoreCase = true) }
      (uiState.genreFeedMovies + localGenre).distinctBy { it.id }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("discover_list"),
    contentPadding = PaddingValues(bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(38.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.LocalMovies,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${uiState.allMovies.size} titles • Movies, Series, Anime & HD",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onOpenDownloads,
            modifier = Modifier.testTag("discover_downloads_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Download,
              contentDescription = "Open Downloads",
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
          IconButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("discover_refresh_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Refresh catalog",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("discover_settings_button"),
          ) {
            BadgedBox(
              badge = {
                if (updateState.hasUpdate) {
                  Badge(containerColor = MaterialTheme.colorScheme.secondary)
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Account and Settings",
                tint = MaterialTheme.colorScheme.onBackground,
              )
            }
          }
        }
      }
    }

    if (uiState.isOfflineFallback) {
      item {
        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.offline_catalog_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
          }
        }
      }
    }

    if (uiState.errorMessage != null && uiState.allMovies.isEmpty()) {
      item {
        Column(
          modifier = Modifier.fillMaxWidth().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = uiState.errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
        }
      }
    }

    if (uiState.spotlight.isNotEmpty() && !isGridMode && !isRankingsMode) {
      item {
        val pagerState = rememberPagerState(pageCount = { uiState.spotlight.size })
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth(),
          ) { page ->
            val movie = uiState.spotlight[page]
            HeroSpotlightCard(
              movie = movie,
              savedEntry = savedLibraryMap[movie.id],
              onOpenDetail = { onOpenMovie(movie) },
              onToggleWatchlist = { onToggleWatchlist(movie) },
              onPlayNow = { onPlayMovie(movie) },
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            repeat(uiState.spotlight.size) { index ->
              val active = pagerState.currentPage == index
              Box(
                modifier =
                  Modifier.padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(if (active) 22.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                      if (active) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline
                    )
              )
            }
          }
        }
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(MediaFilter.entries, key = { it.name }) { filter ->
            FilterChip(
              selected = uiState.selectedMediaFilter == filter,
              onClick = { onSelectMediaFilter(filter) },
              leadingIcon =
                if (filter == MediaFilter.RANKINGS) {
                  {
                    Icon(
                      imageVector = Icons.Filled.TrendingUp,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  }
                } else null,
              label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  containerColor = MaterialTheme.colorScheme.surfaceContainer,
                  labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
              modifier = Modifier.testTag("media_filter_${filter.name}"),
            )
          }
        }

        if (!isRankingsMode) {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(uiState.availableGenres, key = { it }) { genre ->
              val selected = genre == uiState.selectedGenre
              FilterChip(
                selected = selected,
                onClick = { onSelectGenre(genre) },
                label = { Text(genre, style = MaterialTheme.typography.labelLarge) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  ),
                modifier = Modifier.testTag("discover_genre_$genre"),
              )
            }
          }
        }
      }
    }

    when {
      isRankingsMode -> {
        item {
          Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
              text = "Top Chart Rankings",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Live most-watched movies, series & anime on Decan Cinema",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        itemsIndexed(filteredMovies, key = { idx, m -> "rank_${idx}_${m.id}" }) { index, movie ->
          RankedChartCard(
            rank = movie.rankPosition ?: (index + 1),
            movie = movie,
            savedEntry = savedLibraryMap[movie.id],
            onOpen = { onOpenMovie(movie) },
            onPlay = { onPlayMovie(movie) },
            onToggleWatchlist = { onToggleWatchlist(movie) },
            modifier = Modifier.padding(horizontal = 20.dp),
          )
        }
      }

      isGridMode -> {
        item {
          val headerTitle =
            buildString {
              append(uiState.selectedMediaFilter.label)
              if (uiState.selectedGenre != "All") append(" • ${uiState.selectedGenre}")
              append(" (${filteredMovies.size})")
            }
          Text(
            text = headerTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp),
          )
        }

        items(filteredMovies.chunked(2), key = { row -> row.joinToString("_") { it.id } }) { rowMovies ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            for (movie in rowMovies) {
              MoviePosterCard(
                movie = movie,
                savedEntry = savedLibraryMap[movie.id],
                onClick = { onOpenMovie(movie) },
                onToggleWatchlist = { onToggleWatchlist(movie) },
                modifier = Modifier.weight(1f),
              )
            }
            if (rowMovies.size == 1) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }

        if (
          uiState.selectedMediaFilter == MediaFilter.MOVIES ||
            uiState.selectedMediaFilter == MediaFilter.SERIES ||
            uiState.selectedMediaFilter == MediaFilter.ANIME
        ) {
          item {
            Box(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              OutlinedButton(
                onClick = onLoadMore,
                enabled = !uiState.isLoadingMore,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("discover_load_more"),
              ) {
                if (uiState.isLoadingMore) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Load More ${uiState.selectedMediaFilter.label}")
              }
            }
          }
        }
      }

      else -> {
        if (uiState.rankings.isNotEmpty()) {
          item {
            DiscoverHorizontalSection(
              title = "Top 10 Rankings",
              subtitle = "Trending worldwide right now",
              movies = uiState.rankings.take(12),
              keyPrefix = "top_rank",
              showRankBadge = true,
              savedLibraryMap = savedLibraryMap,
              onOpenMovie = onOpenMovie,
              onToggleWatchlist = onToggleWatchlist,
              onSeeAll = { onSelectMediaFilter(MediaFilter.RANKINGS) },
            )
          }
        }

        if (uiState.recommendations.isNotEmpty()) {
          item {
            DiscoverHorizontalSection(
              title = "Recommended For You",
              subtitle = "Smart picks matched to trending cinema",
              movies = uiState.recommendations,
              keyPrefix = "rec_for_you",
              showRankBadge = false,
              savedLibraryMap = savedLibraryMap,
              onOpenMovie = onOpenMovie,
              onToggleWatchlist = onToggleWatchlist,
            )
          }
        }

        if (uiState.anime.isNotEmpty()) {
          item {
            DiscoverHorizontalSection(
              title = "Anime & Animation",
              subtitle = "Top anime series & animated features",
              movies = uiState.anime,
              keyPrefix = "anime_rail",
              showRankBadge = false,
              savedLibraryMap = savedLibraryMap,
              onOpenMovie = onOpenMovie,
              onToggleWatchlist = onToggleWatchlist,
              onSeeAll = { onSelectMediaFilter(MediaFilter.ANIME) },
            )
          }
        }

        items(uiState.rails, key = { it.id }) { rail ->
          DiscoverHorizontalSection(
            title = rail.title,
            subtitle = rail.subtitle,
            movies = rail.movies,
            keyPrefix = rail.id,
            showRankBadge = false,
            savedLibraryMap = savedLibraryMap,
            onOpenMovie = onOpenMovie,
            onToggleWatchlist = onToggleWatchlist,
          )
        }
      }
    }
  }
}

@Composable
private fun DiscoverHorizontalSection(
  title: String,
  subtitle: String,
  movies: List<Movie>,
  keyPrefix: String,
  showRankBadge: Boolean,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  onOpenMovie: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  onSeeAll: (() -> Unit)? = null,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (onSeeAll != null) {
        TextButton(onClick = onSeeAll) { Text("See All") }
      }
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      itemsIndexed(movies, key = { idx, m -> "${keyPrefix}_${idx}_${m.id}" }) { index, movie ->
        MoviePosterCard(
          movie = movie,
          savedEntry = savedLibraryMap[movie.id],
          onClick = { onOpenMovie(movie) },
          onToggleWatchlist = { onToggleWatchlist(movie) },
          rankOverride = if (showRankBadge) (movie.rankPosition ?: (index + 1)) else null,
          modifier = Modifier.width(142.dp),
        )
      }
    }
  }
}

@Composable
private fun RankedChartCard(
  rank: Int,
  movie: Movie,
  savedEntry: SavedMovieEntry?,
  onOpen: () -> Unit,
  onPlay: () -> Unit,
  onToggleWatchlist: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier =
      modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .clickable(onClick = onOpen)
        .testTag("ranked_card_${movie.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color =
          if (rank <= 3) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(40.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "#$rank",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color =
              if (rank <= 3) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      MoviePosterImage(
        imageUrl = movie.posterUrl,
        contentDescription = movie.title,
        modifier = Modifier.width(66.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium),
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${movie.genre} • ${movie.releaseYear} • ${movie.mediaType}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!movie.imdbRating.isNullOrBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Star,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "IMDb ${movie.imdbRating}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        FilledTonalIconButton(onClick = onPlay) {
          Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Watch now")
        }
        IconButton(onClick = onToggleWatchlist) {
          Icon(
            imageVector =
              if (savedEntry != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
            contentDescription = "Toggle watchlist",
            tint =
              if (savedEntry != null) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
