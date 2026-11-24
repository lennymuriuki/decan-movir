package com.decanmovie.desktop.data.remote

import androidx.compose.runtime.Immutable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringReader
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource

@Immutable
data class WindowsReleaseAsset(
  val name: String,
  val downloadUrl: String,
  val sizeMb: Double,
  val sizeBytes: Long = 0L,
  val contentType: String = "",
  val sha256: String? = null,
) {
  val isWindowsInstaller: Boolean
    get() {
      val n = name.lowercase()
      return (n.endsWith(".exe") || n.endsWith(".msi")) && !n.endsWith(".apk")
    }

  val isWindowsPortableZip: Boolean
    get() {
      val n = name.lowercase()
      return n.endsWith(".zip") &&
        (n.contains("portable") || n.contains("win") || n.contains("decan"))
    }

  val isAndroidAsset: Boolean
    get() {
      val n = name.lowercase()
      return n.endsWith(".apk") ||
        n.endsWith(".aab") ||
        contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
    }

  val isWindowsDesktopPackage: Boolean
    get() = !isAndroidAsset && (isWindowsInstaller || isWindowsPortableZip)
}

@Immutable
data class DesktopGitHubReleaseInfo(
  val tagName: String,
  val title: String,
  val changelog: String,
  val publishedAt: String,
  val htmlUrl: String,
  val isPrerelease: Boolean,
  val assets: List<WindowsReleaseAsset>,
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
                  put(
                    "size",
                    if (a.sizeBytes > 0L) a.sizeBytes else (a.sizeMb * 1024.0 * 1024.0).toLong(),
                  )
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

class DesktopGitHubUpdateRepository(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .build(),
) {
  suspend fun checkLatestWindowsRelease(
    repoSlug: String,
    currentVersion: String,
  ): Result<DesktopGitHubReleaseInfo?> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanSlug = normalizeRepoSlug(repoSlug)
        require(cleanSlug.contains("/")) { "Invalid GitHub repository slug" }

        val latestReq =
          Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases/latest")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "DecanMovie-WindowsDesktop")
            .build()

        val apiResult =
          runCatching {
            client.newCall(latestReq).execute().use { resp ->
              when {
                resp.isSuccessful -> {
                  val parsed = parseRelease(JSONObject(resp.body?.string().orEmpty()), currentVersion)
                  if (selectWindowsAsset(parsed.assets) != null) parsed
                  else fetchWindowsCompatibleFromList(cleanSlug, currentVersion) ?: parsed
                }
                resp.code == 404 -> fetchWindowsCompatibleFromList(cleanSlug, currentVersion)
                else -> null
              }
            }
          }.getOrNull()

        if (apiResult != null) {
          return@runCatching enrichReleaseChecksums(apiResult)
        }

        fetchReleasesAtom(cleanSlug, currentVersion).firstOrNull()?.let { enrichReleaseChecksums(it) }
      }
    }

  private fun fetchWindowsCompatibleFromList(
    cleanSlug: String,
    currentVersion: String,
  ): DesktopGitHubReleaseInfo? {
    val listReq =
      Request.Builder()
        .url("https://api.github.com/repos/$cleanSlug/releases?per_page=10")
        .addHeader("Accept", "application/vnd.github+json")
        .addHeader("User-Agent", "DecanMovie-WindowsDesktop")
        .build()

    val releases =
      runCatching {
        client.newCall(listReq).execute().use { resp ->
          if (!resp.isSuccessful) return@use emptyList<DesktopGitHubReleaseInfo>()
          val arr = JSONArray(resp.body?.string().orEmpty())
          buildList {
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              if (obj.optBoolean("draft", false)) continue
              add(parseRelease(obj, currentVersion))
            }
          }
        }
      }.getOrDefault(emptyList())

    return releases.firstOrNull { selectWindowsAsset(it.assets) != null }
      ?: releases.firstOrNull { !it.isPrerelease }
      ?: releases.firstOrNull()
  }

  suspend fun downloadWindowsPackage(
    packageUrl: String,
    destFile: File,
    expectedSha256: String? = null,
    onProgress: (Float) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val partFile = File(destFile.parentFile, "${destFile.name}.part")
      runCatching {
        require(!packageUrl.lowercase().substringBefore('?').endsWith(".apk")) {
          "Refusing Android APK package on Windows desktop"
        }
        destFile.parentFile?.mkdirs()
        if (partFile.exists()) partFile.delete()

        val req =
          Request.Builder()
            .url(packageUrl)
            .addHeader("User-Agent", "DecanMovie-WindowsDesktop")
            .build()

        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) {
            throw IllegalStateException("Windows update download failed (HTTP ${resp.code})")
          }
          val body = resp.body ?: throw IllegalStateException("Empty Windows update package")
          val totalBytes = body.contentLength()
          var copied = 0L
          val digest = MessageDigest.getInstance("SHA-256")
          val buffer = ByteArray(32 * 1024)

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
            throw IllegalStateException("Downloaded Windows update file is empty")
          }
          if (!hasValidWindowsPackageHeader(partFile, destFile.name)) {
            throw IllegalStateException("Downloaded file does not match a valid Windows package signature")
          }

          val expectedClean = expectedSha256?.trim()?.lowercase()?.takeIf { it.length == 64 }
          if (expectedClean != null) {
            val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHex.equals(expectedClean, ignoreCase = true)) {
              throw IllegalStateException("Update integrity check failed (SHA-256 mismatch)")
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

  private fun enrichReleaseChecksums(info: DesktopGitHubReleaseInfo): DesktopGitHubReleaseInfo {
    val desktopAssets = info.assets.filter { it.isWindowsDesktopPackage }
    if (desktopAssets.isEmpty() || desktopAssets.all { !it.sha256.isNullOrBlank() }) return info

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
            .addHeader("User-Agent", "DecanMovie-WindowsDesktop")
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

  internal fun fetchReleasesAtom(
    cleanSlug: String,
    currentVersion: String,
  ): List<DesktopGitHubReleaseInfo> {
    val atomUrl = "https://github.com/$cleanSlug/releases.atom"
    val xml =
      runCatching {
        val req =
          Request.Builder()
            .url(atomUrl)
            .addHeader("User-Agent", "DecanMovie-WindowsDesktop")
            .build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
        }
      }.getOrDefault("")
    return if (xml.isBlank()) emptyList() else parseAtomXml(xml, cleanSlug, currentVersion)
  }

  internal fun parseAtomXml(
    xml: String,
    cleanSlug: String,
    currentVersion: String,
  ): List<DesktopGitHubReleaseInfo> {
    return runCatching {
      val factory = DocumentBuilderFactory.newInstance()
      factory.isNamespaceAware = true
      val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
      val entryNodes = doc.getElementsByTagName("entry")
      val list = mutableListOf<DesktopGitHubReleaseInfo>()
      for (i in 0 until entryNodes.length) {
        val entry = entryNodes.item(i) as? Element ?: continue
        val title = entry.getElementsByTagName("title").item(0)?.textContent.orEmpty().trim()
        val updated = entry.getElementsByTagName("updated").item(0)?.textContent.orEmpty().trim()
        val content = entry.getElementsByTagName("content").item(0)?.textContent.orEmpty()
        var linkHref = ""
        val links = entry.getElementsByTagName("link")
        for (l in 0 until links.length) {
          val el = links.item(l) as? Element ?: continue
          val href = el.getAttribute("href")
          if (href.isNotBlank()) {
            linkHref = href
            break
          }
        }
        val tagFromUrl = linkHref.substringAfterLast("/").trim()
        val tagName =
          tagFromUrl.takeIf { it.isNotBlank() }
            ?: title.split(" ").firstOrNull().orEmpty().ifBlank { "v1.0.0" }
        val cleanText =
          content.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        val exeName = "Decan-Movie-Setup.exe"
        val portableName = "Decan-Movie-Portable.zip"
        val checksumMap = parseChecksumsFromText(cleanText, listOf(exeName, portableName))
        list.add(
          DesktopGitHubReleaseInfo(
            tagName = tagName,
            title = title.ifBlank { tagName },
            changelog = cleanText.ifBlank { "New Decan Movie desktop release available." },
            publishedAt = updated.take(10),
            htmlUrl = linkHref.ifBlank { "https://github.com/$cleanSlug/releases/tag/$tagName" },
            isPrerelease =
              tagName.contains("beta", true) ||
                tagName.contains("rc", true) ||
                tagName.contains("alpha", true),
            assets =
              listOf(
                WindowsReleaseAsset(
                  name = exeName,
                  downloadUrl = "https://github.com/$cleanSlug/releases/download/$tagName/$exeName",
                  sizeMb = 0.0,
                  contentType = "application/x-msdownload",
                  sha256 = checksumMap[exeName] ?: checksumMap["*"],
                ),
                WindowsReleaseAsset(
                  name = portableName,
                  downloadUrl =
                    "https://github.com/$cleanSlug/releases/download/$tagName/$portableName",
                  sizeMb = 0.0,
                  contentType = "application/zip",
                  sha256 = checksumMap[portableName] ?: checksumMap["*"],
                ),
              ),
            isNewerThanCurrent = isNewerVersion(remoteTag = tagName, currentVersion = currentVersion),
          )
        )
      }
      list
    }.getOrDefault(emptyList())
  }

  internal fun parseRelease(obj: JSONObject, currentVersion: String): DesktopGitHubReleaseInfo {
    val tag = obj.optString("tag_name").ifBlank { "v1.0.0" }
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
            WindowsReleaseAsset(
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
        else if (asset.isWindowsDesktopPackage) {
          asset.copy(sha256 = checksumMap[asset.name] ?: checksumMap["*"])
        } else asset
      }
    return DesktopGitHubReleaseInfo(
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
    const val DEFAULT_REPO = "lennymuriuki/decan-movir"
    private val HEX_64_REGEX = Regex("(?i)\\b([0-9a-f]{64})\\b")

    fun normalizeRepoSlug(raw: String): String {
      val clean = raw.trim().removePrefix("https://github.com/").trim('/')
      return if (clean.isBlank() || clean.equals("lennymuriuki4/decan-movir", ignoreCase = true)) {
        DEFAULT_REPO
      } else {
        clean
      }
    }

    fun parseReleaseJson(json: String, currentVersion: String): DesktopGitHubReleaseInfo? =
      runCatching {
        DesktopGitHubUpdateRepository().parseRelease(JSONObject(json), currentVersion)
      }.getOrNull()

    fun selectWindowsAsset(
      assets: List<WindowsReleaseAsset>,
      preferPortable: Boolean = false,
    ): WindowsReleaseAsset? {
      val compatible = assets.filter { it.isWindowsDesktopPackage }
      if (compatible.isEmpty()) return null
      return compatible.maxByOrNull { asset ->
        val n = asset.name.lowercase()
        var score = 0
        if (preferPortable) {
          if (n.endsWith(".zip") && n.contains("portable")) score += 600
          if (n.endsWith(".exe")) score += 300
          if (n.endsWith(".msi")) score += 200
        } else {
          if (n.endsWith(".exe")) score += 600
          if (n.endsWith(".msi")) score += 450
          if (n.endsWith(".zip") && n.contains("portable")) score += 250
        }
        if (n.contains("setup") || n.contains("installer")) score += 120
        if (n.contains("win") || n.contains("x64") || n.contains("amd64") || n.contains("decan-movie")) {
          score += 50
        }
        if (n.contains("arm64") && !System.getProperty("os.arch").orEmpty().contains("aarch64", true)) {
          score -= 200
        }
        if (!asset.sha256.isNullOrBlank()) score += 25
        score
      }
    }

    fun hasValidWindowsPackageHeader(file: File, targetFileName: String = file.name): Boolean {
      if (!file.exists() || file.length() < 4L) return false
      val magic = ByteArray(4)
      FileInputStream(file).use { fis ->
        if (fis.read(magic) < 4) return false
      }
      val lower = targetFileName.lowercase()
      return when {
        lower.endsWith(".exe") ->
          magic[0] == 0x4D.toByte() && magic[1] == 0x5A.toByte() // 'M' 'Z' PE Header
        lower.endsWith(".msi") ->
          magic[0] == 0xD0.toByte() &&
            magic[1] == 0xCF.toByte() &&
            magic[2] == 0x11.toByte() &&
            magic[3] == 0xE0.toByte() // OLE Compound Document
        lower.endsWith(".zip") ->
          magic[0] == 0x50.toByte() &&
            magic[1] == 0x4B.toByte() &&
            magic[2] == 0x03.toByte() &&
            magic[3] == 0x04.toByte() // PK\x03\x04
        else -> true
      }
    }

    fun computeSha256Hex(file: File): String? =
      runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        FileInputStream(file).use { fis ->
          while (true) {
            val read = fis.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
          }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
      }.getOrNull()

    fun parseChecksumsFromText(text: String, assetNames: List<String>): Map<String, String> {
      if (text.isBlank()) return emptyMap()
      val map = LinkedHashMap<String, String>()
      text.lines().forEach { rawLine ->
        val line = rawLine.trim()
        val match = HEX_64_REGEX.find(line) ?: return@forEach
        val hash = match.groupValues[1].lowercase()
        val matchedAsset =
          assetNames.firstOrNull { name -> line.contains(name, ignoreCase = true) }
        if (matchedAsset != null) {
          map[matchedAsset] = hash
        } else if (!map.containsKey("*")) {
          map["*"] = hash
        }
      }
      return map
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
      val (remoteCore, remotePre) = splitSemVer(remoteTag)
      val (currentCore, currentPre) = splitSemVer(currentVersion)
      val maxLen = maxOf(remoteCore.size, currentCore.size)
      for (i in 0 until maxLen) {
        val r = remoteCore.getOrElse(i) { 0 }
        val c = currentCore.getOrElse(i) { 0 }
        if (r != c) return r > c
      }
      if (remotePre.isEmpty() && currentPre.isEmpty()) return false
      if (remotePre.isEmpty() && currentPre.isNotEmpty()) return true
      if (remotePre.isNotEmpty() && currentPre.isEmpty()) return false
      val preLen = maxOf(remotePre.size, currentPre.size)
      for (i in 0 until preLen) {
        val rTok = remotePre.getOrNull(i) ?: return false
        val cTok = currentPre.getOrNull(i) ?: return true
        val rInt = rTok.toIntOrNull()
        val cInt = cTok.toIntOrNull()
        val cmp =
          when {
            rInt != null && cInt != null -> rInt.compareTo(cInt)
            rInt != null -> -1
            cInt != null -> 1
            else -> rTok.compareTo(cTok, ignoreCase = true)
          }
        if (cmp != 0) return cmp > 0
      }
      return false
    }

    private fun splitSemVer(raw: String): Pair<List<Int>, List<String>> {
      val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore("+")
      val corePart = cleaned.substringBefore("-")
      val prePart = cleaned.substringAfter("-", "")
      val coreNums =
        corePart
          .split(".")
          .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
          .ifEmpty { listOf(0) }
      val preTokens =
        if (prePart.isBlank()) emptyList()
        else prePart.split(".", "-").filter { it.isNotBlank() }
      return coreNums to preTokens
    }
  }
}
