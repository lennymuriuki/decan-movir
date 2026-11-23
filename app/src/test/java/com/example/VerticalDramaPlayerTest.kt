package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.example.data.cache.MediaResourceCacheManager
import com.example.data.model.DramaExperienceDetector
import com.example.data.model.Movie
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.settings.PlaybackProgressStore
import com.example.ui.components.DramaPlayerOverlay
import com.example.ui.components.DramaPlayerUiState
import java.io.File
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
class VerticalDramaPlayerTest {

  @get:Rule val composeTestRule = createComposeRule()

  private fun sampleMovie(
    id: String = "mbox_1",
    title: String = "Sample Title",
    genre: String = "Drama",
    mediaType: String = "Series",
    sourceProvider: String = "Decan",
  ) =
    Movie(
      id = id,
      trackId = 1L,
      title = title,
      director = "Director",
      genre = genre,
      releaseYear = "2025",
      releaseDateLabel = "2025-01-01",
      posterUrl = "",
      backdropUrl = "",
      previewUrl = null,
      synopsis = "A gripping drama series.",
      runtimeMinutes = 45,
      contentRating = "TV-14",
      subjectId = "100",
      mediaType = mediaType,
      sourceProvider = sourceProvider,
    )

  @Test
  fun dramaDetector_activatesOnlyForEpisodicDramasAndPreservesMoviesSeriesAnime() {
    val dramaSeries = sampleMovie(title = "Breaking Bad", genre = "Crime Drama", mediaType = "Series")
    val kDrama = sampleMovie(title = "Queen of Tears", genre = "Romance, Drama", mediaType = "Drama")
    val standaloneMovieDrama = sampleMovie(title = "Oppenheimer", genre = "Drama", mediaType = "Movie")
    val sciFiSeries = sampleMovie(title = "Stranger Things", genre = "Sci-Fi, Horror", mediaType = "Series")
    val animeSeries = sampleMovie(id = "mal_16498", title = "Attack on Titan", genre = "Action, Drama", mediaType = "Anime", sourceProvider = "Decan Anime")

    assertTrue(DramaExperienceDetector.isEpisodicDrama(dramaSeries))
    assertTrue(DramaExperienceDetector.isEpisodicDrama(kDrama))
    assertFalse(DramaExperienceDetector.isEpisodicDrama(standaloneMovieDrama))
    assertFalse(DramaExperienceDetector.isEpisodicDrama(sciFiSeries))
    assertFalse(DramaExperienceDetector.isEpisodicDrama(animeSeries))
  }

  @Test
  fun buildOrderedDramaEpisodes_ordersAcrossSeasonsDeterministically() {
    val drama = sampleMovie(title = "Hidden Truth", genre = "Romance Drama", mediaType = "Drama")
    val unorderedSeasons =
      listOf(
        MovieBoxSeason(season = 2, episodeCount = 2),
        MovieBoxSeason(season = 1, episodeCount = 3),
      )

    val episodes = DramaExperienceDetector.buildOrderedDramaEpisodes(drama, unorderedSeasons)
    assertEquals(5, episodes.size)
    assertEquals("Season 01 • Episode 01", episodes[0].subtitleLabel)
    assertEquals("Season 01 • Episode 03", episodes[2].subtitleLabel)
    assertEquals("Season 02 • Episode 01", episodes[3].subtitleLabel)
    assertEquals("Season 02 • Episode 02", episodes[4].subtitleLabel)
    assertEquals(3, DramaExperienceDetector.findEpisodePageIndex(episodes, season = 2, episode = 1))
  }

  @Test
  fun playbackProgressStore_retainsPerEpisodeProgressIndependently() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("decan_playback_progress", Context.MODE_PRIVATE).edit().clear().commit()
    val store = PlaybackProgressStore(context)

    store.saveProgress(
      movieId = "mbox_drama_99",
      title = "Moonlit Love",
      posterUrl = "",
      mediaType = "Drama",
      season = 1,
      episode = 1,
      positionMs = 42_000L,
      durationMs = 180_000L,
    )
    store.saveProgress(
      movieId = "mbox_drama_99",
      title = "Moonlit Love",
      posterUrl = "",
      mediaType = "Drama",
      season = 1,
      episode = 2,
      positionMs = 15_000L,
      durationMs = 180_000L,
    )

    assertEquals(42_000L, store.getEpisodeProgress("mbox_drama_99", season = 1, episode = 1))
    assertEquals(15_000L, store.getEpisodeProgress("mbox_drama_99", season = 1, episode = 2))
    assertEquals(2, store.getProgress("mbox_drama_99")?.episode)
  }

  @Test
  fun mediaResourceCacheManager_capsAdaptiveCacheWithin40MbTarget() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val budget = MediaResourceCacheManager.computeAdaptiveDiskBudgetBytes(context.cacheDir)
    assertTrue(budget in 1L..MediaResourceCacheManager.TARGET_TOTAL_MEDIA_CACHE_BYTES)
    val zeroFallback = MediaResourceCacheManager.computeAdaptiveDiskBudgetBytes(File("/nonexistent_dir_xyz"))
    assertTrue(zeroFallback <= MediaResourceCacheManager.TARGET_TOTAL_MEDIA_CACHE_BYTES)
  }

  @Test
  fun dramaPlayerOverlay_verticalSwipeTransitionsSingleEpisodeAtATimeAndTriggersActions() {
    val drama = sampleMovie(title = "City Lights", genre = "Romance Drama", mediaType = "Drama")
    val episodes =
      DramaExperienceDetector.buildOrderedDramaEpisodes(
        drama,
        listOf(MovieBoxSeason(season = 1, episodeCount = 3)),
      )
    var liked = false
    var watchlisted = false
    var backPressed = false

    composeTestRule.setContent {
      MaterialTheme {
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { episodes.size })
        VerticalPager(
          state = pagerState,
          modifier = Modifier.fillMaxSize().testTag("test_drama_pager"),
        ) { page ->
          Box(modifier = Modifier.fillMaxSize()) {
            val ep = episodes[page]
            DramaPlayerOverlay(
              state =
                DramaPlayerUiState(
                  dramaTitle = drama.title,
                  episode = ep,
                  nextEpisode = episodes.getOrNull(page + 1),
                  isPlaying = true,
                  isBuffering = false,
                  isLoadingStreams = false,
                  errorMessage = null,
                  controlsVisible = true,
                  isLocked = false,
                  isLiked = liked,
                  isInWatchlist = watchlisted,
                  ccActive = false,
                  activeCaptionText = null,
                  currentPositionMs = 12_000L,
                  durationMs = 120_000L,
                  qualityLabel = "1080p",
                  playbackSpeed = 1.0f,
                  fillScreen = true,
                ),
              onBack = { backPressed = true },
              onPlayPause = {},
              onSeekTo = {},
              onToggleLike = { liked = !liked },
              onToggleWatchlist = { watchlisted = !watchlisted },
              onShare = {},
              onOpenMoreOptions = {},
              onOpenSubtitles = {},
              onOpenSettings = {},
              onNextEpisode = {},
              onRetryStream = {},
              onUnlock = {},
            )
          }
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drama_episode_badge").assertTextContains("Season 01 • Episode 01")
    composeTestRule.onNodeWithTag("drama_like_button").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("drama_watchlist_button").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("drama_share_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drama_cc_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drama_settings_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drama_next_indicator").assertIsDisplayed()
    assertTrue(liked)
    assertTrue(watchlisted)

    // Swipe UP -> advances exactly one episode to Episode 02
    composeTestRule.onNodeWithTag("test_drama_pager").performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drama_episode_badge").assertTextContains("Season 01 • Episode 02")

    // Swipe DOWN -> returns cleanly to Episode 01
    composeTestRule.onNodeWithTag("test_drama_pager").performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drama_episode_badge").assertTextContains("Season 01 • Episode 01")

    composeTestRule.onNodeWithTag("drama_back_button").performClick()
    assertTrue(backPressed)
  }
}
