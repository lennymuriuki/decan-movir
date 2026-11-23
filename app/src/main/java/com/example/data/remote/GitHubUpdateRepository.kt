package com.example.data.remote

import android.os.Build
import androidx.compose.runtime.Immutable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
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
  val sizeBytes: Long = 0L,
  val contentType: String = "",
  val sha256: String? = null,
) {
  val isApkPackage: Boolean
    get() =
      name.endsWith(".apk", ignoreCase = true) ||
        contentType.equals(APK_MIME_TYPE, ignoreCase = true)

  companion object {
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
  }
}

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
) {
  fun toJsonString(): String =
    JSONObject()
      .apply {
        put("tag_name", tagName)
        put("name", title)
        put("body", changelog)
        put("published_at", publishedAt)
        put("html_url", htmlUrl)
        put("prerelease", isPrerelease)
        put(
          "assets",
          JSONArray().apply {
            assets.forEach { a ->
              put(
                JSONObject().apply {
                  put("name", a.name)
                  put("browser_download_url", a.downloadUrl)
                  put("size", if (a.sizeBytes > 0L) a.sizeBytes else (a.sizeMb * 1024.0 * 1024.0).toLong())
                  put("content_type", a.contentType)
                  if (!a.sha256.isNullOrBlank()) put("sha256", a.sha256)
                }
              )
            }
          },
        )
      }
      .toString()
}

class GitHubUpdateRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
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

        if (apiResult != null) {
          return@runCatching enrichReleaseChecksums(apiResult)
        }

        // Fallback to Atom feed (not subject to GitHub REST API 60 req/hr unauthenticated limit)
        fetchReleasesAtom(cleanSlug, currentVersion).firstOrNull()?.let { enrichReleaseChecksums(it) }
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
    expectedSha256: String? = null,
    onProgress: (Float) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val partFile = File(destFile.parentFile, "${destFile.name}.part")
      runCatching {
        destFile.parentFile?.mkdirs()
        if (partFile.exists()) partFile.delete()

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
          val digest = MessageDigest.getInstance("SHA-256")
          val buffer = ByteArray(16 * 1024)

          body.byteStream().use { input ->
            FileOutputStream(partFile).use { output ->
              while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                copied += read
                if (totalBytes > 0L) {
                  onProgress((copied.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                }
              }
              output.flush()
            }
          }

          if (copied <= 0L || !partFile.exists() || partFile.length() <= 0L) {
            throw IllegalStateException("Downloaded APK file is empty")
          }
          if (!hasValidApkHeader(partFile)) {
            throw IllegalStateException("Downloaded file is not a valid APK archive")
          }

          val expectedClean = expectedSha256?.trim()?.lowercase()?.takeIf { it.length == 64 }
          if (expectedClean != null) {
            val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHex.equals(expectedClean, ignoreCase = true)) {
              throw IllegalStateException("APK verification failed (SHA-256 mismatch)")
            }
          }

          if (destFile.exists()) destFile.delete()
          if (!partFile.renameTo(destFile)) {
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
          }
          onProgress(1f)
          destFile
        }
      }.onFailure {
        runCatching { if (partFile.exists()) partFile.delete() }
      }
    }

  private fun enrichReleaseChecksums(info: GitHubReleaseInfo): GitHubReleaseInfo {
    if (info.assets.isEmpty() || info.assets.all { !it.sha256.isNullOrBlank() }) return info
    val checksumAsset =
      info.assets.firstOrNull { asset ->
        val n = asset.name.lowercase()
        (n.endsWith(".sha256") || n.endsWith(".sha256sum") || n.contains("checksum") || n.contains("sha256")) &&
          asset.sizeBytes in 1L..(64 * 1024L)
      } ?: return info

    val checksumText =
      runCatching {
        val req =
          Request.Builder()
            .url(checksumAsset.downloadUrl)
            .addHeader("User-Agent", "DecanMovie-Android")
            .build()
        client.newCall(req).execute().use { resp ->
          if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
        }
      }.getOrDefault("")

    if (checksumText.isBlank()) return info
    val parsedMap = parseChecksumsFromText(checksumText, info.assets.map { it.name })
    if (parsedMap.isEmpty()) return info

    val updatedAssets =
      info.assets.map { asset ->
        if (!asset.sha256.isNullOrBlank()) asset
        else asset.copy(sha256 = parsedMap[asset.name] ?: parsedMap["*"])
      }
    return info.copy(assets = updatedAssets)
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
              val debugApkName = "decan-movie-$tagName-debug.apk"
              val releaseApkName = "decan-movie-$tagName.apk"
              val checksumMap =
                parseChecksumsFromText(cleanText, listOf(debugApkName, releaseApkName))
              val debugApkUrl =
                "https://github.com/$cleanSlug/releases/download/$tagName/$debugApkName"
              val releaseApkUrl =
                "https://github.com/$cleanSlug/releases/download/$tagName/$releaseApkName"
              list.add(
                GitHubReleaseInfo(
                  tagName = tagName,
                  title = title.trim().ifBlank { tagName },
                  changelog = cleanText.ifBlank { "New Decan Movie update available." },
                  publishedAt = updated.take(10),
                  htmlUrl =
                    linkHref.ifBlank { "https://github.com/$cleanSlug/releases/tag/$tagName" },
                  isPrerelease = tagName.contains("beta", ignoreCase = true) ||
                    tagName.contains("rc", ignoreCase = true) ||
                    tagName.contains("alpha", ignoreCase = true),
                  assets =
                    listOf(
                      GitHubReleaseAsset(
                        name = debugApkName,
                        downloadUrl = debugApkUrl,
                        sizeMb = 0.0,
                        contentType = GitHubReleaseAsset.APK_MIME_TYPE,
                        sha256 = checksumMap[debugApkName] ?: checksumMap["*"],
                      ),
                      GitHubReleaseAsset(
                        name = releaseApkName,
                        downloadUrl = releaseApkUrl,
                        sizeMb = 0.0,
                        contentType = GitHubReleaseAsset.APK_MIME_TYPE,
                        sha256 = checksumMap[releaseApkName] ?: checksumMap["*"],
                      ),
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

  internal fun parseRelease(obj: JSONObject, currentVersion: String): GitHubReleaseInfo {
    val tag = obj.optString("tag_name").ifBlank { "v1.0" }
    val bodyText = obj.optString("body").ifBlank { "No changelog notes provided." }
    val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
    val rawAssets = buildList {
      for (i in 0 until assetsArr.length()) {
        val a = assetsArr.optJSONObject(i) ?: continue
        val url = a.optString("browser_download_url")
        if (url.isNotBlank()) {
          val sizeBytes = a.optLong("size", 0L)
          val sizeMb = ((sizeBytes / (1024.0 * 1024.0)) * 10.0).toInt() / 10.0
          add(
            GitHubReleaseAsset(
              name = a.optString("name", "release-asset"),
              downloadUrl = url,
              sizeMb = sizeMb,
              sizeBytes = sizeBytes,
              contentType = a.optString("content_type", ""),
              sha256 = a.optString("sha256").takeIf { it.isNotBlank() && it != "null" },
            )
          )
        }
      }
    }
    val checksumMap = parseChecksumsFromText(bodyText, rawAssets.map { it.name })
    val assets =
      rawAssets.map { asset ->
        if (!asset.sha256.isNullOrBlank()) asset
        else if (asset.isApkPackage) {
          asset.copy(sha256 = checksumMap[asset.name] ?: checksumMap["*"])
        } else asset
      }
    return GitHubReleaseInfo(
      tagName = tag,
      title = obj.optString("name").ifBlank { tag },
      changelog = bodyText,
      publishedAt = obj.optString("published_at").take(10),
      htmlUrl = obj.optString("html_url"),
      isPrerelease = obj.optBoolean("prerelease", false),
      assets = assets,
      isNewerThanCurrent = isNewerVersion(remoteTag = tag, currentVersion = currentVersion),
    )
  }

  companion object {
    private val HEX_64_REGEX = Regex("(?i)\\b([0-9a-f]{64})\\b")

    fun parseReleaseJson(json: String, currentVersion: String): GitHubReleaseInfo? =
      runCatching {
        val obj = JSONObject(json)
        val repo = GitHubUpdateRepository()
        repo.parseRelease(obj, currentVersion)
      }.getOrNull()

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
      val (remoteCore, remotePre) = splitSemVer(remoteTag)
      val (currentCore, currentPre) = splitSemVer(currentVersion)
      val maxLen = maxOf(remoteCore.size, currentCore.size)
      for (i in 0 until maxLen) {
        val r = remoteCore.getOrElse(i) { 0 }
        val c = currentCore.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
      }
      // Same core numbers: stable release > prerelease
      if (remotePre == null && currentPre != null) return true
      if (remotePre != null && currentPre == null) return false
      if (remotePre == null || currentPre == null) return false
      return comparePrereleaseIdentifiers(remotePre, currentPre) > 0
    }

    private fun splitSemVer(raw: String): Pair<List<Int>, String?> {
      val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore("+")
      val corePart = cleaned.substringBefore("-")
      val prePart =
        if (cleaned.contains("-")) cleaned.substringAfter("-").takeIf { it.isNotBlank() } else null
      val nums = corePart.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
      return nums to prePart
    }

    private fun comparePrereleaseIdentifiers(remotePre: String, currentPre: String): Int {
      val rParts = remotePre.split(".")
      val cParts = currentPre.split(".")
      val max = maxOf(rParts.size, cParts.size)
      for (i in 0 until max) {
        val rp = rParts.getOrNull(i) ?: return -1
        val cp = cParts.getOrNull(i) ?: return 1
        val rn = rp.toIntOrNull()
        val cn = cp.toIntOrNull()
        val cmp =
          when {
            rn != null && cn != null -> rn.compareTo(cn)
            rn != null -> -1 // numeric identifiers have lower precedence than non-numeric
            cn != null -> 1
            else -> rp.lowercase().compareTo(cp.lowercase())
          }
        if (cmp != 0) return cmp
      }
      return 0
    }

    fun selectCompatibleApkAsset(
      assets: List<GitHubReleaseAsset>,
      supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty(),
      isDebugBuild: Boolean = false,
    ): GitHubReleaseAsset? {
      val apks = assets.filter { it.isApkPackage }
      if (apks.isEmpty()) return null

      val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
      val abiCompatible =
        apks.filter { asset ->
          val lowerName = asset.name.lowercase()
          val matchedAbi = knownAbis.firstOrNull { lowerName.contains(it) }
          matchedAbi == null ||
            lowerName.contains("universal") ||
            supportedAbis.any { it.equals(matchedAbi, ignoreCase = true) }
        }.ifEmpty { apks }

      fun scoreAsset(asset: GitHubReleaseAsset): Int {
        val nameLower = asset.name.lowercase()
        var score = 0
        if (asset.contentType.equals(GitHubReleaseAsset.APK_MIME_TYPE, ignoreCase = true)) {
          score += 50
        }
        if (!asset.sha256.isNullOrBlank()) score += 20
        val abiIndex =
          supportedAbis.indexOfFirst { abi -> nameLower.contains(abi.lowercase()) }
        if (abiIndex >= 0) {
          score += (40 - abiIndex * 8).coerceAtLeast(10)
        } else if (nameLower.contains("universal")) {
          score += 15
        }
        val isReleaseAsset = nameLower.contains("release")
        val isDebugAsset = nameLower.contains("debug")
        if (isDebugBuild) {
          if (isDebugAsset) score += 25 else if (!isReleaseAsset) score += 10
        } else {
          if (isReleaseAsset) score += 25 else if (!isDebugAsset) score += 10
        }
        return score
      }

      return abiCompatible.maxByOrNull(::scoreAsset)
    }

    internal fun parseChecksumsFromText(
      text: String,
      assetNames: List<String>,
    ): Map<String, String> {
      if (text.isBlank()) return emptyMap()
      val result = mutableMapOf<String, String>()
      val lines = text.lines()
      for (line in lines) {
        val match = HEX_64_REGEX.find(line) ?: continue
        val hash = match.groupValues[1].lowercase()
        val matchedName = assetNames.firstOrNull { line.contains(it, ignoreCase = true) }
        if (matchedName != null) {
          result[matchedName] = hash
        } else if (!result.containsKey("*")) {
          result["*"] = hash
        }
      }
      if (result.isEmpty()) {
        HEX_64_REGEX.find(text)?.let { result["*"] = it.groupValues[1].lowercase() }
      }
      return result
    }

    fun hasValidApkHeader(file: File): Boolean {
      if (!file.exists() || file.length() < 4L) return false
      return runCatching {
        FileInputStream(file).use { fis ->
          val header = ByteArray(4)
          val read = fis.read(header)
          read == 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
        }
      }.getOrDefault(false)
    }

    fun computeSha256Hex(file: File): String {
      val digest = MessageDigest.getInstance("SHA-256")
      val buf = ByteArray(16 * 1024)
      FileInputStream(file).use { fis ->
        while (true) {
          val read = fis.read(buf)
          if (read <= 0) break
          digest.update(buf, 0, read)
        }
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }
  }
}
