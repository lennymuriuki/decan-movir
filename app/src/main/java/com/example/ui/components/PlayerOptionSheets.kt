package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Movie
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerSettingsSheet(
  streams: List<MovieBoxStream>,
  activeStream: MovieBoxStream?,
  playbackSpeed: Float,
  zoomFill: Boolean,
  volume: Float,
  isMuted: Boolean,
  onSelectStream: (MovieBoxStream) -> Unit,
  onSelectSpeed: (Float) -> Unit,
  onSelectZoomFill: (Boolean) -> Unit,
  onVolumeChange: (Float) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Playback Settings",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (streams.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(imageVector = Icons.Filled.Hd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Video Quality", style = MaterialTheme.typography.titleSmall)
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            streams.filter { it.isPlayableDirect }.forEach { st ->
              val selected = activeStream?.url == st.url
              FilterChip(
                selected = selected,
                onClick = { onSelectStream(st) },
                label = {
                  Text("${st.quality}${if (st.sizeMb > 0) " (${st.sizeMb}MB)" else ""}")
                },
              )
            }
          }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(imageVector = Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Playback Speed", style = MaterialTheme.typography.titleSmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
            FilterChip(
              selected = playbackSpeed == spd,
              onClick = { onSelectSpeed(spd) },
              label = { Text("${spd}x") },
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Text("Audio Volume", style = MaterialTheme.typography.titleSmall)
          }
          Text(
            text = if (isMuted) "Muted" else "${(volume * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = if (isMuted) 0f else volume,
          onValueChange = onVolumeChange,
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Video Scaling", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !zoomFill,
            onClick = { onSelectZoomFill(false) },
            label = { Text("Fit") },
          )
          FilterChip(
            selected = zoomFill,
            onClick = { onSelectZoomFill(true) },
            label = { Text("Zoom Fill") },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSubtitlesSheet(
  subtitles: List<MovieBoxSubtitle>,
  activeSubtitle: MovieBoxSubtitle?,
  onDisableSubtitles: () -> Unit,
  onSelectSubtitle: (MovieBoxSubtitle) -> Unit,
  onDownloadSubtitle: (MovieBoxSubtitle) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Subtitles / Closed Captions (CC)",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Surface(
        color =
          if (activeSubtitle == null) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border =
          if (activeSubtitle == null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
          else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDisableSubtitles),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Subtitles Off", style = MaterialTheme.typography.titleSmall)
          if (activeSubtitle == null) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      subtitles.forEach { sub ->
        val selected = activeSubtitle?.url == sub.url
        Surface(
          color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth().clickable { onSelectSubtitle(sub) },
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(sub.language, style = MaterialTheme.typography.titleSmall)
              Text(
                text = sub.code.uppercase().ifBlank { "SRT" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { onDownloadSubtitle(sub) }) {
                Icon(imageVector = Icons.Filled.Download, contentDescription = "Download subtitle")
              }
              if (selected) {
                Icon(
                  imageVector = Icons.Filled.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }

      if (subtitles.isEmpty()) {
        Text(
          text = "Subtitles unavailable for this stream.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun PlayerRecommendationPosterCard(
  movie: Movie,
  isSaved: Boolean,
  onPlay: () -> Unit,
  onToggleWatchlist: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier =
      modifier
        .clip(MaterialTheme.shapes.large)
        .clickable(onClick = onPlay)
        .testTag("player_rec_card_${movie.id}"),
  ) {
    Column {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      ) {
        MoviePosterImage(
          imageUrl = movie.posterUrl,
          contentDescription = movie.title,
          modifier = Modifier.fillMaxSize(),
        )

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(74.dp)
              .align(Alignment.BottomCenter)
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
              )
        )

        if (!movie.imdbRating.isNullOrBlank()) {
          Surface(
            color = Color.Black.copy(alpha = 0.78f),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
          ) {
            Text(
              text = "★ ${movie.imdbRating}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
          }
        } else {
          Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
          ) {
            Text(
              text = movie.mediaType.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
          }
        }

        FilledTonalIconButton(
          onClick = onToggleWatchlist,
          colors =
            IconButtonDefaults.filledTonalIconButtonColors(
              containerColor =
                if (isSaved) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.58f),
              contentColor =
                if (isSaved) MaterialTheme.colorScheme.onPrimary else Color.White,
            ),
          modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp),
        ) {
          Icon(
            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
            contentDescription = if (isSaved) "Saved" else "Add to watchlist",
            modifier = Modifier.size(16.dp),
          )
        }

        Surface(
          color = MaterialTheme.colorScheme.primary,
          shape = CircleShape,
          modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = "Play ${movie.title}",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Surface(
          color = Color.Black.copy(alpha = 0.7f),
          shape = MaterialTheme.shapes.extraSmall,
          modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        ) {
          Text(
            text = movie.releaseYear,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }

      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Text(
          text = movie.title,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = listOfNotNull(movie.genre, movie.mediaType).joinToString(" • "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = movie.synopsis.ifBlank { "Tap to stream in HD on Decan Theater." },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
