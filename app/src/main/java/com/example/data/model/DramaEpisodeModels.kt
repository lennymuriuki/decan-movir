package com.example.data.model

import androidx.compose.runtime.Immutable
import com.example.data.remote.moviebox.MovieBoxSeason

@Immutable
data class DramaEpisodeItem(
  val season: Int,
  val episode: Int,
  val globalIndex: Int,
  val totalCount: Int,
  val title: String,
  val subtitleLabel: String,
  val description: String,
)

object DramaExperienceDetector {
  private val DRAMA_GENRE_KEYWORDS =
    listOf(
      "drama",
      "melodrama",
      "romance",
      "k-drama",
      "kdrama",
      "c-drama",
      "cdrama",
      "short drama",
      "soap",
      "telenovela",
      "mini-series",
      "miniseries",
    )

  /**
   * Returns true ONLY for episodic dramas so that normal Movies, non-drama TV Series,
   * and Anime continue using their existing detail screens and standard Theater Player.
   */
  fun isEpisodicDrama(
    movie: Movie?,
    seasons: List<MovieBoxSeason> = emptyList(),
  ): Boolean {
    if (movie == null) return false

    // Anime always keeps its dedicated Anime & Theater Player flow
    if (
      movie.mediaType.equals("Anime", ignoreCase = true) ||
        movie.id.startsWith("mal_") ||
        movie.id.startsWith("anilist_") ||
        movie.sourceProvider.equals("Decan Anime", ignoreCase = true)
    ) {
      return false
    }

    val totalEpisodes = seasons.sumOf { it.episodeCount.coerceAtLeast(0) }
    val isExplicitDramaType = movie.mediaType.equals("Drama", ignoreCase = true)
    val isEpisodicType =
      isExplicitDramaType ||
        movie.mediaType.equals("Series", ignoreCase = true) ||
        seasons.isNotEmpty()

    // Standalone movies (not marked Drama mediaType and no episodic seasons) stay in Theater Player
    if (!isEpisodicType) return false
    if (movie.mediaType.equals("Movie", ignoreCase = true) && totalEpisodes <= 1) return false

    if (isExplicitDramaType) return true

    val genreLower = movie.genre.lowercase()
    return DRAMA_GENRE_KEYWORDS.any { kw -> genreLower.contains(kw) }
  }

  fun buildOrderedDramaEpisodes(
    movie: Movie?,
    seasons: List<MovieBoxSeason>,
  ): List<DramaEpisodeItem> {
    val cleanTitle = movie?.title?.ifBlank { "Drama" } ?: "Drama"
    val baseDescription =
      movie?.synopsis?.takeIf { it.isNotBlank() }
        ?: "Swipe up for the next episode or tap to toggle controls."

    if (seasons.isEmpty()) {
      return listOf(
        DramaEpisodeItem(
          season = 1,
          episode = 1,
          globalIndex = 0,
          totalCount = 1,
          title = "$cleanTitle • Episode 1",
          subtitleLabel = "Season 01 • Episode 01",
          description = baseDescription,
        )
      )
    }

    val sortedSeasons = seasons.sortedBy { it.season }
    val total = sortedSeasons.sumOf { it.episodeCount.coerceAtLeast(1) }
    var globalIdx = 0
    return buildList(total) {
      for (seasonObj in sortedSeasons) {
        val count = seasonObj.episodeCount.coerceAtLeast(1)
        for (ep in 1..count) {
          add(
            DramaEpisodeItem(
              season = seasonObj.season,
              episode = ep,
              globalIndex = globalIdx,
              totalCount = total,
              title = "$cleanTitle • Episode $ep",
              subtitleLabel = "Season %02d • Episode %02d".format(seasonObj.season, ep),
              description = baseDescription,
            )
          )
          globalIdx++
        }
      }
    }
  }

  fun findEpisodePageIndex(
    episodes: List<DramaEpisodeItem>,
    season: Int,
    episode: Int,
  ): Int {
    if (episodes.isEmpty()) return 0
    if (season <= 0 || episode <= 0) return 0
    val exact = episodes.indexOfFirst { it.season == season && it.episode == episode }
    if (exact >= 0) return exact
    val sameSeason = episodes.indexOfFirst { it.season == season }
    return if (sameSeason >= 0) sameSeason else 0
  }
}
