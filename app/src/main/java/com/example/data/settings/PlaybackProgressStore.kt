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

  fun getProgress(movieId: String): ContinueWatchingEntry? =
    _items.value.firstOrNull { it.movieId == movieId }

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
    if (movieId.isBlank() || positionMs < 3_000L) return
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
  }
}
