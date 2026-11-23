package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.remote.AnimeSortMode
import com.example.data.remote.CategorySortMode
import com.example.data.settings.ContinueWatchingEntry
import com.example.ui.components.CinemaCompactGridSkeleton
import com.example.ui.components.CinemaDiscoverLoadingView
import com.example.ui.components.HeroSpotlightCard
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.MoviePosterImage
import com.example.ui.viewmodel.DiscoverUiState
import com.example.ui.viewmodel.MediaFilter
import com.example.ui.viewmodel.UpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
  uiState: DiscoverUiState,
  updateState: UpdateUiState,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  continueWatching: List<ContinueWatchingEntry>,
  unreadNotifications: Int,
  onRefresh: () -> Unit,
  onSelectMediaFilter: (MediaFilter) -> Unit,
  onSelectGenre: (String) -> Unit,
  onSelectCategorySort: (CategorySortMode) -> Unit = {},
  onSelectAnimeGenre: (String) -> Unit = {},
  onSelectAnimeSort: (AnimeSortMode) -> Unit = {},
  onOpenMovie: (Movie) -> Unit,
  onPlayMovie: (Movie) -> Unit,
  onResumeWatching: (ContinueWatchingEntry) -> Unit,
  onRemoveContinueWatching: (String) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  onLoadMore: () -> Unit,
  onOpenDownloads: () -> Unit,
  onOpenNotifications: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  onOpenSearch: () -> Unit = {},
) {
  if (uiState.isLoading && uiState.allMovies.isEmpty()) {
    CinemaDiscoverLoadingView(modifier = modifier)
    return
  }

  val isAnimeMode = uiState.selectedMediaFilter == MediaFilter.ANIME
  val isRankingsMode = uiState.selectedMediaFilter == MediaFilter.RANKINGS

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
      MediaFilter.ANIME -> {
        val base = uiState.animeFeed.ifEmpty { uiState.anime }
        val localExtras =
          uiState.allMovies.filter {
            it.mediaType.equals("Anime", ignoreCase = true) ||
              it.genre.contains("Anime", ignoreCase = true)
          }
        val combinedAnime = (base + localExtras).distinctBy { it.id }
        if (uiState.selectedAnimeGenre == "All") {
          combinedAnime
        } else {
          combinedAnime
            .filter { it.genre.contains(uiState.selectedAnimeGenre, ignoreCase = true) }
            .ifEmpty { uiState.animeFeed }
        }
      }
      MediaFilter.RANKINGS -> {
        val baseRankings = (uiState.rankings + uiState.allMovies).distinctBy { it.id }
        if (uiState.selectedGenre == "All") {
          (uiState.rankings.ifEmpty { baseRankings.take(25) }).distinctBy { it.id }
        } else {
          val matchingLocal =
            baseRankings.filter { matchesCategory(it, uiState.selectedGenre) }
          (uiState.genreFeedMovies + matchingLocal).distinctBy { it.id }
        }
      }
    }

  val isGridMode =
    !isRankingsMode &&
      (uiState.selectedGenre != "All" || uiState.selectedMediaFilter != MediaFilter.ALL)

  val rawFiltered =
    if (isAnimeMode || isRankingsMode) {
      mediaFiltered
    } else if (uiState.selectedGenre == "All") {
      mediaFiltered
    } else {
      val localCategory = mediaFiltered.filter { matchesCategory(it, uiState.selectedGenre) }
      (uiState.genreFeedMovies + localCategory).distinctBy { it.id }
    }

  val filteredMovies =
    if (isAnimeMode) {
      rawFiltered
    } else {
      applyUiSort(rawFiltered, uiState.selectedCategorySort)
    }

  val showCategorySortRow =
    !isAnimeMode &&
      (uiState.selectedGenre != "All" || uiState.selectedMediaFilter != MediaFilter.ALL)

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val gridColumns =
      when {
        maxWidth >= 840.dp -> 5
        maxWidth >= 600.dp -> 4
        else -> 3
      }

    PullToRefreshBox(
      isRefreshing = uiState.isRefreshing,
      onRefresh = onRefresh,
      modifier = Modifier.fillMaxSize(),
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("discover_list"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
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
                modifier = Modifier.size(36.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Filled.LocalMovies,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onBackground,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${uiState.allMovies.size} titles • Movies, Series & Anime",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier.testTag("discover_notifications_button"),
              ) {
                BadgedBox(
                  badge = {
                    if (unreadNotifications > 0) {
                      Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text(unreadNotifications.coerceAtMost(9).toString())
                      }
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onBackground,
                  )
                }
              }
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

        item {
          Surface(
            onClick = onOpenSearch,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(44.dp)
                .testTag("discover_search_button"),
          ) {
            Row(
              modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search catalog",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
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
                        .height(5.dp)
                        .width(if (active) 20.dp else 5.dp)
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

        // Category, Media Filter & Sort Hub
        item {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

            if (isAnimeMode) {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(uiState.availableAnimeGenres, key = { it }) { animeGenre ->
                  val selected = animeGenre == uiState.selectedAnimeGenre
                  FilterChip(
                    selected = selected,
                    onClick = { onSelectAnimeGenre(animeGenre) },
                    label = { Text(animeGenre, style = MaterialTheme.typography.labelMedium) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      ),
                    modifier = Modifier.testTag("anime_genre_$animeGenre"),
                  )
                }
              }

              LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(AnimeSortMode.entries, key = { it.name }) { mode ->
                  FilterChip(
                    selected = uiState.selectedAnimeSort == mode,
                    onClick = { onSelectAnimeSort(mode) },
                    leadingIcon = {
                      Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                      )
                    },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      ),
                    modifier = Modifier.testTag("anime_sort_${mode.name}"),
                  )
                }
              }
            } else {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                items(uiState.availableGenres, key = { it }) { genre ->
                  val selected = genre == uiState.selectedGenre
                  FilterChip(
                    selected = selected,
                    onClick = { onSelectGenre(genre) },
                    label = { Text(genre, style = MaterialTheme.typography.labelMedium) },
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

              if (showCategorySortRow) {
                LazyRow(
                  contentPadding = PaddingValues(horizontal = 20.dp),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  items(CategorySortMode.entries, key = { it.name }) { sortMode ->
                    FilterChip(
                      selected = uiState.selectedCategorySort == sortMode,
                      onClick = { onSelectCategorySort(sortMode) },
                      leadingIcon = {
                        Icon(
                          imageVector = Icons.Filled.Sort,
                          contentDescription = null,
                          modifier = Modifier.size(14.dp),
                        )
                      },
                      label = { Text(sortMode.label, style = MaterialTheme.typography.labelMedium) },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                          selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                          containerColor = MaterialTheme.colorScheme.surfaceContainer,
                          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                      modifier = Modifier.testTag("category_sort_${sortMode.name}"),
                    )
                  }
                }
              }
            }
          }
        }

        when {
          isRankingsMode -> {
            item {
              Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Text(
                  text =
                    if (uiState.selectedGenre == "All") "Top Chart Rankings"
                    else "${uiState.selectedGenre} Rankings",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                  text = "Verified cinema & anime charts (${uiState.selectedCategorySort.label})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            if (uiState.isLoadingCategory && filteredMovies.isEmpty()) {
              item {
                CinemaCompactGridSkeleton(
                  columns = gridColumns,
                  rows = 2,
                  modifier = Modifier.padding(horizontal = 20.dp),
                )
              }
            } else {
              itemsIndexed(filteredMovies, key = { idx, m -> "rank_${idx}_${m.id}" }) { index, movie ->
                val effectiveRank =
                  if (uiState.selectedGenre == "All" && uiState.selectedCategorySort == CategorySortMode.FEATURED) {
                    movie.rankPosition ?: (index + 1)
                  } else {
                    index + 1
                  }
                RankedChartCard(
                  rank = effectiveRank,
                  movie = movie,
                  savedEntry = savedLibraryMap[movie.id],
                  onOpen = { onOpenMovie(movie) },
                  onPlay = { onPlayMovie(movie) },
                  onToggleWatchlist = { onToggleWatchlist(movie) },
                  modifier = Modifier.padding(horizontal = 20.dp),
                )
              }
            }
          }

          isGridMode -> {
            val isLoadingSection =
              (isAnimeMode && uiState.isLoadingAnime) || (!isAnimeMode && uiState.isLoadingCategory)

            item {
              val headerTitle =
                if (isAnimeMode) {
                  buildString {
                    append("Anime • ${uiState.selectedAnimeSort.label}")
                    if (uiState.selectedAnimeGenre != "All") append(" • ${uiState.selectedAnimeGenre}")
                    append(" (${filteredMovies.size})")
                  }
                } else {
                  buildString {
                    if (uiState.selectedGenre != "All") append(uiState.selectedGenre)
                    else append(uiState.selectedMediaFilter.label)
                    if (uiState.selectedMediaFilter != MediaFilter.ALL && uiState.selectedGenre != "All") {
                      append(" • ${uiState.selectedMediaFilter.label}")
                    }
                    append(" • ${uiState.selectedCategorySort.label} (${filteredMovies.size})")
                  }
                }
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = headerTitle,
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onBackground,
                  modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (isLoadingSection) {
                  LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                  )
                }
              }
            }

            if (isLoadingSection && filteredMovies.isEmpty()) {
              item {
                CinemaCompactGridSkeleton(
                  columns = gridColumns,
                  rows = 3,
                  modifier = Modifier.padding(horizontal = 20.dp),
                )
              }
            } else {
              items(
                filteredMovies.chunked(gridColumns),
                key = { row -> row.joinToString("_") { it.id } },
              ) { rowMovies ->
                Row(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  for (movie in rowMovies) {
                    val rankBadge =
                      when {
                        isAnimeMode && uiState.selectedAnimeSort == AnimeSortMode.TOP_RANKED ->
                          movie.rankPosition ?: (filteredMovies.indexOfFirst { it.id == movie.id } + 1)
                        !isAnimeMode && uiState.selectedCategorySort == CategorySortMode.TOP_RANKED ->
                          filteredMovies.indexOfFirst { it.id == movie.id } + 1
                        else -> null
                      }
                    MoviePosterCard(
                      movie = movie,
                      savedEntry = savedLibraryMap[movie.id],
                      onClick = { onOpenMovie(movie) },
                      onToggleWatchlist = { onToggleWatchlist(movie) },
                      rankOverride = rankBadge,
                      compact = true,
                      modifier = Modifier.weight(1f),
                    )
                  }
                  repeat(gridColumns - rowMovies.size) {
                    Spacer(modifier = Modifier.weight(1f))
                  }
                }
              }
            }

            if (
              uiState.selectedMediaFilter == MediaFilter.MOVIES ||
                uiState.selectedMediaFilter == MediaFilter.SERIES ||
                uiState.selectedMediaFilter == MediaFilter.ANIME ||
                uiState.selectedGenre != "All"
            ) {
              item {
                Box(
                  modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
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
                    Text(
                      if (uiState.selectedGenre != "All" && !isAnimeMode)
                        "Load More ${uiState.selectedGenre}"
                      else "Load More ${uiState.selectedMediaFilter.label}"
                    )
                  }
                }
              }
            }
          }

          else -> {
            if (continueWatching.isNotEmpty()) {
              item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                      text = "Continue Watching",
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                      color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                      text = "Resume right where you left off",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    items(continueWatching, key = { "cw_${it.movieId}" }) { cw ->
                      ContinueWatchingCard(
                        entry = cw,
                        onResume = { onResumeWatching(cw) },
                        onRemove = { onRemoveContinueWatching(cw.movieId) },
                      )
                    }
                  }
                }
              }
            }

            if (uiState.rankings.isNotEmpty()) {
              item {
                DiscoverHorizontalSection(
                  title = "Top 10 Rankings",
                  subtitle = "Trending cinema worldwide right now",
                  movies = uiState.rankings.take(15),
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
                  subtitle = "Supernatural, adventure & top anime",
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
  }
}

private fun matchesCategory(movie: Movie, category: String): Boolean {
  if (category.equals("All", ignoreCase = true)) return true
  if (category.equals("Anime", ignoreCase = true)) {
    return movie.mediaType.equals("Anime", ignoreCase = true) ||
      movie.genre.contains("Anime", ignoreCase = true)
  }
  return movie.genre.contains(category, ignoreCase = true) ||
    movie.synopsis.contains(category, ignoreCase = true)
}

private fun applyUiSort(movies: List<Movie>, sortMode: CategorySortMode): List<Movie> =
  when (sortMode) {
    CategorySortMode.FEATURED -> movies
    CategorySortMode.TOP_RANKED ->
      movies.sortedWith(
        compareBy<Movie> { it.rankPosition ?: Int.MAX_VALUE }
          .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
          .thenByDescending { if (it.subjectId.isNotBlank()) 1 else 0 }
      )
    CategorySortMode.HIGHEST_RATED ->
      movies.sortedWith(
        compareByDescending<Movie> { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
          .thenBy { it.rankPosition ?: Int.MAX_VALUE }
      )
    CategorySortMode.NEWEST ->
      movies.sortedWith(
        compareByDescending<Movie> { it.releaseYear.toIntOrNull() ?: 0 }
          .thenByDescending { it.releaseDateLabel }
      )
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
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      itemsIndexed(movies, key = { idx, m -> "${keyPrefix}_${idx}_${m.id}" }) { index, movie ->
        MoviePosterCard(
          movie = movie,
          savedEntry = savedLibraryMap[movie.id],
          onClick = { onOpenMovie(movie) },
          onToggleWatchlist = { onToggleWatchlist(movie) },
          rankOverride = if (showRankBadge) (movie.rankPosition ?: (index + 1)) else null,
          compact = true,
          modifier = Modifier.width(112.dp),
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
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        color =
          if (rank <= 3) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.size(34.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color =
              if (rank <= 3) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      MoviePosterImage(
        imageUrl = movie.posterUrl,
        contentDescription = movie.title,
        modifier = Modifier.width(52.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small),
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
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
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "IMDb ${movie.imdbRating}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
          Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Watch now")
        }
        IconButton(onClick = onToggleWatchlist, modifier = Modifier.size(40.dp)) {
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

@Composable
private fun ContinueWatchingCard(
  entry: ContinueWatchingEntry,
  onResume: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.width(172.dp)
        .clip(MaterialTheme.shapes.large)
        .clickable(onClick = onResume)
        .testTag("continue_watching_${entry.movieId}"),
  ) {
    Column {
      Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        MoviePosterImage(
          imageUrl = entry.posterUrl,
          contentDescription = entry.title,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = 0.28f)),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            shape = CircleShape,
            modifier = Modifier.size(34.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Resume",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
        IconButton(
          onClick = onRemove,
          modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Remove from continue watching",
            tint = Color.White,
            modifier = Modifier.size(15.dp),
          )
        }
      }
      LinearProgressIndicator(
        progress = { entry.progressFraction },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      )
      Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = entry.title,
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val sub =
          if (entry.season > 0 && entry.episode > 0) "S${entry.season}E${entry.episode} • Tap to resume"
          else "${(entry.progressFraction * 100).toInt()}% watched"
        Text(
          text = sub,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
        )
      }
    }
  }
}
