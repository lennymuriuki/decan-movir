package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.components.PlayerControlsOverlay
import com.example.ui.components.PlayerOverlayState
import com.example.ui.components.formatPlaybackDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerControlsOverlayTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun formatPlaybackDuration_formatsMinutesAndHoursCorrectly() {
    assertEquals("00:00", formatPlaybackDuration(0L))
    assertEquals("00:59", formatPlaybackDuration(59_000L))
    assertEquals("05:12", formatPlaybackDuration(312_000L))
    assertEquals("1:02:03", formatPlaybackDuration(3_723_000L))
  }

  @Test
  fun playerControlsOverlay_triggersPlayPauseVolumeCcAndFullscreenCallbacks() {
    var playPauseClicked = false
    var ccClicked = false
    var fullscreenClicked = false

    val sampleState =
      PlayerOverlayState(
        title = "Cinema Feature",
        subtitleLine = "S1 E2 • 1080p",
        isPlaying = true,
        isBuffering = false,
        isLoadingMedia = false,
        isFullscreen = false,
        isLocked = false,
        controlsVisible = true,
        zoomFill = false,
        hasPlaybackUrl = true,
        canGoPrevious = true,
        canGoNext = true,
        canDownload = true,
        ccActive = true,
        currentPositionMs = 45_000L,
        durationMs = 120_000L,
        bufferedFraction = 0.6f,
        volume = 0.8f,
        isMuted = false,
        activeCaptionText = null,
        seekFeedbackText = null,
        upNextSeconds = null,
        gestureHudType = null,
        gestureHudFraction = 0f,
      )

    composeTestRule.setContent {
      MaterialTheme {
        PlayerControlsOverlay(
          state = sampleState,
          onBack = {},
          onPlayPause = { playPauseClicked = true },
          onPrevious = {},
          onNext = {},
          onSeekBy = {},
          onSeekTo = {},
          onToggleMute = {},
          onVolumeChange = {},
          onToggleFullscreen = { fullscreenClicked = true },
          onToggleLock = {},
          onToggleZoomFill = {},
          onOpenSubtitles = { ccClicked = true },
          onOpenSettings = {},
          onOpenDownload = {},
          onOpenExternal = {},
          onPlayNextNow = {},
          onDismissUpNext = {},
          onControlInteraction = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("player_seek_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("player_play_pause_button").performClick()
    composeTestRule.onNodeWithTag("player_cc_button").performClick()
    composeTestRule.onNodeWithTag("player_fullscreen_button").performClick()
    composeTestRule.onNodeWithTag("player_volume_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("player_volume_slider").assertIsDisplayed()

    assertTrue(playPauseClicked)
    assertTrue(ccClicked)
    assertTrue(fullscreenClicked)
  }
}
