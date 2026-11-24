package com.decanmovie.desktop.data.store

import com.decanmovie.desktop.model.DesktopSettings
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.PlaybackHistoryItem
import com.decanmovie.desktop.model.WatchStatus
import com.decanmovie.desktop.model.WatchlistEntry
import com.decanmovie.desktop.platform.WindowsAppPaths
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class DesktopAppStorage(
  private val baseDir: File = WindowsAppPaths.appDataDirectory(),
) {
  private val settingsFile = File(baseDir, "settings.json")
  private val watchlistFile = File(baseDir, "watchlist.json")
  private val historyFile = File(baseDir, "history.json")
  private val cacheSnapshotFile = File(baseDir, "home_snapshot.json")

  private val _settings = MutableStateFlow(loadSettingsFromDisk())
  val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

  private val _watchlist = MutableStateFlow(loadWatchlistFromDisk())
  val watchlist: StateFlow<List<WatchlistEntry>> = _watchlist.asStateFlow()

  private val _history = MutableStateFlow(loadHistoryFromDisk())
  val history: StateFlow<List<PlaybackHistoryItem>> = _history.asStateFlow()

  fun currentSettings(): DesktopSettings = _settings.value

  fun updateSettings(transform: (DesktopSettings) -> DesktopSettings) {
    val updated = transform(_settings.value)
    _settings.value = updated
    persistSettings(updated)
  }

  fun shouldAutoCheckUpdates(nowMs: Long = System.currentTimeMillis()): Boolean {
    val s = _settings.value
    if (!s.autoCheckUpdates) return false
    return (nowMs - s.lastUpdateCheckMs) >= UPDATE_CHECK_COOLDOWN_MS
  }

  fun toggleWatchlist(movie: Movie): Boolean {
    val existing = _watchlist.value.firstOrNull { it.movie.id == movie.id }
    val nextList =
      if (existing != null) {
        _watchlist.value.filterNot { it.movie.id == movie.id }
      } else {
        listOf(WatchlistEntry(movie = movie, watchStatus = WatchStatus.WANT_TO_WATCH)) +
          _watchlist.value
      }
    _watchlist.value = nextList
    persistWatchlist(nextList)
    return existing == null
  }

  fun saveEntry(
    movie: Movie,
    watchStatus: WatchStatus = WatchStatus.WANT_TO_WATCH,
    favorite: Boolean = false,
    personalRating: Int = 0,
    personalNote: String = "",
  ) {
    val updatedEntry =
      WatchlistEntry(
        movie = movie,
        watchStatus = watchStatus,
        favorite = favorite,
        personalRating = personalRating.coerceIn(0, 5),
        personalNote = personalNote.take(500),
        updatedAtMs = System.currentTimeMillis(),
      )
    val next =
      listOf(updatedEntry) + _watchlist.value.filterNot { it.movie.id == movie.id }
    _watchlist.value = next
    persistWatchlist(next)
  }

  fun removeFromWatchlist(movieId: String) {
    val next = _watchlist.value.filterNot { it.movie.id == movieId }
    _watchlist.value = next
    persistWatchlist(next)
  }

  fun getWatchlistEntry(movieId: String): WatchlistEntry? =
    _watchlist.value.firstOrNull { it.movie.id == movieId }

  fun savePlaybackProgress(
    movie: Movie,
    season: Int,
    episode: Int,
    positionMs: Long,
    durationMs: Long,
  ) {
    if (positionMs < 1_000L && season <= 0 && episode <= 0) return
    val existing = _history.value.firstOrNull { it.movieId == movie.id }
    val key = "${season.coerceAtLeast(0)}:${episode.coerceAtLeast(0)}"
    val updatedMap = (existing?.episodePositions ?: emptyMap()).toMutableMap()
    updatedMap[key] = positionMs.coerceAtLeast(0L)

    val item =
      PlaybackHistoryItem(
        movieId = movie.id,
        title = movie.title,
        posterUrl = movie.posterUrl,
        mediaType = movie.mediaType,
        genre = movie.genre,
        subjectId = movie.subjectId,
        detailPath = movie.detailPath,
        season = season,
        episode = episode,
        positionMs = positionMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        updatedAtMs = System.currentTimeMillis(),
        episodePositions = updatedMap,
      )
    val next = (listOf(item) + _history.value.filterNot { it.movieId == movie.id }).take(60)
    _history.value = next
    persistHistory(next)
  }

  fun getEpisodeProgress(movieId: String, season: Int, episode: Int): Long {
    val item = _history.value.firstOrNull { it.movieId == movieId } ?: return 0L
    val key = item.episodeKey(season, episode)
    item.episodePositions[key]?.let { return it }
    return if (item.season == season && item.episode == episode) item.positionMs else 0L
  }

  fun removeHistoryItem(movieId: String) {
    val next = _history.value.filterNot { it.movieId == movieId }
    _history.value = next
    persistHistory(next)
  }

  fun clearHistory() {
    _history.value = emptyList()
    persistHistory(emptyList())
  }

  fun readSnapshotJson(): String? =
    runCatching {
      if (cacheSnapshotFile.exists()) cacheSnapshotFile.readText(Charsets.UTF_8) else null
    }.getOrNull()

  fun writeSnapshotJson(json: String) {
    runCatching {
      baseDir.mkdirs()
      cacheSnapshotFile.writeText(json, Charsets.UTF_8)
    }
  }

  private fun loadSettingsFromDisk(): DesktopSettings {
    val defaultDir = WindowsAppPaths.defaultDownloadsDirectory().absolutePath
    val fallback =
      DesktopSettings(
        downloadDirectory = defaultDir,
        preferPortableUpdates = WindowsAppPaths.isPortableExecution(),
      )
    if (!settingsFile.exists()) return fallback
    return runCatching {
      val obj = JSONObject(settingsFile.readText(Charsets.UTF_8))
      val rawRepo = obj.optString("githubRepo", "lennymuriuki/decan-movir")
      val cleanRepo =
        if (rawRepo.equals("lennymuriuki4/decan-movir", ignoreCase = true) || rawRepo.isBlank()) {
          "lennymuriuki/decan-movir"
        } else {
          rawRepo
        }
      DesktopSettings(
        downloadDirectory = obj.optString("downloadDirectory", defaultDir).ifBlank { defaultDir },
        preferredQuality = obj.optInt("preferredQuality", 1080),
        defaultPlaybackSpeed = obj.optDouble("defaultPlaybackSpeed", 1.0).toFloat(),
        autoPlayNextEpisode = obj.optBoolean("autoPlayNextEpisode", true),
        autoLoadSubtitles = obj.optBoolean("autoLoadSubtitles", true),
        includeSubtitlesInDownloads = obj.optBoolean("includeSubtitlesInDownloads", true),
        autoCheckUpdates = obj.optBoolean("autoCheckUpdates", true),
        lastUpdateCheckMs = obj.optLong("lastUpdateCheckMs", 0L),
        dismissedReleaseTag = obj.optString("dismissedReleaseTag", ""),
        cachedReleaseJson = obj.optString("cachedReleaseJson", ""),
        githubRepo = cleanRepo,
        minimizeToTrayOnClose = obj.optBoolean("minimizeToTrayOnClose", false),
        launchAtStartup = obj.optBoolean("launchAtStartup", false),
        enableDesktopNotifications = obj.optBoolean("enableDesktopNotifications", true),
        compactCards = obj.optBoolean("compactCards", false),
        preferPortableUpdates =
          obj.optBoolean("preferPortableUpdates", WindowsAppPaths.isPortableExecution()),
      )
    }.getOrDefault(fallback)
  }

  private fun persistSettings(s: DesktopSettings) {
    runCatching {
      baseDir.mkdirs()
      val obj =
        JSONObject().apply {
          put("downloadDirectory", s.downloadDirectory)
          put("preferredQuality", s.preferredQuality)
          put("defaultPlaybackSpeed", s.defaultPlaybackSpeed.toDouble())
          put("autoPlayNextEpisode", s.autoPlayNextEpisode)
          put("autoLoadSubtitles", s.autoLoadSubtitles)
          put("includeSubtitlesInDownloads", s.includeSubtitlesInDownloads)
          put("autoCheckUpdates", s.autoCheckUpdates)
          put("lastUpdateCheckMs", s.lastUpdateCheckMs)
          put("dismissedReleaseTag", s.dismissedReleaseTag)
          put("cachedReleaseJson", s.cachedReleaseJson)
          put("githubRepo", s.githubRepo)
          put("minimizeToTrayOnClose", s.minimizeToTrayOnClose)
          put("launchAtStartup", s.launchAtStartup)
          put("enableDesktopNotifications", s.enableDesktopNotifications)
          put("compactCards", s.compactCards)
          put("preferPortableUpdates", s.preferPortableUpdates)
        }
      writeAtomic(settingsFile, obj.toString(2))
    }
  }

  private fun loadWatchlistFromDisk(): List<WatchlistEntry> {
    if (!watchlistFile.exists()) return emptyList()
    return runCatching {
      val arr = JSONArray(watchlistFile.readText(Charsets.UTF_8))
      buildList {
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val mObj = o.optJSONObject("movie") ?: continue
          val movie = movieFromJson(mObj) ?: continue
          add(
            WatchlistEntry(
              movie = movie,
              watchStatus = WatchStatus.fromString(o.optString("watchStatus")),
              favorite = o.optBoolean("favorite", false),
              personalRating = o.optInt("personalRating", 0),
              personalNote = o.optString("personalNote", ""),
              updatedAtMs = o.optLong("updatedAtMs", System.currentTimeMillis()),
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun persistWatchlist(list: List<WatchlistEntry>) {
    runCatching {
      baseDir.mkdirs()
      val arr =
        JSONArray().apply {
          list.forEach { entry ->
            put(
              JSONObject().apply {
                put("movie", movieToJson(entry.movie))
                put("watchStatus", entry.watchStatus.name)
                put("favorite", entry.favorite)
                put("personalRating", entry.personalRating)
                put("personalNote", entry.personalNote)
                put("updatedAtMs", entry.updatedAtMs)
              }
            )
          }
        }
      writeAtomic(watchlistFile, arr.toString(2))
    }
  }

  private fun loadHistoryFromDisk(): List<PlaybackHistoryItem> {
    if (!historyFile.exists()) return emptyList()
    return runCatching {
      val arr = JSONArray(historyFile.readText(Charsets.UTF_8))
      buildList {
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val id = o.optString("movieId")
          val title = o.optString("title")
          if (id.isBlank() || title.isBlank()) continue
          val epObj = o.optJSONObject("episodePositions")
          val epMap = buildMap {
            if (epObj != null) {
              epObj.keys().forEach { k -> put(k, epObj.optLong(k, 0L)) }
            }
          }
          add(
            PlaybackHistoryItem(
              movieId = id,
              title = title,
              posterUrl = o.optString("posterUrl", ""),
              mediaType = o.optString("mediaType", "Movie"),
              genre = o.optString("genre", ""),
              subjectId = o.optString("subjectId", ""),
              detailPath = o.optString("detailPath").takeIf { it.isNotBlank() },
              season = o.optInt("season", 0),
              episode = o.optInt("episode", 0),
              positionMs = o.optLong("positionMs", 0L),
              durationMs = o.optLong("durationMs", 0L),
              updatedAtMs = o.optLong("updatedAtMs", 0L),
              episodePositions = epMap,
            )
          )
        }
      }.sortedByDescending { it.updatedAtMs }
    }.getOrDefault(emptyList())
  }

  private fun persistHistory(items: List<PlaybackHistoryItem>) {
    runCatching {
      baseDir.mkdirs()
      val arr =
        JSONArray().apply {
          items.forEach { item ->
            put(
              JSONObject().apply {
                put("movieId", item.movieId)
                put("title", item.title)
                put("posterUrl", item.posterUrl)
                put("mediaType", item.mediaType)
                put("genre", item.genre)
                put("subjectId", item.subjectId)
                if (!item.detailPath.isNullOrBlank()) put("detailPath", item.detailPath)
                put("season", item.season)
                put("episode", item.episode)
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("updatedAtMs", item.updatedAtMs)
                put("episodePositions", JSONObject(item.episodePositions))
              }
            )
          }
        }
      writeAtomic(historyFile, arr.toString(2))
    }
  }

  private fun writeAtomic(target: File, content: String) {
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeText(content, Charsets.UTF_8)
    if (target.exists()) target.delete()
    if (!tmp.renameTo(target)) {
      tmp.copyTo(target, overwrite = true)
      tmp.delete()
    }
  }

  companion object {
    const val UPDATE_CHECK_COOLDOWN_MS = 6 * 60 * 60 * 1000L

    fun movieToJson(m: Movie): JSONObject =
      JSONObject().apply {
        put("id", m.id)
        put("trackId", m.trackId)
        put("title", m.title)
        put("director", m.director)
        put("genre", m.genre)
        put("releaseYear", m.releaseYear)
        put("releaseDateLabel", m.releaseDateLabel)
        put("posterUrl", m.posterUrl)
        put("backdropUrl", m.backdropUrl)
        if (!m.previewUrl.isNullOrBlank()) put("previewUrl", m.previewUrl)
        put("synopsis", m.synopsis)
        put("runtimeMinutes", m.runtimeMinutes)
        put("contentRating", m.contentRating)
        put("subjectId", m.subjectId)
        put("mediaType", m.mediaType)
        if (!m.imdbRating.isNullOrBlank()) put("imdbRating", m.imdbRating)
        if (!m.country.isNullOrBlank()) put("country", m.country)
        if (!m.language.isNullOrBlank()) put("language", m.language)
        if (!m.detailPath.isNullOrBlank()) put("detailPath", m.detailPath)
        put("sourceProvider", m.sourceProvider)
        if (m.rankPosition != null) put("rankPosition", m.rankPosition)
      }

    fun movieFromJson(o: JSONObject): Movie? {
      val id = o.optString("id")
      val title = o.optString("title")
      if (id.isBlank() || title.isBlank()) return null
      return Movie(
        id = id,
        trackId = o.optLong("trackId", 0L),
        title = title,
        director = o.optString("director", ""),
        genre = o.optString("genre", "Cinema"),
        releaseYear = o.optString("releaseYear", "2025"),
        releaseDateLabel = o.optString("releaseDateLabel", "2025"),
        posterUrl = o.optString("posterUrl", ""),
        backdropUrl = o.optString("backdropUrl", ""),
        previewUrl = o.optString("previewUrl").takeIf { it.isNotBlank() && it != "null" },
        synopsis = o.optString("synopsis", ""),
        runtimeMinutes = o.optInt("runtimeMinutes", 0),
        contentRating = o.optString("contentRating", "HD"),
        subjectId = o.optString("subjectId", ""),
        mediaType = o.optString("mediaType", "Movie"),
        imdbRating = o.optString("imdbRating").takeIf { it.isNotBlank() && it != "null" },
        country = o.optString("country").takeIf { it.isNotBlank() && it != "null" },
        language = o.optString("language").takeIf { it.isNotBlank() && it != "null" },
        detailPath = o.optString("detailPath").takeIf { it.isNotBlank() && it != "null" },
        sourceProvider = o.optString("sourceProvider", "Decan"),
        rankPosition = o.optInt("rankPosition", 0).takeIf { it > 0 },
      )
    }
  }
}
