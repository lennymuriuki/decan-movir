package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.Movie
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class TmdbRichDetail(
  val movie: Movie,
  val recommendations: List<Movie>,
)

class TmdbService(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(7, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
      .build(),
  private val tokenProvider: () -> String = { BuildConfig.TMDB_ACCESS_TOKEN },
) {

  val isConfigured: Boolean
    get() = isValidToken(tokenProvider())

  suspend fun getTrendingAll(page: Int = 1): List<Movie> {
    if (!isConfigured) return emptyList()
    return withContext(Dispatchers.IO) {
      fetchJsonObject("$TMDB_BASE/trending/all/week?language=en-US&page=$page")
        ?.let { parseListJson(it, forcedMediaKind = null, startRank = ((page - 1) * 20) + 1) }
        .orEmpty()
    }
  }

  suspend fun getPopularMovies(page: Int = 1): List<Movie> {
    if (!isConfigured) return emptyList()
    return withContext(Dispatchers.IO) {
      fetchJsonObject("$TMDB_BASE/movie/popular?language=en-US&page=$page")
        ?.let { parseListJson(it, forcedMediaKind = "movie", startRank = null) }
        .orEmpty()
    }
  }

  suspend fun getTopRatedMovies(page: Int = 1): List<Movie> {
    if (!isConfigured) return emptyList()
    return withContext(Dispatchers.IO) {
      fetchJsonObject("$TMDB_BASE/movie/top_rated?language=en-US&page=$page")
        ?.let { parseListJson(it, forcedMediaKind = "movie", startRank = ((page - 1) * 20) + 1) }
        .orEmpty()
    }
  }

  suspend fun getPopularTv(page: Int = 1): List<Movie> {
    if (!isConfigured) return emptyList()
    return withContext(Dispatchers.IO) {
      fetchJsonObject("$TMDB_BASE/tv/popular?language=en-US&page=$page")
        ?.let { parseListJson(it, forcedMediaKind = "tv", startRank = null) }
        .orEmpty()
    }
  }

  suspend fun getTopRatedTv(page: Int = 1): List<Movie> {
    if (!isConfigured) return emptyList()
    return withContext(Dispatchers.IO) {
      fetchJsonObject("$TMDB_BASE/tv/top_rated?language=en-US&page=$page")
        ?.let { parseListJson(it, forcedMediaKind = "tv", startRank = ((page - 1) * 20) + 1) }
        .orEmpty()
    }
  }

  suspend fun discoverByCategory(
    category: String,
    mediaFilterName: String,
    sortMode: CategorySortMode,
    page: Int = 1,
  ): List<Movie> = coroutineScope {
    if (!isConfigured) return@coroutineScope emptyList()
    val cleanCategory = category.trim().ifBlank { "All" }
    val wantMovies = mediaFilterName == "ALL" || mediaFilterName == "MOVIES" || mediaFilterName == "RANKINGS"
    val wantSeries = mediaFilterName == "ALL" || mediaFilterName == "SERIES" || mediaFilterName == "RANKINGS"

    val movieSort =
      when (sortMode) {
        CategorySortMode.HIGHEST_RATED, CategorySortMode.TOP_RANKED -> "vote_average.desc"
        CategorySortMode.NEWEST -> "primary_release_date.desc"
        CategorySortMode.FEATURED -> "popularity.desc"
      }
    val tvSort =
      when (sortMode) {
        CategorySortMode.HIGHEST_RATED, CategorySortMode.TOP_RANKED -> "vote_average.desc"
        CategorySortMode.NEWEST -> "first_air_date.desc"
        CategorySortMode.FEATURED -> "popularity.desc"
      }

    val movieGenreId = movieGenreIdFor(cleanCategory)
    val tvGenreId = tvGenreIdFor(cleanCategory)
    val minVotes =
      if (sortMode == CategorySortMode.HIGHEST_RATED || sortMode == CategorySortMode.TOP_RANKED) {
        "&vote_count.gte=150"
      } else {
        "&vote_count.gte=25"
      }

    val moviesDef =
      if (wantMovies && (cleanCategory.equals("All", true) || movieGenreId != null)) {
        async {
          withTimeoutOrNull(5000L) {
            withContext(Dispatchers.IO) {
              val genreParam = movieGenreId?.let { "&with_genres=$it" }.orEmpty()
              val url =
                "$TMDB_BASE/discover/movie?language=en-US&include_adult=false&page=$page&sort_by=$movieSort$minVotes$genreParam&without_genres=10402"
              fetchJsonObject(url)
                ?.let { parseListJson(it, forcedMediaKind = "movie", startRank = null) }
                .orEmpty()
            }
          }.orEmpty()
        }
      } else null

    val tvDef =
      if (wantSeries && (cleanCategory.equals("All", true) || tvGenreId != null)) {
        async {
          withTimeoutOrNull(5000L) {
            withContext(Dispatchers.IO) {
              val genreParam = tvGenreId?.let { "&with_genres=$it" }.orEmpty()
              val url =
                "$TMDB_BASE/discover/tv?language=en-US&include_adult=false&page=$page&sort_by=$tvSort$minVotes$genreParam&without_genres=10402"
              fetchJsonObject(url)
                ?.let { parseListJson(it, forcedMediaKind = "tv", startRank = null) }
                .orEmpty()
            }
          }.orEmpty()
        }
      } else null

    val combined = (moviesDef?.await().orEmpty() + tvDef?.await().orEmpty()).distinctBy { it.id }
    combined
  }

  suspend fun discoverAnime(
    genreLabel: String,
    sortMode: AnimeSortMode,
    page: Int = 1,
  ): List<Movie> = coroutineScope {
    if (!isConfigured) return@coroutineScope emptyList()
    val tvSort =
      when (sortMode) {
        AnimeSortMode.TOP_RANKED -> "vote_average.desc"
        AnimeSortMode.NEWEST, AnimeSortMode.AIRING -> "first_air_date.desc"
        AnimeSortMode.POPULAR -> "popularity.desc"
      }
    val extraGenre =
      if (genreLabel.equals("All", true) || genreLabel.equals("Anime", true)) null
      else tvGenreIdFor(genreLabel)
    val genresQuery = if (extraGenre != null && extraGenre != "16") "16,$extraGenre" else "16"

    val tvAnimeDef = async {
      withTimeoutOrNull(5000L) {
        withContext(Dispatchers.IO) {
          val url =
            "$TMDB_BASE/discover/tv?language=en-US&include_adult=false&page=$page&sort_by=$tvSort&with_genres=$genresQuery&with_original_language=ja&without_genres=10402&vote_count.gte=40"
          fetchJsonObject(url)
            ?.let {
              parseListJson(
                it,
                forcedMediaKind = "tv",
                startRank = if (sortMode == AnimeSortMode.TOP_RANKED) ((page - 1) * 20) + 1 else null,
              )
            }
            .orEmpty()
        }
      }.orEmpty()
    }

    val movieExtraGenre =
      if (genreLabel.equals("All", true) || genreLabel.equals("Anime", true)) null
      else movieGenreIdFor(genreLabel)
    val movieGenresQuery =
      if (movieExtraGenre != null && movieExtraGenre != "16") "16,$movieExtraGenre" else "16"
    val movieAnimeDef = async {
      withTimeoutOrNull(4500L) {
        withContext(Dispatchers.IO) {
          val movieSort =
            when (sortMode) {
              AnimeSortMode.TOP_RANKED -> "vote_average.desc"
              AnimeSortMode.NEWEST, AnimeSortMode.AIRING -> "primary_release_date.desc"
              AnimeSortMode.POPULAR -> "popularity.desc"
            }
          val url =
            "$TMDB_BASE/discover/movie?language=en-US&include_adult=false&page=$page&sort_by=$movieSort&with_genres=$movieGenresQuery&with_original_language=ja&without_genres=10402&vote_count.gte=50"
          fetchJsonObject(url)
            ?.let { parseListJson(it, forcedMediaKind = "movie", startRank = null) }
            .orEmpty()
        }
      }.orEmpty()
    }

    (tvAnimeDef.await() + movieAnimeDef.await()).distinctBy { it.id }
  }

  suspend fun searchMulti(query: String, page: Int = 1): List<Movie> {
    if (!isConfigured) return emptyList()
    val clean = query.trim()
    if (clean.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
      val encoded = URLEncoder.encode(clean, "UTF-8")
      val url = "$TMDB_BASE/search/multi?query=$encoded&include_adult=false&language=en-US&page=$page"
      fetchJsonObject(url)
        ?.let { parseListJson(it, forcedMediaKind = null, startRank = null) }
        .orEmpty()
    }
  }

  suspend fun getDetail(mediaKind: String, tmdbId: Long): TmdbRichDetail? {
    if (!isConfigured || tmdbId <= 0L) return null
    val kind = if (mediaKind.equals("tv", ignoreCase = true)) "tv" else "movie"
    return withContext(Dispatchers.IO) {
      val url = "$TMDB_BASE/$kind/$tmdbId?language=en-US&append_to_response=credits,recommendations"
      fetchJsonObject(url)?.let { parseDetailJson(it, kind) }
    }
  }

  private fun fetchJsonObject(url: String): JSONObject? {
    val token = tokenProvider().trim()
    if (!isValidToken(token)) return null
    val request =
      Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .get()
        .build()
    return runCatching {
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val body = response.body?.string() ?: return@use null
        JSONObject(body)
      }
    }.getOrNull()
  }

  companion object {
    private const val TMDB_BASE = "https://api.themoviedb.org/3"
    private const val IMAGE_POSTER_BASE = "https://image.tmdb.org/t/p/w500"
    private const val IMAGE_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280"
    const val PLACEHOLDER_TOKEN = "YOUR_TMDB_READ_ACCESS_TOKEN"

    fun isValidToken(raw: String?): Boolean {
      val trimmed = raw?.trim().orEmpty()
      return trimmed.isNotBlank() &&
        trimmed != PLACEHOLDER_TOKEN &&
        !trimmed.equals("null", ignoreCase = true)
    }

    internal fun parseListJson(
      root: JSONObject,
      forcedMediaKind: String?,
      startRank: Int?,
    ): List<Movie> {
      val results = root.optJSONArray("results") ?: return emptyList()
      val items = mutableListOf<Movie>()
      for (i in 0 until results.length()) {
        val obj = results.optJSONObject(i) ?: continue
        val rank = startRank?.let { it + items.size }
        parseItemJson(obj, forcedMediaKind = forcedMediaKind, rankPosition = rank)?.let { items.add(it) }
      }
      return items
    }

    internal fun parseDetailJson(obj: JSONObject, mediaKind: String): TmdbRichDetail? {
      val base = parseItemJson(obj, forcedMediaKind = mediaKind, rankPosition = null) ?: return null
      val genresArr = obj.optJSONArray("genres") ?: JSONArray()
      val genreNames = buildList {
        for (i in 0 until genresArr.length()) {
          val name = genresArr.optJSONObject(i)?.optString("name").orEmpty().trim()
          if (name.isNotBlank()) add(name)
        }
      }
      val isAnime =
        base.mediaType.equals("Anime", ignoreCase = true) ||
          (obj.optString("original_language") == "ja" && genreNames.any { it.equals("Animation", true) })

      val runtime =
        if (mediaKind == "tv") {
          val epRun = obj.optJSONArray("episode_run_time")
          if (epRun != null && epRun.length() > 0) epRun.optInt(0, 45) else 45
        } else {
          obj.optInt("runtime", 0)
        }

      val director =
        if (mediaKind == "tv") {
          val creators = obj.optJSONArray("created_by")
          val firstCreator =
            if (creators != null && creators.length() > 0) {
              creators.optJSONObject(0)?.optString("name").orEmpty()
            } else ""
          firstCreator.ifBlank { base.director }
        } else {
          val crew = obj.optJSONObject("credits")?.optJSONArray("crew")
          var dirName = ""
          if (crew != null) {
            for (i in 0 until crew.length()) {
              val member = crew.optJSONObject(i) ?: continue
              if (member.optString("job").equals("Director", ignoreCase = true)) {
                dirName = member.optString("name")
                break
              }
            }
          }
          dirName.ifBlank { base.director }
        }

      val countriesArr = obj.optJSONArray("origin_country")
      val originCountry =
        if (countriesArr != null && countriesArr.length() > 0) countriesArr.optString(0)
        else base.country

      val resolvedGenres =
        when {
          isAnime && genreNames.none { it.equals("Anime", true) } ->
            (listOf("Anime") + genreNames.filterNot { it.equals("Animation", true) }).joinToString(", ")
          genreNames.isNotEmpty() -> genreNames.take(3).joinToString(", ")
          else -> base.genre
        }

      val updated =
        base.copy(
          director = director,
          genre = resolvedGenres,
          runtimeMinutes = runtime,
          country =
            if (originCountry.equals("JP", true)) "Japan"
            else originCountry ?: base.country,
          mediaType =
            when {
              isAnime -> "Anime"
              mediaKind == "tv" -> "Series"
              else -> "Movie"
            },
        )

      val recs =
        obj.optJSONObject("recommendations")?.let {
          parseListJson(it, forcedMediaKind = mediaKind, startRank = null)
        }.orEmpty()

      return TmdbRichDetail(movie = updated, recommendations = recs)
    }

    private fun parseItemJson(
      obj: JSONObject,
      forcedMediaKind: String?,
      rankPosition: Int?,
    ): Movie? {
      val mediaKind =
        forcedMediaKind ?: obj.optString("media_type").trim().lowercase(Locale.US).ifBlank { "movie" }
      if (mediaKind == "person") return null
      val isTv = mediaKind == "tv"

      val tmdbId = obj.optLong("id", 0L)
      if (tmdbId <= 0L) return null

      val rawTitle =
        if (isTv) {
          obj.optString("name").ifBlank { obj.optString("original_name") }
        } else {
          obj.optString("title").ifBlank { obj.optString("original_title") }
        }.trim()
      if (rawTitle.isBlank()) return null

      val genreIds = buildList {
        val arr = obj.optJSONArray("genre_ids") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val gid = arr.optInt(i, -1)
          if (gid > 0) add(gid)
        }
      }
      // Exclude pure music items (genre 10402 = Music)
      if (genreIds.size == 1 && genreIds.first() == 10402) return null

      val originalLang = obj.optString("original_language").trim().lowercase(Locale.US)
      val isJapaneseAnime = originalLang == "ja" && genreIds.contains(16)

      val mappedGenres =
        genreIds.mapNotNull { genreNameForId(it) }.distinct().let { list ->
          when {
            isJapaneseAnime -> listOf("Anime") + list.filterNot { it == "Animation" }
            list.isNotEmpty() -> list
            isTv -> listOf("Drama")
            else -> listOf("Cinema")
          }
        }

      val posterPath = obj.optString("poster_path").takeIf { it.isNotBlank() && it != "null" }
      val backdropPath = obj.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" }
      val posterUrl = posterPath?.let { "$IMAGE_POSTER_BASE$it" }.orEmpty()
      val backdropUrl = backdropPath?.let { "$IMAGE_BACKDROP_BASE$it" } ?: posterUrl

      val releaseDate =
        if (isTv) obj.optString("first_air_date") else obj.optString("release_date")
      val releaseYear = releaseDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()

      val voteAvg = obj.optDouble("vote_average", 0.0)
      val ratingLabel =
        if (voteAvg > 0.1) {
          val rounded = (voteAvg * 10.0).roundToLong() / 10.0
          String.format(Locale.US, "%.1f", rounded)
        } else null

      val mediaType =
        when {
          isJapaneseAnime -> "Anime"
          isTv -> "Series"
          else -> "Movie"
        }

      val prefix = if (isTv) "tmdb_tv_" else "tmdb_movie_"
      val overview = obj.optString("overview").trim()

      return Movie(
        id = "$prefix$tmdbId",
        trackId = tmdbId,
        title = rawTitle,
        director = if (isJapaneseAnime) "Japanese Animation" else if (isTv) "TV Series" else "Feature Film",
        genre = mappedGenres.take(3).joinToString(", "),
        releaseYear = releaseYear.ifBlank { "2024" },
        releaseDateLabel = releaseDate.ifBlank { releaseYear },
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        previewUrl = null,
        synopsis = overview.ifBlank { "Explore cast, ratings, and details for $rawTitle on Decan Movie." },
        runtimeMinutes = 0,
        contentRating =
          when {
            isJapaneseAnime -> "ANIME"
            isTv -> "SERIES"
            else -> "HD"
          },
        subjectId = "",
        mediaType = mediaType,
        imdbRating = ratingLabel,
        country = if (isJapaneseAnime) "Japan" else null,
        language = if (originalLang == "ja") "Japanese" else originalLang.uppercase(Locale.US).takeIf { it.isNotBlank() },
        detailPath = null,
        sourceProvider = "Decan Cinema",
        rankPosition = rankPosition,
      )
    }

    private fun genreNameForId(id: Int): String? =
      when (id) {
        28 -> "Action"
        12 -> "Adventure"
        16 -> "Animation"
        35 -> "Comedy"
        80 -> "Crime"
        99 -> "Documentary"
        18 -> "Drama"
        10751 -> "Family"
        14 -> "Fantasy"
        36 -> "History"
        27 -> "Horror"
        9648 -> "Mystery"
        10749 -> "Romance"
        878 -> "Sci-Fi"
        53 -> "Thriller"
        10752 -> "War"
        37 -> "Western"
        10759 -> "Action & Adventure"
        10762 -> "Kids"
        10765 -> "Sci-Fi & Fantasy"
        10768 -> "War & Politics"
        else -> null
      }

    private fun movieGenreIdFor(category: String): String? =
      when (category.trim().lowercase(Locale.US)) {
        "action" -> "28"
        "adventure" -> "12"
        "animation" -> "16"
        "comedy" -> "35"
        "crime" -> "80"
        "documentary" -> "99"
        "drama" -> "18"
        "family" -> "10751"
        "fantasy" -> "14"
        "history" -> "36"
        "horror" -> "27"
        "mystery" -> "9648"
        "romance" -> "10749"
        "sci-fi", "science fiction" -> "878"
        "supernatural" -> "14|9648"
        "suspense", "thriller" -> "53"
        "war" -> "10752"
        "western" -> "37"
        else -> null
      }

    private fun tvGenreIdFor(category: String): String? =
      when (category.trim().lowercase(Locale.US)) {
        "action", "adventure", "action & adventure" -> "10759"
        "animation" -> "16"
        "comedy" -> "35"
        "crime" -> "80"
        "documentary" -> "99"
        "drama" -> "18"
        "family" -> "10751"
        "fantasy", "sci-fi", "sci-fi & fantasy", "science fiction" -> "10765"
        "mystery", "horror", "thriller", "suspense" -> "9648"
        "supernatural" -> "10765|9648"
        "war" -> "10768"
        "western" -> "37"
        else -> null
      }
  }
}
