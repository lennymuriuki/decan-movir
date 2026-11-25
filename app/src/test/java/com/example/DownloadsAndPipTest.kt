package com.example

import android.app.DownloadManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.download.DownloadTaskInfo
import com.example.data.remote.GitHubUpdateRepository
import com.example.data.settings.DownloadStorageLocation
import com.example.data.settings.PlaybackProgressStore
import com.example.ui.components.PlayerControlsOverlay
import com.example.ui.components.PlayerOverlayState
import com.example.ui.pip.PipSupport
import com.example.ui.screens.DownloadsScreen
import com.example.ui.theme.DecanMovieTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadsAndPipTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun playbackProgressStore_tracksDownloadWatchPercentAndFinishedState() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = PlaybackProgressStore(context)

    // 23% watched of 100s
    store.saveProgress(
      movieId = "mbox_101",
      title = "Solo Leveling",
      posterUrl = "https://example.com/solo.jpg",
      mediaType = "Anime",
      season = 1,
      episode = 2,
      positionMs = 23_000L,
      durationMs = 100_000L,
      localUri = "file:///downloads/DecanMovie/Series/Solo_Leveling/Season_01/S01E02_Solo_Leveling_1080P.mp4",
    )

    val partial =
      store.findDownloadWatchProgress(
        movieId = "mbox_101",
        season = 1,
        episode = 2,
        localUri = "file:///downloads/DecanMovie/Series/Solo_Leveling/Season_01/S01E02_Solo_Leveling_1080P.mp4",
      )
    assertNotNull(partial)
    assertEquals(23, partial!!.percentWatched)
    assertTrue(partial.isInProgress)
    assertFalse(partial.isFinished)
    assertEquals("Continue Watching • 23% watched", partial.statusLabel)
    assertEquals(
      23_000L,
      store.getLocalProgress("file:///downloads/DecanMovie/Series/Solo_Leveling/Season_01/S01E02_Solo_Leveling_1080P.mp4"),
    )

    // 95% watched -> Finished, resume resets to 0L
    store.saveProgress(
      movieId = "mbox_101",
      title = "Solo Leveling",
      posterUrl = "https://example.com/solo.jpg",
      mediaType = "Anime",
      season = 1,
      episode = 3,
      positionMs = 95_000L,
      durationMs = 100_000L,
      localUri = "file:///downloads/DecanMovie/Series/Solo_Leveling/Season_01/S01E03_Solo_Leveling_1080P.mp4",
    )
    val finished =
      store.findDownloadWatchProgress(
        movieId = "mbox_101",
        season = 1,
        episode = 3,
      )
    assertNotNull(finished)
    assertTrue(finished!!.isFinished)
    assertEquals("Finished", finished.statusLabel)
    assertEquals(
      0L,
      store.getLocalProgress("file:///downloads/DecanMovie/Series/Solo_Leveling/Season_01/S01E03_Solo_Leveling_1080P.mp4"),
    )

    store.clearDownloadWatchProgress(movieId = "mbox_101", season = 1, episode = 2)
    assertNull(store.findDownloadWatchProgress(movieId = "mbox_101", season = 1, episode = 2))
  }

  @Test
  fun downloadsScreen_allowsFolderSelectionDeletionAndShowsLogoAndProgress() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = PlaybackProgressStore(context)
    store.saveProgress(
      movieId = "mbox_99",
      title = "Attack on Titan",
      posterUrl = "",
      mediaType = "Anime",
      season = 1,
      episode = 1,
      positionMs = 23_000L,
      durationMs = 100_000L,
      localUri = "file:///tmp/S01E01_AoT.mp4",
    )

    val deletedIds = mutableListOf<Long>()
    val sampleTasks =
      listOf(
        DownloadTaskInfo(
          id = 11L,
          title = "Attack on Titan • S1E1 (1080P)",
          description = "Decan Movie",
          status = DownloadManager.STATUS_SUCCESSFUL,
          statusLabel = "Completed",
          reasonHint = null,
          bytesDownloaded = 150_000_000L,
          totalBytes = 150_000_000L,
          progressFraction = 1f,
          localUri = "file:///tmp/S01E01_AoT.mp4",
          mimeType = "video/mp4",
          folderName = "Attack_on_Titan",
          isSeriesFolder = true,
          seasonNumber = 1,
          episodeNumber = 1,
          movieId = "mbox_99",
          mediaType = "Anime",
          qualityLabel = "1080P",
        ),
        DownloadTaskInfo(
          id = 12L,
          title = "Inception (1080P)",
          description = "Decan Movie",
          status = DownloadManager.STATUS_SUCCESSFUL,
          statusLabel = "Completed",
          reasonHint = null,
          bytesDownloaded = 400_000_000L,
          totalBytes = 400_000_000L,
          progressFraction = 1f,
          localUri = "file:///tmp/Inception.mp4",
          mimeType = "video/mp4",
          folderName = "Inception",
          isSeriesFolder = false,
          movieId = "mbox_88",
          mediaType = "Movie",
          qualityLabel = "1080P",
        ),
      )

    composeTestRule.setContent {
      DecanMovieTheme {
        DownloadsScreen(
          storageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
          progressStore = store,
          previewTasks = sampleTasks,
          onRemoveDownloads = { ids ->
            deletedIds.addAll(ids)
            ids.size
          },
          onExploreMovies = {},
          onNotify = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    // Decan brand logo and 23% watch summary displayed in folders view
    composeTestRule.onNodeWithTag("downloads_brand_logo").assertIsDisplayed()
    composeTestRule.onNodeWithText("Continue Watching • S01E01 • 23% watched").assertIsDisplayed()

    // Enter multi-select mode right from Folders view
    composeTestRule.onNodeWithTag("downloads_select_mode_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("downloads_selection_bar").assertIsDisplayed()

    // Select the Attack_on_Titan folder and delete it
    composeTestRule.onNodeWithTag("downloads_list")
      .performScrollToNode(hasTestTag("folder_checkbox_series_Attack_on_Titan"))
    composeTestRule.onNodeWithTag("folder_checkbox_series_Attack_on_Titan").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("downloads_delete_selected_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals(listOf(11L), deletedIds)
  }

  @Test
  fun pipSupport_clampsAspectRatioAndOverlayShowsPipButton() {
    val wide = PipSupport.computeSafeAspectRatio(4000, 1000)
    assertTrue(wide.toFloat() in 0.45f..2.35f)
    val tall = PipSupport.computeSafeAspectRatio(400, 2000)
    assertTrue(tall.toFloat() in 0.45f..2.35f)

    var pipClicked = false
    val state =
      PlayerOverlayState(
        title = "Inception",
        subtitleLine = "1080P",
        isPlaying = true,
        isBuffering = false,
        isLoadingMedia = false,
        isFullscreen = false,
        isLocked = false,
        controlsVisible = true,
        zoomFill = false,
        hasPlaybackUrl = true,
        canGoPrevious = false,
        canGoNext = false,
        canDownload = true,
        ccActive = false,
        currentPositionMs = 5_000L,
        durationMs = 100_000L,
        bufferedFraction = 0.5f,
        volume = 0.8f,
        isMuted = false,
        activeCaptionText = null,
        seekFeedbackText = null,
        upNextSeconds = null,
        gestureHudType = null,
        gestureHudFraction = 0f,
        canEnterPip = true,
      )
    composeTestRule.setContent {
      DecanMovieTheme {
        PlayerControlsOverlay(
          state = state,
          onBack = {},
          onPlayPause = {},
          onPrevious = {},
          onNext = {},
          onSeekBy = {},
          onSeekTo = {},
          onToggleMute = {},
          onVolumeChange = {},
          onToggleFullscreen = {},
          onToggleLock = {},
          onToggleZoomFill = {},
          onOpenSubtitles = {},
          onOpenSettings = {},
          onOpenDownload = {},
          onOpenExternal = {},
          onPlayNextNow = {},
          onDismissUpNext = {},
          onControlInteraction = {},
          onEnterPip = { pipClicked = true },
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("player_pip_button").assertIsDisplayed().performClick()
    assertTrue(pipClicked)
  }

  @Test
  fun gitHubAtomParser_supportsReleaseApkAssetSelection() {
    val atomXml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom">
        <entry>
          <title>Decan Movie v3.0.0</title>
          <updated>2026-11-24T10:00:00Z</updated>
          <link rel="alternate" type="text/html" href="https://github.com/lennymuriuki/decan-movir/releases/tag/v3.0.0"/>
          <content type="html">Release APK update</content>
        </entry>
      </feed>
      """.trimIndent()
    val repo = GitHubUpdateRepository()
    val parsed = repo.parseAtomXml(atomXml, "lennymuriuki/decan-movir", "1.0.27")
    assertEquals(1, parsed.size)
    val selectedRelease =
      GitHubUpdateRepository.selectCompatibleApkAsset(
        assets = parsed.first().assets,
        supportedAbis = listOf("arm64-v8a"),
        isDebugBuild = false,
      )
    assertNotNull(selectedRelease)
    assertEquals("decan-movie-v3.0.0-release.apk", selectedRelease!!.name)
  }
}
