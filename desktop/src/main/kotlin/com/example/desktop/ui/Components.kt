package com.example.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Movie
import kotlinx.coroutines.launch

@Composable
fun PosterCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.clickable(onClick = onClick)) {
    Box(
      Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
      RemoteImage(movie.posterUrl.ifBlank { movie.backdropUrl }, Modifier.fillMaxSize(), contentDescription = movie.title)
      movie.rankPosition?.let { rank ->
        Text(
          "#$rank",
          Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
      movie.imdbRating?.takeIf { it.isNotBlank() }?.let { r ->
        Row(
          Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)).padding(horizontal = 5.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Star, null, Modifier.width(12.dp), tint = MaterialTheme.colorScheme.primary)
          Text(" $r", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
    Spacer(Modifier.padding(top = 6.dp))
    Text(movie.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    Text(
      listOf(movie.releaseYear, movie.mediaType).filter { it.isNotBlank() }.joinToString(" • "),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

/** Horizontal poster rail with left/right buttons (mouse wheels don't scroll LazyRows on desktop). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieRail(title: String, movies: List<Movie>, onClick: (Movie) -> Unit, subtitle: String? = null) {
  if (movies.isEmpty()) return
  val state = rememberLazyListState()
  val scope = rememberCoroutineScope()
  Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        SectionTitle(title)
        if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      IconButton({ scope.launch { state.animateScrollBy(-700f) } }) { Icon(Icons.Filled.KeyboardArrowLeft, "Scroll left") }
      IconButton({ scope.launch { state.animateScrollBy(700f) } }) { Icon(Icons.Filled.KeyboardArrowRight, "Scroll right") }
    }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      items(movies, key = { it.id }) { m -> PosterCard(m, { onClick(m) }, Modifier.width(150.dp)) }
    }
  }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
  Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
  }
}

fun formatTime(ms: Long): String {
  val total = (ms / 1000).coerceAtLeast(0)
  val h = total / 3600
  val m = (total % 3600) / 60
  val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String =
  when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
  }
