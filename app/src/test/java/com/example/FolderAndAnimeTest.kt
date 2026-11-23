package com.example

import android.app.DownloadManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.StreamDownloadManager
import com.example.data.remote.GitHubUpdateRepository
import com.example.data.remote.JikanTvMazeService
import com.example.data.settings.AppSettings
import com.example.data.settings.DownloadStorageLocation
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DecanMovieTheme
import com.example.ui.viewmodel.UpdateUiState
import org.json.JSONArray
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
class FolderAndAnimeTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun structuredFolderPaths_placeSeriesAndMoviesSeparately() {
    val moviePath =
      StreamDownloadManager.buildRelativeMediaPath(
        movieTitle = "Dune: Part Two",
        isSeries = false,
        season = 0,
        episode = 0,
        suffix = "1080P",
        extension = "mp4",
      )
    assertTrue(moviePath.startsWith("DecanMovie/Movies/Dune_Part_Two/"))
    assertTrue(moviePath.endsWith(".mp4"))

    val seriesEpPath =
      StreamDownloadManager.buildRelativeMediaPath(
        movieTitle = "Jujutsu Kaisen",
        isSeries = true,
        season = 2,
        episode = 5,
        suffix = "720P",
        extension = "mp4",
      )
    assertEquals("DecanMovie/Series/Jujutsu_Kaisen/Season_02/S02E05_Jujutsu_Kaisen_720P.mp4", seriesEpPath)

    val seriesSubPath =
      StreamDownloadManager.buildRelativeMediaPath(
        movieTitle = "Jujutsu Kaisen",
        isSeries = true,
        season = 2,
        episode = 5,
        suffix = "English",
        extension = "srt",
      )
    assertEquals("DecanMovie/Series/Jujutsu_Kaisen/Season_02/S02E05_Jujutsu_Kaisen_English.srt", seriesSubPath)
  }

  @Test
  fun groupIntoFolders_groupsSeriesApartFromMovies() {
    val tasks =
      listOf(
        DownloadTaskInfo(
          id = 1L,
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
        ),
        DownloadTaskInfo(
          id = 2L,
          title = "Attack on Titan • S1E1 • English (.srt)",
          description = "Decan Movie",
          status = DownloadManager.STATUS_SUCCESSFUL,
          statusLabel = "Completed",
          reasonHint = null,
          bytesDownloaded = 32_000L,
          totalBytes = 32_000L,
          progressFraction = 1f,
          localUri = "file:///tmp/S01E01_AoT_English.srt",
          mimeType = "application/x-subrip",
          folderName = "Attack_on_Titan",
          isSeriesFolder = true,
          seasonNumber = 1,
          episodeNumber = 1,
        ),
        DownloadTaskInfo(
          id = 3L,
          title = "Inception (1080P)",
          description = "Decan Movie",
          status = DownloadManager.STATUS_SUCCESSFUL,
          statusLabel = "Completed",
          reasonHint = null,
          bytesDownloaded = 700_000_000L,
          totalBytes = 700_000_000L,
          progressFraction = 1f,
          localUri = "file:///tmp/Inception_1080P.mp4",
          mimeType = "video/mp4",
          folderName = "Inception",
          isSeriesFolder = false,
        ),
      )

    val folders = StreamDownloadManager.groupIntoFolders(tasks)
    assertEquals(2, folders.size)
    val seriesFolder = folders.first { it.isSeriesFolder }
    assertEquals("Attack on Titan", seriesFolder.folderName)
    assertEquals(1, seriesFolder.videoCount)
    assertEquals(1, seriesFolder.subtitleCount)

    val moviesFolder = folders.first { !it.isSeriesFolder }
    assertEquals("Movies", moviesFolder.folderName)
    assertEquals(1, moviesFolder.videoCount)
  }

  @Test
  fun extractEpisodeTag_parsesSeasonAndEpisode() {
    assertEquals("S01E09", StreamDownloadManager.extractEpisodeTag("Solo Leveling • S1E9 (1080P)"))
    assertEquals("S03E12", StreamDownloadManager.extractEpisodeTag("DecanMovie/Series/A/Season_03/S03E12_Ep.srt"))
  }

  @Test
  fun jikanAndTvMazeParsers_extractSupernaturalAnimeAndShows() {
    val jikanJson =
      JSONObject(
        """
        {
          "data": [
            {
              "mal_id": 5114,
              "title": "Fullmetal Alchemist: Brotherhood",
              "title_english": "Fullmetal Alchemist: Brotherhood",
              "score": 9.1,
              "rank": 1,
              "episodes": 64,
              "year": 2009,
              "genres": [{"name": "Action"}, {"name": "Adventure"}, {"name": "Supernatural"}],
              "studios": [{"name": "Bones"}],
              "images": {"jpg": {"large_image_url": "https://cdn.myanimelist.net/images/anime/1208/94745l.jpg"}}
            }
          ]
        }
        """.trimIndent()
      )
    val service = JikanTvMazeService()
    val animeList = service.parseJikanAnimeList(jikanJson, startRank = 1)
    assertEquals(1, animeList.size)
    assertEquals("Fullmetal Alchemist: Brotherhood", animeList.first().title)
    assertEquals("Anime", animeList.first().mediaType)
    assertTrue(animeList.first().genre.contains("Supernatural"))
    assertEquals(1, animeList.first().rankPosition)

    val tvMazeJson =
      JSONArray(
        """
        [
          {
            "show": {
              "id": 169,
              "name": "Breaking Bad",
              "genres": ["Drama", "Crime", "Thriller"],
              "premiered": "2008-01-20",
              "averageRuntime": 60,
              "rating": {"average": 9.3},
              "network": {"name": "AMC"},
              "image": {"original": "http://static.tvmaze.com/uploads/images/original_untouched/0/2400.jpg"},
              "summary": "<p>Chemistry teacher turns to crime.</p>"
            }
          }
        ]
        """.trimIndent()
      )
    val tvList = service.parseTvMazeShows(tvMazeJson)
    assertEquals(1, tvList.size)
    assertEquals("Breaking Bad", tvList.first().title)
    assertEquals("Series", tvList.first().mediaType)
    assertTrue(tvList.first().posterUrl.startsWith("https://"))
    assertFalse(tvList.first().synopsis.contains("<p>"))
  }

  @Test
  fun gitHubAtomParser_parsesReleaseFeedWithoutApi() {
    val atomXml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom">
        <entry>
          <title>Decan Movie v2.5.0</title>
          <updated>2026-11-20T10:00:00Z</updated>
          <link rel="alternate" type="text/html" href="https://github.com/lennymuriuki4/decan-movir/releases/tag/v2.5.0"/>
          <content type="html">&lt;p&gt;Structured folder downloads and offline CC.&lt;/p&gt;</content>
        </entry>
      </feed>
      """.trimIndent()
    val repo = GitHubUpdateRepository()
    val parsed = repo.parseAtomXml(atomXml, "lennymuriuki4/decan-movir", "1.0.0")
    assertEquals(1, parsed.size)
    assertEquals("v2.5.0", parsed.first().tagName)
    assertTrue(parsed.first().isNewerThanCurrent)
    assertTrue(parsed.first().assets.first().downloadUrl.endsWith("decan-movie-v2.5.0-debug.apk"))
  }

  @Test
  fun settingsScreen_displaysCommunityLinksAndStorageSelector() {
    composeTestRule.setContent {
      DecanMovieTheme {
        SettingsScreen(
          appSettings = AppSettings(storageLocation = DownloadStorageLocation.GALLERY_MOVIES),
          updateState = UpdateUiState(),
          isFirebaseConfigured = true,
          isSignedIn = false,
          userDisplayName = "Guest Cinema",
          userEmail = null,
          userPhotoUrl = null,
          onSignOut = null,
          onAuthSuccess = {},
          onSetThemeMode = {},
          onSetPreferredQuality = {},
          onSetAutoPlayNext = {},
          onSetAutoLoadSubtitles = {},
          onSetDefaultPlaybackSpeed = {},
          onSetAllowMeteredDownloads = {},
          onSetIncludeSubtitlesInBatch = {},
          onSetStorageLocation = {},
          onSetEnableDownloadNotifications = {},
          onSetEnableReleaseNotifications = {},
          onSetEnableTrendingNotifications = {},
          onSendTestNotification = {},
          onCheckUpdates = {},
          onNotify = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_list").assertIsDisplayed()
    composeTestRule.onNodeWithText("Settings & Account").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("community_links_card"))
    composeTestRule.onNodeWithTag("link_decankonnect_website").assertIsDisplayed()
    composeTestRule.onNodeWithTag("link_whatsapp_group").assertIsDisplayed()
  }

  @Test
  fun downloadsScreen_displaysFolderAndStorageControls() {
    composeTestRule.setContent {
      DecanMovieTheme {
        DownloadsScreen(
          storageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
          onChangeStorageLocation = {},
          onPlayLocalFile = { _, _ -> },
          onExploreMovies = {},
          onNotify = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Downloads Hub").assertIsDisplayed()
    composeTestRule.onNodeWithTag("storage_loc_PUBLIC_DOWNLOADS").assertIsDisplayed()
    composeTestRule.onNodeWithTag("storage_loc_GALLERY_MOVIES").assertIsDisplayed()
    composeTestRule.onNodeWithTag("download_filter_FOLDERS").assertIsDisplayed()
  }
}
