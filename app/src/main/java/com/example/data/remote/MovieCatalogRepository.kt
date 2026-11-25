package com.example.data.remote

import android.content.SharedPreferences
import com.example.data.model.Movie
import com.example.data.model.MovieRail
import com.example.data.remote.moviebox.MovieBoxEngine
import com.example.data.remote.moviebox.MovieBoxRichDetail
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.data.remote.moviebox.MovieBoxSuggestion
import com.example.data.remote.moviebox.SubtitleCue
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

enum class CategorySortMode(val label: String) {
  FEATURED("Featured"),
  TOP_RANKED("Top Ranked"),
  HIGHEST_RATED("Top IMDb"),
  NEWEST("Newest"),
}

data class CatalogFeedResult(
  val spotlight: List<Movie>,
  val rankings: List<Movie>,
  val anime: List<Movie>,
  val recommendations: List<Movie>,
  val rails: List<MovieRail>,
  val allMovies: List<Movie>,
  val trendingKeywords: List<String>,
  val isOfflineFallback: Boolean,
)

class MovieCatalogRepository(
  private val api: ItunesMovieApi = createDefaultApi(),
  private val movieBox: MovieBoxEngine = MovieBoxEngine(),
  private val jikanTvMaze: JikanTvMazeService = JikanTvMazeService(),
  private val snapshotPrefs: SharedPreferences? = null,
  private val tmdbService: TmdbService = TmdbService(),
) {
  private val cache = ConcurrentHashMap<String, Movie>()
  @Volatile private var cachedHomeFeed: Pair<Long, CatalogFeedResult>? = null
  private val richDetailCache = ConcurrentHashMap<String, Pair<Long, MovieBoxRichDetail>>()
  private val streamCache = ConcurrentHashMap<String, Pair<Long, List<MovieBoxStream>>>()
  private val subtitleCache = ConcurrentHashMap<String, Pair<Long, List<MovieBoxSubtitle>>>()
  private val recCache = ConcurrentHashMap<String, Pair<Long, List<Movie>>>()

  fun getCachedHomeSnapshot(): CatalogFeedResult? {
    cachedHomeFeed?.second?.takeIf { !it.isOfflineFallback && it.allMovies.isNotEmpty() }?.let {
      return it
    }
    val raw = snapshotPrefs?.getString(SNAPSHOT_KEY, null) ?: return null
    return runCatching {
      val obj = JSONObject(raw)
      val savedAt = obj.optLong("savedAt", 0L)
      if (System.currentTimeMillis() - savedAt > SNAPSHOT_MAX_AGE_MS) return null
      val all = parseSnapshotMovies(obj.optJSONArray("allMovies")).filter(::isValidCinemaEntry)
      if (all.isEmpty()) return null
      all.forEach { cache[it.id] = it }
      val spotlight = parseSnapshotMovies(obj.optJSONArray("spotlight")).filter(::isValidCinemaEntry)
      val rankings =
        parseSnapshotMovies(obj.optJSONArray("rankings"))
          .filter(::isValidCinemaEntry)
          .mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) }
      val anime = parseSnapshotMovies(obj.optJSONArray("anime")).filter(::isGenuineAnime)
      val recommendations =
        parseSnapshotMovies(obj.optJSONArray("recommendations")).filter(::isValidCinemaEntry)
      val rails = parseSnapshotRails(obj.optJSONArray("rails"))
      val keywords = buildList {
        val arr = obj.optJSONArray("trendingKeywords") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val s = arr.optString(i)
          if (s.isNotBlank()) add(s)
        }
      }
      CatalogFeedResult(
        spotlight = spotlight.ifEmpty { all.take(5) },
        rankings = rankings,
        anime = anime,
        recommendations = recommendations.ifEmpty { all.take(12) },
        rails = rails,
        allMovies = all,
        trendingKeywords = keywords,
        isOfflineFallback = false,
      )
    }.getOrNull()
  }

  suspend fun loadHomeCatalog(forceRefresh: Boolean = false): CatalogFeedResult = coroutineScope {
    val cached = cachedHomeFeed
    if (!forceRefresh && cached != null && !cached.second.isOfflineFallback &&
        (System.currentTimeMillis() - cached.first) < HOME_CACHE_TTL_MS
    ) {
      return@coroutineScope cached.second
    }

    try {
      val tokenWarmDef = async {
        withTimeoutOrNull(4500L) { runCatching { movieBox.bootstrapToken() }.getOrNull() }
      }
      val mboxHomeDeferred = async {
        withTimeoutOrNull(5500L) { runCatching { movieBox.getHome() }.getOrNull() }
      }
      val mboxRankingsDeferred = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getRankings(page = 1, perPage = 30) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val mboxMoviesDeferred = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getMovies(page = 1, perPage = 24) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val mboxSeriesDeferred = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getTvSeries(page = 1, perPage = 24) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val mboxAnimeDeferred = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getAnime(page = 1, perPage = 24) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val jikanAnimeDeferred = async {
        withTimeoutOrNull(4500L) {
          runCatching {
            jikanTvMaze.browseAnime("All", AnimeSortMode.TOP_RANKED, page = 1, limit = 18)
          }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val mboxTrendingDeferred = async {
        withTimeoutOrNull(4000L) {
          runCatching { movieBox.getTrendingKeywords() }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val topItunesDeferred = async {
        withTimeoutOrNull(4500L) {
          runCatching { api.getTopMovies().feed?.entry.orEmpty().mapNotNull { it.toDomain() } }
            .getOrDefault(emptyList())
        }.orEmpty()
      }
      val sciFiItunesDeferred = async {
        withTimeoutOrNull(4500L) {
          runCatching {
              api.searchMovies(term = "sci-fi", limit = 15).results.mapNotNull { it.toDomain() }
            }
            .getOrDefault(emptyList())
        }.orEmpty()
      }
      val tmdbTrendingDeferred =
        if (tmdbService.isConfigured) {
          async {
            withTimeoutOrNull(5000L) {
              runCatching { tmdbService.getTrendingAll(page = 1) }.getOrDefault(emptyList())
            }.orEmpty()
          }
        } else null
      val tmdbMoviesDeferred =
        if (tmdbService.isConfigured) {
          async {
            withTimeoutOrNull(5000L) {
              runCatching { tmdbService.getPopularMovies(page = 1) }.getOrDefault(emptyList())
            }.orEmpty()
          }
        } else null
      val tmdbTopRatedDeferred =
        if (tmdbService.isConfigured) {
          async {
            withTimeoutOrNull(5000L) {
              runCatching { tmdbService.getTopRatedMovies(page = 1) }.getOrDefault(emptyList())
            }.orEmpty()
          }
        } else null
      val tmdbTvDeferred =
        if (tmdbService.isConfigured) {
          async {
            withTimeoutOrNull(5000L) {
              runCatching { tmdbService.getPopularTv(page = 1) }.getOrDefault(emptyList())
            }.orEmpty()
          }
        } else null

      tokenWarmDef.await()
      val mboxHome = mboxHomeDeferred.await()
      val mboxRankings = mboxRankingsDeferred.await().filter(::isValidCinemaEntry)
      val mboxMovies = mboxMoviesDeferred.await().filter(::isValidCinemaEntry)
      val mboxSeries = mboxSeriesDeferred.await().filter(::isValidCinemaEntry)
      val mboxAnime = mboxAnimeDeferred.await().filter(::isGenuineAnime)
      val jikanAnime = jikanAnimeDeferred.await().filter(::isGenuineAnime)
      val trendingKeywords = mboxTrendingDeferred.await()
      val topItunes = topItunesDeferred.await().filter(::isValidCinemaEntry)
      val sciFiItunes = sciFiItunesDeferred.await().filter(::isValidCinemaEntry)
      val tmdbTrending = tmdbTrendingDeferred?.await().orEmpty().filter(::isValidCinemaEntry)
      val tmdbMovies = tmdbMoviesDeferred?.await().orEmpty().filter(::isValidCinemaEntry)
      val tmdbTopRated = tmdbTopRatedDeferred?.await().orEmpty().filter(::isValidCinemaEntry)
      val tmdbTv = tmdbTvDeferred?.await().orEmpty().filter(::isValidCinemaEntry)

      val homeSpotlight = mboxHome?.spotlight.orEmpty().filter(::isValidCinemaEntry)
      val homeAll = mboxHome?.allMovies.orEmpty().filter(::isValidCinemaEntry)
      val homeRails =
        mboxHome?.rails.orEmpty().mapNotNull { rail ->
          val cleanMovies = rail.movies.filter(::isValidCinemaEntry)
          if (cleanMovies.isEmpty()) null else rail.copy(movies = cleanMovies)
        }

      val streamPool =
        (homeSpotlight + mboxRankings + homeAll + mboxMovies + mboxSeries + mboxAnime)
          .filter { it.subjectId.isNotBlank() }
      val tmdbLookupPool =
        (tmdbTrending + tmdbMovies + tmdbTopRated + tmdbTv).associateBy { normalizeTitleKey(it.title) }

      val enrichedMboxMovies = enrichStreamListWithMetadata(mboxMovies, tmdbLookupPool)
      val enrichedMboxSeries = enrichStreamListWithMetadata(mboxSeries, tmdbLookupPool)
      val enrichedMboxRankings = enrichStreamListWithMetadata(mboxRankings, tmdbLookupPool)
      val enrichedTmdbTrending = linkPlayableMatches(tmdbTrending, streamPool)
      val enrichedTmdbTopRated = linkPlayableMatches(tmdbTopRated, streamPool)

      val mboxAnimeByTitle = mboxAnime.associateBy { normalizeTitleKey(it.title) }
      val enrichedHomeAnime =
        jikanAnime.map { anime ->
          val streamMatch = mboxAnimeByTitle[normalizeTitleKey(anime.title)]
          if (streamMatch != null) {
            anime.copy(subjectId = streamMatch.subjectId, detailPath = streamMatch.detailPath)
          } else anime
        }

      val combined =
        (homeSpotlight +
            enrichedMboxRankings +
            homeAll +
            enrichedMboxMovies +
            enrichedMboxSeries +
            mboxAnime +
            enrichedHomeAnime +
            enrichedTmdbTrending +
            linkPlayableMatches(tmdbMovies, streamPool) +
            enrichedTmdbTopRated +
            linkPlayableMatches(tmdbTv, streamPool) +
            topItunes +
            sciFiItunes)
          .filter(::isValidCinemaEntry)
          .distinctBy { it.id }

      if (combined.isEmpty()) {
        val fallback = OfflineCatalogFallback.movies
        fallback.forEach { cache[it.id] = it }
        return@coroutineScope CatalogFeedResult(
          spotlight = fallback.take(5),
          rankings = fallback.mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) },
          anime = fallback.filter(::isGenuineAnime),
          recommendations = fallback.shuffled(),
          rails = buildOfflineRails(fallback),
          allMovies = fallback,
          trendingKeywords = listOf("Avatar", "Breaking Bad", "Anime", "Oppenheimer", "Sci-Fi"),
          isOfflineFallback = true,
        )
      }

      combined.forEach { cache[it.id] = it }

      val effectiveRankings =
        if (enrichedMboxRankings.isNotEmpty()) {
          enrichedMboxRankings.mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) }
        } else if (enrichedTmdbTrending.isNotEmpty()) {
          enrichedTmdbTrending.take(20).mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) }
        } else {
          combined
            .sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .take(15)
            .mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) }
        }

      val seenAnimeTitles = HashSet<String>()
      val effectiveAnime =
        (enrichedHomeAnime + mboxAnime + combined.filter(::isGenuineAnime))
          .filter(::isGenuineAnime)
          .filter { seenAnimeTitles.add(normalizeTitleKey(it.title)) }

      val recommendations =
        (homeRails.firstOrNull()?.movies.orEmpty() +
            enrichedTmdbTrending.take(6) +
            effectiveRankings.take(6) +
            enrichedMboxMovies.take(6))
          .filter(::isValidCinemaEntry)
          .distinctBy { it.id }
          .take(14)

      val spotlight =
        (homeSpotlight + enrichedTmdbTrending.take(4) + effectiveRankings.take(3) + enrichedMboxMovies.take(3))
          .filter(::isValidCinemaEntry)
          .distinctBy { it.id }
          .take(7)

      val rails = buildList {
        if (enrichedTmdbTrending.isNotEmpty()) {
          add(
            MovieRail(
              id = "tmdb_trending",
              title = "Trending Worldwide",
              subtitle = "Weekly top picks ready to stream",
              movies = enrichedTmdbTrending.take(18),
            )
          )
        }
        if (enrichedMboxMovies.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_movies",
              title = "Trending Movies",
              subtitle = "1080p stream & multi-subtitles",
              movies = enrichedMboxMovies,
            )
          )
        }
        if (enrichedMboxSeries.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_series",
              title = "Popular TV Series",
              subtitle = "Seasons, episodes & batch downloads",
              movies = enrichedMboxSeries,
            )
          )
        }
        if (enrichedTmdbTopRated.isNotEmpty()) {
          add(
            MovieRail(
              id = "tmdb_top_rated",
              title = "Critically Acclaimed",
              subtitle = "Highest-rated films worldwide",
              movies = enrichedTmdbTopRated.take(18),
            )
          )
        }
        if (effectiveAnime.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_anime",
              title = "Anime & Animation",
              subtitle = "Subbed & dubbed series and films",
              movies = effectiveAnime,
            )
          )
        }
        homeRails.take(5).forEach { add(it) }
        if (topItunes.isNotEmpty()) {
          add(
            MovieRail(
              id = "top_box_office",
              title = "Top Box Office",
              subtitle = "Official releases & trailers",
              movies = topItunes,
            )
          )
        }
        if (sciFiItunes.isNotEmpty()) {
          add(
            MovieRail(
              id = "sci_fi_trailers",
              title = "Sci-Fi & Trailers",
              subtitle = "Instant previews",
              movies = sciFiItunes,
            )
          )
        }
      }

      val result =
        CatalogFeedResult(
          spotlight = spotlight,
          rankings = effectiveRankings,
          anime = effectiveAnime,
          recommendations = recommendations,
          rails = rails,
          allMovies = combined,
          trendingKeywords =
            trendingKeywords.ifEmpty {
              listOf("Avatar", "Breaking Bad", "Attack on Titan", "Dune", "Action")
            },
          isOfflineFallback = false,
        )
      cachedHomeFeed = System.currentTimeMillis() to result
      persistHomeSnapshot(result)
      result
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      val fallback = OfflineCatalogFallback.movies
      fallback.forEach { cache[it.id] = it }
      CatalogFeedResult(
        spotlight = fallback.take(5),
        rankings = fallback.mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) },
        anime = fallback.filter { it.mediaType.equals("Anime", ignoreCase = true) },
        recommendations = fallback,
        rails = buildOfflineRails(fallback),
        allMovies = fallback,
        trendingKeywords = listOf("Avatar", "Breaking Bad", "Anime", "Oppenheimer"),
        isOfflineFallback = true,
      )
    }
  }

  suspend fun loadGenreFeed(genre: String, page: Int = 1): List<Movie> =
    loadCategoryCatalog(
      category = genre,
      mediaFilterName = "ALL",
      sortMode = CategorySortMode.FEATURED,
      page = page,
    )

  suspend fun loadCategoryCatalog(
    category: String,
    mediaFilterName: String = "ALL",
    sortMode: CategorySortMode = CategorySortMode.FEATURED,
    page: Int = 1,
  ): List<Movie> = coroutineScope {
    val cleanCategory = category.trim().ifBlank { "All" }
    val wantAnimeOnly = mediaFilterName == "ANIME" || cleanCategory.equals("Anime", ignoreCase = true)
    if (wantAnimeOnly) {
      val animeSort =
        when (sortMode) {
          CategorySortMode.TOP_RANKED, CategorySortMode.HIGHEST_RATED -> AnimeSortMode.TOP_RANKED
          CategorySortMode.NEWEST -> AnimeSortMode.NEWEST
          CategorySortMode.FEATURED -> AnimeSortMode.POPULAR
        }
      val animeGenre =
        if (cleanCategory.equals("All", ignoreCase = true) || cleanCategory.equals("Anime", ignoreCase = true)) {
          "All"
        } else {
          cleanCategory
        }
      val animeResults = loadAnimeCatalog(genreLabel = animeGenre, sortMode = animeSort, page = page)
      return@coroutineScope sortCatalogMovies(animeResults, sortMode)
    }

    val mboxSort =
      when (sortMode) {
        CategorySortMode.NEWEST -> "LATEST"
        else -> "RECOMMEND"
      }
    val mboxGenre =
      if (cleanCategory.equals("All", ignoreCase = true)) "ALL" else cleanCategory

    val wantMovies = mediaFilterName == "ALL" || mediaFilterName == "MOVIES" || mediaFilterName == "RANKINGS"
    val wantSeries = mediaFilterName == "ALL" || mediaFilterName == "SERIES" || mediaFilterName == "RANKINGS"

    val moviesDef =
      if (wantMovies) {
        async {
          withTimeoutOrNull(5000L) {
            runCatching {
              movieBox.getMovies(page = page, perPage = 24, sort = mboxSort, genre = mboxGenre)
            }.getOrDefault(emptyList())
          }.orEmpty()
        }
      } else null

    val seriesDef =
      if (wantSeries) {
        async {
          withTimeoutOrNull(5000L) {
            runCatching {
              movieBox.getTvSeries(page = page, perPage = 20, sort = mboxSort, genre = mboxGenre)
            }.getOrDefault(emptyList())
          }.orEmpty()
        }
      } else null

    val jikanDef =
      if (
        (mediaFilterName == "ALL" || mediaFilterName == "RANKINGS") &&
          JikanTvMazeService.ANIME_GENRES.any { it.label.equals(cleanCategory, ignoreCase = true) }
      ) {
        async {
          withTimeoutOrNull(4500L) {
            val animeSort =
              if (sortMode == CategorySortMode.NEWEST) AnimeSortMode.NEWEST else AnimeSortMode.TOP_RANKED
            runCatching { jikanTvMaze.browseAnime(cleanCategory, animeSort, page, limit = 16) }
              .getOrDefault(emptyList())
          }.orEmpty()
        }
      } else null

    val tmdbDef =
      if (tmdbService.isConfigured) {
        async {
          withTimeoutOrNull(5000L) {
            runCatching {
              tmdbService.discoverByCategory(
                category = cleanCategory,
                mediaFilterName = mediaFilterName,
                sortMode = sortMode,
                page = page,
              )
            }.getOrDefault(emptyList())
          }.orEmpty()
        }
      } else null

    val mboxMoviesHits = moviesDef?.await().orEmpty()
    val mboxSeriesHits = seriesDef?.await().orEmpty()
    val jikanHits = jikanDef?.await().orEmpty()
    val tmdbHits = tmdbDef?.await().orEmpty()

    val streamPool =
      (mboxMoviesHits + mboxSeriesHits + cache.values.filter { it.subjectId.isNotBlank() })
        .filter { it.subjectId.isNotBlank() }
    val tmdbLookup = tmdbHits.associateBy { normalizeTitleKey(it.title) }
    val enrichedMbox = enrichStreamListWithMetadata(mboxMoviesHits + mboxSeriesHits, tmdbLookup)
    val linkedTmdb = linkPlayableMatches(tmdbHits, streamPool)

    val combined =
      (enrichedMbox + linkedTmdb + linkPlayableMatches(jikanHits, streamPool))
        .filter(::isValidCinemaEntry)
        .groupBy { normalizeTitleKey(it.title) }
        .values
        .mapNotNull { mergeProviderGroup(it) }

    combined.forEach { cache[it.id] = it }
    sortCatalogMovies(combined, sortMode)
  }

  suspend fun loadAnimeCatalog(
    genreLabel: String = "All",
    sortMode: AnimeSortMode = AnimeSortMode.TOP_RANKED,
    page: Int = 1,
  ): List<Movie> = coroutineScope {
    val jikanDef = async {
      withTimeoutOrNull(5000L) {
        runCatching { jikanTvMaze.browseAnime(genreLabel, sortMode, page, limit = 24) }
          .getOrDefault(emptyList())
      }.orEmpty()
    }
    val mboxDef = async {
      withTimeoutOrNull(5000L) {
        runCatching {
          val mboxSort =
            when (sortMode) {
              AnimeSortMode.NEWEST -> "LATEST"
              AnimeSortMode.TOP_RANKED, AnimeSortMode.POPULAR, AnimeSortMode.AIRING -> "RECOMMEND"
            }
          val mboxGenre =
            if (genreLabel.equals("All", ignoreCase = true)) "Animation" else genreLabel
          movieBox.getFilteredFeed(
            tabId = 5,
            page = page,
            perPage = 20,
            sort = mboxSort,
            genre = mboxGenre,
            country = "Japan",
          )
        }.getOrDefault(emptyList())
      }.orEmpty()
    }
    val tmdbAnimeDef =
      if (tmdbService.isConfigured) {
        async {
          withTimeoutOrNull(5000L) {
            runCatching { tmdbService.discoverAnime(genreLabel, sortMode, page) }
              .getOrDefault(emptyList())
          }.orEmpty()
        }
      } else null

    val jikanHits = jikanDef.await().filter(::isGenuineAnime)
    val tmdbAnimeHits = tmdbAnimeDef?.await().orEmpty().filter(::isGenuineAnime)
    val mboxHits =
      mboxDef.await().filter { m ->
        isGenuineAnime(m) &&
          (genreLabel.equals("All", ignoreCase = true) ||
            m.genre.contains(genreLabel, ignoreCase = true))
      }

    // Merge Jikan/AniList & TMDb rankings with streamable MovieBox items matched by normalized title
    val cachedAnimeStreams =
      cache.values.filter { it.subjectId.isNotBlank() && isGenuineAnime(it) }
    val mboxByTitle =
      (cachedAnimeStreams + mboxHits).associateBy { normalizeTitleKey(it.title) }
    val enrichedJikan =
      (jikanHits + tmdbAnimeHits).map { anime ->
        val streamMatch = mboxByTitle[normalizeTitleKey(anime.title)]
        if (streamMatch != null) {
          anime.copy(
            subjectId = streamMatch.subjectId,
            detailPath = streamMatch.detailPath,
          )
        } else anime
      }

    val seenTitles = enrichedJikan.mapTo(HashSet()) { normalizeTitleKey(it.title) }
    val uniqueMbox = mboxHits.filter { seenTitles.add(normalizeTitleKey(it.title)) }
    val merged =
      sortAnimeMovies(
        (enrichedJikan + uniqueMbox).filter(::isGenuineAnime).distinctBy { it.id },
        sortMode,
      )
    merged.forEach { cache[it.id] = it }
    merged
  }

  suspend fun loadMorePage(isSeries: Boolean, isAnime: Boolean, page: Int): List<Movie> {
    val fetched =
      when {
        isAnime -> loadAnimeCatalog(genreLabel = "All", sortMode = AnimeSortMode.TOP_RANKED, page = page)
        isSeries -> runCatching { movieBox.getTvSeries(page = page, perPage = 24) }.getOrDefault(emptyList())
        else -> runCatching { movieBox.getMovies(page = page, perPage = 24) }.getOrDefault(emptyList())
      }
    fetched.forEach { cache[it.id] = it }
    return fetched
  }

  suspend fun resolvePlayableSubject(movie: Movie): Movie? {
    if (movie.subjectId.isNotBlank()) return movie
    val cacheHit = cache[movie.id]
    if (cacheHit != null && cacheHit.subjectId.isNotBlank()) return cacheHit

    val targetNorm = normalizeTitleKey(movie.title)
    val candidates =
      runCatching { movieBox.search(movie.title, page = 1, perPage = 10) }.getOrDefault(emptyList()) +
        runCatching { movieBox.searchSuggestFull(movie.title).second }.getOrDefault(emptyList())

    val best =
      candidates.firstOrNull { normalizeTitleKey(it.title) == targetNorm && it.subjectId.isNotBlank() }
        ?: candidates.firstOrNull {
          normalizeTitleKey(it.title).contains(targetNorm) && it.subjectId.isNotBlank()
        }
        ?: candidates.firstOrNull { it.subjectId.isNotBlank() }
        ?: return null

    val updated =
      movie.copy(
        subjectId = best.subjectId,
        detailPath = best.detailPath ?: movie.detailPath,
      )
    cache[movie.id] = updated
    cache[best.id] = best
    return updated
  }

  suspend fun getRecommendations(subjectId: String, genreHint: String? = null): List<Movie> {
    if (subjectId.isNotBlank()) {
      val hit = recCache[subjectId]
      if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
        return hit.second
      }
    }
    val remote =
      if (subjectId.isNotBlank()) {
        runCatching { movieBox.getRecommendations(subjectId) }.getOrDefault(emptyList())
      } else {
        emptyList()
      }
    remote.forEach { cache[it.id] = it }
    if (remote.isNotEmpty()) {
      if (subjectId.isNotBlank()) {
        recCache[subjectId] = System.currentTimeMillis() to remote
      }
      return remote
    }
    val pool = (cache.values.toList() + OfflineCatalogFallback.movies).distinctBy { it.id }
    val matching =
      if (!genreHint.isNullOrBlank()) {
        pool.filter { it.genre.contains(genreHint, ignoreCase = true) && it.subjectId != subjectId }
      } else {
        emptyList()
      }
    return (matching + pool.filter { it.subjectId != subjectId }).distinctBy { it.id }.take(12)
  }

  suspend fun searchMovies(query: String): Pair<List<Movie>, Boolean> {
    val (movies, _, offline) = searchCatalogWithSuggestions(query)
    return movies to offline
  }

  suspend fun searchCatalogWithSuggestions(
    query: String,
  ): Triple<List<Movie>, List<MovieBoxSuggestion>, Boolean> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      if (cache.size < 10) {
        val homeFeed = runCatching { loadHomeCatalog() }.getOrNull()
        if (homeFeed != null && homeFeed.allMovies.isNotEmpty()) {
          return@coroutineScope Triple(
            homeFeed.allMovies,
            emptyList(),
            homeFeed.isOfflineFallback,
          )
        }
      }
      val current = cache.values.toList().ifEmpty { OfflineCatalogFallback.movies }
      return@coroutineScope Triple(current, emptyList(), current === OfflineCatalogFallback.movies)
    }

    try {
      val directIdDeferred =
        if (cleanQuery.length >= 10 && cleanQuery.all(Char::isDigit)) {
          async { runCatching { getMovieBoxRichDetail(cleanQuery)?.movie }.getOrNull() }
        } else null

      val mboxDeferred = async {
        runCatching { movieBox.search(cleanQuery, page = 1, perPage = 28) }
          .getOrDefault(emptyList())
      }
      val suggestDeferred = async {
        runCatching { movieBox.searchSuggestFull(cleanQuery) }
          .getOrDefault(emptyList<MovieBoxSuggestion>() to emptyList())
      }
      val itunesDeferred = async {
        runCatching {
            api.searchMovies(term = cleanQuery, limit = 20).results.mapNotNull { it.toDomain() }
          }
          .getOrDefault(emptyList())
      }
      val tvMazeDeferred = async {
        runCatching { jikanTvMaze.searchTvShows(cleanQuery, limit = 15) }.getOrDefault(emptyList())
      }
      val jikanDeferred = async {
        runCatching { jikanTvMaze.searchAnime(cleanQuery, limit = 15) }.getOrDefault(emptyList())
      }
      val tmdbDeferred =
        if (tmdbService.isConfigured) {
          async {
            withTimeoutOrNull(4500L) {
              runCatching { tmdbService.searchMulti(cleanQuery, page = 1) }.getOrDefault(emptyList())
            }.orEmpty()
          }
        } else null

      val directMovie = directIdDeferred?.await()
      val mboxHits = mboxDeferred.await()
      val (suggestions, suggestMovies) = suggestDeferred.await()
      val itunesHits = itunesDeferred.await()
      val tvHits = tvMazeDeferred.await()
      val animeHits = jikanDeferred.await()
      val tmdbHits = tmdbDeferred?.await().orEmpty()

      val rawCombined =
        (listOfNotNull(directMovie) +
            mboxHits +
            suggestMovies +
            tmdbHits +
            tvHits +
            animeHits +
            itunesHits)
          .filter(::isValidCinemaEntry)
      // Prefer entries that already have stream subjectId while merging richer TMDb metadata/artwork
      val deduped =
        rawCombined
          .distinctBy { it.id }
          .groupBy { normalizeTitleKey(it.title) }
          .values
          .mapNotNull { group -> mergeProviderGroup(group) }

      deduped.forEach { cache[it.id] = it }

      val cacheExtras =
        cache.values.filter { cached ->
          isValidCinemaEntry(cached) &&
            deduped.none { normalizeTitleKey(it.title) == normalizeTitleKey(cached.title) } &&
            matchesQueryTokens(cleanQuery, cached)
        }
      val combinedAll = (deduped + cacheExtras).distinctBy { it.id }
      val ranked = rankSearchResults(cleanQuery, combinedAll)

      Triple(ranked, suggestions, deduped.isEmpty() && ranked.isEmpty())
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      val localMatches = rankSearchResults(cleanQuery, searchLocalCache(cleanQuery))
      Triple(localMatches, emptyList(), localMatches.isEmpty())
    }
  }

  suspend fun searchSuggestions(query: String): List<MovieBoxSuggestion> {
    val clean = query.trim()
    if (clean.length < 2) return emptyList()
    return runCatching { movieBox.searchSuggest(clean) }.getOrDefault(emptyList())
  }

  suspend fun getTrendingKeywords(): List<String> =
    runCatching { movieBox.getTrendingKeywords() }.getOrDefault(emptyList())

  suspend fun getMovieDetail(movieId: String): Movie? {
    val cached = cache[movieId]
    if (movieId.startsWith("mbox_")) {
      val subjectId = movieId.removePrefix("mbox_")
      val rich = getMovieBoxRichDetail(subjectId)
      if (rich != null) {
        return rich.movie
      }
      return cached
    }

    if (movieId.startsWith("tmdb_movie_") || movieId.startsWith("tmdb_tv_")) {
      val mediaKind = if (movieId.startsWith("tmdb_tv_")) "tv" else "movie"
      val tmdbId =
        movieId.removePrefix("tmdb_tv_").removePrefix("tmdb_movie_").toLongOrNull()
          ?: cached?.trackId
      if (tmdbId != null && tmdbId > 0L && tmdbService.isConfigured) {
        val rich = runCatching { tmdbService.getDetail(mediaKind, tmdbId) }.getOrNull()
        if (rich != null) {
          val merged =
            rich.movie.copy(
              subjectId = cached?.subjectId ?: rich.movie.subjectId,
              detailPath = cached?.detailPath ?: rich.movie.detailPath,
            )
          cache[merged.id] = merged
          rich.recommendations.forEach { cache.putIfAbsent(it.id, it) }
          return merged
        }
      }
      return cached
    }

    val trackId = cached?.trackId ?: movieId.removePrefix("itunes_").toLongOrNull()
    if (cached != null && !cached.previewUrl.isNullOrBlank() && cached.runtimeMinutes > 0) {
      return cached
    }
    if (trackId != null && trackId > 0L) {
      try {
        val fetched = api.lookupMovie(id = trackId).results.firstOrNull()?.toDomain()
        if (fetched != null) {
          cache[fetched.id] = fetched
          return fetched
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // Fall back to cached or offline entry
      }
    }
    return cached ?: OfflineCatalogFallback.movies.firstOrNull { it.id == movieId }
  }

  suspend fun getMovieBoxRichDetail(subjectId: String): MovieBoxRichDetail? {
    val hit = richDetailCache[subjectId]
    if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.getDetails(subjectId).also { detail ->
        cache[detail.movie.id] = detail.movie
        richDetailCache[subjectId] = System.currentTimeMillis() to detail
      }
    }.getOrNull()
  }

  suspend fun getSeriesSeasons(subjectId: String): List<MovieBoxSeason> {
    richDetailCache[subjectId]?.let { if (it.second.seasons.isNotEmpty()) return it.second.seasons }
    return runCatching { movieBox.getEpisodes(subjectId) }.getOrDefault(emptyList())
  }

  suspend fun getStreams(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
    detailPath: String? = null,
    forceRefresh: Boolean = false,
  ): List<MovieBoxStream> {
    val key = "${subjectId}_${season}_${episode}"
    val hit = streamCache[key]
    if (!forceRefresh && hit != null && (System.currentTimeMillis() - hit.first) < STREAM_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.getStreams(
        subjectId = subjectId,
        season = season,
        episode = episode,
        detailPath = detailPath,
      ).also { resolved ->
        if (resolved.isNotEmpty()) {
          streamCache[key] = System.currentTimeMillis() to resolved
        }
      }
    }.getOrDefault(emptyList())
  }

  suspend fun getSubtitles(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
  ): List<MovieBoxSubtitle> {
    val key = "${subjectId}_${season}_${episode}"
    val hit = subtitleCache[key]
    if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.getSubtitles(subjectId = subjectId, season = season, episode = episode).also { subs ->
        if (subs.isNotEmpty()) {
          subtitleCache[key] = System.currentTimeMillis() to subs
        }
      }
    }.getOrDefault(emptyList())
  }

  private val subtitleCuesCache = ConcurrentHashMap<String, Pair<Long, List<SubtitleCue>>>()

  suspend fun fetchSubtitleCues(subtitleUrl: String): List<SubtitleCue> {
    if (subtitleUrl.isBlank()) return emptyList()
    val hit = subtitleCuesCache[subtitleUrl]
    if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.fetchSubtitleCues(subtitleUrl).also { cues ->
        if (cues.isNotEmpty()) {
          subtitleCuesCache[subtitleUrl] = System.currentTimeMillis() to cues
        }
      }
    }.getOrDefault(emptyList())
  }

  /**
   * Prefetches streams, subtitles, and default English cues for upcoming episodes
   * (e.g., currentPage + 1 in the Vertical Drama Player) without buffering full video blobs.
   */
  suspend fun preloadEpisodeResources(
    subjectId: String,
    season: Int,
    episode: Int,
    detailPath: String? = null,
    preloadEnglishCues: Boolean = true,
  ) = coroutineScope {
    if (subjectId.isBlank() || season <= 0 || episode <= 0) return@coroutineScope
    val streamsDef = async { runCatching { getStreams(subjectId, season, episode, detailPath) }.getOrNull() }
    val subsDef = async { runCatching { getSubtitles(subjectId, season, episode) }.getOrDefault(emptyList()) }
    streamsDef.await()
    val subs = subsDef.await()
    if (preloadEnglishCues && subs.isNotEmpty()) {
      val preferred =
        subs.firstOrNull { it.language.contains("English", ignoreCase = true) } ?: subs.first()
      runCatching { fetchSubtitleCues(preferred.url) }
    }
  }

  fun getCachedMovie(movieId: String): Movie? =
    cache[movieId] ?: OfflineCatalogFallback.movies.firstOrNull { it.id == movieId }

  private fun searchLocalCache(cleanQuery: String): List<Movie> =
    (cache.values.toList() + OfflineCatalogFallback.movies)
      .distinctBy { it.id }
      .filter { matchesQueryTokens(cleanQuery, it) }

  private fun matchesQueryTokens(cleanQuery: String, movie: Movie): Boolean {
    if (
      movie.title.contains(cleanQuery, ignoreCase = true) ||
        movie.director.contains(cleanQuery, ignoreCase = true) ||
        movie.genre.contains(cleanQuery, ignoreCase = true) ||
        movie.mediaType.contains(cleanQuery, ignoreCase = true)
    ) {
      return true
    }
    val tokens = cleanQuery.split(Regex("\\s+")).filter { it.length >= 2 }
    if (tokens.size <= 1) return false
    val haystack = "${movie.title} ${movie.director} ${movie.genre} ${movie.mediaType}"
    return tokens.all { haystack.contains(it, ignoreCase = true) }
  }

  internal fun normalizeTitleKey(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9]"), "")

  internal fun mergeProviderGroup(group: List<Movie>): Movie? {
    if (group.isEmpty()) return null
    val primary =
      group.firstOrNull { it.subjectId.isNotBlank() && it.posterUrl.isNotBlank() }
        ?: group.firstOrNull { it.subjectId.isNotBlank() }
        ?: group.firstOrNull { (it.id.startsWith("tmdb_") || it.sourceProvider == "Decan Cinema") && it.posterUrl.isNotBlank() }
        ?: group.firstOrNull { it.posterUrl.isNotBlank() }
        ?: group.first()
    val metadataRich =
      group.firstOrNull { (it.id.startsWith("tmdb_") || it.sourceProvider == "Decan Cinema") && it.posterUrl.isNotBlank() }
        ?: group.firstOrNull { it !== primary && it.posterUrl.isNotBlank() }
        ?: return primary
    return primary.copy(
      posterUrl = primary.posterUrl.ifBlank { metadataRich.posterUrl },
      backdropUrl = primary.backdropUrl.ifBlank { metadataRich.backdropUrl },
      synopsis =
        if (primary.synopsis.length < 25 && metadataRich.synopsis.length > primary.synopsis.length) {
          metadataRich.synopsis
        } else {
          primary.synopsis
        },
      imdbRating = primary.imdbRating ?: metadataRich.imdbRating,
      genre =
        if (primary.genre.isBlank() || primary.genre.equals("Cinema", true)) metadataRich.genre
        else primary.genre,
      releaseYear = primary.releaseYear.ifBlank { metadataRich.releaseYear },
    )
  }

  private fun enrichStreamListWithMetadata(
    streamItems: List<Movie>,
    tmdbByTitle: Map<String, Movie>,
  ): List<Movie> {
    if (tmdbByTitle.isEmpty()) return streamItems
    return streamItems.map { item ->
      val tmdb = tmdbByTitle[normalizeTitleKey(item.title)] ?: return@map item
      mergeProviderGroup(listOf(item, tmdb)) ?: item
    }
  }

  private fun linkPlayableMatches(
    items: List<Movie>,
    streamPool: List<Movie>,
  ): List<Movie> {
    if (streamPool.isEmpty()) return items
    val streamByTitle = streamPool.associateBy { normalizeTitleKey(it.title) }
    return items.map { item ->
      if (item.subjectId.isNotBlank()) return@map item
      val match = streamByTitle[normalizeTitleKey(item.title)] ?: return@map item
      item.copy(
        subjectId = match.subjectId,
        detailPath = match.detailPath ?: item.detailPath,
      )
    }
  }

  internal fun rankSearchResults(query: String, movies: List<Movie>): List<Movie> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return movies
    val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    return movies.sortedByDescending { movie ->
      val titleLower = movie.title.trim().lowercase()
      val baseScore =
        when {
          titleLower == q -> 10_000
          titleLower.startsWith(q) -> 7_500
          titleLower.contains(q) -> 5_000
          tokens.isNotEmpty() && tokens.all { titleLower.contains(it) } -> 3_500
          tokens.any { titleLower.contains(it) } -> 1_500
          else -> 200
        }
      val streamBonus = if (movie.subjectId.isNotBlank()) 500 else 0
      val posterBonus = if (movie.posterUrl.isNotBlank()) 50 else 0
      baseScore + streamBonus + posterBonus
    }
  }

  private fun buildOfflineRails(movies: List<Movie>): List<MovieRail> =
    listOf(
      MovieRail("featured", "Featured Classics", "Offline curated collection", movies.take(6)),
      MovieRail(
        "anime_rail",
        "Anime & Animation",
        "Curated anime series & films",
        movies.filter { it.mediaType.equals("Anime", ignoreCase = true) || it.genre.contains("Anim", true) },
      ),
      MovieRail(
        "sci_fi",
        "Sci-Fi & Thrillers",
        "Atmospheric cinema",
        movies.filter {
          it.genre.contains("Sci-Fi", ignoreCase = true) ||
            it.genre.contains("Thriller", ignoreCase = true) ||
            it.genre.contains("Action", ignoreCase = true)
        },
      ),
      MovieRail(
        "drama",
        "Award-Winning Drama",
        "Essential stories",
        movies.filter {
          it.genre.contains("Drama", ignoreCase = true) ||
            it.genre.contains("Crime", ignoreCase = true)
        },
      ),
    ).filter { it.movies.isNotEmpty() }

  internal fun isValidCinemaEntry(movie: Movie): Boolean {
    val typeLower = movie.mediaType.trim().lowercase()
    if (typeLower in setOf("music", "song", "songs", "audio", "podcast", "pv", "cm", "karaoke")) {
      return false
    }
    val ratingLower = movie.contentRating.trim().lowercase()
    if (
      ratingLower.startsWith("music") ||
        ratingLower.startsWith("pv ") ||
        ratingLower.startsWith("cm ") ||
        ratingLower == "pv" ||
        ratingLower == "cm"
    ) {
      return false
    }
    val genreTokens =
      movie.genre.split(",", "/", "&", "|").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    val bannedGenres = setOf("music", "song", "songs", "music video", "mv", "audio", "podcast", "karaoke", "single", "ost", "soundtrack", "radio")
    if (genreTokens.any { it in bannedGenres }) return false

    val titleLower = movie.title.lowercase()
    if (
      titleLower.contains("(official music video)") ||
        titleLower.contains("(official video)") ||
        titleLower.contains("(official audio)") ||
        titleLower.contains("(lyric video)") ||
        titleLower.contains("[mv]") ||
        titleLower.contains("(mv)")
    ) {
      return false
    }
    return true
  }

  internal fun isGenuineAnime(movie: Movie): Boolean {
    if (!isValidCinemaEntry(movie)) return false
    if (movie.id.startsWith("mal_") || movie.id.startsWith("anilist_")) return true
    if (movie.sourceProvider.equals("Decan Anime", ignoreCase = true)) return true
    val nonAnimeLiveCountries =
      setOf(
        "united states",
        "usa",
        "us",
        "south africa",
        "nigeria",
        "united kingdom",
        "uk",
        "canada",
        "australia",
        "france",
        "spain",
        "india",
        "brazil",
        "mexico",
        "turkey",
      )
    val countryLower = movie.country.orEmpty().trim().lowercase()
    if (countryLower in nonAnimeLiveCountries) return false

    val hasAnimeGenre = movie.genre.contains("Anime", ignoreCase = true)
    val hasAnimationGenre = movie.genre.contains("Animation", ignoreCase = true)
    val isEastAsianOrJapanese =
      countryLower in setOf("japan", "china", "south korea") ||
        movie.language.orEmpty().contains("Japanese", ignoreCase = true)
    if (hasAnimeGenre && (countryLower.isBlank() || isEastAsianOrJapanese)) return true
    if (hasAnimationGenre && isEastAsianOrJapanese) return true
    return false
  }

  fun sortAnimeMovies(movies: List<Movie>, sortMode: AnimeSortMode): List<Movie> {
    val clean = movies.filter(::isGenuineAnime)
    return when (sortMode) {
      AnimeSortMode.TOP_RANKED ->
        clean.sortedWith(
          compareBy<Movie> { it.rankPosition ?: Int.MAX_VALUE }
            .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { if (it.subjectId.isNotBlank()) 1 else 0 }
        )
      AnimeSortMode.POPULAR ->
        clean.sortedWith(
          compareByDescending<Movie> { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .thenBy { it.rankPosition ?: Int.MAX_VALUE }
            .thenByDescending { if (it.subjectId.isNotBlank()) 1 else 0 }
        )
      AnimeSortMode.AIRING ->
        clean.sortedWith(
          compareByDescending<Movie> { (it.releaseYear.toIntOrNull() ?: 0) >= 2024 }
            .thenByDescending { it.releaseYear.toIntOrNull() ?: 0 }
            .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
        )
      AnimeSortMode.NEWEST ->
        clean.sortedWith(
          compareByDescending<Movie> { it.releaseYear.toIntOrNull() ?: 0 }
            .thenByDescending { it.releaseDateLabel }
            .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
        )
    }
  }

  fun sortCatalogMovies(movies: List<Movie>, sortMode: CategorySortMode): List<Movie> {
    val clean = movies.filter(::isValidCinemaEntry)
    return when (sortMode) {
      CategorySortMode.FEATURED -> clean
      CategorySortMode.TOP_RANKED ->
        clean.sortedWith(
          compareBy<Movie> { it.rankPosition ?: Int.MAX_VALUE }
            .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .thenByDescending { if (it.subjectId.isNotBlank()) 1 else 0 }
        )
      CategorySortMode.HIGHEST_RATED ->
        clean.sortedWith(
          compareByDescending<Movie> { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .thenBy { it.rankPosition ?: Int.MAX_VALUE }
            .thenByDescending { if (it.subjectId.isNotBlank()) 1 else 0 }
        )
      CategorySortMode.NEWEST ->
        clean.sortedWith(
          compareByDescending<Movie> { it.releaseYear.toIntOrNull() ?: 0 }
            .thenByDescending { it.releaseDateLabel }
        )
    }
  }

  private fun persistHomeSnapshot(feed: CatalogFeedResult) {
    val prefs = snapshotPrefs ?: return
    runCatching {
      val root =
        JSONObject().apply {
          put("savedAt", System.currentTimeMillis())
          put("spotlight", encodeSnapshotMovies(feed.spotlight.take(7)))
          put("rankings", encodeSnapshotMovies(feed.rankings.take(24)))
          put("anime", encodeSnapshotMovies(feed.anime.take(24)))
          put("recommendations", encodeSnapshotMovies(feed.recommendations.take(14)))
          put("allMovies", encodeSnapshotMovies(feed.allMovies.take(80)))
          put(
            "rails",
            JSONArray().apply {
              feed.rails.take(6).forEach { r ->
                put(
                  JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("subtitle", r.subtitle)
                    put("movies", encodeSnapshotMovies(r.movies.take(18)))
                  }
                )
              }
            },
          )
          put(
            "trendingKeywords",
            JSONArray().apply { feed.trendingKeywords.forEach { put(it) } },
          )
        }
      prefs.edit().putString(SNAPSHOT_KEY, root.toString()).apply()
    }
  }

  private fun encodeSnapshotMovies(movies: List<Movie>): JSONArray =
    JSONArray().apply {
      movies.forEach { m ->
        put(
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
            put("synopsis", m.synopsis.take(320))
            put("runtimeMinutes", m.runtimeMinutes)
            put("contentRating", m.contentRating)
            put("subjectId", m.subjectId)
            put("mediaType", m.mediaType)
            m.imdbRating?.let { put("imdbRating", it) }
            m.rankPosition?.let { put("rankPosition", it) }
            m.detailPath?.let { put("detailPath", it) }
            m.country?.let { put("country", it) }
            m.language?.let { put("language", it) }
            put("sourceProvider", m.sourceProvider)
          }
        )
      }
    }

  private fun parseSnapshotMovies(arr: JSONArray?): List<Movie> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optString("id")
        val title = o.optString("title")
        if (id.isBlank() || title.isBlank()) continue
        add(
          Movie(
            id = id,
            trackId = o.optLong("trackId", 0L),
            title = title,
            director = o.optString("director"),
            genre = o.optString("genre"),
            releaseYear = o.optString("releaseYear"),
            releaseDateLabel = o.optString("releaseDateLabel"),
            posterUrl = o.optString("posterUrl"),
            backdropUrl = o.optString("backdropUrl"),
            previewUrl = null,
            synopsis = o.optString("synopsis"),
            runtimeMinutes = o.optInt("runtimeMinutes", 0),
            contentRating = o.optString("contentRating"),
            subjectId = o.optString("subjectId"),
            mediaType = o.optString("mediaType", "Movie"),
            imdbRating = o.optString("imdbRating").takeIf { it.isNotBlank() && it != "null" },
            detailPath = o.optString("detailPath").takeIf { it.isNotBlank() && it != "null" },
            country = o.optString("country").takeIf { it.isNotBlank() && it != "null" },
            language = o.optString("language").takeIf { it.isNotBlank() && it != "null" },
            sourceProvider = o.optString("sourceProvider", "Decan"),
            rankPosition = o.optInt("rankPosition", 0).takeIf { it > 0 },
          )
        )
      }
    }
  }

  private fun parseSnapshotRails(arr: JSONArray?): List<MovieRail> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val movies = parseSnapshotMovies(o.optJSONArray("movies")).filter(::isValidCinemaEntry)
        if (movies.isNotEmpty()) {
          add(
            MovieRail(
              id = o.optString("id"),
              title = o.optString("title"),
              subtitle = o.optString("subtitle"),
              movies = movies,
            )
          )
        }
      }
    }
  }

  companion object {
    private const val BASE_URL = "https://itunes.apple.com/"
    private const val SNAPSHOT_KEY = "catalog_home_snapshot_v3"
    private const val SNAPSHOT_MAX_AGE_MS = 12 * 3600_000L
    private const val HOME_CACHE_TTL_MS = 5 * 60_000L
    private const val DETAIL_CACHE_TTL_MS = 5 * 60_000L
    private const val STREAM_CACHE_TTL_MS = 3 * 60_000L

    fun createDefaultApi(): ItunesMovieApi {
      val okHttp =
        OkHttpClient.Builder()
          .connectTimeout(5, TimeUnit.SECONDS)
          .readTimeout(8, TimeUnit.SECONDS)
          .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
          .build()
      val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
      return Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ItunesMovieApi::class.java)
    }
  }
}

private object OfflineCatalogFallback {
  val movies =
    listOf(
      Movie(
        id = "mbox_8906247916759695608",
        trackId = 8906247916759695608L,
        title = "Avatar",
        director = "James Cameron",
        genre = "Sci-Fi",
        releaseYear = "2009",
        releaseDateLabel = "2009-12-18",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "A paraplegic Marine dispatched to the moon Pandora on a unique mission becomes torn between following his orders and protecting the world he feels is his home.",
        runtimeMinutes = 162,
        contentRating = "PG-13",
        subjectId = "8906247916759695608",
        mediaType = "Movie",
        imdbRating = "7.9",
        sourceProvider = "Decan",
      ),
      Movie(
        id = "mbox_6207982430134357800",
        trackId = 6207982430134357800L,
        title = "Breaking Bad",
        director = "Vince Gilligan",
        genre = "Crime Drama",
        releaseYear = "2008",
        releaseDateLabel = "2008-01-20",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "A high school chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine to secure his family's future.",
        runtimeMinutes = 47,
        contentRating = "TV-MA",
        subjectId = "6207982430134357800",
        mediaType = "Series",
        imdbRating = "9.5",
        sourceProvider = "Decan",
      ),
      Movie(
        id = "itunes_1733555739",
        trackId = 1733555739L,
        title = "Dune: Part Two",
        director = "Denis Villeneuve",
        genre = "Sci-Fi & Fantasy",
        releaseYear = "2024",
        releaseDateLabel = "2024-03-01",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "Paul Atreides unites with Chani and the Fremen while on a warpath of revenge against the conspirators who destroyed his family.",
        runtimeMinutes = 166,
        contentRating = "PG-13",
      ),
      Movie(
        id = "itunes_1695817025",
        trackId = 1695817025L,
        title = "Oppenheimer",
        director = "Christopher Nolan",
        genre = "Drama",
        releaseYear = "2023",
        releaseDateLabel = "2023-07-21",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.",
        runtimeMinutes = 180,
        contentRating = "R",
      ),
      Movie(
        id = "mal_16498",
        trackId = 16498L,
        title = "Attack on Titan",
        director = "Wit Studio",
        genre = "Action, Drama, Supernatural",
        releaseYear = "2013",
        releaseDateLabel = "2013-04-07",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "After his hometown is destroyed, young Eren Yeager vows to cleanse the earth of the giant humanoid Titans that have brought humanity to the brink of extinction.",
        runtimeMinutes = 24,
        contentRating = "TV • ANIME",
        mediaType = "Anime",
        imdbRating = "9.1",
        country = "Japan",
        language = "Japanese",
        sourceProvider = "Decan Anime",
      ),
      Movie(
        id = "itunes_1488022263",
        trackId = 1488022263L,
        title = "Parasite",
        director = "Bong Joon-ho",
        genre = "Thriller",
        releaseYear = "2019",
        releaseDateLabel = "2019-10-11",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan.",
        runtimeMinutes = 132,
        contentRating = "R",
      ),
    )
}
