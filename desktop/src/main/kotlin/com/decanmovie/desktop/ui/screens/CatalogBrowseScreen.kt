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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.data.remote.JikanTvMazeService
import com.decanmovie.desktop.model.AnimeSortMode
import com.decanmovie.desktop.model.CategorySortMode
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.ui.components.DesktopMediaCard
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import com.decanmovie.desktop.viewmodel.CatalogSectionState

enum class CatalogBrowseKind {
  MOVIES,
  SERIES,
  ANIME,
  DRAMAS,
}

private val VIDEO_GENRE_FILTERS =
  listOf("All", "Action", "Adventure", "Comedy", "Crime", "Drama", "Fantasy", "Horror", "Romance", "Sci-Fi", "Thriller")

private val DRAMA_REGION_FILTERS = listOf("All", "K-Drama", "C-Drama", "J-Drama", "Western")

@Composable
fun CatalogBrowseScreen(
  kind: CatalogBrowseKind,
  state: CatalogSectionState,
  watchlistIds: Set<String>,
  compactCards: Boolean,
  onSelectFilter: (String) -> Unit,
  onSelectCategorySort: (CategorySortMode) -> Unit,
  onSelectAnimeSort: (AnimeSortMode) -> Unit,
  onRefresh: () -> Unit,
  onLoadMore: () -> Unit,
  onMovieClick: (Movie) -> Unit,
  onQuickPlay: (Movie) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
) {
  val title =
    when (kind) {
      CatalogBrowseKind.MOVIES -> "Movies"
      CatalogBrowseKind.SERIES -> "TV Series"
      CatalogBrowseKind.ANIME -> "Anime"
      CatalogBrowseKind.DRAMAS -> "Dramas"
    }
  val subtitle =
    when (kind) {
      CatalogBrowseKind.MOVIES -> "Feature films streamed in Desktop Theater Mode"
      CatalogBrowseKind.SERIES -> "Multi-season series with quick episode switching"
      CatalogBrowseKind.ANIME -> "Curated anime series and movies ready to stream"
      CatalogBrowseKind.DRAMAS -> "K-Dramas, C-Dramas & short dramas — launches in TikTok-style Vertical Drama Mode"
    }

  val filterChips =
    when (kind) {
      CatalogBrowseKind.ANIME -> JikanTvMazeService.ANIME_GENRES.map { it.label }
      CatalogBrowseKind.DRAMAS -> DRAMA_REGION_FILTERS
      else -> VIDEO_GENRE_FILTERS
    }

  val minCellWidth = if (compactCards) 156.dp else 182.dp

  Column(
    modifier = Modifier.fillMaxSize().testTag("catalog_${kind.name.lowercase()}"),
  ) {
    // Header + Filters
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.headlineMedium,
              color = DecanDesktopColors.Mist,
            )
            if (kind == CatalogBrowseKind.DRAMAS) {
              Surface(
                color = DecanDesktopColors.CrimsonContainer,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DecanDesktopColors.Crimson),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = DecanDesktopColors.CrimsonBright,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = "Vertical Swipe Player",
                    style = MaterialTheme.typography.labelMedium,
                    color = DecanDesktopColors.CrimsonOnContainer,
                  )
                }
              }
            }
          }
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = DecanDesktopColors.Slate,
          )
        }

        OutlinedButton(
          onClick = onRefresh,
          border = BorderStroke(1.dp, DecanDesktopColors.Outline),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = DecanDesktopColors.Mist),
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Refresh")
        }
      }

      // Genre / Region filter row
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        filterChips.forEach { label ->
          FilterPillChip(
            label = label,
            selected = state.selectedGenre.equals(label, ignoreCase = true),
            onClick = { onSelectFilter(label) },
          )
        }
      }

      // Sort row
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Sort:",
          style = MaterialTheme.typography.labelMedium,
          color = DecanDesktopColors.Slate,
          modifier = Modifier.padding(end = 4.dp),
        )
        if (kind == CatalogBrowseKind.ANIME) {
          AnimeSortMode.entries.forEach { mode ->
            FilterPillChip(
              label = mode.label,
              selected = state.animeSortMode == mode,
              accentSky = true,
              onClick = { onSelectAnimeSort(mode) },
            )
          }
        } else {
          CategorySortMode.entries.forEach { mode ->
            FilterPillChip(
              label = mode.label,
              selected = state.sortMode == mode,
              accentSky = true,
              onClick = { onSelectCategorySort(mode) },
            )
          }
        }
      }
    }

    if (state.isLoading && state.items.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DecanDesktopColors.Crimson)
      }
    } else if (state.items.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "No $title titles matched this filter.",
            style = MaterialTheme.typography.titleMedium,
            color = DecanDesktopColors.Mist,
          )
          OutlinedButton(onClick = { onSelectFilter("All") }) {
            Text("Reset Filters")
          }
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellWidth),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(state.items, key = { it.id }) { movie ->
          DesktopMediaCard(
            movie = movie,
            onClick = { onMovieClick(movie) },
            onQuickPlay = { onQuickPlay(movie) },
            isInWatchlist = movie.id in watchlistIds,
            onToggleWatchlist = { onToggleWatchlist(movie) },
            cardWidth = minCellWidth,
          )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
          ) {
            if (state.isLoadingMore) {
              CircularProgressIndicator(
                color = DecanDesktopColors.Crimson,
                modifier = Modifier.size(28.dp),
              )
            } else {
              OutlinedButton(
                onClick = onLoadMore,
                border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DecanDesktopColors.Mist),
                modifier = Modifier.testTag("catalog_load_more"),
              ) {
                Text("Load More $title")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun FilterPillChip(
  label: String,
  selected: Boolean,
  accentSky: Boolean = false,
  onClick: () -> Unit,
) {
  val selectedColor = if (accentSky) DecanDesktopColors.SkyContainer else DecanDesktopColors.Crimson
  val selectedBorder =
    if (accentSky) DecanDesktopColors.SkyAccent else DecanDesktopColors.CrimsonBright

  Surface(
    color = if (selected) selectedColor else DecanDesktopColors.SurfaceElevated,
    shape = RoundedCornerShape(999.dp),
    border =
      BorderStroke(
        width = 1.dp,
        color = if (selected) selectedBorder else DecanDesktopColors.Outline,
      ),
    modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
  ) {
    Text(
      text = label,
      style =
        MaterialTheme.typography.labelMedium.copy(
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        ),
      color = if (selected) Color.White else DecanDesktopColors.Slate,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
    )
  }
}
