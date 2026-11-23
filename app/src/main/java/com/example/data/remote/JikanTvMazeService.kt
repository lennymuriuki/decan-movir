package com.example.data.remote

import com.example.data.model.Movie
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class AnimeSortMode(val label: String) {
  TOP_RANKED("Top Ranked"),
  POPULAR("Most Popular"),
  AIRING("Airing Now"),
  NEWEST("Newest"),
}

data class AnimeGenreOption(
  val label: String,
  val jikanGenreId: Int?,
)

class JikanTvMazeService(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(14, TimeUnit.SECONDS)
      .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
      .build(),
) {

  suspend fun searchAnime(query: String, limit: Int = 15): List<Movie> =
    withContext(Dispatchers.IO) {
      val clean = query.trim()
      if (clean.isBlank()) return@withContext emptyList()
      val encoded = URLEncoder.encode(clean, "UTF-8")
      val url = "$JIKAN_BASE/anime?q=$encoded&limit=$limit&sfw=true"
      fetchJsonObject(url)?.let { parseJikanAnimeList(it, startRank = null) }.orEmpty()
    }

  suspend fun browseAnime(
    genreLabel: String,
    sortMode: AnimeSortMode,
    page: Int = 1,
    limit: Int = 24,
  ): List<Movie> =
    withContext(Dispatchers.IO) {
      val genreId =
        ANIME_GENRES.firstOrNull { it.label.equals(genreLabel, ignoreCase = true) }?.jikanGenreId
      val url =
        if (genreId == null) {
          when (sortMode) {
            AnimeSortMode.TOP_RANKED -> "$JIKAN_BASE/top/anime?page=$page&limit=$limit&sfw=true"
            AnimeSortMode.POPULAR ->
              "$JIKAN_BASE/top/anime?filter=bypopularity&page=$page&limit=$limit&sfw=true"
            AnimeSortMode.AIRING ->
              "$JIKAN_BASE/top/anime?filter=airing&page=$page&limit=$limit&sfw=true"
            AnimeSortMode.NEWEST ->
              "$JIKAN_BASE/anime?order_by=start_date&sort=desc&page=$page&limit=$limit&sfw=true"
          }
        } else {
          val orderParams =
            when (sortMode) {
              AnimeSortMode.TOP_RANKED -> "order_by=score&sort=desc"
              AnimeSortMode.POPULAR -> "order_by=popularity&sort=asc"
              AnimeSortMode.AIRING -> "status=airing&order_by=score&sort=desc"
              AnimeSortMode.NEWEST -> "order_by=start_date&sort=desc"
            }
          "$JIKAN_BASE/anime?genres=$genreId&$orderParams&page=$page&limit=$limit&sfw=true"
        }
      val rankStart = if (sortMode == AnimeSortMode.TOP_RANKED) ((page - 1) * limit) + 1 else null
      fetchJsonObject(url)?.let { parseJikanAnimeList(it, startRank = rankStart) }.orEmpty()
    }

  suspend fun searchTvShows(query: String, limit: Int = 15): List<Movie> =
    withContext(Dispatchers.IO) {
      val clean = query.trim()
      if (clean.isBlank()) return@withContext emptyList()
      val encoded = URLEncoder.encode(clean, "UTF-8")
      val url = "$TVMAZE_BASE/search/shows?q=$encoded"
      fetchJsonArray(url)?.let { parseTvMazeShows(it).take(limit) }.orEmpty()
    }

  private fun fetchJsonObject(url: String): JSONObject? =
    runCatching {
      val req =
        Request.Builder()
          .url(url)
          .get()
          .addHeader("Accept", "application/json")
          .addHeader("User-Agent", "DecanMovie-Android/1.0")
          .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        JSONObject(resp.body?.string().orEmpty())
      }
    }.getOrNull()

  private fun fetchJsonArray(url: String): JSONArray? =
    runCatching {
      val req =
        Request.Builder()
          .url(url)
          .get()
          .addHeader("Accept", "application/json")
          .addHeader("User-Agent", "DecanMovie-Android/1.0")
          .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        JSONArray(resp.body?.string().orEmpty())
      }
    }.getOrNull()

  internal fun parseJikanAnimeList(root: JSONObject, startRank: Int? = null): List<Movie> {
    val data = root.optJSONArray("data") ?: return emptyList()
    val out = mutableListOf<Movie>()
    for (i in 0 until data.length()) {
      val item = data.optJSONObject(i) ?: continue
      val malId = item.optLong("mal_id", 0L)
      val title =
        item.optString("title_english").takeIf { it.isNotBlank() && it != "null" }
          ?: item.optString("title").trim()
      if (malId <= 0L || title.isBlank()) continue

      val rawType = item.optString("type").trim()
      if (
        rawType.equals("Music", ignoreCase = true) ||
          rawType.equals("PV", ignoreCase = true) ||
          rawType.equals("CM", ignoreCase = true) ||
          rawType.equals("Unknown", ignoreCase = true)
      ) {
        continue
      }

      val genresArr = item.optJSONArray("genres") ?: JSONArray()
      val themesArr = item.optJSONArray("themes") ?: JSONArray()
      val genreNames = buildList {
        for (g in 0 until genresArr.length()) {
          val n = genresArr.optJSONObject(g)?.optString("name").orEmpty()
          if (n.isNotBlank()) add(n)
        }
        for (t in 0 until themesArr.length()) {
          val n = themesArr.optJSONObject(t)?.optString("name").orEmpty()
          if (n.isNotBlank()) add(n)
        }
      }
      val episodesCount = item.optInt("episodes", 0)
      val durationStr = item.optString("duration").lowercase()
      val hasMusicTag = genreNames.any { it.equals("Music", ignoreCase = true) }
      val isShortClip =
        durationStr.contains("sec") ||
          Regex("^([1-6])\\s*min").containsMatchIn(durationStr.trim())
      val lowerTitle = title.lowercase()
      if (
        (hasMusicTag && (episodesCount <= 1 && isShortClip)) ||
          lowerTitle.contains("(mv)") ||
          lowerTitle.contains("[mv]") ||
          lowerTitle.contains("music video")
      ) {
        continue
      }

      val images = item.optJSONObject("images")?.optJSONObject("jpg")
      val largePoster =
        images?.optString("large_image_url").orEmpty().ifBlank {
          images?.optString("image_url").orEmpty()
        }
      val genreStr = genreNames.distinct().take(3).joinToString(", ").ifBlank { "Anime" }
      val yearInt = item.optInt("year", 0)
      val airedFrom = item.optJSONObject("aired")?.optString("from").orEmpty()
      val yearStr =
        when {
          yearInt > 1950 -> yearInt.toString()
          airedFrom.length >= 4 -> airedFrom.take(4)
          else -> "2024"
        }
      val scoreVal = item.optDouble("score", 0.0)
      val scoreStr =
        if (!scoreVal.isNaN() && scoreVal > 0.0) String.format(java.util.Locale.US, "%.1f", scoreVal)
        else null
      val rankField = item.optInt("rank", 0).takeIf { it > 0 }
      val computedRank =
        rankField ?: startRank?.let { it + out.size }
      val studioName =
        item.optJSONArray("studios")?.optJSONObject(0)?.optString("name").orEmpty()
          .ifBlank { "Japanese Animation" }
      val synopsis =
        item.optString("synopsis").takeIf { it.isNotBlank() && it != "null" }
          ?: "$genreStr anime series streaming on Decan Movie."
      val typeLabel = rawType.ifBlank { "TV" }
      val ratingLabel =
        item.optString("rating").takeIf { it.isNotBlank() && it != "null" }?.substringBefore(" ")
          ?: "ANIME"

      out.add(
        Movie(
          id = Movie.sanitizeId("mal_$malId"),
          trackId = malId,
          title = title.take(200),
          director =
            buildString {
              append(studioName)
              if (episodesCount > 0) append(" • $episodesCount eps")
            }.take(120),
          genre = genreStr.take(60),
          releaseYear = yearStr,
          releaseDateLabel = airedFrom.take(10).ifBlank { yearStr },
          posterUrl = largePoster.take(500),
          backdropUrl = largePoster.take(500),
          previewUrl = item.optJSONObject("trailer")?.optString("url")?.takeIf { it.isNotBlank() && it != "null" },
          synopsis = synopsis.take(2000),
          runtimeMinutes = 24,
          contentRating = "$typeLabel • $ratingLabel".take(24),
          subjectId = "",
          mediaType = "Anime",
          imdbRating = scoreStr,
          rankPosition = computedRank,
          country = "Japan",
          language = "Japanese",
          sourceProvider = "Decan Anime",
        )
      )
    }
    return out
  }

  internal fun parseTvMazeShows(arr: JSONArray): List<Movie> {
    val out = mutableListOf<Movie>()
    for (i in 0 until arr.length()) {
      val entry = arr.optJSONObject(i) ?: continue
      val show = entry.optJSONObject("show") ?: entry
      val id = show.optLong("id", 0L)
      val title = show.optString("name").trim()
      if (id <= 0L || title.isBlank()) continue

      val imageObj = show.optJSONObject("image")
      val poster =
        imageObj?.optString("original").orEmpty().ifBlank { imageObj?.optString("medium").orEmpty() }
          .replace("http://", "https://")
      val genresArr = show.optJSONArray("genres") ?: JSONArray()
      val genres = buildList {
        for (g in 0 until genresArr.length()) {
          val s = genresArr.optString(g)
          if (s.isNotBlank()) add(s)
        }
      }
      val genreStr = genres.take(3).joinToString(", ").ifBlank { "Series" }
      val premiered = show.optString("premiered").takeIf { it != "null" }.orEmpty()
      val year = premiered.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "2024"
      val rating = show.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0
      val ratingStr =
        if (!rating.isNaN() && rating > 0.0) String.format(java.util.Locale.US, "%.1f", rating)
        else null
      val network =
        show.optJSONObject("network")?.optString("name")
          ?: show.optJSONObject("webChannel")?.optString("name")
          ?: "TV Series"
      val rawSummary =
        show.optString("summary")
          .takeIf { it != "null" }
          .orEmpty()
          .replace(Regex("<[^>]*>"), "")
          .trim()
      val runtime = show.optInt("averageRuntime", show.optInt("runtime", 45)).coerceIn(0, 600)
      val isAnime = genres.any { it.equals("Anime", ignoreCase = true) }

      out.add(
        Movie(
          id = Movie.sanitizeId("tvmaze_$id"),
          trackId = id,
          title = title.take(200),
          director = network.take(120),
          genre = genreStr.take(60),
          releaseYear = year,
          releaseDateLabel = premiered.ifBlank { year },
          posterUrl = poster.take(500),
          backdropUrl = poster.take(500),
          previewUrl = null,
          synopsis = rawSummary.ifBlank { "$title streaming series." }.take(2000),
          runtimeMinutes = runtime,
          contentRating = if (isAnime) "ANIME" else "TV SERIES",
          subjectId = "",
          mediaType = if (isAnime) "Anime" else "Series",
          imdbRating = ratingStr,
          language = show.optString("language").takeIf { it.isNotBlank() && it != "null" },
          sourceProvider = "Decan",
        )
      )
    }
    return out
  }

  companion object {
    private const val JIKAN_BASE = "https://api.jikan.moe/v4"
    private const val TVMAZE_BASE = "https://api.tvmaze.com"

    val ANIME_GENRES =
      listOf(
        AnimeGenreOption("All", null),
        AnimeGenreOption("Supernatural", 37),
        AnimeGenreOption("Adventure", 2),
        AnimeGenreOption("Action", 1),
        AnimeGenreOption("Fantasy", 10),
        AnimeGenreOption("Sci-Fi", 24),
        AnimeGenreOption("Comedy", 4),
        AnimeGenreOption("Drama", 8),
        AnimeGenreOption("Romance", 22),
        AnimeGenreOption("Mystery", 7),
        AnimeGenreOption("Horror", 14),
        AnimeGenreOption("Suspense", 41),
      )
  }
}
