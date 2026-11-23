package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.remote.moviebox.MovieBoxSuggestion
import com.example.ui.components.CinemaCompactGridSkeleton
import com.example.ui.components.MoviePosterCard
import com.example.ui.viewmodel.MovieSortOption
import com.example.ui.viewmodel.SearchUiState

@Composable
fun SearchScreen(
  uiState: SearchUiState,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  onQueryChange: (String) -> Unit,
  onSubmitSearch: () -> Unit,
  onSelectSuggestion: (MovieBoxSuggestion) -> Unit,
  onSelectGenre: (String) -> Unit,
  onSelectSort: (MovieSortOption) -> Unit,
  onOpenMovie: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  modifier: Modifier = Modifier,
  onSubmitKeyword: (String) -> Unit = {
    onQueryChange(it)
    onSubmitSearch()
  },
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  fun triggerSearch() {
    keyboardController?.hide()
    focusManager.clearFocus()
    onSubmitSearch()
  }

  val genres =
    remember(uiState.results) {
      listOf("All") +
        uiState.results.map { it.genre }.filter { it.isNotBlank() }.distinct().sorted().take(8)
    }

  val displayedMovies =
    remember(uiState.results, uiState.selectedGenre, uiState.sortOption) {
      val genreFiltered =
        if (uiState.selectedGenre == "All") uiState.results
        else uiState.results.filter { it.genre.contains(uiState.selectedGenre, ignoreCase = true) }

      when (uiState.sortOption) {
        MovieSortOption.FEATURED -> genreFiltered
        MovieSortOption.NEWEST -> genreFiltered.sortedByDescending { it.releaseYear }
        MovieSortOption.RUNTIME ->
          genreFiltered.sortedBy { if (it.runtimeMinutes > 0) it.runtimeMinutes else Int.MAX_VALUE }
      }
    }

  Column(modifier = modifier.fillMaxSize().padding(top = 10.dp)) {
    Column(
      modifier = Modifier.padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Explore Catalog",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = uiState.query,
          onValueChange = onQueryChange,
          placeholder = { Text(stringResource(R.string.search_placeholder)) },
          leadingIcon = {
            IconButton(
              onClick = { triggerSearch() },
              modifier = Modifier.testTag("search_leading_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          },
          trailingIcon = {
            if (uiState.query.isNotEmpty()) {
              IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.testTag("search_clear_button"),
              ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.large,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { triggerSearch() }),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
          modifier = Modifier.weight(1f).testTag("movie_search_input"),
        )

        FilledIconButton(
          onClick = { triggerSearch() },
          colors =
            IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.size(54.dp).testTag("search_submit_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search catalog",
          )
        }
      }

      if (uiState.suggestions.isNotEmpty() && uiState.query.isNotBlank()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(uiState.suggestions, key = { "${it.subjectId}_${it.title}" }) { sug ->
            AssistChip(
              onClick = { onSelectSuggestion(sug) },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
              label = { Text(sug.title, style = MaterialTheme.typography.labelMedium) },
              modifier = Modifier.testTag("search_suggestion_${sug.subjectId}"),
            )
          }
        }
      } else if (uiState.query.isBlank() && uiState.trendingKeywords.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(uiState.trendingKeywords, key = { it }) { keyword ->
            AssistChip(
              onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onSubmitKeyword(keyword)
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Filled.TrendingUp,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
              label = { Text(keyword, style = MaterialTheme.typography.labelMedium) },
              modifier = Modifier.testTag("trending_keyword_$keyword"),
            )
          }
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MovieSortOption.entries.forEach { option ->
          FilterChip(
            selected = uiState.sortOption == option,
            onClick = { onSelectSort(option) },
            label = { Text(option.label, style = MaterialTheme.typography.labelMedium) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          )
        }
      }
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(genres, key = { it }) { genre ->
        FilterChip(
          selected = genre == uiState.selectedGenre,
          onClick = { onSelectGenre(genre) },
          label = { Text(genre, style = MaterialTheme.typography.labelMedium) },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
      }
    }

    if (uiState.isLoading) {
      androidx.compose.material3.LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      )
    }

    when {
      uiState.isLoading && displayedMovies.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
          CinemaCompactGridSkeleton(columns = 3, rows = 3)
        }
      }
      displayedMovies.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.MovieFilter,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = "No matching films found",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Try another title or tap Search again to query live releases.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { triggerSearch() },
                modifier = Modifier.height(48.dp),
              ) { Text("Search Again") }
              if (uiState.query.isNotBlank() || uiState.selectedGenre != "All") {
                OutlinedButton(
                  onClick = {
                    onSelectGenre("All")
                    onQueryChange("")
                  },
                  modifier = Modifier.height(48.dp),
                ) { Text("Reset") }
              }
            }
          }
        }
      }
      else -> {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 108.dp),
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier = Modifier.fillMaxSize().testTag("search_results_grid"),
        ) {
          items(displayedMovies, key = { it.id }) { movie ->
            MoviePosterCard(
              movie = movie,
              savedEntry = savedLibraryMap[movie.id],
              onClick = { onOpenMovie(movie) },
              onToggleWatchlist = { onToggleWatchlist(movie) },
              compact = true,
            )
          }
        }
      }
    }
  }
}
