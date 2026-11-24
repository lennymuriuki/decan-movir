package com.example.desktop.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.data.model.Movie
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class AppSettings(
  val darkTheme: Boolean = true,
  /** Preferred playback / download resolution (e.g. 480, 720, 1080). */
  val defaultQuality: Int = 720,
  val autoPlayNext: Boolean = true,
  val subtitlesOnByDefault: Boolean = true,
  val downloadDir: String = "",
  val githubRepo: String = "",
)

/**
 * Persists settings, the local watchlist and resume positions as JSON files in [AppPaths.dataDir].
 * (Cloud sync / Google sign-in from the Android app is intentionally not part of the desktop build.)
 */
class LocalStore(private val dir: File = AppPaths.dataDir) {
  private val settingsFile = File(dir, "settings.json")
  private val watchlistFile = File(dir, "watchlist.json")
  private val progressFile = File(dir, "progress.json")

  val settings = mutableStateOf(loadSettings())
  val watchlist = mutableStateListOf<Movie>().also { it.addAll(loadWatchlist()) }
  private val progress = loadProgress()

  // ---- settings -------------------------------------------------------------------------------

  fun updateSettings(transform: (AppSettings) -> AppSettings) {
    settings.value = transform(settings.value)
    runCatching {
      val s = settings.value
      settingsFile.writeText(
        JSONObject()
          .put("darkTheme", s.darkTheme)
          .put("defaultQuality", s.defaultQuality)
          .put("autoPlayNext", s.autoPlayNext)
          .put("subtitlesOnByDefault", s.subtitlesOnByDefault)
          .put("downloadDir", s.downloadDir)
          .put("githubRepo", s.githubRepo)
          .toString(2)
      )
    }
  }

  private fun loadSettings(): AppSettings =
    runCatching {
      val o = JSONObject(settingsFile.readText())
      val d = AppSettings()
      AppSettings(
        darkTheme = o.optBoolean("darkTheme", d.darkTheme),
        defaultQuality = o.optInt("defaultQuality", d.defaultQuality),
        autoPlayNext = o.optBoolean("autoPlayNext", d.autoPlayNext),
        subtitlesOnByDefault = o.optBoolean("subtitlesOnByDefault", d.subtitlesOnByDefault),
        downloadDir = o.optString("downloadDir", d.downloadDir),
        githubRepo = o.optString("githubRepo", d.githubRepo),
      )
    }.getOrDefault(AppSettings())

  // ---- watchlist ------------------------------------------------------------------------------

  fun isSaved(movieId: String): Boolean = watchlist.any { it.id == movieId }

  fun toggleSaved(movie: Movie) {
    val idx = watchlist.indexOfFirst { it.id == movie.id }
    if (idx >= 0) watchlist.removeAt(idx) else watchlist.add(0, movie)
    runCatching {
      watchlistFile.writeText(JSONArray().also { arr -> watchlist.forEach { arr.put(MovieJson.encode(it)) } }.toString())
    }
  }

  private fun loadWatchlist(): List<Movie> =
    runCatching {
      val arr = JSONArray(watchlistFile.readText())
      (0 until arr.length()).mapNotNull { MovieJson.decode(arr.optJSONObject(it)) }
    }.getOrDefault(emptyList())

  // ---- resume positions -----------------------------------------------------------------------

  private fun key(movieId: String, season: Int, episode: Int) = "${movieId}_${season}_$episode"

  fun savedPosition(movieId: String, season: Int, episode: Int): Long = progress.optLong(key(movieId, season, episode), 0L)

  fun savePosition(movieId: String, season: Int, episode: Int, positionMs: Long) {
    if (positionMs < 5_000L) return
    progress.put(key(movieId, season, episode), positionMs)
    runCatching { progressFile.writeText(progress.toString()) }
  }

  fun clearPosition(movieId: String, season: Int, episode: Int) {
    progress.remove(key(movieId, season, episode))
    runCatching { progressFile.writeText(progress.toString()) }
  }

  private fun loadProgress(): JSONObject = runCatching { JSONObject(progressFile.readText()) }.getOrDefault(JSONObject())
}

object MovieJson {
  fun encode(m: Movie): JSONObject =
    JSONObject()
      .put("id", m.id).put("trackId", m.trackId).put("title", m.title).put("director", m.director)
      .put("genre", m.genre).put("releaseYear", m.releaseYear).put("releaseDateLabel", m.releaseDateLabel)
      .put("posterUrl", m.posterUrl).put("backdropUrl", m.backdropUrl).put("previewUrl", m.previewUrl ?: JSONObject.NULL)
      .put("synopsis", m.synopsis).put("runtimeMinutes", m.runtimeMinutes).put("contentRating", m.contentRating)
      .put("subjectId", m.subjectId).put("mediaType", m.mediaType).put("imdbRating", m.imdbRating ?: JSONObject.NULL)
      .put("country", m.country ?: JSONObject.NULL).put("language", m.language ?: JSONObject.NULL)
      .put("detailPath", m.detailPath ?: JSONObject.NULL).put("sourceProvider", m.sourceProvider)

  fun decode(o: JSONObject?): Movie? {
    if (o == null) return null
    fun opt(k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
    val id = opt("id") ?: return null
    return Movie(
      id = id,
      trackId = o.optLong("trackId"),
      title = o.optString("title"),
      director = o.optString("director"),
      genre = o.optString("genre"),
      releaseYear = o.optString("releaseYear"),
      releaseDateLabel = o.optString("releaseDateLabel"),
      posterUrl = o.optString("posterUrl"),
      backdropUrl = o.optString("backdropUrl"),
      previewUrl = opt("previewUrl"),
      synopsis = o.optString("synopsis"),
      runtimeMinutes = o.optInt("runtimeMinutes"),
      contentRating = o.optString("contentRating"),
      subjectId = o.optString("subjectId"),
      mediaType = o.optString("mediaType", "Movie"),
      imdbRating = opt("imdbRating"),
      country = opt("country"),
      language = opt("language"),
      detailPath = opt("detailPath"),
      sourceProvider = o.optString("sourceProvider", "iTunes"),
    )
  }
}
