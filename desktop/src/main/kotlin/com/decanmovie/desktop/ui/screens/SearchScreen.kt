package com.decanmovie.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.ui.components.DesktopMediaCard
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import com.decanmovie.desktop.viewmodel.DesktopSearchState

private val SEARCH_MEDIA_FILTERS = listOf("All", "Movies", "Series", "Anime", "Dramas")

@Composable
fun SearchScreen(
  state: DesktopSearchState,
  trendingKeywords: List<String>,
  watchlistIds: Set<String>,
  compactCards: Boolean,
  onQueryChange: (String) -> Unit,
  onSearchSubmit: () -> Unit,
  onFilterChange: (String) -> Unit,
  onMovieClick: (Movie) -> Unit,
  onQuickPlay: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
) {
  val filteredResults =
    remember(state.results, state.mediaFilter) {
      when (state.mediaFilter) {
        "Movies" -> state.results.filter { it.mediaType.equals("Movie", true) }
        "Series" ->
          state.results.filter {
            it.mediaType.equals("Series", true) && !DramaExperienceDetector.isEpisodicDrama(it)
          }
        "Anime" -> state.results.filter { it.mediaType.equals("Anime", true) }
        "Dramas" ->
          state.results.filter {
            it.mediaType.equals("Drama", true) || DramaExperienceDetector.isEpisodicDrama(it)
          }
        else -> state.results
      }
    }

  val minCellWidth = if (compactCards) 156.dp else 182.dp

  Column(
    modifier = Modifier.fillMaxSize().testTag("search_screen"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Search Catalog",
        style = MaterialTheme.typography.headlineMedium,
        color = DecanDesktopColors.Mist,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = DecanDesktopColors.SurfaceElevated,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
          modifier = Modifier.weight(1f).height(46.dp).testTag("search_screen_input"),
        ) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = null,
              tint = DecanDesktopColors.Slate,
              modifier = Modifier.size(20.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
              if (state.query.isEmpty()) {
                Text(
                  text = "Search movies, series, anime, K-dramas, actors, or IDs…",
                  style = MaterialTheme.typography.bodyMedium,
                  color = DecanDesktopColors.Slate.copy(alpha = 0.7f),
                )
              }
              BasicTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle =
                  MaterialTheme.typography.bodyMedium.copy(color = DecanDesktopColors.Mist),
                cursorBrush = SolidColor(DecanDesktopColors.Crimson),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                modifier = Modifier.fillMaxWidth(),
              )
            }
            if (state.query.isNotEmpty()) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                tint = DecanDesktopColors.Slate,
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
              )
            }
          }
        }

        Button(
          onClick = onSearchSubmit,
          colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(46.dp).testTag("search_submit_button"),
        ) {
          Text("Search", fontWeight = FontWeight.Bold)
        }
      }

      // Suggestions or Trending Keywords
      val chips =
        if (state.suggestions.isNotEmpty()) state.suggestions
        else trendingKeywords
      if (chips.isNotEmpty()) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (state.suggestions.isNotEmpty()) "Suggestions:" else "Trending:",
            style = MaterialTheme.typography.labelMedium,
            color = DecanDesktopColors.Slate,
          )
          chips.take(10).forEach { keyword ->
            Surface(
              color = DecanDesktopColors.SurfaceGlass,
              shape = RoundedCornerShape(999.dp),
              border = BorderStroke(1.dp, DecanDesktopColors.Outline),
              modifier =
                Modifier.clip(RoundedCornerShape(999.dp)).clickable {
                  onQueryChange(keyword)
                  onSearchSubmit()
                },
            ) {
              Text(
                text = keyword,
                style = MaterialTheme.typography.labelMedium,
                color = DecanDesktopColors.Mist,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      // Media Category Filter Tabs
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        SEARCH_MEDIA_FILTERS.forEach { cat ->
          FilterPillChip(
            label = cat,
            selected = state.mediaFilter == cat,
            onClick = { onFilterChange(cat) },
          )
        }
      }
    }

    when {
      state.isSearching -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = DecanDesktopColors.Crimson)
        }
      }
      state.query.isBlank() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = null,
              tint = DecanDesktopColors.Slate.copy(alpha = 0.5f),
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = "Type a title or click a trending chip to search across Movies, Series, Anime & Dramas.",
              style = MaterialTheme.typography.bodyMedium,
              color = DecanDesktopColors.Slate,
            )
          }
        }
      }
      filteredResults.isEmpty() -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = "No results found for \"${state.query}\" in ${state.mediaFilter}.",
            style = MaterialTheme.typography.titleMedium,
            color = DecanDesktopColors.Slate,
          )
        }
      }
      else -> {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = minCellWidth),
          contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(filteredResults, key = { it.id }) { movie ->
            DesktopMediaCard(
              movie = movie,
              onClick = { onMovieClick(movie) },
              onQuickPlay = { onQuickPlay(movie) },
              isInWatchlist = movie.id in watchlistIds,
              onToggleWatchlist = { onToggleWatchlist(movie) },
              cardWidth = minCellWidth,
            )
          }
        }
      }
    }
  }
}
