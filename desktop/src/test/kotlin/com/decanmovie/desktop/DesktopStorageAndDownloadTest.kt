package com.decanmovie.desktop

import com.decanmovie.desktop.data.download.DesktopDownloadManager
import com.decanmovie.desktop.data.store.DesktopAppStorage
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.WatchStatus
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStorageAndDownloadTest {
  @Test
  fun storagePersistsWatchlistSettingsAndEpisodeResumeAcrossReload() {
    val dir = Files.createTempDirectory("decan_store_test").toFile()
    try {
      val store1 = DesktopAppStorage(baseDir = dir)
      val drama = Movie(id = "mbox_d99", title = "My Drama", genre = "Drama", mediaType = "Drama")
      store1.saveEntry(drama, WatchStatus.WATCHING, favorite = true, personalRating = 5, personalNote = "Great")
      store1.savePlaybackProgress(drama, season = 1, episode = 4, positionMs = 123_456L, durationMs = 2_400_000L)
      store1.updateSettings { it.copy(preferredQuality = 720, lastUpdateCheckMs = System.currentTimeMillis()) }

      val store2 = DesktopAppStorage(baseDir = dir)
      assertEquals(720, store2.currentSettings().preferredQuality)
      assertFalse(store2.shouldAutoCheckUpdates())
      assertEquals(1, store2.watchlist.value.size)
      assertEquals(5, store2.watchlist.value.first().personalRating)
      assertEquals(123_456L, store2.getEpisodeProgress("mbox_d99", 1, 4))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun structuredDownloadFoldersOrganizeMoviesSeriesAnimeDramasAndSrtSiblings() {
    val root = Files.createTempDirectory("decan_dl_test").toFile()
    try {
      val movie = Movie(id = "m1", title = "Avatar: The Way", mediaType = "Movie")
      val series = Movie(id = "s1", title = "Breaking Bad", mediaType = "Series")
      val drama = Movie(id = "d1", title = "Queen of Tears", mediaType = "Drama")

      val movieDir = DesktopDownloadManager.buildStructuredFolder(root.absolutePath, movie)
      val epDir = DesktopDownloadManager.buildStructuredFolder(root.absolutePath, series, season = 2, episode = 5)
      val dramaDir = DesktopDownloadManager.buildStructuredFolder(root.absolutePath, drama, season = 1, episode = 3)

      assertEquals("Movies", movieDir.parentFile.name)
      assertEquals("Season 02", epDir.name)
      assertEquals("Breaking Bad", epDir.parentFile.name)
      assertEquals("Dramas", dramaDir.parentFile.parentFile.name)

      epDir.mkdirs()
      val vidFile = File(epDir, "Breaking_Bad_S02E05_1080p.mp4").apply { writeBytes(ByteArray(128)) }
      val srtFile = File(epDir, "Breaking_Bad_S02E05_en.srt").apply { writeText("1\n00:00:01,000 --> 00:00:02,000\nTest") }

      val scanned = DesktopDownloadManager.scanLocalLibrary(root.absolutePath)
      assertEquals(1, scanned.size)
      assertEquals("Series", scanned.first().category)
      assertEquals("Season 02", scanned.first().seasonFolder)
      assertNotNull(scanned.first().subtitlePath)
      assertEquals(srtFile.absolutePath, scanned.first().subtitlePath)
      assertTrue(File(vidFile.absolutePath).exists())
    } finally {
      root.deleteRecursively()
    }
  }
}
