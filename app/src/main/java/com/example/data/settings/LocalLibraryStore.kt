package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.WatchStatus
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local JSON library store so users who skip sign-in (or use the app without cloud sync)
 * still enjoy persistent watchlist, favorites, watch status, 5-star ratings, and personal notes.
 */
class LocalLibraryStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("decan_local_library", Context.MODE_PRIVATE)

  private val _entries = MutableStateFlow(readAll())
  val entries: StateFlow<List<SavedMovieEntry>> = _entries.asStateFlow()

  fun toggleQuickWatchlist(movie: Movie, existingEntry: SavedMovieEntry?): String {
    return if (existingEntry == null) {
      upsertEntry(
        movie = movie,
        watchStatus = WatchStatus.WANT_TO_WATCH,
        favorite = false,
        personalRating = 0,
        personalNote = null,
      )
      "Added \"${movie.title}\" to Watchlist"
    } else {
      removeMovie(movie.id, movie.title)
    }
  }

  fun toggleFavorite(movie: Movie, existingEntry: SavedMovieEntry?): String {
    val nextFav = !(existingEntry?.favorite ?: false)
    val status = existingEntry?.watchStatusEnum() ?: WatchStatus.WANT_TO_WATCH
    val rating = existingEntry?.personalRating?.toInt() ?: 0
    val note = existingEntry?.personalNote
    upsertEntry(
      movie = movie,
      watchStatus = status,
      favorite = nextFav,
      personalRating = rating,
      personalNote = note,
    )
    return if (nextFav) "Starred \"${movie.title}\" as favorite" else "Removed from favorites"
  }

  fun saveEntryDetails(
    movie: Movie,
    watchStatus: WatchStatus,
    favorite: Boolean,
    personalRating: Int,
    personalNote: String,
  ): String {
    upsertEntry(
      movie = movie,
      watchStatus = watchStatus,
      favorite = favorite,
      personalRating = personalRating,
      personalNote = personalNote,
    )
    return "Saved \"${movie.title}\" to your library"
  }

  fun removeMovie(movieId: String, title: String): String {
    val safeId = Movie.sanitizeId(movieId)
    val next = _entries.value.filterNot { it.movieId == safeId || it.id == safeId || it.movieId == movieId }
    writeAll(next)
    _entries.value = next
    return "Removed \"$title\" from Library"
  }

  fun clearAll() {
    prefs.edit().remove(KEY_ENTRIES).apply()
    _entries.value = emptyList()
  }

  private fun upsertEntry(
    movie: Movie,
    watchStatus: WatchStatus,
    favorite: Boolean,
    personalRating: Int,
    personalNote: String?,
  ) {
    val safeMovieId = Movie.sanitizeId(movie.id)
    val nowMs = System.currentTimeMillis()
    val nowTs = Timestamp(nowMs / 1000L, ((nowMs % 1000L) * 1_000_000L).toInt())
    val existing = _entries.value.firstOrNull { it.movieId == safeMovieId || it.id == safeMovieId }
    val safeNote = personalNote?.trim()?.take(1000)?.takeIf { it.isNotEmpty() }

    val updated =
      SavedMovieEntry(
        id = safeMovieId,
        userId = "guest",
        movieId = safeMovieId,
        title = movie.title.trim().ifBlank { "Untitled Film" }.take(200),
        director = movie.director.trim().take(120),
        genre = movie.genre.trim().ifBlank { "Cinema" }.take(60),
        releaseYear = movie.releaseYear.trim().take(10),
        posterUrl = movie.posterUrl.trim().take(500),
        previewUrl = movie.previewUrl?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
        synopsis = movie.synopsis.trim().take(2000).takeIf { it.isNotEmpty() },
        runtimeMinutes = movie.runtimeMinutes.coerceIn(0, 1000).toLong(),
        contentRating = movie.contentRating.trim().take(20).takeIf { it.isNotEmpty() },
        watchStatus = watchStatus.name,
        favorite = favorite,
        personalRating = personalRating.coerceIn(0, 5).toLong(),
        personalNote = safeNote,
        createdAt = existing?.createdAt ?: nowTs,
        updatedAt = nowTs,
      )

    val next = listOf(updated) + _entries.value.filterNot { it.movieId == safeMovieId || it.id == safeMovieId }
    writeAll(next)
    _entries.value = next
  }

  private fun readAll(): List<SavedMovieEntry> {
    val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val movieId = obj.optString("movieId")
          if (movieId.isBlank()) continue
          val createdMs = obj.optLong("createdAtMs", 0L)
          val updatedMs = obj.optLong("updatedAtMs", createdMs)
          add(
            SavedMovieEntry(
              id = movieId,
              userId = "guest",
              movieId = movieId,
              title = obj.optString("title"),
              director = obj.optString("director"),
              genre = obj.optString("genre", "Cinema"),
              releaseYear = obj.optString("releaseYear"),
              posterUrl = obj.optString("posterUrl"),
              previewUrl = obj.optString("previewUrl").takeIf { it.isNotBlank() },
              synopsis = obj.optString("synopsis").takeIf { it.isNotBlank() },
              runtimeMinutes = obj.optLong("runtimeMinutes", 0L),
              contentRating = obj.optString("contentRating").takeIf { it.isNotBlank() },
              watchStatus = obj.optString("watchStatus", WatchStatus.WANT_TO_WATCH.name),
              favorite = obj.optBoolean("favorite", false),
              personalRating = obj.optLong("personalRating", 0L),
              personalNote = obj.optString("personalNote").takeIf { it.isNotBlank() },
              createdAt = Timestamp(createdMs / 1000L, ((createdMs % 1000L) * 1_000_000L).toInt()),
              updatedAt = Timestamp(updatedMs / 1000L, ((updatedMs % 1000L) * 1_000_000L).toInt()),
            )
          )
        }
      }.sortedByDescending { it.updatedAt?.seconds ?: 0L }
    }.getOrDefault(emptyList())
  }

  private fun writeAll(list: List<SavedMovieEntry>) {
    val arr = JSONArray()
    list.forEach { item ->
      val createdMs = (item.createdAt?.seconds ?: 0L) * 1000L + (item.createdAt?.nanoseconds ?: 0) / 1_000_000L
      val updatedMs = (item.updatedAt?.seconds ?: 0L) * 1000L + (item.updatedAt?.nanoseconds ?: 0) / 1_000_000L
      arr.put(
        JSONObject().apply {
          put("movieId", item.movieId.ifBlank { item.id })
          put("title", item.title)
          put("director", item.director)
          put("genre", item.genre)
          put("releaseYear", item.releaseYear)
          put("posterUrl", item.posterUrl)
          put("previewUrl", item.previewUrl ?: "")
          put("synopsis", item.synopsis ?: "")
          put("runtimeMinutes", item.runtimeMinutes)
          put("contentRating", item.contentRating ?: "")
          put("watchStatus", item.watchStatus)
          put("favorite", item.favorite)
          put("personalRating", item.personalRating)
          put("personalNote", item.personalNote ?: "")
          put("createdAtMs", createdMs)
          put("updatedAtMs", updatedMs)
        }
      )
    }
    prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
  }

  private companion object {
    const val KEY_ENTRIES = "local_library_entries_json"
  }
}
