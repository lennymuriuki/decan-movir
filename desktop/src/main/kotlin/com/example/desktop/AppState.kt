package com.example.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.model.Movie
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.desktop.data.AppPaths
import com.example.desktop.data.DownloadManager
import com.example.desktop.data.FileSharedPreferences
import com.example.desktop.data.LocalStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What the player should play: a catalog title (streamed) or a downloaded local file. */
data class PlayRequest(
  val title: String,
  val movie: Movie? = null,
  val season: Int = 0,
  val episode: Int = 0,
  val localFile: File? = null,
)

sealed interface Route {
  data object Home : Route
  data object Movies : Route
  data object Series : Route
  data object Anime : Route
  data object Search : Route
  data object Library : Route
  data object Downloads : Route
  data object Settings : Route
  data class Detail(val movie: Movie) : Route
  data class Player(val request: PlayRequest) : Route
}

/** Single place that holds services and navigation for the desktop app. */
class AppState {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  val store = LocalStore()
  val repo = MovieCatalogRepository(snapshotPrefs = FileSharedPreferences(File(AppPaths.dataDir, "catalog_snapshot.json")))
  val downloads = DownloadManager(store)

  private val backStack = mutableStateListOf<Route>()
  var route by mutableStateOf<Route>(Route.Home)
    private set
  var fullscreen by mutableStateOf(false)
  var toast by mutableStateOf<String?>(null)

  /** Switch top-level section (clears history). */
  fun goTo(top: Route) {
    backStack.clear()
    route = top
    fullscreen = false
  }

  /** Open a detail/player page on top of the current one. */
  fun push(next: Route) {
    backStack.add(route)
    route = next
  }

  fun openMovie(movie: Movie) = push(Route.Detail(movie))

  fun play(request: PlayRequest) = push(Route.Player(request))

  fun back() {
    fullscreen = false
    route = backStack.removeLastOrNull() ?: Route.Home
  }

  /** Queue every episode of a season at the preferred quality. */
  fun downloadSeason(movie: Movie, season: Int, episodeCount: Int) {
    scope.launch {
      toast = "Queuing $episodeCount episodes…"
      var queued = 0
      for (ep in 1..episodeCount) {
        val streams = repo.getStreams(movie.subjectId, season, ep, movie.detailPath)
        val pick = pickStream(streams, store.settings.value.defaultQuality) ?: continue
        val sub = repo.getSubtitles(movie.subjectId, season, ep).firstOrNull { it.code.startsWith("en", true) || it.language.contains("English", true) }
        downloads.enqueue(movie.title, pick, sub, label = "S${season}E${ep.toString().padStart(2, '0')}")
        queued++
      }
      toast = if (queued > 0) "Queued $queued episodes — see Downloads" else "No downloadable streams found"
    }
  }
}

/** Best directly-playable stream not above [preferred] resolution (falls back to the lowest available). */
fun pickStream(streams: List<MovieBoxStream>, preferred: Int): MovieBoxStream? {
  val playable = streams.filter { it.isPlayableDirect && it.url.isNotBlank() }
  if (playable.isEmpty()) return null
  return playable.filter { it.qualityValue <= preferred }.maxByOrNull { it.qualityValue }
    ?: playable.minByOrNull { it.qualityValue }
}
