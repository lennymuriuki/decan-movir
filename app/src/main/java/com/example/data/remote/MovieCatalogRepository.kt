package com.example.data.remote

import com.example.data.model.Movie
import com.example.data.model.MovieRail
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
) {
  private val cache = LinkedHashMap<String, Movie>()

  suspend fun loadHomeCatalog(): CatalogFeedResult = coroutineScope {
    try {
      val topDeferred = async {
        runCatching { api.getTopMovies().feed?.entry.orEmpty().mapNotNull { it.toDomain() } }
          .getOrDefault(emptyList())
      }
      val sciFiDeferred = async {
        runCatching { api.searchMovies(term = "sci-fi", limit = 18).results.mapNotNull { it.toDomain() } }
          .getOrDefault(emptyList())
      }
      val actionDeferred = async {
        runCatching { api.searchMovies(term = "action", limit = 18).results.mapNotNull { it.toDomain() } }
          .getOrDefault(emptyList())
      }
      val dramaDeferred = async {
        runCatching { api.searchMovies(term = "drama", limit = 18).results.mapNotNull { it.toDomain() } }
          .getOrDefault(emptyList())
      }
      val animationDeferred = async {
        runCatching { api.searchMovies(term = "animation", limit = 18).results.mapNotNull { it.toDomain() } }
          .getOrDefault(emptyList())
      }

      val topMovies = topDeferred.await()
      val sciFi = sciFiDeferred.await()
      val action = actionDeferred.await()
      val drama = dramaDeferred.await()
      val animation = animationDeferred.await()

      val combined = (topMovies + sciFi + action + drama + animation).distinctBy { it.id }
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
        (sciFi.filter { !it.previewUrl.isNullOrBlank() }.take(3) + topMovies.take(4))
          .distinctBy { it.id }
          .take(6)

      val rails =
        buildList {
          if (topMovies.isNotEmpty()) {
            add(MovieRail("top_box_office", "Top Box Office", "Trending cinema releases", topMovies))
          }
          if (sciFi.isNotEmpty()) {
            add(MovieRail("sci_fi", "Sci-Fi & Mind-Benders", "Worlds beyond the horizon", sciFi))
          }
          if (action.isNotEmpty()) {
            add(MovieRail("action", "High-Octane Action", "Adrenaline & spectacle", action))
          }
          if (drama.isNotEmpty()) {
            add(MovieRail("drama", "Acclaimed Drama", "Character-driven stories", drama))
          }
          if (animation.isNotEmpty()) {
            add(MovieRail("animation", "Animated Worlds", "Visually crafted adventures", animation))
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

  suspend fun searchMovies(query: String): Pair<List<Movie>, Boolean> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      val current = cache.values.toList().ifEmpty { OfflineCatalogFallback.movies }
      return current to false
    }
    return try {
      val live = api.searchMovies(term = cleanQuery, limit = 30).results.mapNotNull { it.toDomain() }
      live.forEach { cache[it.id] = it }
      live to false
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      val localMatches =
        (cache.values.toList() + OfflineCatalogFallback.movies)
          .distinctBy { it.id }
          .filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
              it.director.contains(cleanQuery, ignoreCase = true) ||
              it.genre.contains(cleanQuery, ignoreCase = true)
          }
      localMatches to true
    }
  }

  suspend fun getMovieDetail(movieId: String): Movie? {
    val cached = cache[movieId]
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

  fun getCachedMovie(movieId: String): Movie? =
    cache[movieId] ?: OfflineCatalogFallback.movies.firstOrNull { it.id == movieId }

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
        id = "itunes_1606024604",
        trackId = 1606024604L,
        title = "The Batman",
        director = "Matt Reeves",
        genre = "Action & Adventure",
        releaseYear = "2022",
        releaseDateLabel = "2022-03-04",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "When a sadistic serial killer begins murdering key political figures in Gotham, Batman is forced to investigate the city's hidden corruption.",
        runtimeMinutes = 176,
        contentRating = "PG-13",
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
      Movie(
        id = "itunes_960891136",
        trackId = 960891136L,
        title = "Interstellar",
        director = "Christopher Nolan",
        genre = "Sci-Fi & Fantasy",
        releaseYear = "2014",
        releaseDateLabel = "2014-11-07",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis =
          "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
        runtimeMinutes = 169,
        contentRating = "PG-13",
      ),
    )
}
