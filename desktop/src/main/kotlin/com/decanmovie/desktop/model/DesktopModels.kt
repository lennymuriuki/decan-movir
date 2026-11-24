package com.decanmovie.desktop.model

import androidx.compose.runtime.Immutable

enum class DesktopDestination(val label: String) {
  HOME("Home"),
  MOVIES("Movies"),
  SERIES("Series"),
  ANIME("Anime"),
  DRAMAS("Dramas"),
  SEARCH("Search"),
  WATCHLIST("Watchlist"),
  DOWNLOADS("Downloads"),
  HISTORY("History"),
  SETTINGS("Settings"),
}

enum class CategorySortMode(val label: String) {
  FEATURED("Featured"),
  TOP_RANKED("Top Ranked"),
  HIGHEST_RATED("Top IMDb"),
  NEWEST("Newest"),
}

enum class AnimeSortMode(val label: String) {
  TOP_RANKED("Top Ranked"),
  POPULAR("Most Popular"),
  AIRING("Airing Now"),
  NEWEST("Newest"),
}

enum class WatchStatus(val label: String) {
  WANT_TO_WATCH("Watchlist"),
  WATCHING("Watching"),
  WATCHED("Watched");

  companion object {
    fun fromString(raw: String?): WatchStatus =
      entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WANT_TO_WATCH
  }
}

enum class PlaybackAspectMode(val label: String) {
  FIT("Fit"),
  FILL("Fill"),
  RATIO_16_9("16:9"),
  RATIO_21_9("21:9"),
}

@Immutable
data class Movie(
  val id: String,
  val trackId: Long = 0L,
  val title: String,
  val director: String = "",
  val genre: String = "Cinema",
  val releaseYear: String = "2025",
  val releaseDateLabel: String = "2025",
  val posterUrl: String = "",
  val backdropUrl: String = "",
  val previewUrl: String? = null,
  val synopsis: String = "",
  val runtimeMinutes: Int = 0,
  val contentRating: String = "HD",
  val subjectId: String = "",
  val mediaType: String = "Movie",
  val imdbRating: String? = null,
  val country: String? = null,
  val language: String? = null,
  val detailPath: String? = null,
  val sourceProvider: String = "Decan",
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

@Immutable
data class WatchlistEntry(
  val movie: Movie,
  val watchStatus: WatchStatus = WatchStatus.WANT_TO_WATCH,
  val favorite: Boolean = false,
  val personalRating: Int = 0,
  val personalNote: String = "",
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Immutable
data class PlaybackHistoryItem(
  val movieId: String,
  val title: String,
  val posterUrl: String,
  val mediaType: String,
  val genre: String = "",
  val subjectId: String = "",
  val detailPath: String? = null,
  val season: Int,
  val episode: Int,
  val positionMs: Long,
  val durationMs: Long,
  val updatedAtMs: Long,
  val episodePositions: Map<String, Long> = emptyMap(),
) {
  fun episodeKey(s: Int, e: Int): String = "${s.coerceAtLeast(0)}:${e.coerceAtLeast(0)}"

  fun progressFraction(): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
  }
}

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
   * Returns true ONLY for episodic dramas so normal Movies, non-drama TV Series,
   * and Anime continue using their standard Desktop Theater Player & detail screens.
   */
  fun isEpisodicDrama(
    movie: Movie?,
    seasons: List<com.decanmovie.desktop.data.remote.moviebox.MovieBoxSeason> = emptyList(),
  ): Boolean {
    if (movie == null) return false

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

    if (!isEpisodicType) return false
    if (movie.mediaType.equals("Movie", ignoreCase = true) && totalEpisodes <= 1) return false
    if (isExplicitDramaType) return true

    val genreLower = movie.genre.lowercase()
    return DRAMA_GENRE_KEYWORDS.any { kw -> genreLower.contains(kw) }
  }

  fun buildOrderedDramaEpisodes(
    movie: Movie?,
    seasons: List<com.decanmovie.desktop.data.remote.moviebox.MovieBoxSeason>,
  ): List<DramaEpisodeItem> {
    val cleanTitle = movie?.title?.ifBlank { "Drama" } ?: "Drama"
    val baseDescription =
      movie?.synopsis?.takeIf { it.isNotBlank() }
        ?: "Scroll or use Up/Down arrows to switch drama episodes."

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

enum class DownloadTaskStatus {
  QUEUED,
  DOWNLOADING,
  PAUSED,
  COMPLETED,
  FAILED,
  CANCELED,
}

@Immutable
data class DownloadTaskState(
  val id: String,
  val title: String,
  val displayLabel: String,
  val url: String,
  val destinationPath: String,
  val folderPath: String,
  val isSubtitle: Boolean = false,
  val season: Int = 0,
  val episode: Int = 0,
  val bytesDownloaded: Long = 0L,
  val totalBytes: Long = 0L,
  val status: DownloadTaskStatus = DownloadTaskStatus.QUEUED,
  val errorMessage: String? = null,
) {
  val progress: Float
    get() =
      if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
      else 0f
}

@Immutable
data class LocalMediaItem(
  val name: String,
  val filePath: String,
  val sizeBytes: Long,
  val category: String,
  val titleFolder: String,
  val seasonFolder: String?,
  val subtitlePath: String?,
  val modifiedAtMs: Long,
)

@Immutable
data class DesktopSettings(
  val downloadDirectory: String,
  val preferredQuality: Int = 1080,
  val defaultPlaybackSpeed: Float = 1.0f,
  val autoPlayNextEpisode: Boolean = true,
  val autoLoadSubtitles: Boolean = true,
  val includeSubtitlesInDownloads: Boolean = true,
  val autoCheckUpdates: Boolean = true,
  val lastUpdateCheckMs: Long = 0L,
  val dismissedReleaseTag: String = "",
  val cachedReleaseJson: String = "",
  val githubRepo: String = "lennymuriuki/decan-movir",
  val minimizeToTrayOnClose: Boolean = false,
  val launchAtStartup: Boolean = false,
  val enableDesktopNotifications: Boolean = true,
  val compactCards: Boolean = false,
  val preferPortableUpdates: Boolean = false,
)
