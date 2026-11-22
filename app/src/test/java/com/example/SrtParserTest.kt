package com.example

import com.example.data.remote.moviebox.SrtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SrtParserTest {

  @Test
  fun parse_validSrtWithMultipleCues_andFindsActiveCue() {
    val sample =
      """
      1
      00:00:01,500 --> 00:00:04,000
      Welcome to <i>Decan Cinema</i>.

      2
      00:00:05,250 --> 00:00:08,750
      Theater mode is now active.
      Enjoy the film!
      """.trimIndent()

    val cues = SrtParser.parse(sample)
    assertEquals(2, cues.size)
    assertEquals(1500L, cues[0].startMs)
    assertEquals(4000L, cues[0].endMs)
    assertEquals("Welcome to Decan Cinema.", cues[0].text)

    assertNull(SrtParser.findActiveCue(cues, 1000L))
    assertEquals("Welcome to Decan Cinema.", SrtParser.findActiveCue(cues, 2500L)?.text)
    assertNull(SrtParser.findActiveCue(cues, 4500L))
    assertEquals(
      "Theater mode is now active.\nEnjoy the film!",
      SrtParser.findActiveCue(cues, 6000L)?.text,
    )
  }
}
