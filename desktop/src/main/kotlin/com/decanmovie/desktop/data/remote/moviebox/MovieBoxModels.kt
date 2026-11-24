package com.decanmovie.desktop.data.remote.moviebox

import androidx.compose.runtime.Immutable
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.MovieRail

@Immutable
data class MovieBoxDub(
  val subjectId: String,
  val langName: String,
  val language: String,
)

@Immutable
data class MovieBoxSeason(
  val season: Int,
  val episodeCount: Int,
  val resolutions: List<Int> = emptyList(),
)

@Immutable
data class MovieBoxStream(
  val source: String,
  val format: String,
  val quality: String,
  val qualityValue: Int,
  val resolutionTag: String,
  val url: String,
  val sizeMb: Double,
  val codec: String? = null,
  val durationSec: Long? = null,
  val cookie: String? = null,
  val headers: Map<String, String> = emptyMap(),
) {
  val isPlayableDirect: Boolean
    get() = !format.equals("DASH", ignoreCase = true) && !url.contains(".mpd", ignoreCase = true)
}

@Immutable
data class MovieBoxSubtitle(
  val id: String,
  val language: String,
  val code: String,
  val url: String,
  val sizeBytes: Long? = null,
)

@Immutable
data class MovieBoxSuggestion(
  val title: String,
  val slug: String,
  val subjectId: String,
)

@Immutable
data class MovieBoxRichDetail(
  val movie: Movie,
  val subjectId: String,
  val detailPath: String,
  val dubs: List<MovieBoxDub>,
  val seasons: List<MovieBoxSeason>,
)

@Immutable
data class MovieBoxHomeFeed(
  val spotlight: List<Movie>,
  val rails: List<MovieRail>,
  val allMovies: List<Movie>,
)
