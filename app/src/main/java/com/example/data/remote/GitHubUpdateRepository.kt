package com.example.data.remote

import androidx.compose.runtime.Immutable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class GitHubReleaseAsset(
  val name: String,
  val downloadUrl: String,
  val sizeMb: Double,
)

@Immutable
data class GitHubReleaseInfo(
  val tagName: String,
  val title: String,
  val changelog: String,
  val publishedAt: String,
  val htmlUrl: String,
  val isPrerelease: Boolean,
  val assets: List<GitHubReleaseAsset>,
  val isNewerThanCurrent: Boolean,
)

class GitHubUpdateRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build(),
) {
  suspend fun checkLatestRelease(
    repoSlug: String,
    currentVersion: String,
  ): Result<GitHubReleaseInfo?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        require(cleanSlug.contains("/")) { "Enter repository as owner/repo" }

        val latestReq =
          Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases/latest")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "DecanMovie-Android")
            .build()

        client.newCall(latestReq).execute().use { resp ->
          if (resp.code == 404) {
            return@runCatching fetchFallbackRelease(cleanSlug, currentVersion)
          }
          if (!resp.isSuccessful) {
            throw IllegalStateException("GitHub API returned HTTP ${resp.code}")
          }
          val obj = JSONObject(resp.body?.string().orEmpty())
          parseRelease(obj, currentVersion)
        }
      }
    }

  suspend fun fetchRecentReleases(
    repoSlug: String,
    currentVersion: String,
  ): Result<List<GitHubReleaseInfo>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        require(cleanSlug.contains("/")) { "Enter repository as owner/repo" }
        val req =
          Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases?per_page=6")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "DecanMovie-Android")
            .build()

        client.newCall(req).execute().use { resp ->
          if (resp.code == 404) return@runCatching emptyList()
          if (!resp.isSuccessful) {
            throw IllegalStateException("GitHub API returned HTTP ${resp.code}")
          }
          val arr = JSONArray(resp.body?.string().orEmpty())
          buildList {
            for (i in 0 until arr.length()) {
              arr.optJSONObject(i)?.let { add(parseRelease(it, currentVersion)) }
            }
          }
        }
      }
    }

  private fun fetchFallbackRelease(
    cleanSlug: String,
    currentVersion: String,
  ): GitHubReleaseInfo? {
    val req =
      Request.Builder()
        .url("https://api.github.com/repos/$cleanSlug/releases?per_page=1")
        .addHeader("Accept", "application/vnd.github+json")
        .addHeader("User-Agent", "DecanMovie-Android")
        .build()
    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val arr = JSONArray(resp.body?.string().orEmpty())
      val first = arr.optJSONObject(0) ?: return null
      return parseRelease(first, currentVersion)
    }
  }

  private fun parseRelease(obj: JSONObject, currentVersion: String): GitHubReleaseInfo {
    val tag = obj.optString("tag_name").ifBlank { "v1.0" }
    val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
    val assets = buildList {
      for (i in 0 until assetsArr.length()) {
        val a = assetsArr.optJSONObject(i) ?: continue
        val url = a.optString("browser_download_url")
        if (url.isNotBlank()) {
          val sizeMb = ((a.optLong("size", 0L) / (1024.0 * 1024.0)) * 10.0).toInt() / 10.0
          add(
            GitHubReleaseAsset(
              name = a.optString("name", "release-asset"),
              downloadUrl = url,
              sizeMb = sizeMb,
            )
          )
        }
      }
    }
    return GitHubReleaseInfo(
      tagName = tag,
      title = obj.optString("name").ifBlank { tag },
      changelog = obj.optString("body").ifBlank { "No changelog notes provided." },
      publishedAt = obj.optString("published_at").take(10),
      htmlUrl = obj.optString("html_url"),
      isPrerelease = obj.optBoolean("prerelease", false),
      assets = assets,
      isNewerThanCurrent = isNewerVersion(remoteTag = tag, currentVersion = currentVersion),
    )
  }

  companion object {
    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
      val remoteParts = parseSemVer(remoteTag)
      val currentParts = parseSemVer(currentVersion)
      val maxLen = maxOf(remoteParts.size, currentParts.size)
      for (i in 0 until maxLen) {
        val r = remoteParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
      }
      return false
    }

    private fun parseSemVer(raw: String): List<Int> =
      raw
        .trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore("-")
        .split(".")
        .mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
  }
}
