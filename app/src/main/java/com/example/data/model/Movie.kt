package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Movie(
  val id: String,
  val trackId: Long,
  val title: String,
  val director: String,
  val genre: String,
  val releaseYear: String,
  val releaseDateLabel: String,
  val posterUrl: String,
  val backdropUrl: String,
  val previewUrl: String?,
  val synopsis: String,
  val runtimeMinutes: Int,
  val contentRating: String,
  val priceFormatted: String? = null,
  val storeUrl: String? = null,
  val subjectId: String = "",
  val mediaType: String = "Movie",
  val imdbRating: String? = null,
  val country: String? = null,
  val language: String? = null,
  val detailPath: String? = null,
  val sourceProvider: String = "iTunes",
  val rankPosition: Int? = null,
) {
  companion object {
    fun sanitizeId(raw: String): String {
      val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
      return cleaned.ifBlank { "movie_unknown" }.take(120)
    }
  }
}

@Immutable
data class MovieRail(
  val id: String,
  val title: String,
  val subtitle: String,
  val movies: List<Movie>,
)
