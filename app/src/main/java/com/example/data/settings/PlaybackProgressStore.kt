package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class ContinueWatchingEntry(
  val movieId: String,
  val title: String,
  val posterUrl: String,
  val mediaType: String,
  val season: Int,
  val episode: Int,
  val positionMs: Long,
  val durationMs: Long,
  val updatedAt: Long,
) {
  val progressFraction: Float
    get() =
      if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
      else 0f
}

class PlaybackProgressStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("decan_playback_progress", Context.MODE_PRIVATE)

  private val _items = MutableStateFlow(readAll())
  val items: StateFlow<List<ContinueWatchingEntry>> = _items.asStateFlow()

  private val episodePositions: MutableMap<String, Long> = readEpisodePositions()

  fun getProgress(movieId: String): ContinueWatchingEntry? =
    _items.value.firstOrNull { it.movieId == movieId }

  fun getEpisodeProgress(movieId: String, season: Int, episode: Int): Long {
    if (movieId.isBlank() || season <= 0 || episode <= 0) return 0L
    return episodePositions[episodeKey(movieId, season, episode)] ?: 0L
  }

  fun saveEpisodeProgress(
    movieId: String,
    season: Int,
    episode: Int,
    positionMs: Long,
    durationMs: Long,
  ) {
    if (movieId.isBlank() || season <= 0 || episode <= 0) return
    val key = episodeKey(movieId, season, episode)
    if (durationMs > 20_000L && positionMs.toFloat() / durationMs.toFloat() > 0.96f) {
      episodePositions.remove(key)
    } else if (positionMs >= 2_000L) {
      episodePositions[key] = positionMs
    }
    if (episodePositions.size > MAX_EPISODE_ENTRIES) {
      val keysToDrop = episodePositions.keys.take(episodePositions.size - MAX_EPISODE_ENTRIES)
      keysToDrop.forEach { episodePositions.remove(it) }
    }
    writeEpisodePositions(episodePositions)
  }

  fun saveProgress(
    movieId: String,
    title: String,
    posterUrl: String,
    mediaType: String,
    season: Int,
    episode: Int,
    positionMs: Long,
    durationMs: Long,
  ) {
    if (movieId.isBlank()) return
    if (season > 0 && episode > 0) {
      saveEpisodeProgress(movieId, season, episode, positionMs, durationMs)
    }
    if (positionMs < 3_000L) return
    // If within last 15 seconds of a standalone movie (> 95% watched), remove from Continue Watching
    if (season == 0 && durationMs > 30_000L && positionMs.toFloat() / durationMs.toFloat() > 0.96f) {
      removeProgress(movieId)
      return
    }
    val entry =
      ContinueWatchingEntry(
        movieId = movieId,
        title = title,
        posterUrl = posterUrl,
        mediaType = mediaType,
        season = season,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = System.currentTimeMillis(),
      )
    val next =
      (listOf(entry) + _items.value.filterNot { it.movieId == movieId })
        .take(20)
    writeAll(next)
    _items.value = next
  }

  fun removeProgress(movieId: String) {
    val next = _items.value.filterNot { it.movieId == movieId }
    writeAll(next)
    _items.value = next
  }

  private fun episodeKey(movieId: String, season: Int, episode: Int): String =
    "${movieId}__s${season}_e${episode}"

  private fun readEpisodePositions(): MutableMap<String, Long> {
    val raw = prefs.getString(KEY_EPISODE_POSITIONS, null) ?: return LinkedHashMap()
    return runCatching {
      val obj = JSONObject(raw)
      val map = LinkedHashMap<String, Long>()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val v = obj.optLong(k, 0L)
        if (v > 0L) map[k] = v
      }
      map
    }.getOrDefault(LinkedHashMap())
  }

  private fun writeEpisodePositions(map: Map<String, Long>) {
    val obj = JSONObject()
    map.forEach { (k, v) -> obj.put(k, v) }
    prefs.edit().putString(KEY_EPISODE_POSITIONS, obj.toString()).apply()
  }

  private fun readAll(): List<ContinueWatchingEntry> {
    val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val id = obj.optString("movieId")
          if (id.isBlank()) continue
          add(
            ContinueWatchingEntry(
              movieId = id,
              title = obj.optString("title"),
              posterUrl = obj.optString("posterUrl"),
              mediaType = obj.optString("mediaType", "Movie"),
              season = obj.optInt("season", 0),
              episode = obj.optInt("episode", 0),
              positionMs = obj.optLong("positionMs", 0L),
              durationMs = obj.optLong("durationMs", 0L),
              updatedAt = obj.optLong("updatedAt", 0L),
            )
          )
        }
      }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())
  }

  private fun writeAll(entries: List<ContinueWatchingEntry>) {
    val arr = JSONArray()
    entries.forEach { e ->
      arr.put(
        JSONObject().apply {
          put("movieId", e.movieId)
          put("title", e.title)
          put("posterUrl", e.posterUrl)
          put("mediaType", e.mediaType)
          put("season", e.season)
          put("episode", e.episode)
          put("positionMs", e.positionMs)
          put("durationMs", e.durationMs)
          put("updatedAt", e.updatedAt)
        }
      )
    }
    prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
  }

  private companion object {
    const val KEY_ITEMS = "continue_watching_json"
    const val KEY_EPISODE_POSITIONS = "episode_positions_json"
    const val MAX_EPISODE_ENTRIES = 120
  }
}
