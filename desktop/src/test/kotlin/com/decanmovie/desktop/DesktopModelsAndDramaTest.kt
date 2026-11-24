package com.decanmovie.desktop

import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSeason
import com.decanmovie.desktop.data.remote.moviebox.SrtParser
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopModelsAndDramaTest {
  @Test
  fun dramaDetectorAcceptsEpisodicDramasAndRejectsMoviesAnimeAndActionSeries() {
    val kDrama = Movie(id = "mbox_1", title = "Queen of Tears", genre = "Drama, Romance", mediaType = "Drama")
    val romanceSeries = Movie(id = "mbox_2", title = "Hidden Love", genre = "Romance, Melodrama", mediaType = "Series")
    val movie = Movie(id = "mbox_3", title = "Avatar", genre = "Drama, Sci-Fi", mediaType = "Movie")
    val animeDrama = Movie(id = "mal_16498", title = "Attack on Titan", genre = "Action, Drama", mediaType = "Anime", sourceProvider = "Decan Anime")
    val actionSeries = Movie(id = "mbox_4", title = "Breaking Bad", genre = "Crime, Thriller", mediaType = "Series")

    assertTrue(DramaExperienceDetector.isEpisodicDrama(kDrama))
    assertTrue(DramaExperienceDetector.isEpisodicDrama(romanceSeries, listOf(MovieBoxSeason(1, 24))))
    assertFalse(DramaExperienceDetector.isEpisodicDrama(movie))
    assertFalse(DramaExperienceDetector.isEpisodicDrama(animeDrama, listOf(MovieBoxSeason(1, 25))))
    assertFalse(DramaExperienceDetector.isEpisodicDrama(actionSeries, listOf(MovieBoxSeason(1, 13))))
  }

  @Test
  fun orderedDramaEpisodesAcrossMultipleSeasonsPreserveOrder() {
    val drama = Movie(id = "mbox_d1", title = "Glory", genre = "Drama", mediaType = "Drama")
    val seasons = listOf(
      MovieBoxSeason(season = 2, episodeCount = 3),
      MovieBoxSeason(season = 1, episodeCount = 2),
    )

    val ordered = DramaExperienceDetector.buildOrderedDramaEpisodes(drama, seasons)
    assertEquals(5, ordered.size)
    assertEquals("Season 01 • Episode 01", ordered[0].subtitleLabel)
    assertEquals("Season 01 • Episode 02", ordered[1].subtitleLabel)
    assertEquals("Season 02 • Episode 01", ordered[2].subtitleLabel)
    assertEquals(2, DramaExperienceDetector.findEpisodePageIndex(ordered, season = 2, episode = 1))
    assertEquals(4, DramaExperienceDetector.findEpisodePageIndex(ordered, season = 2, episode = 3))
  }

  @Test
  fun srtParserParsesHtmlStrippedCuesAndFindsActiveTimestamp() {
    val sampleSrt =
      """
      1
      00:00:01,000 --> 00:00:04,500
      <i>Hello</i> from <b>Decan Movie</b>

      2
      00:00:06,000 --> 00:00:09,000
      Second line subtitle
      """.trimIndent()

    val cues = SrtParser.parse(sampleSrt)
    assertEquals(2, cues.size)
    assertEquals("Hello from Decan Movie", cues[0].text)

    val hit = SrtParser.findActiveCue(cues, 2_500L)
    assertNotNull(hit)
    assertEquals("Hello from Decan Movie", hit!!.text)
    assertNull(SrtParser.findActiveCue(cues, 5_000L))
  }
}
