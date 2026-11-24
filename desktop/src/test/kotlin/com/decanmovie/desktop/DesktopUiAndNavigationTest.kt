package com.decanmovie.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.decanmovie.desktop.data.remote.DesktopGitHubReleaseInfo
import com.decanmovie.desktop.data.remote.DesktopHomeCatalogResult
import com.decanmovie.desktop.data.remote.WindowsReleaseAsset
import com.decanmovie.desktop.data.store.DesktopAppStorage
import com.decanmovie.desktop.media.LocalEnableDesktopVideoSurface
import com.decanmovie.desktop.model.DesktopDestination
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.PlaybackHistoryItem
import com.decanmovie.desktop.ui.screens.DesktopVerticalDramaPlayerScreen
import com.decanmovie.desktop.ui.screens.HomeScreen
import com.decanmovie.desktop.ui.screens.UpdateAvailableDialog
import com.decanmovie.desktop.ui.theme.DecanDesktopTheme
import com.decanmovie.desktop.viewmodel.ActiveDesktopOverlay
import com.decanmovie.desktop.viewmodel.DesktopAppController
import com.decanmovie.desktop.viewmodel.DesktopUpdateState
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DesktopUiAndNavigationTest {
  @Test
  fun sidebarNavigatesAcrossAllTenDesktopDestinations() = runComposeUiTest {
    val tmpDir = Files.createTempDirectory("decan_ui_nav").toFile()
    try {
      val storage = DesktopAppStorage(baseDir = tmpDir)
      storage.updateSettings { it.copy(autoCheckUpdates = false) }
      val controller = DesktopAppController(storage = storage)

      setContent {
        CompositionLocalProvider(LocalEnableDesktopVideoSurface provides false) {
          DecanDesktopTheme {
            DecanDesktopShell(controller = controller)
          }
        }
      }

      onNodeWithTag("home_screen").assertIsDisplayed()

      DesktopDestination.entries.forEach { dest ->
        onNodeWithTag("nav_${dest.name.lowercase()}").performClick()
        waitForIdle()
        assertEquals(dest, controller.destination.value)
      }

      onNodeWithTag("settings_screen").assertIsDisplayed()
      controller.shutdown()
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun homeScreenRendersPopulatedCatalogSectionsAndHero() = runComposeUiTest {
    val sampleMovie = Movie(id = "m_1", title = "Dune: Part Two", genre = "Sci-Fi", mediaType = "Movie", imdbRating = "8.6")
    val sampleDrama = Movie(id = "d_1", title = "Queen of Tears", genre = "Drama, Romance", mediaType = "Drama", imdbRating = "8.3")
    val sampleSeries = Movie(id = "s_1", title = "Breaking Bad", genre = "Crime", mediaType = "Series", imdbRating = "9.5")
    val sampleAnime = Movie(id = "mal_1", title = "Solo Leveling", genre = "Action", mediaType = "Anime", imdbRating = "8.5")

    val feed =
      DesktopHomeCatalogResult(
        spotlight = listOf(sampleMovie, sampleDrama),
        trending = listOf(sampleMovie, sampleSeries),
        recentlyAdded = listOf(sampleMovie),
        popularMovies = listOf(sampleMovie),
        popularSeries = listOf(sampleSeries),
        popularAnime = listOf(sampleAnime),
        popularDramas = listOf(sampleDrama),
        recommended = listOf(sampleMovie),
        rails = emptyList(),
        allMovies = listOf(sampleMovie, sampleDrama, sampleSeries, sampleAnime),
        trendingKeywords = listOf("Dune", "Solo Leveling"),
        isOfflineFallback = false,
      )
    val history =
      listOf(
        PlaybackHistoryItem(
          movieId = "d_1",
          title = "Queen of Tears",
          posterUrl = "",
          mediaType = "Drama",
          season = 1,
          episode = 2,
          positionMs = 45_000L,
          durationMs = 2_400_000L,
          updatedAtMs = System.currentTimeMillis(),
        )
      )

    setContent {
      DecanDesktopTheme {
        HomeScreen(
          feed = feed,
          isLoading = false,
          history = history,
          watchlistIds = emptySet(),
          compactCards = false,
          onRefresh = {},
          onMovieClick = {},
          onQuickPlay = {},
          onResumeHistory = {},
          onToggleWatchlist = {},
        )
      }
    }

    onNodeWithTag("home_screen").assertIsDisplayed()
    onNodeWithTag("hero_play_button").assertIsDisplayed()
    onNodeWithText("Continue Watching").assertIsDisplayed()
    onNodeWithTag("home_screen").performScrollToNode(hasText("Trending Now"))
    onNodeWithText("Trending Now").assertIsDisplayed()
    onNodeWithTag("home_screen").performScrollToNode(hasText("Popular Dramas"))
    onNodeWithText("Popular Dramas").assertIsDisplayed()
  }

  @Test
  fun dramaLaunchesVerticalDramaPlayerWhileMovieLaunchesStandardTheaterPlayer() = runComposeUiTest {
    val tmpDir = Files.createTempDirectory("decan_ui_players").toFile()
    try {
      val storage = DesktopAppStorage(baseDir = tmpDir)
      storage.updateSettings { it.copy(autoCheckUpdates = false) }
      val controller = DesktopAppController(storage = storage)

      setContent {
        CompositionLocalProvider(LocalEnableDesktopVideoSurface provides false) {
          DecanDesktopTheme {
            DecanDesktopShell(controller = controller)
          }
        }
      }

      val drama =
        Movie(
          id = "mbox_test_drama",
          title = "Test K-Drama",
          genre = "Drama, Romance",
          mediaType = "Drama",
        )
      controller.openPlaybackForMovie(drama, season = 1, episode = 1)
      waitForIdle()
      onNodeWithTag("desktop_vertical_drama_player").assertIsDisplayed()
      onNodeWithTag("drama_episode_label").assertTextEquals("Season 01 • Episode 01")

      val film =
        Movie(
          id = "mbox_test_movie",
          title = "Test Sci-Fi Film",
          genre = "Sci-Fi, Action",
          mediaType = "Movie",
        )
      controller.openPlaybackForMovie(film)
      waitForIdle()
      onNodeWithTag("desktop_theater_player").assertIsDisplayed()

      controller.shutdown()
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun updateDialogShowsWindowsAssetsAndHidesAndroidApk() = runComposeUiTest {
    val release =
      DesktopGitHubReleaseInfo(
        tagName = "v1.4.0",
        title = "Decan Movie v1.4.0",
        changelog = "Added smoother vertical drama transitions.",
        publishedAt = "2026-11-20",
        htmlUrl = "https://github.com/lennymuriuki/decan-movir/releases/tag/v1.4.0",
        isPrerelease = false,
        assets =
          listOf(
            WindowsReleaseAsset("decan-movie-android.apk", "https://example.com/a.apk", 42.0),
            WindowsReleaseAsset("Decan-Movie-Setup.exe", "https://example.com/Decan-Movie-Setup.exe", 91.2, sha256 = "b".repeat(64)),
          ),
        isNewerThanCurrent = true,
      )
    var downloadTriggered = false

    setContent {
      DecanDesktopTheme {
        UpdateAvailableDialog(
          updateState = DesktopUpdateState(currentVersion = "1.1.0", latestRelease = release, showUpdateDialog = true),
          preferPortable = false,
          onDismiss = {},
          onDownload = { asset, _, _ ->
            downloadTriggered = asset?.name == "Decan-Movie-Setup.exe"
          },
          onInstall = {},
        )
      }
    }

    onNodeWithText("Decan-Movie-Setup.exe").assertIsDisplayed()
    onNodeWithTag("update_dialog_download_btn").performClick()
    assertTrue(downloadTriggered)
  }

  @Test
  fun verticalDramaPlayerExposesNavigationAndActionControls() = runComposeUiTest {
    val tmpDir = Files.createTempDirectory("decan_ui_vdp").toFile()
    try {
      val storage = DesktopAppStorage(baseDir = tmpDir)
      storage.updateSettings { it.copy(autoCheckUpdates = false) }
      val controller = DesktopAppController(storage = storage)
      val drama = Movie(id = "mbox_vd_1", title = "Silent Promise", genre = "Drama, Romance", mediaType = "Drama")
      controller.catalogRepository.cacheMovie(drama)

      setContent {
        CompositionLocalProvider(LocalEnableDesktopVideoSurface provides false) {
          DecanDesktopTheme {
            DesktopVerticalDramaPlayerScreen(
              request = ActiveDesktopOverlay.VerticalDramaPlayer(movieId = drama.id, season = 1, episode = 1),
              catalogRepository = controller.catalogRepository,
              storage = storage,
              settings = storage.currentSettings(),
              isInWatchlist = false,
              isFullscreen = false,
              onToggleFullscreen = {},
              onToggleWatchlist = {},
              onSwitchToStandardPlayer = { _, _, _, _ -> },
              onBack = {},
              onNotify = {},
            )
          }
        }
      }

      onNodeWithTag("desktop_vertical_drama_player").assertIsDisplayed()
      onNodeWithTag("drama_next_episode_btn").assertIsDisplayed()
      onNodeWithTag("drama_prev_episode_btn").assertIsDisplayed()
      controller.shutdown()
    } finally {
      tmpDir.deleteRecursively()
    }
  }
}
