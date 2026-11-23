package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.Movie
import com.example.data.remote.CategorySortMode
import com.example.data.remote.JikanTvMazeService
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxEngine
import com.example.ui.screens.DiscoverScreen
import com.example.ui.theme.DecanMovieTheme
import com.example.ui.viewmodel.DiscoverUiState
import com.example.ui.viewmodel.MediaFilter
import com.example.ui.viewmodel.UpdateUiState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoveryAndFilterTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun jikanParser_rejectsMusicVideosPvsAndCmsFromAnimeCharts() {
    val root =
      JSONObject(
        """
        {
          "data": [
            {
              "mal_id": 1001,
              "title": "Demon Slayer: Kimetsu no Yaiba",
              "type": "TV",
              "episodes": 26,
              "duration": "24 min per ep",
              "score": 8.6,
              "genres": [{"name": "Action"}, {"name": "Supernatural"}]
            },
            {
              "mal_id": 1002,
              "title": "YOASOBI - Idol (Official Music Video)",
              "type": "Music",
              "episodes": 1,
              "duration": "3 min",
              "score": 8.9,
              "genres": [{"name": "Music"}]
            },
            {
              "mal_id": 1003,
              "title": "Promo Trailer PV",
              "type": "PV",
              "episodes": 1,
              "duration": "2 min",
              "genres": [{"name": "Action"}]
            }
          ]
        }
        """.trimIndent()
      )

    val parsed = JikanTvMazeService().parseJikanAnimeList(root, startRank = 1)
    assertEquals(1, parsed.size)
    assertEquals("Demon Slayer: Kimetsu no Yaiba", parsed.first().title)
    assertEquals("Anime", parsed.first().mediaType)
  }

  @Test
  fun movieBoxEngine_rejectsMusicSubjectsAndOfficialMusicVideos() {
    val engine = MovieBoxEngine()
    val payload =
      JSONObject(
        """
        {
          "data": {
            "items": [
              {
                "subjectId": "111",
                "title": "Dune: Part Two",
                "subjectType": 1,
                "genre": "Sci-Fi, Adventure",
                "releaseDate": "2024-03-01",
                "imdbRatingValue": "8.6",
                "duration": 9960
              },
              {
                "subjectId": "222",
                "title": "Top Hit Song (Official Music Video)",
                "subjectType": 3,
                "genre": "Music",
                "releaseDate": "2025-01-10",
                "duration": 210
              },
              {
                "subjectId": "333",
                "title": "Anime Opening OST [MV]",
                "subjectType": 1,
                "genre": "Animation",
                "releaseDate": "2024-06-01",
                "duration": 240
              }
            ]
          }
        }
        """.trimIndent()
      )

    val parsed = engine.parseSearchResultsJson(payload)
    assertEquals(1, parsed.size)
    assertEquals("Dune: Part Two", parsed.first().title)
  }

  @Test
  fun catalogRepository_filtersSongsAndSortsCategoriesProperly() {
    val repo = MovieCatalogRepository()
    val validOld =
      Movie(
        id = "m1",
        trackId = 1L,
        title = "Inception",
        director = "Nolan",
        genre = "Action, Sci-Fi",
        releaseYear = "2010",
        releaseDateLabel = "2010-07-16",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis = "Dream heist.",
        runtimeMinutes = 148,
        contentRating = "PG-13",
        mediaType = "Movie",
        imdbRating = "8.8",
        rankPosition = 5,
      )
    val validNew =
      Movie(
        id = "m2",
        trackId = 2L,
        title = "Solo Leveling",
        director = "A-1",
        genre = "Action, Supernatural",
        releaseYear = "2025",
        releaseDateLabel = "2025-01-05",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis = "Hunter rises.",
        runtimeMinutes = 24,
        contentRating = "TV-14",
        mediaType = "Anime",
        imdbRating = "8.5",
        rankPosition = 1,
      )
    val song =
      Movie(
        id = "s1",
        trackId = 3L,
        title = "Theme Song (Official Video)",
        director = "Singer",
        genre = "Music",
        releaseYear = "2025",
        releaseDateLabel = "2025-02-01",
        posterUrl = "",
        backdropUrl = "",
        previewUrl = null,
        synopsis = "Track",
        runtimeMinutes = 3,
        contentRating = "MUSIC",
        mediaType = "Music",
      )

    assertTrue(repo.isValidCinemaEntry(validOld))
    assertTrue(repo.isValidCinemaEntry(validNew))
    assertFalse(repo.isValidCinemaEntry(song))

    val byRank = repo.sortCatalogMovies(listOf(validOld, validNew, song), CategorySortMode.TOP_RANKED)
    assertEquals(listOf("m2", "m1"), byRank.map { it.id })

    val byRating = repo.sortCatalogMovies(listOf(validOld, validNew, song), CategorySortMode.HIGHEST_RATED)
    assertEquals(listOf("m1", "m2"), byRating.map { it.id })

    val byNewest = repo.sortCatalogMovies(listOf(validOld, validNew, song), CategorySortMode.NEWEST)
    assertEquals(listOf("m2", "m1"), byNewest.map { it.id })
  }

  @Test
  fun discoverScreen_showsShimmerLoadingViewDuringColdStart() {
    composeTestRule.setContent {
      DecanMovieTheme {
        DiscoverScreen(
          uiState = DiscoverUiState(isLoading = true, allMovies = emptyList()),
          updateState = UpdateUiState(),
          savedLibraryMap = emptyMap(),
          continueWatching = emptyList(),
          unreadNotifications = 0,
          onRefresh = {},
          onSelectMediaFilter = {},
          onSelectGenre = {},
          onOpenMovie = {},
          onPlayMovie = {},
          onResumeWatching = {},
          onRemoveContinueWatching = {},
          onToggleWatchlist = {},
          onLoadMore = {},
          onOpenDownloads = {},
          onOpenNotifications = {},
          onOpenSettings = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("cinema_loading_skeleton").assertIsDisplayed()
  }

  @Test
  fun discoverScreen_rendersCategoryChipsSortChipsAndCompactGrid() {
    val sampleMovies =
      listOf(
        Movie(
          id = "a1",
          trackId = 10L,
          title = "Jujutsu Kaisen",
          director = "MAPPA",
          genre = "Action, Supernatural",
          releaseYear = "2023",
          releaseDateLabel = "2023-07-06",
          posterUrl = "",
          backdropUrl = "",
          previewUrl = null,
          synopsis = "Cursed energy.",
          runtimeMinutes = 24,
          contentRating = "TV-14",
          mediaType = "Anime",
          imdbRating = "8.7",
        ),
        Movie(
          id = "a2",
          trackId = 11L,
          title = "Mad Max: Fury Road",
          director = "George Miller",
          genre = "Action, Adventure",
          releaseYear = "2015",
          releaseDateLabel = "2015-05-15",
          posterUrl = "",
          backdropUrl = "",
          previewUrl = null,
          synopsis = "Desert chase.",
          runtimeMinutes = 120,
          contentRating = "R",
          mediaType = "Movie",
          imdbRating = "8.1",
        ),
        Movie(
          id = "a3",
          trackId = 12L,
          title = "Dandadan",
          director = "Science SARU",
          genre = "Action, Supernatural",
          releaseYear = "2024",
          releaseDateLabel = "2024-10-04",
          posterUrl = "",
          backdropUrl = "",
          previewUrl = null,
          synopsis = "Ghosts and aliens.",
          runtimeMinutes = 24,
          contentRating = "TV-14",
          mediaType = "Anime",
          imdbRating = "8.6",
        ),
      )

    var clickedSort: CategorySortMode? = null
    composeTestRule.setContent {
      DecanMovieTheme {
        DiscoverScreen(
          uiState =
            DiscoverUiState(
              isLoading = false,
              allMovies = sampleMovies,
              selectedMediaFilter = MediaFilter.ALL,
              selectedGenre = "Action",
              selectedCategorySort = CategorySortMode.FEATURED,
              availableGenres = listOf("All", "Action", "Adventure", "Anime", "Supernatural"),
            ),
          updateState = UpdateUiState(),
          savedLibraryMap = emptyMap(),
          continueWatching = emptyList(),
          unreadNotifications = 0,
          onRefresh = {},
          onSelectMediaFilter = {},
          onSelectGenre = {},
          onSelectCategorySort = { clickedSort = it },
          onOpenMovie = {},
          onPlayMovie = {},
          onResumeWatching = {},
          onRemoveContinueWatching = {},
          onToggleWatchlist = {},
          onLoadMore = {},
          onOpenDownloads = {},
          onOpenNotifications = {},
          onOpenSettings = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("discover_genre_Action").assertIsDisplayed()
    composeTestRule.onNodeWithTag("discover_genre_Adventure").assertIsDisplayed()
    composeTestRule.onNodeWithTag("discover_genre_Anime").assertIsDisplayed()
    composeTestRule.onNodeWithTag("discover_genre_Supernatural").assertIsDisplayed()
    composeTestRule.onNodeWithTag("category_sort_TOP_RANKED").assertIsDisplayed()
    composeTestRule.onNodeWithTag("movie_card_a1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("movie_card_a2").assertIsDisplayed()
    composeTestRule.onNodeWithTag("movie_card_a3").assertIsDisplayed()
    composeTestRule.onNodeWithText("Jujutsu Kaisen").assertIsDisplayed()

    composeTestRule.onNodeWithTag("category_sort_TOP_RANKED").performClick()
    assertEquals(CategorySortMode.TOP_RANKED, clickedSort)
  }
}
