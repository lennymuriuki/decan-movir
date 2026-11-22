package com.example.data.remote.moviebox

import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object MovieBoxCrypto {
  private const val SECRET_KEY_DEFAULT_B64 =
    "NzZpUmwwN3MweFNOOWpxbUVXQXQ3OUVCSlp1bElRSXNWNjRGWnIyTw=="
  private const val SECRET_KEY_ALT_B64 =
    "WHFuMm5uTzQxL0w5Mm8xaXVYaFNMSFRiWHZZNFo1Wlo2Mm04bVNMQQ=="

  const val MODERN_USER_AGENT =
    "com.community.mbox.in/50020130 (Linux; U; Android 14; en_IN; Pixel 8; Build/UD1A.230803.041; Cronet/145.0.7582.0)"
  const val WEB_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Mobile Safari/537.36"

  private val secureRandom = SecureRandom()

  @OptIn(ExperimentalEncodingApi::class)
  private fun b64Decode(encoded: String): ByteArray {
    val cleaned = encoded.trim().replace("\n", "").replace("\r", "")
    val normalized = cleaned.replace("-", "+").replace("_", "/")
    val padLen = (4 - normalized.length % 4) % 4
    val padded = normalized + "=".repeat(padLen)
    return Base64.decode(padded)
  }

  fun getSecretBytes(useAltKey: Boolean = false): ByteArray {
    val outer = if (useAltKey) SECRET_KEY_ALT_B64 else SECRET_KEY_DEFAULT_B64
    val inner = String(b64Decode(outer), Charsets.UTF_8)
    return b64Decode(inner)
  }

  fun md5Hex(data: ByteArray): String {
    val digest = MessageDigest.getInstance("MD5").digest(data)
    return digest.joinToString("") { "%02x".format(it) }
  }

  fun generateDeviceId(): String {
    val bytes = ByteArray(16)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun generateXClientToken(timestamp: Long): String {
    val tsStr = timestamp.toString()
    val reversedTs = tsStr.reversed().toByteArray(Charsets.UTF_8)
    return "$tsStr,${md5Hex(reversedTs)}"
  }

  fun generateClientInfo(deviceId: String): String =
    JSONObject()
      .apply {
        put("package_name", "com.community.mbox.in")
        put("version_name", "4.0.03.0920.03")
        put("version_code", 50020130)
        put("os", "android")
        put("os_version", "14")
        put("device_id", deviceId)
        put("install_store", "official")
        put("gaid", "1b2212c1-dadf-43c3-a0c8-bd6ce48ae22d")
        put("brand", "Google")
        put("model", "Pixel 8")
        put("system_language", "en")
        put("net", "NETWORK_WIFI")
        put("region", "IN")
        put("timezone", "Asia/Calcutta")
        put("sp_code", "")
      }
      .toString()

  fun buildCanonicalString(
    method: String,
    accept: String?,
    contentType: String?,
    url: String,
    body: String?,
    timestamp: Long,
  ): String {
    val uri = URI(url)
    val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
    val rawQuery = uri.rawQuery
    val canonicalUrl =
      if (!rawQuery.isNullOrEmpty()) {
        val pairs =
          rawQuery
            .split("&")
            .filter { it.isNotEmpty() }
            .map { segment ->
              val idx = segment.indexOf('=')
              if (idx >= 0) segment.substring(0, idx) to segment.substring(idx + 1)
              else segment to ""
            }
            .sortedBy { it.first }
        val canonicalQuery = pairs.joinToString("&") { "${it.first}=${it.second}" }
        "$path?$canonicalQuery"
      } else {
        path
      }

    val bodyBytes = body?.toByteArray(Charsets.UTF_8)
    val bodyLength: String
    val bodyHash: String
    if (bodyBytes != null && bodyBytes.isNotEmpty()) {
      bodyLength = bodyBytes.size.toString()
      val slice = if (bodyBytes.size > 102400) bodyBytes.copyOf(102400) else bodyBytes
      bodyHash = md5Hex(slice)
    } else {
      bodyLength = ""
      bodyHash = ""
    }

    return listOf(
        method.uppercase(),
        accept.orEmpty(),
        contentType.orEmpty(),
        bodyLength,
        timestamp.toString(),
        bodyHash,
        canonicalUrl,
      )
      .joinToString("\n")
  }

  @OptIn(ExperimentalEncodingApi::class)
  fun generateXTrSignature(
    method: String,
    accept: String?,
    contentType: String?,
    url: String,
    body: String?,
    timestamp: Long,
    useAltKey: Boolean = false,
  ): String {
    val canonical = buildCanonicalString(method, accept, contentType, url, body, timestamp)
    val key = getSecretBytes(useAltKey)
    val mac = Mac.getInstance("HmacMD5")
    mac.init(SecretKeySpec(key, "HmacMD5"))
    val digest = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
    val sigB64 = Base64.encode(digest)
    return "$timestamp|2|$sigB64"
  }

  fun buildAuthHeaders(
    method: String,
    url: String,
    body: String? = null,
    token: String? = null,
    deviceId: String? = null,
    useAltKey: Boolean = false,
    contentType: String = "application/json",
    accept: String = "application/json",
  ): Map<String, String> {
    val ts = System.currentTimeMillis()
    val devId = deviceId ?: generateDeviceId()
    val map =
      linkedMapOf(
        "user-agent" to MODERN_USER_AGENT,
        "accept" to accept,
        "content-type" to contentType,
        "connection" to "keep-alive",
        "x-client-token" to generateXClientToken(ts),
        "x-tr-signature" to
          generateXTrSignature(method, accept, contentType, url, body, ts, useAltKey),
        "x-client-info" to generateClientInfo(devId),
        "x-client-status" to "0",
      )
    if (!token.isNullOrBlank()) {
      map["authorization"] = "Bearer $token"
    }
    return map
  }

  fun extractPolicyResource(signCookie: String?): String? {
    if (signCookie.isNullOrBlank()) return null

    val edgeMatch = Regex("Edge-Cache-Cookie=urlprefix=([^:;\\s]+)").find(signCookie)
    if (edgeMatch != null) {
      runCatching {
          val decoded =
            String(b64Decode(edgeMatch.groupValues[1]), Charsets.UTF_8).trimEnd('/')
          return "$decoded/index.mpd"
        }
    }

    val cfMatch = Regex("CloudFront-Policy=([^;]+)").find(signCookie)
    if (cfMatch != null) {
      val raw = cfMatch.groupValues[1]
      for (variant in listOf("cf", "std")) {
        runCatching {
            val normalized =
              if (variant == "cf") raw.replace("-", "+").replace("_", "=").replace("~", "/")
              else raw
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            @OptIn(ExperimentalEncodingApi::class)
            val json = JSONObject(String(Base64.decode(padded), Charsets.UTF_8))
            val statements = json.optJSONArray("Statement") ?: return@runCatching
            for (i in 0 until statements.length()) {
              val res = statements.optJSONObject(i)?.optString("Resource").orEmpty()
              if (res.isNotBlank()) {
                return res.trimEnd('*').trimEnd('/') + "/index.mpd"
              }
            }
          }
      }
    }
    return null
  }
}
