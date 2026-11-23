package com.example.data.remote

import androidx.compose.runtime.Immutable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

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
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .followRedirects(true)
      .build(),
) {
  suspend fun checkLatestRelease(
    repoSlug: String,
    currentVersion: String,
  ): Result<GitHubReleaseInfo?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        require(cleanSlug.contains("/")) { "Updates unavailable right now" }

        val latestReq =
          Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases/latest")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "DecanMovie-Android")
            .build()

        val apiResult =
          runCatching {
            client.newCall(latestReq).execute().use { resp ->
              when {
                resp.isSuccessful -> parseRelease(JSONObject(resp.body?.string().orEmpty()), currentVersion)
                resp.code == 404 -> fetchFallbackRelease(cleanSlug, currentVersion)
                else -> null
              }
            }
          }.getOrNull()

        if (apiResult != null) return@runCatching apiResult

        // Fallback to Atom feed (not subject to GitHub REST API 60 req/hr unauthenticated limit)
        fetchReleasesAtom(cleanSlug, currentVersion).firstOrNull()
      }
    }

  suspend fun fetchRecentReleases(
    repoSlug: String,
    currentVersion: String,
  ): Result<List<GitHubReleaseInfo>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        require(cleanSlug.contains("/")) { "Updates unavailable right now" }
        val req =
          Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases?per_page=8")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "DecanMovie-Android")
            .build()

        val apiList =
          runCatching {
            client.newCall(req).execute().use { resp ->
              if (!resp.isSuccessful) return@use null
              val arr = JSONArray(resp.body?.string().orEmpty())
              buildList<GitHubReleaseInfo> {
                for (i in 0 until arr.length()) {
                  arr.optJSONObject(i)?.let { add(parseRelease(it, currentVersion)) }
                }
              }
            }
          }.getOrNull()

        if (apiList != null && apiList.isNotEmpty()) return@runCatching apiList

        fetchReleasesAtom(cleanSlug, currentVersion)
      }
    }

  suspend fun downloadApkToFile(
    apkUrl: String,
    destFile: File,
    onProgress: (Float) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        destFile.parentFile?.mkdirs()
        if (destFile.exists()) destFile.delete()
        val req =
          Request.Builder()
            .url(apkUrl)
            .addHeader("User-Agent", "DecanMovie-Android")
            .build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            throw IllegalStateException("Update package download failed (HTTP ${resp.code})")
          }
          val body = resp.body ?: throw IllegalStateException("Empty update package")
          val totalBytes = body.contentLength()
          var copied = 0L
          val buffer = ByteArray(16 * 1024)
          body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
              while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                copied += read
                if (totalBytes > 0L) {
                  onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                }
              }
              output.flush()
            }
          }
          onProgress(1f)
          destFile
        }
      }
    }

  private fun fetchFallbackRelease(
    cleanSlug: String,
    currentVersion: String,
  ): GitHubReleaseInfo? {
    val req =
      Request.Builder()
        .url("https://api.github.com/repos/$cleanSlug/releases?per_page=3")
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

  private fun fetchReleasesAtom(
    cleanSlug: String,
    currentVersion: String,
  ): List<GitHubReleaseInfo> {
    val req =
      Request.Builder()
        .url("https://github.com/$cleanSlug/releases.atom")
        .addHeader("User-Agent", "DecanMovie-Android")
        .build()
    return runCatching {
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use emptyList()
        val xml = resp.body?.string().orEmpty()
        parseAtomXml(xml, cleanSlug, currentVersion)
      }
    }.getOrDefault(emptyList())
  }

  internal fun parseAtomXml(
    xml: String,
    cleanSlug: String,
    currentVersion: String,
  ): List<GitHubReleaseInfo> {
    if (xml.isBlank()) return emptyList()
    return runCatching {
      val factory = XmlPullParserFactory.newInstance()
      val parser = factory.newPullParser()
      parser.setInput(xml.reader())

      val list = mutableListOf<GitHubReleaseInfo>()
      var insideEntry = false
      var currentTag = ""
      var title = ""
      var linkHref = ""
      var updated = ""
      var content = ""

      var event = parser.eventType
      while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
          XmlPullParser.START_TAG -> {
            currentTag = parser.name.orEmpty()
            if (currentTag == "entry") {
              insideEntry = true
              title = ""
              linkHref = ""
              updated = ""
              content = ""
            } else if (insideEntry && currentTag == "link") {
              val href = parser.getAttributeValue(null, "href").orEmpty()
              if (href.isNotBlank()) linkHref = href
            }
          }
          XmlPullParser.TEXT -> {
            if (insideEntry) {
              when (currentTag) {
                "title" -> title += parser.text
                "updated" -> updated += parser.text
                "content" -> content += parser.text
              }
            }
          }
          XmlPullParser.END_TAG -> {
            val endName = parser.name.orEmpty()
            if (endName == "entry" && insideEntry) {
              insideEntry = false
              val tagFromUrl = linkHref.substringAfterLast("/").trim()
              val tagName =
                tagFromUrl.takeIf { it.isNotBlank() }
                  ?: title.trim().split(" ").firstOrNull().orEmpty().ifBlank { "v1.0" }
              val cleanText =
                content.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
              val guessedApkUrl =
                "https://github.com/$cleanSlug/releases/download/$tagName/decan-movie-$tagName-debug.apk"
              list.add(
                GitHubReleaseInfo(
                  tagName = tagName,
                  title = title.trim().ifBlank { tagName },
                  changelog = cleanText.ifBlank { "New Decan Movie update available." },
                  publishedAt = updated.take(10),
                  htmlUrl =
                    linkHref.ifBlank { "https://github.com/$cleanSlug/releases/tag/$tagName" },
                  isPrerelease = tagName.contains("beta", ignoreCase = true) ||
                    tagName.contains("rc", ignoreCase = true),
                  assets =
                    listOf(
                      GitHubReleaseAsset(
                        name = "decan-movie-$tagName.apk",
                        downloadUrl = guessedApkUrl,
                        sizeMb = 0.0,
                      )
                    ),
                  isNewerThanCurrent =
                    isNewerVersion(remoteTag = tagName, currentVersion = currentVersion),
                )
              )
            }
            currentTag = ""
          }
        }
        event = parser.next()
      }
      list
    }.getOrDefault(emptyList())
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
