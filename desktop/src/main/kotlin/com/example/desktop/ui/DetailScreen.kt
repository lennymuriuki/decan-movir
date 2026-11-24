package com.example.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Movie
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.desktop.AppState
import com.example.desktop.PlayRequest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(app: AppState, initial: Movie) {
  var movie by remember(initial.id) { mutableStateOf(initial) }
  var seasons by remember(initial.id) { mutableStateOf<List<MovieBoxSeason>>(emptyList()) }
  var recs by remember(initial.id) { mutableStateOf<List<Movie>>(emptyList()) }
  var loading by remember(initial.id) { mutableStateOf(true) }
  var season by remember(initial.id) { mutableStateOf(0) }
  var episode by remember(initial.id) { mutableStateOf(1) }
  var seasonMenu by remember { mutableStateOf(false) }
  var showDownload by remember { mutableStateOf(false) }

  LaunchedEffect(initial.id) {
    loading = true
    val base = runCatching { app.repo.resolvePlayableSubject(initial) }.getOrNull() ?: initial
    movie = base
    if (base.subjectId.isNotBlank()) {
      app.repo.getMovieBoxRichDetail(base.subjectId)?.let { rich ->
        movie = base.copy(
          synopsis = rich.movie.synopsis.ifBlank { base.synopsis },
          backdropUrl = rich.movie.backdropUrl.ifBlank { base.backdropUrl },
          genre = rich.movie.genre.ifBlank { base.genre },
          runtimeMinutes = if (rich.movie.runtimeMinutes > 0) rich.movie.runtimeMinutes else base.runtimeMinutes,
          detailPath = rich.detailPath.ifBlank { base.detailPath },
          imdbRating = rich.movie.imdbRating ?: base.imdbRating,
        )
        seasons = rich.seasons
        rich.seasons.firstOrNull()?.let { season = it.season }
      }
      recs = app.repo.getRecommendations(base.subjectId, movie.genre).filter { it.id != movie.id }
    }
    loading = false
  }

  val isSeries = seasons.isNotEmpty()
  val episodeCount = seasons.firstOrNull { it.season == season }?.episodeCount ?: 0
  val canPlay = movie.subjectId.isNotBlank()
  val playSeason = if (isSeries) season else 0
  val playEpisode = if (isSeries) episode else 0

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
      RemoteImage(movie.backdropUrl.ifBlank { movie.posterUrl }, Modifier.fillMaxSize(), ContentScale.Crop)
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x440A0E17), MaterialTheme.colorScheme.background))))
      IconButton(app::back, Modifier.padding(12.dp).background(Color(0x88000000), RoundedCornerShape(50))) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
      }
    }
    Row(Modifier.padding(horizontal = 32.dp).offset(y = (-90).dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
      RemoteImage(movie.posterUrl, Modifier.width(200.dp).height(300.dp).clip(RoundedCornerShape(12.dp)))
      Column(Modifier.weight(1f).padding(top = 60.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(movie.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
          listOf(movie.releaseYear, movie.mediaType, movie.genre, movie.runtimeMinutes.takeIf { it > 0 }?.let { "${it} min" }, movie.imdbRating?.let { "IMDb $it" }, movie.country)
            .filterNotNull().filter { it.isNotBlank() }.joinToString(" • "),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button({ app.play(PlayRequest(movie.title, movie, playSeason, playEpisode)) }, enabled = canPlay) {
            Icon(Icons.Filled.PlayArrow, null)
            Text(if (isSeries) " Play S$season E$episode" else " Play")
          }
          FilledTonalButton({ showDownload = true }, enabled = canPlay) { Text("⬇ Download") }
          val saved = app.store.isSaved(movie.id)
          OutlinedButton({ app.store.toggleSaved(movie) }) {
            Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null)
            Text(if (saved) " In watchlist" else " Add to watchlist")
          }
        }
        if (loading) Text("Loading details…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (!canPlay) Text("This title has no streamable source right now.", color = MaterialTheme.colorScheme.error)
        Text(movie.synopsis, style = MaterialTheme.typography.bodyLarge)
      }
    }

    if (isSeries) {
      Column(Modifier.padding(horizontal = 32.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          SectionTitle("Episodes")
          Box {
            OutlinedButton({ seasonMenu = true }) { Text("Season $season ▾") }
            DropdownMenu(seasonMenu, { seasonMenu = false }) {
              seasons.forEach { s -> DropdownMenuItem({ Text("Season ${s.season} (${s.episodeCount} eps)") }, { season = s.season; episode = 1; seasonMenu = false }) }
            }
          }
          if (episodeCount > 0) TextButton({ app.downloadSeason(movie, season, episodeCount) }) { Text("⬇ Download whole season") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          (1..episodeCount).forEach { ep ->
            FilterChip(ep == episode, { episode = ep }, { Text("$ep") })
          }
        }
      }
    }

    Column(Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) { MovieRail("More like this", recs, app::openMovie) }
    Spacer(Modifier.height(32.dp))
  }

  if (showDownload) {
    DownloadDialog(app, movie, playSeason, playEpisode, if (isSeries) "S${season}E${episode.toString().padStart(2, '0')}" else "") { showDownload = false }
  }
}

@Composable
private fun DownloadDialog(app: AppState, movie: Movie, season: Int, episode: Int, label: String, onClose: () -> Unit) {
  var streams by remember { mutableStateOf<List<MovieBoxStream>?>(null) }
  var subs by remember { mutableStateOf<List<MovieBoxSubtitle>>(emptyList()) }
  var withSubs by remember { mutableStateOf(true) }

  LaunchedEffect(movie.subjectId, season, episode) {
    streams = app.repo.getStreams(movie.subjectId, season, episode, movie.detailPath).filter { it.isPlayableDirect && it.url.isNotBlank() }.sortedByDescending { it.qualityValue }
    subs = app.repo.getSubtitles(movie.subjectId, season, episode)
  }
  val sub = subs.firstOrNull { it.code.startsWith("en", true) || it.language.contains("English", true) } ?: subs.firstOrNull()

  AlertDialog(
    onDismissRequest = onClose,
    title = { Text("Download ${movie.title} $label".trim()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val list = streams
        when {
          list == null -> Text("Finding available qualities…")
          list.isEmpty() -> Text("No downloadable streams were found.")
          else -> list.forEach { st ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text("${st.resolutionTag.ifBlank { st.quality }}  •  ${"%.0f".format(st.sizeMb)} MB", Modifier.weight(1f))
              Button({
                app.downloads.enqueue(movie.title, st, if (withSubs) sub else null, label)
                app.toast = "Added to Downloads"
                onClose()
              }) { Text("Download") }
            }
          }
        }
        if (sub != null) Row(verticalAlignment = Alignment.CenterVertically) {
          androidx.compose.material3.Checkbox(withSubs, { withSubs = it })
          Text("Include ${sub.language} subtitles (.srt)")
        }
      }
    },
    confirmButton = { TextButton(onClose) { Text("Close") } },
  )
}
