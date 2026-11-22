package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class WatchStatus(val label: String) {
  WANT_TO_WATCH("Watchlist"),
  WATCHING("Watching"),
  WATCHED("Watched");

  companion object {
    fun fromString(raw: String?): WatchStatus =
      entries.firstOrNull { it.name == raw } ?: WANT_TO_WATCH
  }
}

@Keep
@IgnoreExtraProperties
data class SavedMovieEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val movieId: String = "",
  val title: String = "",
  val director: String = "",
  val genre: String = "Drama",
  val releaseYear: String = "",
  val posterUrl: String = "",
  val previewUrl: String? = null,
  val synopsis: String? = null,
  val runtimeMinutes: Long = 0L,
  val contentRating: String? = null,
  val watchStatus: String = WatchStatus.WANT_TO_WATCH.name,
  val favorite: Boolean = false,
  val personalRating: Long = 0L,
  val personalNote: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun watchStatusEnum(): WatchStatus = WatchStatus.fromString(watchStatus)

  fun toMovie(): Movie =
    Movie(
      id = movieId.ifBlank { id },
      trackId = movieId.removePrefix("itunes_").toLongOrNull() ?: 0L,
      title = title,
      director = director,
      genre = genre,
      releaseYear = releaseYear,
      releaseDateLabel = releaseYear,
      posterUrl = posterUrl,
      backdropUrl = posterUrl,
      previewUrl = previewUrl,
      synopsis = synopsis.orEmpty(),
      runtimeMinutes = runtimeMinutes.toInt(),
      contentRating = contentRating.orEmpty(),
    )
}
