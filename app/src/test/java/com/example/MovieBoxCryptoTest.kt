package com.example

import com.example.data.remote.moviebox.MovieBoxCrypto
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieBoxCryptoTest {

  @Test
  fun clientToken_containsTimestampAndReversedMd5() {
    val token = MovieBoxCrypto.generateXClientToken(1700000000123L)
    val parts = token.split(",")
    assertEquals(2, parts.size)
    assertEquals("1700000000123", parts[0])
    assertEquals(32, parts[1].length)
  }

  @Test
  fun canonicalString_sortsQueryParameters() {
    val canonical =
      MovieBoxCrypto.buildCanonicalString(
        method = "get",
        accept = "application/json",
        contentType = "application/json",
        url =
          "https://api3.aoneroom.com/wefeed-mobile-bff/subject-api/play-info?se=1&ep=2&subjectId=123",
        body = null,
        timestamp = 1700000000000L,
      )
    assertTrue(
      canonical.endsWith("/wefeed-mobile-bff/subject-api/play-info?ep=2&se=1&subjectId=123")
    )
  }

  @Test
  fun xTrSignature_formatsTimestampAndBase64Hmac() {
    val sig =
      MovieBoxCrypto.generateXTrSignature(
        method = "GET",
        accept = "application/json",
        contentType = "application/json",
        url = "https://api3.aoneroom.com/wefeed-mobile-bff/subject-api/get?subjectId=123",
        body = null,
        timestamp = 1700000000000L,
      )
    val parts = sig.split("|")
    assertEquals(3, parts.size)
    assertEquals("1700000000000", parts[0])
    assertEquals("2", parts[1])
    assertTrue(parts[2].isNotBlank())
  }

  @OptIn(ExperimentalEncodingApi::class)
  @Test
  fun extractPolicyResource_parsesEdgeCacheCookie() {
    val prefix = Base64.encode("https://cdn.example.com/hls/123".toByteArray())
    val url =
      MovieBoxCrypto.extractPolicyResource("Edge-Cache-Cookie=urlprefix=$prefix:Expires=12345")
    assertEquals("https://cdn.example.com/hls/123/index.mpd", url)
  }
}
