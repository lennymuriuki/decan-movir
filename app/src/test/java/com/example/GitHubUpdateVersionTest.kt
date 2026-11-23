package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.GitHubReleaseAsset
import com.example.data.remote.GitHubReleaseInfo
import com.example.data.remote.GitHubUpdateRepository
import com.example.data.settings.AppSettings
import com.example.data.settings.AppSettingsRepository
import com.example.ui.components.UpdateAvailableDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.UpdateUiState
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
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
class GitHubUpdateVersionTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun isNewerVersion_handlesSemverPrereleasesAndPrefix() {
    assertTrue(GitHubUpdateRepository.isNewerVersion("v1.1.0", "1.0"))
    assertTrue(GitHubUpdateRepository.isNewerVersion("2.0.0", "1.9.5"))
    assertTrue(GitHubUpdateRepository.isNewerVersion("v1.0.10", "1.0.2"))
    assertTrue(GitHubUpdateRepository.isNewerVersion("v1.0.2", "1.0.2-beta.1"))
    assertTrue(GitHubUpdateRepository.isNewerVersion("v1.0.2-beta.2", "1.0.2-beta.1"))

    assertFalse(GitHubUpdateRepository.isNewerVersion("v1.0.0", "1.0"))
    assertFalse(GitHubUpdateRepository.isNewerVersion("v0.9.5", "1.0.0"))
    assertFalse(GitHubUpdateRepository.isNewerVersion("v1.0.2-beta.1", "1.0.2"))
  }

  @Test
  fun appSettings_defaultsAndMigratesLegacyGitHubRepo() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = context.getSharedPreferences("decan_movie_settings", Context.MODE_PRIVATE)
    prefs.edit().clear().putString("github_repo", "lennymuriuki4/decan-movir").commit()

    val repo = AppSettingsRepository(context)
    assertEquals("lennymuriuki/decan-movir", AppSettings.DEFAULT_GITHUB_REPO)
    assertEquals("lennymuriuki/decan-movir", repo.settings.value.githubRepo)

    // Cooldown & caching behavior
    assertTrue(repo.shouldAutoCheckUpdates(nowMs = 1_000_000L))
    repo.markUpdateCheckedNow(nowMs = 1_000_000L)
    assertFalse(repo.shouldAutoCheckUpdates(nowMs = 1_005_000L))
    assertTrue(repo.shouldAutoCheckUpdates(nowMs = 1_000_000L + AppSettings.UPDATE_CHECK_COOLDOWN_MS + 1L))

    repo.setAutoCheckUpdates(false)
    assertFalse(repo.shouldAutoCheckUpdates(nowMs = 99_000_000_000L))
  }

  @Test
  fun parseReleaseAndSelectCompatibleApkAsset_respectsAbiAndSha256() {
    val fakeHash = "a".repeat(64)
    val releaseJson =
      """
      {
        "tag_name": "v2.4.0",
        "name": "Decan Movie v2.4.0",
        "body": "Added Vertical Drama Player.\nSHA-256 (decan-arm64-v8a-release.apk): $fakeHash",
        "published_at": "2026-11-20T12:00:00Z",
        "html_url": "https://github.com/lennymuriuki/decan-movir/releases/tag/v2.4.0",
        "prerelease": false,
        "assets": [
          {
            "name": "decan-x86_64-release.apk",
            "browser_download_url": "https://example.com/x86.apk",
            "size": 15728640,
            "content_type": "application/vnd.android.package-archive"
          },
          {
            "name": "decan-arm64-v8a-release.apk",
            "browser_download_url": "https://example.com/arm64.apk",
            "size": 16777216,
            "content_type": "application/vnd.android.package-archive"
          }
        ]
      }
      """.trimIndent()

    val parsed = GitHubUpdateRepository.parseReleaseJson(releaseJson, currentVersion = "1.0")
    assertNotNull(parsed)
    assertTrue(parsed!!.isNewerThanCurrent)
    val chosen =
      GitHubUpdateRepository.selectCompatibleApkAsset(
        assets = parsed.assets,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        isDebugBuild = false,
      )
    assertNotNull(chosen)
    assertEquals("decan-arm64-v8a-release.apk", chosen!!.name)
    assertEquals(fakeHash, chosen.sha256)
  }

  @Test
  fun downloadApkToFile_verifiesSha256AndRejectsCorruptFiles() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val validApkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(256) { 0x2A }
    val validSha256 =
      MessageDigest.getInstance("SHA-256")
        .digest(validApkBytes)
        .joinToString("") { "%02x".format(it) }

    val fakeClient =
      OkHttpClient.Builder()
        .addInterceptor { chain ->
          val url = chain.request().url.toString()
          val payload =
            when {
              url.endsWith("/valid.apk") -> validApkBytes
              url.endsWith("/corrupt.apk") -> "<html>not an apk</html>".toByteArray()
              else -> ByteArray(0)
            }
          val code = if (url.endsWith("/404.apk")) 404 else 200
          Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Not Found")
            .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
            .build()
        }
        .build()

    val repo = GitHubUpdateRepository(fakeClient)
    val outDir = File(context.cacheDir, "test_updates_${System.nanoTime()}")

    // Valid APK + matching SHA-256 succeeds
    val validDest = File(outDir, "valid.apk")
    val okRes = repo.downloadApkToFile("https://example.com/valid.apk", validDest, validSha256) {}
    assertTrue(okRes.isSuccess)
    assertTrue(validDest.exists())
    assertFalse(File(outDir, "valid.apk.part").exists())

    // SHA-256 mismatch fails and cleans up
    val mismatchDest = File(outDir, "mismatch.apk")
    val mismatchRes =
      repo.downloadApkToFile("https://example.com/valid.apk", mismatchDest, "0".repeat(64)) {}
    assertTrue(mismatchRes.isFailure)
    assertFalse(mismatchDest.exists())

    // Non-APK payload rejected by header validation
    val corruptDest = File(outDir, "corrupt.apk")
    val corruptRes = repo.downloadApkToFile("https://example.com/corrupt.apk", corruptDest, null) {}
    assertTrue(corruptRes.isFailure)
    assertFalse(corruptDest.exists())

    outDir.deleteRecursively()
  }

  @Test
  fun parseAtomXml_extractsReleaseAndVersionComparison() {
    val atomXml =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <feed xmlns="http://www.w3.org/2005/Atom">
        <entry>
          <title>Decan Movie v3.0.0</title>
          <updated>2026-11-22T08:00:00Z</updated>
          <link rel="alternate" type="text/html" href="https://github.com/lennymuriuki/decan-movir/releases/tag/v3.0.0" />
          <content type="html">&lt;p&gt;TikTok drama player and updater&lt;/p&gt;</content>
        </entry>
      </feed>
      """.trimIndent()
    val repo = GitHubUpdateRepository()
    val list = repo.parseAtomXml(atomXml, "lennymuriuki/decan-movir", "1.0")
    assertEquals(1, list.size)
    assertEquals("v3.0.0", list.first().tagName)
    assertTrue(list.first().isNewerThanCurrent)
  }

  @Test
  fun updateAvailableDialog_rendersWhenNewerAndTriggersActions() {
    var downloadClicked = false
    var laterClicked = false
    val release =
      GitHubReleaseInfo(
        tagName = "v2.0.0",
        title = "Decan Movie v2.0.0",
        changelog = "Vertical Drama Player and faster search.",
        publishedAt = "2026-11-22",
        htmlUrl = "https://github.com/lennymuriuki/decan-movir/releases/tag/v2.0.0",
        isPrerelease = false,
        assets =
          listOf(
            GitHubReleaseAsset(
              name = "decan-movie-v2.0.0.apk",
              downloadUrl = "https://example.com/decan.apk",
              sizeMb = 14.2,
              contentType = GitHubReleaseAsset.APK_MIME_TYPE,
            )
          ),
        isNewerThanCurrent = true,
      )

    composeTestRule.setContent {
      MaterialTheme {
        UpdateAvailableDialog(
          updateState = UpdateUiState(latestRelease = release, showUpdateDialog = true),
          onDownloadUpdate = { _, _ -> downloadClicked = true },
          onInstallUpdate = {},
          onLater = { laterClicked = true },
          onNotify = {},
        )
      }
    }

    composeTestRule.onNodeWithText("DECAN MOVIE UPDATE AVAILABLE").assertIsDisplayed()
    composeTestRule.onNodeWithText("v2.0.0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dialog_download_update_button").performClick()
    composeTestRule.onNodeWithTag("dialog_later_button").performClick()
    assertTrue(downloadClicked)
    assertTrue(laterClicked)
  }

  @Test
  fun settingsScreen_applicationUpdatesSectionDisplaysStatusAndToggle() {
    var autoCheckToggled = false
    var checkClicked = false
    val release =
      GitHubReleaseInfo(
        tagName = "v1.0",
        title = "Decan Movie v1.0",
        changelog = "Current release.",
        publishedAt = "2026-11-20",
        htmlUrl = "https://github.com/lennymuriuki/decan-movir/releases/tag/v1.0",
        isPrerelease = false,
        assets = emptyList(),
        isNewerThanCurrent = false,
      )

    composeTestRule.setContent {
      MaterialTheme {
        SettingsScreen(
          appSettings = AppSettings(autoCheckUpdates = true, lastUpdateCheckMs = 1_700_000_000_000L),
          updateState = UpdateUiState(latestRelease = release, checkedOnce = true),
          isFirebaseConfigured = false,
          isSignedIn = false,
          userDisplayName = "Guest",
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
          onSetEnableDownloadNotifications = {},
          onSetEnableReleaseNotifications = {},
          onSetEnableTrendingNotifications = {},
          onSetAutoCheckUpdates = { autoCheckToggled = true },
          onSendTestNotification = {},
          onCheckUpdates = { checkClicked = true },
          onNotify = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("settings_list")
      .performScrollToNode(hasTestTag("application_updates_card"))
    composeTestRule.onNodeWithText("Application Updates").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_installed_version").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_latest_version").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_last_update_check").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("auto_check_updates_switch").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("check_updates_button").performScrollTo().performClick()
    assertTrue(autoCheckToggled)
    assertTrue(checkClicked)
  }
}
