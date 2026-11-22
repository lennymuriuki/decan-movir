package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.ui.components.HeroSpotlightCard
import com.example.ui.components.MoviePosterCard
import com.example.ui.viewmodel.DiscoverUiState

@Composable
fun DiscoverScreen(
  uiState: DiscoverUiState,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  onRefresh: () -> Unit,
  onSelectGenre: (String) -> Unit,
  onOpenMovie: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
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

  val filteredMovies =
    if (uiState.selectedGenre == "All") {
      emptyList()
    } else {
      uiState.allMovies.filter { it.genre.equals(uiState.selectedGenre, ignoreCase = true) }
    }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("discover_list"),
    contentPadding = PaddingValues(bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(22.dp),
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
            )
            Text(
              text = "${uiState.allMovies.size} films • Live cinema feed",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
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

    if (uiState.spotlight.isNotEmpty() && uiState.selectedGenre == "All") {
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

    if (uiState.selectedGenre != "All") {
      item {
        Text(
          text = "${uiState.selectedGenre} Collection (${filteredMovies.size})",
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
    } else {
      items(uiState.rails, key = { it.id }) { rail ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
              text = rail.title,
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = rail.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            items(rail.movies, key = { "${rail.id}_${it.id}" }) { movie ->
              MoviePosterCard(
                movie = movie,
                savedEntry = savedLibraryMap[movie.id],
                onClick = { onOpenMovie(movie) },
                onToggleWatchlist = { onToggleWatchlist(movie) },
                modifier = Modifier.width(142.dp),
              )
            }
          }
        }
      }
    }
  }
}
