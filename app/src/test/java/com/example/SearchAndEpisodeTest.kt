package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Movie
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxEngine
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.ui.components.EpisodeNumberGrid
import com.example.ui.components.calculateEpisodeRanges
import com.example.ui.screens.SearchScreen
import com.example.ui.viewmodel.SearchUiState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SearchAndEpisodeTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun calculateEpisodeRanges_splitsLongSeriesIntoClean30EpisodeChunks() {
    assertEquals(listOf(1..12), calculateEpisodeRanges(12))
    assertEquals(listOf(1..30, 31..60, 61..75), calculateEpisodeRanges(75))
  }

  @Test
  fun episodeNumberGrid_rendersNumberButtonsAndPlaysOnClick() {
    var clickedSeason = 0
    var clickedEpisode = 0
    val seasons = listOf(MovieBoxSeason(season = 1, episodeCount = 45, resolutions = listOf(1080)))

    composeTestRule.setContent {
      MaterialTheme {
        EpisodeNumberGrid(
          seasons = seasons,
          selectedSeason = 1,
          selectedEpisode = 1,
          onSelectSeason = {},
          onSelectEpisode = { se, ep ->
            clickedSeason = se
            clickedEpisode = ep
          },
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("1").assertIsDisplayed()
    composeTestRule.onNodeWithText("2").assertIsDisplayed()
    composeTestRule.onNodeWithText("3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("episode_range_31_45").assertIsDisplayed()

    composeTestRule.onNodeWithTag("episode_btn_3").performClick()
    assertEquals(1, clickedSeason)
    assertEquals(3, clickedEpisode)
  }

  @Test
  fun searchScreen_submitButtonTriggersSearch() {
    var submitCount = 0
    composeTestRule.setContent {
      MaterialTheme {
        SearchScreen(
          uiState = SearchUiState(query = "Inception"),
          savedLibraryMap = emptyMap(),
          onQueryChange = {},
          onSubmitSearch = { submitCount++ },
          onSelectSuggestion = {},
          onSelectGenre = {},
          onSelectSort = {},
          onOpenMovie = {},
          onToggleWatchlist = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("search_submit_button").assertIsDisplayed().performClick()
    assertEquals(1, submitCount)
  }

  @Test
  fun movieBoxEngine_parsesWebSearchAndSuggestPayloads() {
    val engine = MovieBoxEngine()
    val searchPayload =
      JSONObject(
        """
        {
          "code": 0,
          "data": {
            "items": [
              {
                "subjectId": "111222333444",
                "subjectType": 1,
                "title": "Inception",
                "genre": "Sci-Fi,Action",
                "releaseDate": "2010-07-16",
                "imdbRatingValue": "8.8",
                "detailPath": "inception-2010",
                "cover": { "url": "https://example.com/inception.jpg" }
              }
            ]
          }
        }
        """.trimIndent()
      )
    val parsedSearch = engine.parseSearchResultsJson(searchPayload)
    assertEquals(1, parsedSearch.size)
    assertEquals("Inception", parsedSearch.first().title)
    assertEquals("111222333444", parsedSearch.first().subjectId)

    val suggestPayload =
      JSONObject(
        """
        {
          "data": {
            "items": [
              {
                "word": "Interstellar",
                "subject": {
                  "subjectId": "999888777666",
                  "subjectType": 1,
                  "title": "Interstellar",
                  "genre": "Sci-Fi",
                  "releaseDate": "2014-11-07",
                  "detailPath": "interstellar-2014",
                  "cover": { "url": "https://example.com/interstellar.jpg" }
                }
              }
            ]
          }
        }
        """.trimIndent()
      )
    val (suggestions, movies) = engine.parseSearchSuggestPayload(suggestPayload)
    assertEquals(1, suggestions.size)
    assertEquals("Interstellar", suggestions.first().title)
    assertEquals(1, movies.size)
    assertEquals("999888777666", movies.first().subjectId)
  }

  @Test
  fun rankSearchResults_prioritizesExactTitleMatchAndStreamableTitle() {
    val repo = MovieCatalogRepository()
    val loose =
      Movie(
        id = "itunes_1",
        trackId = 1L,
        title = "Soundtrack From Inception",
        director = "Composer",
        genre = "Music",
        releaseYear = "2010",
        releaseDateLabel = "2010",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis = "",
        runtimeMinutes = 100,
        contentRating = "PG",
      )
    val exact =
      Movie(
        id = "mbox_2",
        trackId = 2L,
        title = "Inception",
        director = "Nolan",
        genre = "Sci-Fi",
        releaseYear = "2010",
        releaseDateLabel = "2010",
        posterUrl = "https://example.com/p.jpg",
        backdropUrl = "",
        previewUrl = null,
        synopsis = "",
        runtimeMinutes = 148,
        contentRating = "MOVIE",
        subjectId = "2",
      )
    val ranked = repo.rankSearchResults("inception", listOf(loose, exact))
    assertTrue(ranked.first().id == "mbox_2")
  }
}
