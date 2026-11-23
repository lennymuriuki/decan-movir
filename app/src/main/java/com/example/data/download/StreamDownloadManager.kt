package com.example.data.download

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.data.remote.moviebox.SrtParser
import com.example.data.remote.moviebox.SubtitleCue
import com.example.data.settings.DownloadStorageLocation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Immutable
data class DownloadTaskInfo(
  val id: Long,
  val title: String,
  val description: String,
  val status: Int,
  val statusLabel: String,
  val reasonHint: String?,
  val bytesDownloaded: Long,
  val totalBytes: Long,
  val progressFraction: Float,
  val localUri: String?,
  val mimeType: String?,
  val folderName: String = "Movies",
  val isSeriesFolder: Boolean = false,
  val seasonNumber: Int = 0,
  val episodeNumber: Int = 0,
  val relativePath: String = "",
) {
  val isCompleted: Boolean
    get() = status == DownloadManager.STATUS_SUCCESSFUL

  val isActive: Boolean
    get() =
      status == DownloadManager.STATUS_RUNNING ||
        status == DownloadManager.STATUS_PENDING ||
        status == DownloadManager.STATUS_PAUSED

  val isFailed: Boolean
    get() = status == DownloadManager.STATUS_FAILED

  val isSubtitle: Boolean
    get() =
      title.contains("(.srt)", ignoreCase = true) ||
        localUri?.endsWith(".srt", ignoreCase = true) == true ||
        relativePath.endsWith(".srt", ignoreCase = true)

  val isVideo: Boolean
    get() =
      !isSubtitle &&
        (mimeType?.startsWith("video/") == true ||
          localUri?.endsWith(".mp4", ignoreCase = true) == true ||
          localUri?.endsWith(".mkv", ignoreCase = true) == true ||
          relativePath.endsWith(".mp4", ignoreCase = true) ||
          relativePath.endsWith(".mkv", ignoreCase = true) ||
          title.contains("1080") ||
          title.contains("720") ||
          title.contains("480") ||
          title.contains("360"))

  val episodeCode: String?
    get() =
      if (seasonNumber > 0 && episodeNumber > 0) {
        "S${seasonNumber.toString().padStart(2, '0')}E${episodeNumber.toString().padStart(2, '0')}"
      } else null
}

@Immutable
data class DownloadFolderSummary(
  val key: String,
  val folderName: String,
  val isSeriesFolder: Boolean,
  val items: List<DownloadTaskInfo>,
  val videoCount: Int,
  val subtitleCount: Int,
  val activeCount: Int,
  val totalBytes: Long,
)

data class BatchEnqueueSummary(
  val queuedFiles: Int,
  val failedFiles: Int,
)

object StreamDownloadManager {
  private const val META_PREFS = "decan_download_folder_meta"
  private val EPISODE_TAG_REGEX = Regex("S(\\d{1,2})E(\\d{1,3})", RegexOption.IGNORE_CASE)

  internal fun buildRelativeMediaPath(
    movieTitle: String,
    isSeries: Boolean,
    season: Int,
    episode: Int,
    suffix: String,
    extension: String,
  ): String {
    val folderTitle = sanitizeFolderName(movieTitle)
    val cleanFileBase = sanitizeFilename(movieTitle)
    return if (isSeries || (season > 0 && episode > 0)) {
      val sNum = season.coerceAtLeast(1)
      val eNum = episode.coerceAtLeast(1)
      val sPad = sNum.toString().padStart(2, '0')
      val ePad = eNum.toString().padStart(2, '0')
      "DecanMovie/Series/$folderTitle/Season_$sPad/S${sPad}E${ePad}_${cleanFileBase}_$suffix.$extension"
    } else {
      "DecanMovie/Movies/$folderTitle/${cleanFileBase}_$suffix.$extension"
    }
  }

  fun enqueueStreamDownload(
    context: Context,
    movieTitle: String,
    stream: MovieBoxStream,
    season: Int = 0,
    episode: Int = 0,
    isSeries: Boolean = (season > 0 && episode > 0),
    allowMetered: Boolean = true,
    storageLocation: DownloadStorageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
  ): Result<Long> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val ext =
        if (stream.format.equals("MKV", ignoreCase = true)) "mkv" else "mp4"
      val relPath =
        buildRelativeMediaPath(
          movieTitle = movieTitle,
          isSeries = isSeries,
          season = season,
          episode = episode,
          suffix = stream.quality.replace(" ", "_"),
          extension = ext,
        )
      val labelEp = if (season > 0 && episode > 0) " • S${season}E${episode}" else ""

      val request =
        DownloadManager.Request(Uri.parse(stream.url))
          .setTitle("$movieTitle$labelEp (${stream.quality})")
          .setDescription("Decan Movie • ${stream.format} • $relPath")
          .setMimeType(if (ext == "mkv") "video/x-matroska" else "video/mp4")
          .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
          )
          .setAllowedOverMetered(allowMetered)
          .setAllowedOverRoaming(allowMetered)

      applyDestination(context, request, storageLocation, relPath)

      stream.headers.forEach { (k, v) -> request.addRequestHeader(k, v) }
      if (!stream.cookie.isNullOrBlank()) {
        request.addRequestHeader("Cookie", stream.cookie)
      }

      val id = dm.enqueue(request)
      saveFolderMeta(
        context = context,
        downloadId = id,
        folderName = sanitizeFolderName(movieTitle),
        isSeriesFolder = isSeries || (season > 0 && episode > 0),
        seasonNumber = season,
        episodeNumber = episode,
        relativePath = relPath,
      )
      id
    }

  fun enqueueSubtitleDownload(
    context: Context,
    movieTitle: String,
    subtitle: MovieBoxSubtitle,
    season: Int = 0,
    episode: Int = 0,
    isSeries: Boolean = (season > 0 && episode > 0),
    allowMetered: Boolean = true,
    storageLocation: DownloadStorageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
  ): Result<Long> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val langTag = sanitizeFilename(subtitle.language).take(24)
      val relPath =
        buildRelativeMediaPath(
          movieTitle = movieTitle,
          isSeries = isSeries,
          season = season,
          episode = episode,
          suffix = langTag,
          extension = "srt",
        )
      val labelEp = if (season > 0 && episode > 0) " • S${season}E${episode}" else ""

      val request =
        DownloadManager.Request(Uri.parse(subtitle.url))
          .setTitle("$movieTitle$labelEp • ${subtitle.language} (.srt)")
          .setDescription("Decan Movie • Subtitle • $relPath")
          .setMimeType("application/x-subrip")
          .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
          )
          .setAllowedOverMetered(allowMetered)

      applyDestination(context, request, storageLocation, relPath)

      val id = dm.enqueue(request)
      saveFolderMeta(
        context = context,
        downloadId = id,
        folderName = sanitizeFolderName(movieTitle),
        isSeriesFolder = isSeries || (season > 0 && episode > 0),
        seasonNumber = season,
        episodeNumber = episode,
        relativePath = relPath,
      )
      id
    }

  private fun applyDestination(
    context: Context,
    request: DownloadManager.Request,
    location: DownloadStorageLocation,
    relativePath: String,
  ) {
    when (location) {
      DownloadStorageLocation.PUBLIC_DOWNLOADS ->
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, relativePath)
      DownloadStorageLocation.GALLERY_MOVIES ->
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, relativePath)
      DownloadStorageLocation.APP_PRIVATE ->
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, relativePath)
    }
  }

  fun enqueueMultiSelection(
    context: Context,
    movieTitle: String,
    streams: List<MovieBoxStream>,
    subtitles: List<MovieBoxSubtitle>,
    season: Int = 0,
    episode: Int = 0,
    isSeries: Boolean = (season > 0 && episode > 0),
    allowMetered: Boolean = true,
    storageLocation: DownloadStorageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
  ): BatchEnqueueSummary {
    var queued = 0
    var failed = 0
    streams.forEach { st ->
      if (st.isPlayableDirect) {
        enqueueStreamDownload(
          context = context,
          movieTitle = movieTitle,
          stream = st,
          season = season,
          episode = episode,
          isSeries = isSeries,
          allowMetered = allowMetered,
          storageLocation = storageLocation,
        )
          .onSuccess { queued++ }
          .onFailure { failed++ }
      }
    }
    subtitles.forEach { sub ->
      enqueueSubtitleDownload(
        context = context,
        movieTitle = movieTitle,
        subtitle = sub,
        season = season,
        episode = episode,
        isSeries = isSeries,
        allowMetered = allowMetered,
        storageLocation = storageLocation,
      )
        .onSuccess { queued++ }
        .onFailure { failed++ }
    }
    return BatchEnqueueSummary(queuedFiles = queued, failedFiles = failed)
  }

  fun queryDownloadById(context: Context, downloadId: Long): DownloadTaskInfo? =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
      cursor.use { c ->
        if (!c.moveToFirst()) return null
        parseCursorRow(context, dm, c)
      }
    }.getOrNull()

  fun queryAllDownloads(context: Context): List<DownloadTaskInfo> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val cursor = dm.query(DownloadManager.Query()) ?: return emptyList()
      cursor.use { c ->
        val items = mutableListOf<DownloadTaskInfo>()
        while (c.moveToNext()) {
          val task = parseCursorRow(context, dm, c) ?: continue
          items.add(task)
        }
        items.sortedByDescending { it.id }
      }
    }.getOrDefault(emptyList())

  fun groupIntoFolders(tasks: List<DownloadTaskInfo>): List<DownloadFolderSummary> {
    val moviesFolderItems = tasks.filter { !it.isSeriesFolder }
    val seriesGrouped = tasks.filter { it.isSeriesFolder }.groupBy { it.folderName }

    return buildList {
      seriesGrouped.forEach { (seriesName, list) ->
        val sorted =
          list.sortedWith(
            compareBy<DownloadTaskInfo> { it.seasonNumber }
              .thenBy { it.episodeNumber }
              .thenBy { if (it.isVideo) 0 else 1 }
              .thenByDescending { it.id }
          )
        add(
          DownloadFolderSummary(
            key = "series_$seriesName",
            folderName = seriesName.replace("_", " "),
            isSeriesFolder = true,
            items = sorted,
            videoCount = sorted.count { it.isVideo },
            subtitleCount = sorted.count { it.isSubtitle },
            activeCount = sorted.count { it.isActive },
            totalBytes = sorted.sumOf { it.bytesDownloaded },
          )
        )
      }
      if (moviesFolderItems.isNotEmpty()) {
        add(
          DownloadFolderSummary(
            key = "movies_root",
            folderName = "Movies",
            isSeriesFolder = false,
            items = moviesFolderItems.sortedByDescending { it.id },
            videoCount = moviesFolderItems.count { it.isVideo },
            subtitleCount = moviesFolderItems.count { it.isSubtitle },
            activeCount = moviesFolderItems.count { it.isActive },
            totalBytes = moviesFolderItems.sumOf { it.bytesDownloaded },
          )
        )
      }
    }.sortedWith(compareByDescending<DownloadFolderSummary> { it.isSeriesFolder }.thenBy { it.folderName })
  }

  /**
   * Finds matching downloaded .srt subtitle files for an offline video URI and parses cues.
   * Matches by exact sibling folder path, SxxExx episode code, and movie/series folder title.
   */
  suspend fun findOfflineSubtitlesForMedia(
    context: Context,
    localUri: String,
    mediaTitle: String,
  ): List<Pair<MovieBoxSubtitle, List<SubtitleCue>>> =
    withContext(Dispatchers.IO) {
      val allTasks = queryAllDownloads(context)
      val matchedVideo =
        allTasks.firstOrNull {
          it.localUri == localUri ||
            (!it.relativePath.isBlank() && localUri.contains(it.relativePath, ignoreCase = true))
        }
      val videoEpTag =
        matchedVideo?.episodeCode
          ?: extractEpisodeTag(mediaTitle)
          ?: extractEpisodeTag(localUri)
      val videoFolder =
        matchedVideo?.folderName
          ?: sanitizeFolderName(mediaTitle.substringBefore("•").substringBefore("(").trim())
      val normalizedVideoFolder = videoFolder.lowercase().replace(Regex("[^a-z0-9]"), "")

      val results = mutableListOf<Pair<MovieBoxSubtitle, List<SubtitleCue>>>()
      val seenUris = HashSet<String>()

      // 1. Match completed .srt tasks tracked by DownloadManager
      val subTasks =
        allTasks.filter { task ->
          task.isCompleted && task.isSubtitle && !task.localUri.isNullOrBlank()
        }
      val scoredSubs =
        subTasks.filter { sub ->
          val subEpTag = sub.episodeCode ?: extractEpisodeTag(sub.title) ?: extractEpisodeTag(sub.relativePath)
          val subFolderNorm = sub.folderName.lowercase().replace(Regex("[^a-z0-9]"), "")
          val sameTitle =
            subFolderNorm == normalizedVideoFolder ||
              subFolderNorm.contains(normalizedVideoFolder) ||
              normalizedVideoFolder.contains(subFolderNorm)
          val epCompatible =
            when {
              videoEpTag != null && subEpTag != null -> videoEpTag.equals(subEpTag, ignoreCase = true)
              videoEpTag != null && subEpTag == null -> false
              else -> true
            }
          sameTitle && epCompatible
        }

      scoredSubs.forEach { subTask ->
        val uriStr = subTask.localUri ?: return@forEach
        if (!seenUris.add(uriStr)) return@forEach
        val rawSrt = readTextFromUri(context, uriStr) ?: return@forEach
        val cues = SrtParser.parse(rawSrt)
        if (cues.isNotEmpty()) {
          val lang =
            subTask.title
              .substringAfter("• ", "")
              .substringBefore(" (.srt)")
              .trim()
              .ifBlank { "Offline CC" }
          results.add(
            MovieBoxSubtitle(
              id = "local_${subTask.id}",
              language = "$lang (Offline)",
              code = lang.take(2).lowercase(),
              url = uriStr,
            ) to cues
          )
        }
      }

      // 2. Fallback: scan sibling .srt files in the same directory on disk (works for file:// URIs or app-private files)
      runCatching {
        val parsedUri = Uri.parse(localUri)
        if (parsedUri.scheme == "file" && !parsedUri.path.isNullOrBlank()) {
          val videoFile = File(parsedUri.path!!)
          val parentDir = videoFile.parentFile
          if (parentDir != null && parentDir.isDirectory) {
            parentDir.listFiles { f -> f.isFile && f.extension.equals("srt", ignoreCase = true) }
              ?.forEach { srtFile ->
                val srtUri = Uri.fromFile(srtFile).toString()
                if (!seenUris.add(srtUri)) return@forEach
                val fileEpTag = extractEpisodeTag(srtFile.name)
                if (videoEpTag != null && fileEpTag != null && !videoEpTag.equals(fileEpTag, true)) {
                  return@forEach
                }
                val cues = SrtParser.parse(srtFile.readText())
                if (cues.isNotEmpty()) {
                  results.add(
                    MovieBoxSubtitle(
                      id = "file_${srtFile.name.hashCode()}",
                      language = "${srtFile.nameWithoutExtension.substringAfterLast("_")} (Offline)",
                      code = "en",
                      url = srtUri,
                    ) to cues
                  )
                }
              }
          }
        }
      }

      results
    }

  suspend fun saveVideoToGallery(
    context: Context,
    localUri: String,
    folderTitle: String,
    displayTitle: String,
  ): Result<Uri> =
    withContext(Dispatchers.IO) {
      runCatching {
        val srcUri = Uri.parse(localUri)
        val inputStream =
          if (srcUri.scheme == "file" && !srcUri.path.isNullOrBlank()) {
            File(srcUri.path!!).inputStream()
          } else {
            context.contentResolver.openInputStream(srcUri)
              ?: throw IllegalStateException("Unable to read downloaded file")
          }

        val cleanName = sanitizeFilename(displayTitle)
        val folder = sanitizeFolderName(folderTitle)
        val ext = if (localUri.endsWith(".mkv", ignoreCase = true)) "mkv" else "mp4"
        val mime = if (ext == "mkv") "video/x-matroska" else "video/mp4"
        val resolver = context.contentResolver

        val values =
          ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$cleanName.$ext")
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DecanMovie/$folder")
              put(MediaStore.Video.Media.IS_PENDING, 1)
            }
          }

        val targetUri =
          resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create Gallery entry")

        inputStream.use { input ->
          resolver.openOutputStream(targetUri)?.use { output ->
            input.copyTo(output, bufferSize = 32 * 1024)
            output.flush()
          } ?: throw IllegalStateException("Unable to write to Gallery")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val doneValues = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
          resolver.update(targetUri, doneValues, null, null)
        }
        targetUri
      }
    }

  private fun readTextFromUri(context: Context, uriString: String): String? =
    runCatching {
      val uri = Uri.parse(uriString)
      if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
        File(uri.path!!).takeIf { it.exists() }?.readText()
      } else {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
      }
    }.getOrNull()

  private fun parseCursorRow(context: Context, dm: DownloadManager, c: Cursor): DownloadTaskInfo? {
    val idxId = c.getColumnIndex(DownloadManager.COLUMN_ID)
    val idxTitle = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
    val idxDesc = c.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
    val idxStatus = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
    val idxReason = c.getColumnIndex(DownloadManager.COLUMN_REASON)
    val idxSoFar = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
    val idxTotal = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
    val idxLocalUri = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
    val idxMime = c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)

    val desc = if (idxDesc >= 0) c.getString(idxDesc).orEmpty() else ""
    val rawLocalUri = if (idxLocalUri >= 0) c.getString(idxLocalUri) else null
    val isDecan =
      desc.contains("Decan Movie", ignoreCase = true) ||
        rawLocalUri?.contains("DecanMovie", ignoreCase = true) == true
    if (!isDecan) return null

    val id = if (idxId >= 0) c.getLong(idxId) else 0L
    val title = if (idxTitle >= 0) c.getString(idxTitle).orEmpty() else "Download"
    val status = if (idxStatus >= 0) c.getInt(idxStatus) else DownloadManager.STATUS_PENDING
    val reasonCode = if (idxReason >= 0) c.getInt(idxReason) else 0
    val downloaded = if (idxSoFar >= 0) c.getLong(idxSoFar).coerceAtLeast(0L) else 0L
    val total = if (idxTotal >= 0) c.getLong(idxTotal) else -1L
    val mime = if (idxMime >= 0) c.getString(idxMime) else null

    val resolvedContentUri =
      if (status == DownloadManager.STATUS_SUCCESSFUL) {
        runCatching { dm.getUriForDownloadedFile(id)?.toString() }.getOrNull() ?: rawLocalUri
      } else {
        rawLocalUri
      }

    val meta = loadFolderMeta(context, id)
    val parsedRelPath =
      meta?.relativePath?.takeIf { it.isNotBlank() }
        ?: desc.substringAfterLast("• ", "").takeIf { it.contains("DecanMovie") }
        ?: rawLocalUri?.substringAfter("DecanMovie/")?.let { "DecanMovie/$it" }.orEmpty()

    val isSeries =
      meta?.isSeriesFolder
        ?: (parsedRelPath.contains("/Series/", ignoreCase = true) ||
          EPISODE_TAG_REGEX.containsMatchIn(title))

    val folderName =
      meta?.folderName?.takeIf { it.isNotBlank() }
        ?: extractFolderFromPath(parsedRelPath)
        ?: sanitizeFolderName(title.substringBefore("•").substringBefore("(").trim())

    val epMatch = EPISODE_TAG_REGEX.find(title) ?: EPISODE_TAG_REGEX.find(parsedRelPath)
    val seasonNum = meta?.seasonNumber?.takeIf { it > 0 } ?: epMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val episodeNum = meta?.episodeNumber?.takeIf { it > 0 } ?: epMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0

    val progress =
      when {
        status == DownloadManager.STATUS_SUCCESSFUL -> 1f
        total > 0L -> (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        else -> 0f
      }
    val statusLabel =
      when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> "Completed"
        DownloadManager.STATUS_RUNNING -> "Downloading"
        DownloadManager.STATUS_PAUSED -> "Paused"
        DownloadManager.STATUS_PENDING -> "Queued"
        DownloadManager.STATUS_FAILED -> "Failed"
        else -> "Pending"
      }
    val reasonHint =
      when (status) {
        DownloadManager.STATUS_PAUSED ->
          when (reasonCode) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for network"
            else -> "Paused"
          }
        DownloadManager.STATUS_FAILED ->
          when (reasonCode) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
            DownloadManager.ERROR_CANNOT_RESUME -> "Link expired — re-queue from movie details"
            DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
              "Stream unavailable — re-queue from movie details"
            else -> "Download unavailable right now"
          }
        else -> null
      }

    return DownloadTaskInfo(
      id = id,
      title = title,
      description = desc,
      status = status,
      statusLabel = statusLabel,
      reasonHint = reasonHint,
      bytesDownloaded = downloaded,
      totalBytes = total,
      progressFraction = progress,
      localUri = resolvedContentUri,
      mimeType = mime,
      folderName = folderName,
      isSeriesFolder = isSeries,
      seasonNumber = seasonNum,
      episodeNumber = episodeNum,
      relativePath = parsedRelPath,
    )
  }

  fun removeDownload(context: Context, vararg ids: Long): Int {
    val prefs = context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
    prefs.edit().apply { ids.forEach { remove("dl_$it") } }.apply()
    return runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      dm.remove(*ids)
    }.getOrDefault(0)
  }

  private data class StoredFolderMeta(
    val folderName: String,
    val isSeriesFolder: Boolean,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val relativePath: String,
  )

  private fun saveFolderMeta(
    context: Context,
    downloadId: Long,
    folderName: String,
    isSeriesFolder: Boolean,
    seasonNumber: Int,
    episodeNumber: Int,
    relativePath: String,
  ) {
    val obj =
      JSONObject().apply {
        put("folderName", folderName)
        put("isSeriesFolder", isSeriesFolder)
        put("seasonNumber", seasonNumber)
        put("episodeNumber", episodeNumber)
        put("relativePath", relativePath)
      }
    context.applicationContext
      .getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
      .edit()
      .putString("dl_$downloadId", obj.toString())
      .apply()
  }

  private fun loadFolderMeta(context: Context, downloadId: Long): StoredFolderMeta? {
    val raw =
      context.applicationContext
        .getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        .getString("dl_$downloadId", null)
        ?: return null
    return runCatching {
      val o = JSONObject(raw)
      StoredFolderMeta(
        folderName = o.optString("folderName"),
        isSeriesFolder = o.optBoolean("isSeriesFolder", false),
        seasonNumber = o.optInt("seasonNumber", 0),
        episodeNumber = o.optInt("episodeNumber", 0),
        relativePath = o.optString("relativePath"),
      )
    }.getOrNull()
  }

  private fun extractFolderFromPath(relPath: String): String? {
    val parts = relPath.split("/").filter { it.isNotBlank() }
    val idx = parts.indexOfFirst { it.equals("DecanMovie", ignoreCase = true) }
    if (idx < 0) return null
    return parts.getOrNull(idx + 2)?.takeIf { !it.contains(".") }
  }

  internal fun extractEpisodeTag(raw: String): String? {
    val m = EPISODE_TAG_REGEX.find(raw) ?: return null
    val s = m.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val e = m.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    return "S${s.toString().padStart(2, '0')}E${e.toString().padStart(2, '0')}"
  }

  internal fun sanitizeFolderName(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9.\\-_ ]+"), " ")
      .replace(Regex("\\s+"), "_")
      .trim('_')
      .ifBlank { "Decan_Title" }
      .take(48)

  private fun sanitizeFilename(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9._\\-]+"), "_").trim('_').ifBlank { "decan_movie" }.take(64)
}
