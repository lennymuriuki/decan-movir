package com.decanmovie.desktop.data.remote

import com.decanmovie.desktop.model.Movie
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ItunesMovieClient(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .connectionPool(ConnectionPool(4, 3, TimeUnit.MINUTES))
      .build(),
) {

  suspend fun getTopMovies(): List<Movie> =
    withContext(Dispatchers.IO) {
      val root = fetchJson("https://itunes.apple.com/us/rss/topmovies/limit=25/json")
        ?: return@withContext emptyList()
      val entries = root.optJSONObject("feed")?.optJSONArray("entry") ?: return@withContext emptyList()
      buildList {
        for (i in 0 until entries.length()) {
          val e = entries.optJSONObject(i) ?: continue
          val tid =
            e.optJSONObject("id")?.optJSONObject("attributes")?.optString("im:id")?.toLongOrNull()
              ?: continue
          val title = e.optJSONObject("im:name")?.optString("label")?.trim().orEmpty()
          if (title.isBlank()) continue
          val images = e.optJSONArray("im:image")
          val rawArt =
            if (images != null && images.length() > 0) {
              images.optJSONObject(images.length() - 1)?.optString("label").orEmpty()
            } else ""
          val hdPoster = upgradeArtworkUrl(rawArt, 600)
          val hdBackdrop = upgradeArtworkUrl(rawArt, 1000)
          val relAttr = e.optJSONObject("im:releaseDate")?.optJSONObject("attributes")?.optString("label").orEmpty()
          val relRaw = e.optJSONObject("im:releaseDate")?.optString("label").orEmpty()
          val year =
            relAttr.takeLast(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
              ?: relRaw.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
              ?: "2025"

          var previewHref: String? = null
          val links = e.optJSONArray("link")
          if (links != null) {
            for (l in 0 until links.length()) {
              val attr = links.optJSONObject(l)?.optJSONObject("attributes") ?: continue
              if (attr.optString("type").startsWith("video") && previewHref == null) {
                previewHref = attr.optString("href").takeIf { it.isNotBlank() }
              }
            }
          }

          add(
            Movie(
              id = Movie.sanitizeId("itunes_$tid"),
              trackId = tid,
              title = title.take(200),
              director =
                e.optJSONObject("im:artist")?.optString("label")?.trim().orEmpty()
                  .ifBlank { "Box Office Release" },
              genre =
                e.optJSONObject("category")?.optJSONObject("attributes")?.optString("label")?.trim()
                  .orEmpty()
                  .ifBlank { "Featured" },
              releaseYear = year,
              releaseDateLabel = relAttr.ifBlank { year },
              posterUrl = hdPoster,
              backdropUrl = hdBackdrop,
              previewUrl = previewHref,
              synopsis =
                e.optJSONObject("summary")?.optString("label")?.trim().orEmpty()
                  .ifBlank { "Top trending release on Decan Movie." }
                  .take(2000),
              runtimeMinutes = 0,
              contentRating = "HD",
              sourceProvider = "iTunes",
            )
          )
        }
      }
    }

  suspend fun searchMovies(term: String, limit: Int = 20): List<Movie> =
    withContext(Dispatchers.IO) {
      val clean = term.trim()
      if (clean.isBlank()) return@withContext emptyList()
      val encoded = URLEncoder.encode(clean, "UTF-8")
      val url = "https://itunes.apple.com/search?term=$encoded&media=movie&entity=movie&limit=$limit&country=us"
      val root = fetchJson(url) ?: return@withContext emptyList()
      parseSearchResults(root)
    }

  suspend fun lookupMovie(trackId: Long): Movie? =
    withContext(Dispatchers.IO) {
      if (trackId <= 0L) return@withContext null
      val url = "https://itunes.apple.com/lookup?id=$trackId&entity=movie&country=us"
      val root = fetchJson(url) ?: return@withContext null
      parseSearchResults(root).firstOrNull()
    }

  private fun parseSearchResults(root: JSONObject): List<Movie> {
    val results = root.optJSONArray("results") ?: return emptyList()
    return buildList {
      for (i in 0 until results.length()) {
        val o = results.optJSONObject(i) ?: continue
        val tid = o.optLong("trackId", 0L)
        val title = o.optString("trackName").trim()
        if (tid <= 0L || title.isBlank()) continue
        val rawArt = o.optString("artworkUrl100").ifBlank { o.optString("artworkUrl60") }
        val relDate = o.optString("releaseDate")
        val year = relDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "2024"
        val runtimeMin = (o.optLong("trackTimeMillis", 0L) / 60_000L).toInt().coerceIn(0, 1000)

        add(
          Movie(
            id = Movie.sanitizeId("itunes_$tid"),
            trackId = tid,
            title = title.take(200),
            director = o.optString("artistName").ifBlank { "Featured Filmmaker" }.take(120),
            genre = o.optString("primaryGenreName").ifBlank { "Cinema" }.take(60),
            releaseYear = year,
            releaseDateLabel = relDate.take(10).ifBlank { year },
            posterUrl = upgradeArtworkUrl(rawArt, 600),
            backdropUrl = upgradeArtworkUrl(rawArt, 1000),
            previewUrl =
              o.optString("previewUrl").takeIf {
                it.startsWith("https://") || it.startsWith("http://")
              },
            synopsis =
              o.optString("longDescription").ifBlank {
                o.optString("shortDescription").ifBlank {
                  "A featured cinematic release available on Decan Movie."
                }
              }.trim().take(2000),
            runtimeMinutes = runtimeMin,
            contentRating = o.optString("contentAdvisoryRating").ifBlank { "NR" }.take(20),
            sourceProvider = "iTunes",
          )
        )
      }
    }
  }

  private fun fetchJson(url: String): JSONObject? =
    runCatching {
      val req =
        Request.Builder()
          .url(url)
          .get()
          .addHeader("Accept", "application/json")
          .addHeader("User-Agent", "DecanMovie-Desktop/1.1")
          .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        JSONObject(resp.body?.string().orEmpty())
      }
    }.getOrNull()

  private fun upgradeArtworkUrl(raw: String, size: Int): String {
    if (raw.isBlank()) return ""
    return raw.replace(Regex("\\d+x\\d+bb"), "${size}x${size}bb")
  }
}
