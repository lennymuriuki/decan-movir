package com.example.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Movie
import com.example.data.remote.CatalogFeedResult
import com.example.data.remote.moviebox.MovieBoxSuggestion
import com.example.desktop.AppState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(app: AppState) {
  var feed by remember { mutableStateOf<CatalogFeedResult?>(app.repo.getCachedHomeSnapshot()) }
  var loading by remember { mutableStateOf(feed == null) }
  var error by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    runCatching { app.repo.loadHomeCatalog() }
      .onSuccess { feed = it; error = null }
      .onFailure { error = "Could not load the catalog. Check your internet connection." }
    loading = false
  }

  val f = feed
  if (f == null) {
    if (loading) Loading(Modifier.fillMaxSize()) else Message(error ?: "Nothing to show yet.")
    return
  }

  LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
    f.spotlight.firstOrNull()?.let { hero -> item { Hero(hero, app) } }
    if (f.isOfflineFallback) item { Message("Offline mode — showing saved titles.") }
    item { MovieRail("Top Rankings", f.rankings, app::openMovie) }
    f.rails.forEach { r -> item(key = r.id) { MovieRail(r.title, r.movies, app::openMovie, r.subtitle) } }
    item { MovieRail("Anime", f.anime, app::openMovie) }
    item { MovieRail("Recommended for you", f.recommendations, app::openMovie) }
  }
}

@Composable
private fun Hero(movie: Movie, app: AppState) {
  Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(16.dp))) {
    RemoteImage(movie.backdropUrl.ifBlank { movie.posterUrl }, Modifier.fillMaxSize(), ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE60A0E17), Color(0x330A0E17)))))
    Column(Modifier.align(Alignment.CenterStart).padding(32.dp).fillMaxWidth(0.55f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(movie.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        listOf(movie.releaseYear, movie.genre, movie.mediaType).filter { it.isNotBlank() }.joinToString(" • "),
        color = Color(0xFFC8D4EA), style = MaterialTheme.typography.bodyMedium,
      )
      Text(movie.synopsis, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFE2E8F5), style = MaterialTheme.typography.bodyMedium)
      Button({ app.openMovie(movie) }) {
        Icon(Icons.Filled.PlayArrow, null)
        Text(" Watch now")
      }
    }
  }
}

enum class BrowseKind(val title: String) { MOVIES("Movies"), SERIES("TV Series"), ANIME("Anime") }

@Composable
fun BrowseScreen(app: AppState, kind: BrowseKind) {
  var items by remember(kind) { mutableStateOf<List<Movie>>(emptyList()) }
  var page by remember(kind) { mutableStateOf(0) }
  var loading by remember(kind) { mutableStateOf(false) }
  var ended by remember(kind) { mutableStateOf(false) }
  val gridState = rememberLazyGridState()

  suspend fun loadNext() {
    if (loading || ended) return
    loading = true
    val next = page + 1
    val fetched =
      runCatching { app.repo.loadMorePage(kind == BrowseKind.SERIES, kind == BrowseKind.ANIME, next) }.getOrDefault(emptyList())
    val known = items.mapTo(HashSet()) { it.id }
    val fresh = fetched.filter { known.add(it.id) }
    if (fresh.isEmpty()) ended = true else { items = items + fresh; page = next }
    loading = false
  }

  LaunchedEffect(kind) { loadNext() }
  val nearEnd by remember {
    derivedStateOf { val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; items.isNotEmpty() && last >= items.size - 8 }
  }
  LaunchedEffect(nearEnd, items.size) { if (nearEnd) loadNext() }

  Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
    SectionTitle(kind.title, Modifier.padding(top = 16.dp))
    if (items.isEmpty() && loading) { Loading(Modifier.fillMaxSize()); return@Column }
    if (items.isEmpty()) { Message("Nothing found."); return@Column }
    LazyVerticalGrid(GridCells.Adaptive(160.dp), state = gridState, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      items(items, key = { it.id }) { m -> PosterCard(m, { app.openMovie(m) }) }
      if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(app: AppState) {
  var query by remember { mutableStateOf("") }
  var results by remember { mutableStateOf<List<Movie>>(emptyList()) }
  var suggestions by remember { mutableStateOf<List<MovieBoxSuggestion>>(emptyList()) }
  var trending by remember { mutableStateOf<List<String>>(emptyList()) }
  var loading by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { trending = runCatching { app.repo.getTrendingKeywords() }.getOrDefault(emptyList()) }
  LaunchedEffect(query) {
    if (query.isBlank()) { results = emptyList(); suggestions = emptyList(); loading = false; return@LaunchedEffect }
    delay(450)
    loading = true
    runCatching { app.repo.searchCatalogWithSuggestions(query) }.onSuccess { (m, s, _) -> results = m; suggestions = s.take(8) }
    loading = false
  }

  Column(Modifier.fillMaxSize().padding(24.dp)) {
    OutlinedTextField(
      query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
      placeholder = { Text("Search movies, series, anime…") },
      leadingIcon = { Icon(Icons.Filled.Search, null) },
    )
    if (query.isBlank() && trending.isNotEmpty()) {
      SectionTitle("Trending searches")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        trending.take(16).forEach { k -> AssistChip({ query = k }, { Text(k) }) }
      }
    }
    if (suggestions.isNotEmpty() && results.isEmpty()) {
      FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { s -> AssistChip({ query = s.title }, { Text(s.title) }) }
      }
    }
    if (loading) Loading()
    else if (query.isNotBlank() && results.isEmpty()) Message("No results for \"$query\"")
    else LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      items(results, key = { it.id }) { m -> PosterCard(m, { app.openMovie(m) }) }
    }
  }
}

@Composable
fun LibraryScreen(app: AppState) {
  val list = app.store.watchlist
  Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
    SectionTitle("My Watchlist", Modifier.padding(top = 16.dp))
    if (list.isEmpty()) { Message("Your watchlist is empty. Open a title and press “Add to watchlist”."); return@Column }
    LazyVerticalGrid(GridCells.Adaptive(160.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      items(list.toList(), key = { it.id }) { m -> PosterCard(m, { app.openMovie(m) }) }
    }
  }
}
