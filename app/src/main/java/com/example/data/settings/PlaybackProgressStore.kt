package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt
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
  val localUri: String = "",
) {
  val progressFraction: Float
    get() =
      if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
      else 0f
}

@Immutable
data class DownloadWatchProgress(
  val positionMs: Long,
  val durationMs: Long,
  val updatedAt: Long,
) {
  val fraction: Float
    get() =
      if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
      else 0f

  val percentWatched: Int
    get() =
      if (durationMs > 0L) {
        ((positionMs.toDouble() / durationMs.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
      } else 0

  val isFinished: Boolean
    get() = durationMs > 10_000L && percentWatched >= FINISHED_PERCENT_THRESHOLD

  val isInProgress: Boolean
    get() = !isFinished && (percentWatched >= 1 || positionMs >= 2_000L)

  val statusLabel: String
    get() =
      when {
        isFinished -> "Finished"
        isInProgress && percentWatched > 0 -> "Continue Watching • $percentWatched% watched"
        isInProgress -> "Continue Watching"
        else -> "Ready to watch"
      }

  companion object {
    const val FINISHED_PERCENT_THRESHOLD = 92
  }
}

class PlaybackProgressStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("decan_playback_progress", Context.MODE_PRIVATE)

  private val _items = MutableStateFlow(readAll())
  val items: StateFlow<List<ContinueWatchingEntry>> = _items.asStateFlow()

  private val episodePositions: MutableMap<String, Long> = readEpisodePositions()

  private val _downloadProgressMap = MutableStateFlow(readDownloadRecords())
  val downloadProgressMap: StateFlow<Map<String, DownloadWatchProgress>> =
    _downloadProgressMap.asStateFlow()

  fun getProgress(movieId: String): ContinueWatchingEntry? =
    _items.value.firstOrNull { it.movieId == movieId }

  fun getEpisodeProgress(movieId: String, season: Int, episode: Int): Long {
    if (movieId.isBlank() || season <= 0 || episode <= 0) return 0L
    return episodePositions[episodeKey(movieId, season, episode)] ?: 0L
  }

  fun getLocalProgress(localUri: String): Long {
    val rec = findDownloadWatchProgress(localUri = localUri) ?: return 0L
    return if (rec.isFinished) 0L else rec.positionMs
  }

  fun findDownloadWatchProgress(
    movieId: String = "",
    season: Int = 0,
    episode: Int = 0,
    localUri: String = "",
    relativePath: String = "",
    title: String = "",
  ): DownloadWatchProgress? =
    findInMap(
      records = _downloadProgressMap.value,
      movieId = movieId,
      season = season,
      episode = episode,
      localUri = localUri,
      relativePath = relativePath,
      title = title,
    )

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
    localUri: String = "",
  ) {
    val effectiveId =
      movieId.ifBlank {
        if (localUri.isNotBlank()) "local_${normalizeKeyPart(localUri).hashCode().toUInt()}"
        else ""
      }
    if (effectiveId.isBlank() && localUri.isBlank() && title.isBlank()) return

    if (effectiveId.isNotBlank() && season > 0 && episode > 0) {
      saveEpisodeProgress(effectiveId, season, episode, positionMs, durationMs)
    }

    if (positionMs >= 2_000L || (durationMs > 0L && positionMs >= durationMs)) {
      recordDownloadWatchProgress(
        movieId = movieId,
        title = title,
        season = season,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        localUri = localUri,
      )
    }

    if (effectiveId.isBlank() || positionMs < 3_000L) return

    // If finished (>= 96%), remove standalone items from Discover's Continue Watching carousel
    if (season == 0 && durationMs > 30_000L && positionMs.toFloat() / durationMs.toFloat() > 0.96f) {
      removeProgress(effectiveId)
      return
    }
    val entry =
      ContinueWatchingEntry(
        movieId = effectiveId,
        title = title,
        posterUrl = posterUrl,
        mediaType = mediaType,
        season = season,
        episode = episode,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = System.currentTimeMillis(),
        localUri = localUri,
      )
    val next =
      (listOf(entry) + _items.value.filterNot { it.movieId == effectiveId })
        .take(20)
    writeAll(next)
    _items.value = next
  }

  private fun recordDownloadWatchProgress(
    movieId: String,
    title: String,
    season: Int,
    episode: Int,
    positionMs: Long,
    durationMs: Long,
    localUri: String,
  ) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition =
      if (safeDuration > 0L) positionMs.coerceIn(0L, safeDuration) else positionMs.coerceAtLeast(0L)
    val record =
      DownloadWatchProgress(
        positionMs = safePosition,
        durationMs = safeDuration,
        updatedAt = System.currentTimeMillis(),
      )
    val nextMap = LinkedHashMap(_downloadProgressMap.value)
    if (movieId.isNotBlank()) {
      nextMap[mediaWatchKey(movieId, season, episode)] = record
    }
    if (localUri.isNotBlank()) {
      nextMap[uriWatchKey(localUri)] = record
      extractFileKey(localUri)?.let { nextMap[it] = record }
    }
    if (title.isNotBlank()) {
      nextMap[titleWatchKey(title, season, episode)] = record
    }
    while (nextMap.size > MAX_DOWNLOAD_RECORDS) {
      val oldestKey = nextMap.entries.minByOrNull { it.value.updatedAt }?.key ?: break
      nextMap.remove(oldestKey)
    }
    writeDownloadRecords(nextMap)
    _downloadProgressMap.value = nextMap
  }

  fun removeProgress(movieId: String) {
    val next = _items.value.filterNot { it.movieId == movieId }
    writeAll(next)
    _items.value = next
  }

  fun clearDownloadWatchProgress(
    movieId: String = "",
    season: Int = 0,
    episode: Int = 0,
    localUri: String = "",
    relativePath: String = "",
    title: String = "",
  ) {
    val nextMap = LinkedHashMap(_downloadProgressMap.value)
    var changed = false
    listOfNotNull(
      movieId.takeIf { it.isNotBlank() }?.let { mediaWatchKey(it, season, episode) },
      localUri.takeIf { it.isNotBlank() }?.let { uriWatchKey(it) },
      localUri.takeIf { it.isNotBlank() }?.let { extractFileKey(it) },
      relativePath.takeIf { it.isNotBlank() }?.let { extractFileKey(it) },
      title.takeIf { it.isNotBlank() }?.let { titleWatchKey(it, season, episode) },
    ).forEach { key ->
      if (nextMap.remove(key) != null) changed = true
    }
    if (changed) {
      writeDownloadRecords(nextMap)
      _downloadProgressMap.value = nextMap
    }
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

  private fun readDownloadRecords(): Map<String, DownloadWatchProgress> {
    val raw = prefs.getString(KEY_DOWNLOAD_WATCH_RECORDS, null) ?: return emptyMap()
    return runCatching {
      val obj = JSONObject(raw)
      val map = LinkedHashMap<String, DownloadWatchProgress>()
      val keys = obj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val entry = obj.optJSONObject(k) ?: continue
        map[k] =
          DownloadWatchProgress(
            positionMs = entry.optLong("pos", 0L),
            durationMs = entry.optLong("dur", 0L),
            updatedAt = entry.optLong("ts", 0L),
          )
      }
      map
    }.getOrDefault(emptyMap())
  }

  private fun writeDownloadRecords(map: Map<String, DownloadWatchProgress>) {
    val obj = JSONObject()
    map.forEach { (k, v) ->
      obj.put(
        k,
        JSONObject().apply {
          put("pos", v.positionMs)
          put("dur", v.durationMs)
          put("ts", v.updatedAt)
        },
      )
    }
    prefs.edit().putString(KEY_DOWNLOAD_WATCH_RECORDS, obj.toString()).apply()
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
              localUri = obj.optString("localUri", ""),
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
          if (e.localUri.isNotBlank()) put("localUri", e.localUri)
        }
      )
    }
    prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
  }

  companion object {
    private const val KEY_ITEMS = "continue_watching_json"
    private const val KEY_EPISODE_POSITIONS = "episode_positions_json"
    private const val KEY_DOWNLOAD_WATCH_RECORDS = "download_watch_records_json"
    private const val MAX_EPISODE_ENTRIES = 120
    private const val MAX_DOWNLOAD_RECORDS = 240

    fun mediaWatchKey(movieId: String, season: Int, episode: Int): String =
      "m_${movieId.trim()}__s${season}_e${episode}"

    fun uriWatchKey(uri: String): String = "u_${uri.trim()}"

    fun extractFileKey(pathOrUri: String): String? {
      val clean =
        runCatching { Uri.parse(pathOrUri).lastPathSegment }
          .getOrNull()
          ?.takeIf { it.isNotBlank() }
          ?: pathOrUri.substringAfterLast('/').substringBefore('?')
      val norm = clean.trim().lowercase()
      return if (norm.isNotBlank()) "f_$norm" else null
    }

    fun titleWatchKey(rawTitle: String, season: Int, episode: Int): String {
      val base =
        rawTitle
          .substringBefore("•")
          .substringBefore("(")
          .replace("_", " ")
          .trim()
      val norm = normalizeKeyPart(base)
      return "t_${norm}__s${season}_e${episode}"
    }

    private fun normalizeKeyPart(raw: String): String =
      raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun findInMap(
      records: Map<String, DownloadWatchProgress>,
      movieId: String = "",
      season: Int = 0,
      episode: Int = 0,
      localUri: String = "",
      relativePath: String = "",
      title: String = "",
    ): DownloadWatchProgress? {
      if (records.isEmpty()) return null
      val candidates = buildList {
        if (movieId.isNotBlank()) {
          add(mediaWatchKey(movieId, season, episode))
          if (season == 0 && episode == 0) add(mediaWatchKey(movieId, 0, 0))
        }
        if (localUri.isNotBlank()) {
          add(uriWatchKey(localUri))
          extractFileKey(localUri)?.let { add(it) }
        }
        if (relativePath.isNotBlank()) {
          extractFileKey(relativePath)?.let { add(it) }
        }
        if (title.isNotBlank()) {
          add(titleWatchKey(title, season, episode))
        }
      }
      return candidates.firstNotNullOfOrNull { records[it] }
    }
  }
}
