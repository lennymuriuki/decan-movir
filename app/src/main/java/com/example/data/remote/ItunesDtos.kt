package com.example.data.remote

import com.example.data.model.Movie
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ItunesSearchResponse(
  val resultCount: Int = 0,
  val results: List<ItunesMovieDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ItunesMovieDto(
  val trackId: Long? = null,
  val trackName: String? = null,
  val artistName: String? = null,
  val primaryGenreName: String? = null,
  val releaseDate: String? = null,
  val artworkUrl100: String? = null,
  val artworkUrl60: String? = null,
  val previewUrl: String? = null,
  val longDescription: String? = null,
  val shortDescription: String? = null,
  val trackTimeMillis: Long? = null,
  val contentAdvisoryRating: String? = null,
  val trackPrice: Double? = null,
  val currency: String? = null,
  val trackViewUrl: String? = null,
) {
  fun toDomain(): Movie? {
    val tid = trackId ?: return null
    val title = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val rawArt = artworkUrl100 ?: artworkUrl60 ?: ""
    val hdPoster = upgradeArtworkUrl(rawArt, 600)
    val hdBackdrop = upgradeArtworkUrl(rawArt, 1000)
    val year = releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "2024"
    val runtimeMin = ((trackTimeMillis ?: 0L) / 60_000L).toInt().coerceIn(0, 1000)
    val priceText =
      if (trackPrice != null && trackPrice > 0.0) {
        val sym = if (currency == "USD") "$" else "${currency.orEmpty()} "
        "$sym${String.format(java.util.Locale.US, "%.2f", trackPrice)}"
      } else {
        null
      }

    return Movie(
      id = Movie.sanitizeId("itunes_$tid"),
      trackId = tid,
      title = title.take(200),
      director = (artistName ?: "Featured Filmmaker").trim().take(120),
      genre = (primaryGenreName ?: "Cinema").trim().take(60),
      releaseYear = year,
      releaseDateLabel = releaseDate?.take(10) ?: year,
      posterUrl = hdPoster,
      backdropUrl = hdBackdrop,
      previewUrl = previewUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
      synopsis =
        (longDescription ?: shortDescription ?: "A featured cinematic release available on Decan Movie.")
          .trim()
          .take(2000),
      runtimeMinutes = runtimeMin,
      contentRating = (contentAdvisoryRating ?: "NR").trim().take(20),
      priceFormatted = priceText,
      storeUrl = trackViewUrl,
    )
  }
}

@JsonClass(generateAdapter = true)
data class TopMoviesRssResponse(
  val feed: RssFeedDto? = null,
)

@JsonClass(generateAdapter = true)
data class RssFeedDto(
  val entry: List<RssMovieEntryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class RssMovieEntryDto(
  val id: RssIdDto? = null,
  @Json(name = "im:name") val name: RssLabelDto? = null,
  @Json(name = "im:artist") val artist: RssLabelDto? = null,
  @Json(name = "im:image") val images: List<RssLabelDto> = emptyList(),
  val summary: RssLabelDto? = null,
  val category: RssCategoryDto? = null,
  @Json(name = "im:releaseDate") val releaseDate: RssReleaseDateDto? = null,
  @Json(name = "im:price") val price: RssLabelDto? = null,
  val link: List<RssLinkDto> = emptyList(),
) {
  fun toDomain(): Movie? {
    val rawId = id?.attributes?.id?.trim() ?: return null
    val tid = rawId.toLongOrNull() ?: return null
    val title = name?.label?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val rawArt = images.lastOrNull()?.label.orEmpty()
    val hdPoster = upgradeArtworkUrl(rawArt, 600)
    val hdBackdrop = upgradeArtworkUrl(rawArt, 1000)
    val year =
      releaseDate?.attributes?.label?.takeLast(4)?.takeIf { it.all(Char::isDigit) }
        ?: releaseDate?.label?.take(4)?.takeIf { it.all(Char::isDigit) }
        ?: "2025"

    val previewHref =
      link.firstOrNull { it.attributes?.type?.startsWith("video") == true }?.attributes?.href
    val storeHref =
      link.firstOrNull { it.attributes?.rel == "alternate" }?.attributes?.href

    return Movie(
      id = Movie.sanitizeId("itunes_$tid"),
      trackId = tid,
      title = title.take(200),
      director = (artist?.label ?: "Box Office Release").trim().take(120),
      genre = (category?.attributes?.label ?: "Featured").trim().take(60),
      releaseYear = year,
      releaseDateLabel = releaseDate?.attributes?.label ?: year,
      posterUrl = hdPoster,
      backdropUrl = hdBackdrop,
      previewUrl = previewHref,
      synopsis = (summary?.label ?: "Top trending release on Decan Movie.").trim().take(2000),
      runtimeMinutes = 0,
      contentRating = "HD",
      priceFormatted = price?.label,
      storeUrl = storeHref,
    )
  }
}

@JsonClass(generateAdapter = true)
data class RssIdDto(val attributes: RssIdAttrDto? = null)

@JsonClass(generateAdapter = true)
data class RssIdAttrDto(@Json(name = "im:id") val id: String? = null)

@JsonClass(generateAdapter = true)
data class RssLabelDto(val label: String? = null)

@JsonClass(generateAdapter = true)
data class RssCategoryDto(val attributes: RssCategoryAttrDto? = null)

@JsonClass(generateAdapter = true)
data class RssCategoryAttrDto(val label: String? = null)

@JsonClass(generateAdapter = true)
data class RssReleaseDateDto(
  val label: String? = null,
  val attributes: RssLabelDto? = null,
)

@JsonClass(generateAdapter = true)
data class RssLinkDto(val attributes: RssLinkAttrDto? = null)

@JsonClass(generateAdapter = true)
data class RssLinkAttrDto(
  val rel: String? = null,
  val type: String? = null,
  val href: String? = null,
)

private fun upgradeArtworkUrl(raw: String, size: Int): String {
  if (raw.isBlank()) return ""
  return raw.replace(Regex("\\d+x\\d+bb"), "${size}x${size}bb")
}
