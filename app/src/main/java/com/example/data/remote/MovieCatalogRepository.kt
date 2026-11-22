package com.example.data.remote

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
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

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
) {
  private val cache = ConcurrentHashMap<String, Movie>()
  @Volatile private var cachedHomeFeed: Pair<Long, CatalogFeedResult>? = null
  private val richDetailCache = ConcurrentHashMap<String, Pair<Long, MovieBoxRichDetail>>()
  private val streamCache = ConcurrentHashMap<String, Pair<Long, List<MovieBoxStream>>>()
  private val subtitleCache = ConcurrentHashMap<String, Pair<Long, List<MovieBoxSubtitle>>>()
  private val recCache = ConcurrentHashMap<String, Pair<Long, List<Movie>>>()

  suspend fun loadHomeCatalog(forceRefresh: Boolean = false): CatalogFeedResult = coroutineScope {
    val cached = cachedHomeFeed
    if (!forceRefresh && cached != null && !cached.second.isOfflineFallback &&
        (System.currentTimeMillis() - cached.first) < HOME_CACHE_TTL_MS
    ) {
      return@coroutineScope cached.second
    }

    try {
      val tokenWarmDef = async { runCatching { movieBox.bootstrapToken() }.getOrNull() }
      val mboxHomeDeferred = async { runCatching { movieBox.getHome() }.getOrNull() }
      val mboxRankingsDeferred = async {
        runCatching { movieBox.getRankings(page = 1, perPage = 15) }.getOrDefault(emptyList())
      }
      val mboxMoviesDeferred = async {
        runCatching { movieBox.getMovies(page = 1, perPage = 24) }.getOrDefault(emptyList())
      }
      val mboxSeriesDeferred = async {
        runCatching { movieBox.getTvSeries(page = 1, perPage = 24) }.getOrDefault(emptyList())
      }
      val mboxAnimeDeferred = async {
        runCatching { movieBox.getAnime(page = 1, perPage = 20) }.getOrDefault(emptyList())
      }
      val mboxTrendingDeferred = async {
        runCatching { movieBox.getTrendingKeywords() }.getOrDefault(emptyList())
      }
      val topItunesDeferred = async {
        runCatching { api.getTopMovies().feed?.entry.orEmpty().mapNotNull { it.toDomain() } }
          .getOrDefault(emptyList())
      }
      val sciFiItunesDeferred = async {
        runCatching {
            api.searchMovies(term = "sci-fi", limit = 15).results.mapNotNull { it.toDomain() }
          }
          .getOrDefault(emptyList())
      }

      tokenWarmDef.await()
      val mboxHome = mboxHomeDeferred.await()
      val mboxRankings = mboxRankingsDeferred.await()
      val mboxMovies = mboxMoviesDeferred.await()
      val mboxSeries = mboxSeriesDeferred.await()
      val mboxAnime = mboxAnimeDeferred.await()
      val trendingKeywords = mboxTrendingDeferred.await()
      val topItunes = topItunesDeferred.await()
      val sciFiItunes = sciFiItunesDeferred.await()

      val combined =
        (mboxHome?.spotlight.orEmpty() +
            mboxRankings +
            mboxHome?.allMovies.orEmpty() +
            mboxMovies +
            mboxSeries +
            mboxAnime +
            topItunes +
            sciFiItunes)
          .distinctBy { it.id }

      if (combined.isEmpty()) {
        val fallback = OfflineCatalogFallback.movies
        fallback.forEach { cache[it.id] = it }
        return@coroutineScope CatalogFeedResult(
          spotlight = fallback.take(5),
          rankings = fallback.mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) },
          anime = fallback.filter { it.mediaType.equals("Anime", ignoreCase = true) },
          recommendations = fallback.shuffled(),
          rails = buildOfflineRails(fallback),
          allMovies = fallback,
          trendingKeywords = listOf("Avatar", "Breaking Bad", "Anime", "Oppenheimer", "Sci-Fi"),
          isOfflineFallback = true,
        )
      }

      combined.forEach { cache[it.id] = it }

      val effectiveRankings =
        if (mboxRankings.isNotEmpty()) {
          mboxRankings
        } else {
          combined
            .sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .take(10)
            .mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) }
        }

      val effectiveAnime =
        if (mboxAnime.isNotEmpty()) {
          mboxAnime
        } else {
          combined.filter {
            it.mediaType.equals("Anime", ignoreCase = true) ||
              it.genre.contains("Anim", ignoreCase = true)
          }
        }

      val recommendations =
        (mboxHome?.rails?.firstOrNull()?.movies.orEmpty() +
            effectiveRankings.take(6) +
            mboxMovies.take(6))
          .distinctBy { it.id }
          .take(14)

      val spotlight =
        (mboxHome?.spotlight.orEmpty() + effectiveRankings.take(3) + mboxMovies.take(3))
          .distinctBy { it.id }
          .take(7)

      val rails = buildList {
        if (mboxMovies.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_movies",
              title = "Trending Movies",
              subtitle = "1080p stream & multi-subtitles",
              movies = mboxMovies,
            )
          )
        }
        if (mboxSeries.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_series",
              title = "Popular TV Series",
              subtitle = "Seasons, episodes & batch downloads",
              movies = mboxSeries,
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
        mboxHome?.rails?.take(5)?.forEach { add(it) }
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

  suspend fun loadGenreFeed(genre: String, page: Int = 1): List<Movie> {
    val live =
      runCatching {
        movieBox.getMovies(page = page, perPage = 24, sort = "RECOMMEND", genre = genre) +
          movieBox.getTvSeries(page = page, perPage = 16, sort = "RECOMMEND", genre = genre)
      }.getOrDefault(emptyList()).distinctBy { it.id }
    live.forEach { cache[it.id] = it }
    return live
  }

  suspend fun loadMorePage(isSeries: Boolean, isAnime: Boolean, page: Int): List<Movie> {
    val fetched =
      when {
        isAnime -> runCatching { movieBox.getAnime(page = page, perPage = 24) }.getOrDefault(emptyList())
        isSeries -> runCatching { movieBox.getTvSeries(page = page, perPage = 24) }.getOrDefault(emptyList())
        else -> runCatching { movieBox.getMovies(page = page, perPage = 24) }.getOrDefault(emptyList())
      }
    fetched.forEach { cache[it.id] = it }
    return fetched
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

  suspend fun searchMovies(query: String): Pair<List<Movie>, Boolean> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      val current = cache.values.toList().ifEmpty { OfflineCatalogFallback.movies }
      return@coroutineScope current to false
    }
    try {
      val mboxDeferred = async {
        runCatching { movieBox.search(cleanQuery, page = 1, perPage = 24) }
          .getOrDefault(emptyList())
      }
      val itunesDeferred = async {
        runCatching {
            api.searchMovies(term = cleanQuery, limit = 20).results.mapNotNull { it.toDomain() }
          }
          .getOrDefault(emptyList())
      }
      val live = (mboxDeferred.await() + itunesDeferred.await()).distinctBy { it.id }
      live.forEach { cache[it.id] = it }
      if (live.isNotEmpty()) {
        live to false
      } else {
        val localMatches = searchLocalCache(cleanQuery)
        localMatches to false
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      searchLocalCache(cleanQuery) to true
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

  suspend fun fetchSubtitleCues(subtitleUrl: String): List<SubtitleCue> =
    runCatching { movieBox.fetchSubtitleCues(subtitleUrl) }.getOrDefault(emptyList())

  fun getCachedMovie(movieId: String): Movie? =
    cache[movieId] ?: OfflineCatalogFallback.movies.firstOrNull { it.id == movieId }

  private fun searchLocalCache(cleanQuery: String): List<Movie> =
    (cache.values.toList() + OfflineCatalogFallback.movies)
      .distinctBy { it.id }
      .filter {
        it.title.contains(cleanQuery, ignoreCase = true) ||
          it.director.contains(cleanQuery, ignoreCase = true) ||
          it.genre.contains(cleanQuery, ignoreCase = true) ||
          it.mediaType.contains(cleanQuery, ignoreCase = true)
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

  companion object {
    private const val BASE_URL = "https://itunes.apple.com/"
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
        id = "itunes_1690852725",
        trackId = 1690852725L,
        title = "Spider-Man: Across the Spider-Verse",
        director = "Joaquim Dos Santos",
        genre = "Animation",
        releaseYear = "2023",
        releaseDateLabel = "2023-06-02",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
        runtimeMinutes = 140,
        contentRating = "PG",
        mediaType = "Anime",
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
