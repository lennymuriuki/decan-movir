package com.example.data.remote

import com.example.data.model.Movie
import com.example.data.model.MovieRail
import com.example.data.remote.moviebox.MovieBoxEngine
import com.example.data.remote.moviebox.MovieBoxRichDetail
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.data.remote.moviebox.MovieBoxSuggestion
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

data class CatalogFeedResult(
  val spotlight: List<Movie>,
  val rails: List<MovieRail>,
  val allMovies: List<Movie>,
  val isOfflineFallback: Boolean,
)

class MovieCatalogRepository(
  private val api: ItunesMovieApi = createDefaultApi(),
  private val movieBox: MovieBoxEngine = MovieBoxEngine(),
) {
  private val cache = LinkedHashMap<String, Movie>()

  suspend fun loadHomeCatalog(): CatalogFeedResult = coroutineScope {
    try {
      val mboxHomeDeferred = async { runCatching { movieBox.getHome() }.getOrNull() }
      val mboxMoviesDeferred = async {
        runCatching { movieBox.getMovies(page = 1, perPage = 24) }.getOrDefault(emptyList())
      }
      val mboxSeriesDeferred = async {
        runCatching { movieBox.getTvSeries(page = 1, perPage = 24) }.getOrDefault(emptyList())
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

      val mboxHome = mboxHomeDeferred.await()
      val mboxMovies = mboxMoviesDeferred.await()
      val mboxSeries = mboxSeriesDeferred.await()
      val topItunes = topItunesDeferred.await()
      val sciFiItunes = sciFiItunesDeferred.await()

      val combined =
        (mboxHome?.spotlight.orEmpty() +
            mboxHome?.allMovies.orEmpty() +
            mboxMovies +
            mboxSeries +
            topItunes +
            sciFiItunes)
          .distinctBy { it.id }

      if (combined.isEmpty()) {
        val fallback = OfflineCatalogFallback.movies
        fallback.forEach { cache[it.id] = it }
        return@coroutineScope CatalogFeedResult(
          spotlight = fallback.take(5),
          rails = buildOfflineRails(fallback),
          allMovies = fallback,
          isOfflineFallback = true,
        )
      }

      combined.forEach { cache[it.id] = it }

      val spotlight =
        (mboxHome?.spotlight.orEmpty() + mboxMovies.take(3) + topItunes.take(3))
          .distinctBy { it.id }
          .take(6)

      val rails = buildList {
        if (mboxMovies.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_movies",
              title = "Trending Movies",
              subtitle = "Full 1080p stream & subtitle resolution",
              movies = mboxMovies,
            )
          )
        }
        if (mboxSeries.isNotEmpty()) {
          add(
            MovieRail(
              id = "mbox_series",
              title = "Popular TV Series",
              subtitle = "Browse seasons, episodes & captions",
              movies = mboxSeries,
            )
          )
        }
        mboxHome?.rails?.take(4)?.forEach { add(it) }
        if (topItunes.isNotEmpty()) {
          add(
            MovieRail(
              id = "top_box_office",
              title = "Top Box Office",
              subtitle = "Official releases & trailer previews",
              movies = topItunes,
            )
          )
        }
        if (sciFiItunes.isNotEmpty()) {
          add(
            MovieRail(
              id = "sci_fi_trailers",
              title = "Sci-Fi & Trailers",
              subtitle = "Instant trailer previews",
              movies = sciFiItunes,
            )
          )
        }
      }

      CatalogFeedResult(
        spotlight = spotlight,
        rails = rails,
        allMovies = combined,
        isOfflineFallback = false,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      val fallback = OfflineCatalogFallback.movies
      fallback.forEach { cache[it.id] = it }
      CatalogFeedResult(
        spotlight = fallback.take(5),
        rails = buildOfflineRails(fallback),
        allMovies = fallback,
        isOfflineFallback = true,
      )
    }
  }

  suspend fun searchMovies(query: String): Pair<List<Movie>, Boolean> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      val current = cache.values.toList().ifEmpty { OfflineCatalogFallback.movies }
      return@coroutineScope current to false
    }
    try {
      val mboxDeferred = async {
        runCatching { movieBox.search(cleanQuery, page = 1, perPage = 20) }
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

  suspend fun getMovieDetail(movieId: String): Movie? {
    val cached = cache[movieId]
    if (movieId.startsWith("mbox_")) {
      val subjectId = movieId.removePrefix("mbox_")
      val rich = runCatching { movieBox.getDetails(subjectId) }.getOrNull()
      if (rich != null) {
        cache[rich.movie.id] = rich.movie
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

  suspend fun getMovieBoxRichDetail(subjectId: String): MovieBoxRichDetail? =
    runCatching {
      movieBox.getDetails(subjectId).also { cache[it.movie.id] = it.movie }
    }.getOrNull()

  suspend fun getSeriesSeasons(subjectId: String): List<MovieBoxSeason> =
    runCatching { movieBox.getEpisodes(subjectId) }.getOrDefault(emptyList())

  suspend fun getStreams(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
    detailPath: String? = null,
  ): List<MovieBoxStream> =
    runCatching {
      movieBox.getStreams(
        subjectId = subjectId,
        season = season,
        episode = episode,
        detailPath = detailPath,
      )
    }.getOrDefault(emptyList())

  suspend fun getSubtitles(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
  ): List<MovieBoxSubtitle> =
    runCatching {
      movieBox.getSubtitles(subjectId = subjectId, season = season, episode = episode)
    }.getOrDefault(emptyList())

  fun getCachedMovie(movieId: String): Movie? =
    cache[movieId] ?: OfflineCatalogFallback.movies.firstOrNull { it.id == movieId }

  private fun searchLocalCache(cleanQuery: String): List<Movie> =
    (cache.values.toList() + OfflineCatalogFallback.movies)
      .distinctBy { it.id }
      .filter {
        it.title.contains(cleanQuery, ignoreCase = true) ||
          it.director.contains(cleanQuery, ignoreCase = true) ||
          it.genre.contains(cleanQuery, ignoreCase = true)
      }

  private fun buildOfflineRails(movies: List<Movie>): List<MovieRail> =
    listOf(
      MovieRail("featured", "Featured Classics", "Offline curated collection", movies.take(6)),
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
            it.genre.contains("Animation", ignoreCase = true)
        },
      ),
    ).filter { it.movies.isNotEmpty() }

  companion object {
    private const val BASE_URL = "https://itunes.apple.com/"

    fun createDefaultApi(): ItunesMovieApi {
      val okHttp =
        OkHttpClient.Builder()
          .connectTimeout(12, TimeUnit.SECONDS)
          .readTimeout(15, TimeUnit.SECONDS)
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
        sourceProvider = "MovieBox",
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
        sourceProvider = "MovieBox",
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
