package com.decanmovie.desktop

import com.decanmovie.desktop.data.remote.DesktopGitHubUpdateRepository
import com.decanmovie.desktop.data.remote.WindowsReleaseAsset
import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGitHubUpdaterTest {
  @Test
  fun semVerComparisonHandlesPrereleasesAndVPrefixes() {
    assertTrue(DesktopGitHubUpdateRepository.isNewerVersion("v1.2.0", "1.1.0"))
    assertTrue(DesktopGitHubUpdateRepository.isNewerVersion("1.2.0", "1.2.0-beta.1"))
    assertFalse(DesktopGitHubUpdateRepository.isNewerVersion("1.2.0-beta.1", "1.2.0"))
    assertFalse(DesktopGitHubUpdateRepository.isNewerVersion("v1.1.0", "1.1.0"))
    assertTrue(DesktopGitHubUpdateRepository.isNewerVersion("1.2.0-rc.2", "1.2.0-beta.9"))
  }

  @Test
  fun windowsAssetSelectionNeverPicksAndroidApk() {
    val assets =
      listOf(
        WindowsReleaseAsset(
          name = "decan-movie-v1.2.0.apk",
          downloadUrl = "https://github.com/lennymuriuki/decan-movir/releases/download/v1.2.0/decan-movie-v1.2.0.apk",
          sizeMb = 42.0,
          contentType = "application/vnd.android.package-archive",
        ),
        WindowsReleaseAsset(
          name = "Decan-Movie-Setup.exe",
          downloadUrl = "https://github.com/lennymuriuki/decan-movir/releases/download/v1.2.0/Decan-Movie-Setup.exe",
          sizeMb = 88.4,
        ),
        WindowsReleaseAsset(
          name = "Decan-Movie-Portable.zip",
          downloadUrl = "https://github.com/lennymuriuki/decan-movir/releases/download/v1.2.0/Decan-Movie-Portable.zip",
          sizeMb = 94.1,
        ),
      )

    val installerChoice = DesktopGitHubUpdateRepository.selectWindowsAsset(assets, preferPortable = false)
    assertNotNull(installerChoice)
    assertEquals("Decan-Movie-Setup.exe", installerChoice!!.name)

    val portableChoice = DesktopGitHubUpdateRepository.selectWindowsAsset(assets, preferPortable = true)
    assertNotNull(portableChoice)
    assertEquals("Decan-Movie-Portable.zip", portableChoice!!.name)

    val apkOnly = listOf(assets.first())
    assertNull(DesktopGitHubUpdateRepository.selectWindowsAsset(apkOnly, preferPortable = false))
  }

  @Test
  fun parseReleaseJsonExtractsSha256FromReleaseBody() {
    val hash = "a".repeat(64)
    val json =
      JSONObject(
        """
        {
          "tag_name": "v1.3.0",
          "name": "Decan Movie Desktop 1.3.0",
          "body": "### Checksums\n`$hash  Decan-Movie-Setup.exe`",
          "published_at": "2026-10-01T12:00:00Z",
          "html_url": "https://github.com/lennymuriuki/decan-movir/releases/tag/v1.3.0",
          "prerelease": false,
          "assets": [
            {
              "name": "app-release.apk",
              "browser_download_url": "https://example.com/app-release.apk",
              "size": 40000000
            },
            {
              "name": "Decan-Movie-Setup.exe",
              "browser_download_url": "https://example.com/Decan-Movie-Setup.exe",
              "size": 90000000
            }
          ]
        }
        """.trimIndent()
      )

    val parsed = DesktopGitHubUpdateRepository().parseRelease(json, currentVersion = "1.1.0")
    assertTrue(parsed.isNewerThanCurrent)
    val winAsset = DesktopGitHubUpdateRepository.selectWindowsAsset(parsed.assets)
    assertNotNull(winAsset)
    assertEquals(hash, winAsset!!.sha256)
  }

  @Test
  fun windowsPackageMagicHeaderAndSha256VerificationWork() {
    val tmpDir = Files.createTempDirectory("decan_pkg_test").toFile()
    try {
      val fakeExe = File(tmpDir, "Decan-Movie-Setup.exe")
      fakeExe.writeBytes(byteArrayOf(0x4D, 0x5A, 0x90.toByte(), 0x00, 0x03))
      assertTrue(DesktopGitHubUpdateRepository.hasValidWindowsPackageHeader(fakeExe))

      val badExe = File(tmpDir, "bad.exe")
      badExe.writeText("<html>404 Not Found</html>")
      assertFalse(DesktopGitHubUpdateRepository.hasValidWindowsPackageHeader(badExe))

      val fakeZip = File(tmpDir, "Decan-Movie-Portable.zip")
      fakeZip.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14))
      assertTrue(DesktopGitHubUpdateRepository.hasValidWindowsPackageHeader(fakeZip))

      val digest = DesktopGitHubUpdateRepository.computeSha256Hex(fakeExe)
      assertNotNull(digest)
      assertEquals(64, digest!!.length)
    } finally {
      tmpDir.deleteRecursively()
    }
  }
}
