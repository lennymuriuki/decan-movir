package com.example

import com.example.data.model.Movie
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.TmdbService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TmdbIntegrationTest {

  @Test
  fun tmdbTokenValidation_rejectsPlaceholderAndBlankValues() {
    assertFalse(TmdbService.isValidToken(""))
    assertFalse(TmdbService.isValidToken("   "))
    assertFalse(TmdbService.isValidToken("null"))
    assertFalse(TmdbService.isValidToken(TmdbService.PLACEHOLDER_TOKEN))
    assertTrue(TmdbService.isValidToken("eyJhbGciOiJIUzI1NiJ9.sample.token"))
  }

  @Test
  fun parseListJson_filtersPeopleAndMusicAndClassifiesAnime() {
    val json =
      JSONObject(
        """
        {
          "results": [
            {
              "id": 999,
              "media_type": "person",
              "name": "Some Actor"
            },
            {
              "id": 888,
              "media_type": "movie",
              "title": "Concert Live",
              "genre_ids": [10402]
            },
            {
              "id": 693134,
              "media_type": "movie",
              "title": "Dune: Part Two",
              "overview": "Follow the mythic journey of Paul Atreides.",
              "poster_path": "/dune_poster.jpg",
              "backdrop_path": "/dune_backdrop.jpg",
              "release_date": "2024-02-27",
              "vote_average": 8.26,
              "genre_ids": [878, 12],
              "original_language": "en"
            },
            {
              "id": 1429,
              "media_type": "tv",
              "name": "Attack on Titan",
              "overview": "Humanity fights giant Titans.",
              "poster_path": "/aot.jpg",
              "backdrop_path": "/aot_bg.jpg",
              "first_air_date": "2013-04-07",
              "vote_average": 8.7,
              "genre_ids": [16, 10759],
              "original_language": "ja"
            }
          ]
        }
        """
          .trimIndent()
      )

    val parsed = TmdbService.parseListJson(json, forcedMediaKind = null, startRank = 1)
    assertEquals(2, parsed.size)

    val dune = parsed[0]
    assertEquals("tmdb_movie_693134", dune.id)
    assertEquals("https://image.tmdb.org/t/p/w500/dune_poster.jpg", dune.posterUrl)
    assertEquals("https://image.tmdb.org/t/p/w1280/dune_backdrop.jpg", dune.backdropUrl)
    assertEquals("Sci-Fi, Adventure", dune.genre)
    assertEquals("8.3", dune.imdbRating)
    assertEquals("2024", dune.releaseYear)
    assertEquals(1, dune.rankPosition)

    val aot = parsed[1]
    assertEquals("tmdb_tv_1429", aot.id)
    assertEquals("Anime", aot.mediaType)
    assertEquals("Japan", aot.country)
    assertEquals("Japanese", aot.language)
    assertTrue(aot.genre.contains("Anime"))
  }

  @Test
  fun parseDetailJson_extractsDirectorRuntimeAndRecommendations() {
    val json =
      JSONObject(
        """
        {
          "id": 872585,
          "title": "Oppenheimer",
          "overview": "The story of J. Robert Oppenheimer.",
          "poster_path": "/opp.jpg",
          "backdrop_path": "/opp_bg.jpg",
          "release_date": "2023-07-19",
          "vote_average": 8.1,
          "runtime": 181,
          "genres": [{"id": 18, "name": "Drama"}, {"id": 36, "name": "History"}],
          "credits": {
            "crew": [
              {"job": "Producer", "name": "Emma Thomas"},
              {"job": "Director", "name": "Christopher Nolan"}
            ]
          },
          "recommendations": {
            "results": [
              {
                "id": 27205,
                "title": "Inception",
                "poster_path": "/inc.jpg",
                "release_date": "2010-07-15",
                "vote_average": 8.4,
                "genre_ids": [28, 878]
              }
            ]
          }
        }
        """
          .trimIndent()
      )

    val detail = TmdbService.parseDetailJson(json, "movie")
    assertNotNull(detail)
    assertEquals("Christopher Nolan", detail!!.movie.director)
    assertEquals(181, detail.movie.runtimeMinutes)
    assertEquals("Drama, History", detail.movie.genre)
    assertEquals(1, detail.recommendations.size)
    assertEquals("Inception", detail.recommendations.first().title)
  }

  @Test
  fun repositoryMergeProviderGroup_preservesStreamSubjectAndUpgradesMetadata() {
    val repo = MovieCatalogRepository(tmdbService = TmdbService(tokenProvider = { "" }))
    val mboxItem =
      Movie(
        id = "mbox_12345",
        trackId = 12345L,
        title = "Inception",
        director = "Studio",
        genre = "Cinema",
        releaseYear = "",
        releaseDateLabel = "",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis = "Short",
        runtimeMinutes = 148,
        contentRating = "HD",
        subjectId = "12345",
        sourceProvider = "Decan",
      )
    val tmdbItem =
      Movie(
        id = "tmdb_movie_27205",
        trackId = 27205L,
        title = "Inception",
        director = "Feature Film",
        genre = "Action, Sci-Fi",
        releaseYear = "2010",
        releaseDateLabel = "2010-07-15",
        posterUrl = "https://image.tmdb.org/t/p/w500/inc.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/w1280/inc_bg.jpg",
        previewUrl = null,
        synopsis = "Cobb steals secrets from deep within the subconscious during the dream state.",
        runtimeMinutes = 0,
        contentRating = "HD",
        subjectId = "",
        imdbRating = "8.4",
        sourceProvider = "Decan Cinema",
      )

    val merged = repo.mergeProviderGroup(listOf(mboxItem, tmdbItem))
    assertNotNull(merged)
    assertEquals("12345", merged!!.subjectId)
    assertEquals("https://image.tmdb.org/t/p/w500/inc.jpg", merged.posterUrl)
    assertEquals("https://image.tmdb.org/t/p/w1280/inc_bg.jpg", merged.backdropUrl)
    assertEquals("8.4", merged.imdbRating)
    assertEquals("Action, Sci-Fi", merged.genre)
    assertEquals("2010", merged.releaseYear)
  }
}
