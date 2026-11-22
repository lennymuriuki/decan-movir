package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.remote.moviebox.MovieBoxSeason

internal const val EPISODE_PAGE_SIZE = 30

internal fun calculateEpisodeRanges(
  totalEpisodes: Int,
  pageSize: Int = EPISODE_PAGE_SIZE,
): List<IntRange> {
  if (totalEpisodes <= 0) return emptyList()
  val safePageSize = pageSize.coerceAtLeast(1)
  val ranges = mutableListOf<IntRange>()
  var start = 1
  while (start <= totalEpisodes) {
    val end = minOf(start + safePageSize - 1, totalEpisodes)
    ranges.add(start..end)
    start = end + 1
  }
  return ranges
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeNumberGrid(
  seasons: List<MovieBoxSeason>,
  selectedSeason: Int,
  selectedEpisode: Int,
  onSelectSeason: (Int) -> Unit,
  onSelectEpisode: (season: Int, episode: Int) -> Unit,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onActionClick: (() -> Unit)? = null,
) {
  if (seasons.isEmpty()) return
  val activeSeasonObj = seasons.firstOrNull { it.season == selectedSeason } ?: seasons.first()
  val activeSeason = activeSeasonObj.season
  val totalEpisodes = activeSeasonObj.episodeCount.coerceAtLeast(1)
  val ranges = remember(totalEpisodes) { calculateEpisodeRanges(totalEpisodes) }

  var selectedRangeIndex by remember(activeSeason, totalEpisodes) {
    val initialIdx =
      ranges.indexOfFirst { selectedEpisode in it }.takeIf { it >= 0 } ?: 0
    mutableIntStateOf(initialIdx)
  }

  LaunchedEffect(selectedEpisode, activeSeason, totalEpisodes) {
    val idx = ranges.indexOfFirst { selectedEpisode in it }
    if (idx >= 0) selectedRangeIndex = idx
  }

  val visibleRange = ranges.getOrElse(selectedRangeIndex) { 1..totalEpisodes }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth().testTag("episode_number_grid"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Episodes • S$activeSeason ($totalEpisodes)",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
          TextButton(onClick = onActionClick) { Text(actionLabel) }
        }
      }

      if (seasons.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(seasons, key = { it.season }) { seasonObj ->
            FilterChip(
              selected = seasonObj.season == activeSeason,
              onClick = { onSelectSeason(seasonObj.season) },
              label = { Text("S${seasonObj.season}") },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.testTag("season_chip_${seasonObj.season}"),
            )
          }
        }
      }

      if (ranges.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(ranges.indices.toList(), key = { it }) { idx ->
            val r = ranges[idx]
            FilterChip(
              selected = idx == selectedRangeIndex,
              onClick = { selectedRangeIndex = idx },
              label = { Text("${r.first}–${r.last}") },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
              modifier = Modifier.testTag("episode_range_${r.first}_${r.last}"),
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        for (ep in visibleRange) {
          val isSelected = ep == selectedEpisode
          Surface(
            color =
              if (isSelected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            border =
              if (isSelected) null
              else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier =
              Modifier.widthIn(min = 52.dp)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onSelectEpisode(activeSeason, ep) }
                .testTag("episode_btn_$ep"),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
              Text(
                text = ep.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color =
                  if (isSelected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
}
