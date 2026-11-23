package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun rememberCinemaShimmerBrush(): Brush {
  val transition = rememberInfiniteTransition(label = "cinema_shimmer")
  val shift by
    transition.animateFloat(
      initialValue = -400f,
      targetValue = 1200f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "shimmer_offset",
    )

  val base = MaterialTheme.colorScheme.surfaceContainerHigh
  val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
  val mid = MaterialTheme.colorScheme.surfaceContainerHighest

  return Brush.linearGradient(
    colors = listOf(base, mid, highlight, mid, base),
    start = Offset(shift, shift * 0.45f),
    end = Offset(shift + 420f, shift * 0.45f + 240f),
  )
}

@Composable
fun CinemaDiscoverLoadingView(
  statusLabel: String = "Curating Live Cinema, Anime & Charts…",
  modifier: Modifier = Modifier,
) {
  val brush = rememberCinemaShimmerBrush()

  Column(
    modifier = modifier.fillMaxSize().padding(top = 12.dp).testTag("cinema_loading_skeleton"),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Top Brand Header Skeleton + Live Tuning Badge
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Decan Movie",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }

    // Search Bar Skeleton
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .height(46.dp)
          .clip(MaterialTheme.shapes.large)
          .background(brush)
    )

    // Hero Spotlight Skeleton
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .height(236.dp)
          .clip(MaterialTheme.shapes.extraLarge)
          .background(brush)
    ) {
      Column(
        modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Box(
          modifier =
            Modifier.width(96.dp).height(18.dp).clip(MaterialTheme.shapes.small).background(brush)
        )
        Box(
          modifier =
            Modifier.fillMaxWidth(0.7f)
              .height(26.dp)
              .clip(MaterialTheme.shapes.small)
              .background(brush)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(
            modifier =
              Modifier.width(104.dp)
                .height(34.dp)
                .clip(MaterialTheme.shapes.large)
                .background(brush)
          )
          Box(
            modifier =
              Modifier.width(96.dp)
                .height(34.dp)
                .clip(MaterialTheme.shapes.large)
                .background(brush)
          )
        }
      }
    }

    // Category Filter Chips Shimmer
    LazyRow(
      contentPadding = PaddingValues(horizontal = 20.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(6) { idx ->
        Box(
          modifier =
            Modifier.width((68 + (idx % 3) * 16).dp)
              .height(32.dp)
              .clip(MaterialTheme.shapes.medium)
              .background(brush)
        )
      }
    }

    // Compact 3-Column Poster Grid Shimmer
    CinemaCompactGridSkeleton(columns = 3, rows = 2, modifier = Modifier.padding(horizontal = 20.dp))
  }
}

@Composable
fun CinemaCompactGridSkeleton(
  columns: Int = 3,
  rows: Int = 2,
  modifier: Modifier = Modifier,
) {
  val brush = rememberCinemaShimmerBrush()
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    repeat(rows) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        repeat(columns) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .aspectRatio(2f / 3f)
                  .clip(MaterialTheme.shapes.medium)
                  .background(brush)
            )
            Box(
              modifier =
                Modifier.fillMaxWidth(0.85f)
                  .height(12.dp)
                  .clip(MaterialTheme.shapes.extraSmall)
                  .background(brush)
            )
            Box(
              modifier =
                Modifier.fillMaxWidth(0.5f)
                  .height(10.dp)
                  .clip(MaterialTheme.shapes.extraSmall)
                  .background(brush)
            )
          }
        }
      }
    }
  }
}

@Composable
fun CinemaInlineRailSkeleton(modifier: Modifier = Modifier) {
  val brush = rememberCinemaShimmerBrush()
  LazyRow(
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items(4) {
      Column(
        modifier = Modifier.width(110.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .aspectRatio(2f / 3f)
              .clip(MaterialTheme.shapes.medium)
              .background(brush)
        )
        Box(
          modifier =
            Modifier.fillMaxWidth(0.8f)
              .height(11.dp)
              .clip(MaterialTheme.shapes.extraSmall)
              .background(brush)
        )
      }
    }
  }
}
