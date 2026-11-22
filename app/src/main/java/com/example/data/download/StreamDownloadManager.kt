package com.example.data.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.Immutable
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle

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

  val isVideo: Boolean
    get() =
      mimeType?.startsWith("video/") == true ||
        localUri?.endsWith(".mp4", ignoreCase = true) == true ||
        localUri?.endsWith(".mkv", ignoreCase = true) == true ||
        (!title.contains("(.srt)", ignoreCase = true) &&
          (title.contains("1080") ||
            title.contains("720") ||
            title.contains("480") ||
            title.contains("360")))
}

data class BatchEnqueueSummary(
  val queuedFiles: Int,
  val failedFiles: Int,
)

object StreamDownloadManager {
  fun enqueueStreamDownload(
    context: Context,
    movieTitle: String,
    stream: MovieBoxStream,
    season: Int = 0,
    episode: Int = 0,
    allowMetered: Boolean = true,
  ): Result<Long> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val safeName = sanitizeFilename(movieTitle)
      val epTag =
        if (season > 0 && episode > 0)
          "_S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        else ""
      val ext =
        when {
          stream.format.equals("MKV", ignoreCase = true) -> "mkv"
          else -> "mp4"
        }
      val fileName = "${safeName}${epTag}_${stream.quality.replace(" ", "_")}.$ext"
      val labelEp = if (season > 0 && episode > 0) " • S${season}E${episode}" else ""

      val request =
        DownloadManager.Request(Uri.parse(stream.url))
          .setTitle("$movieTitle$labelEp (${stream.quality})")
          .setDescription("Decan Movie • ${stream.format}")
          .setMimeType(if (ext == "mkv") "video/x-matroska" else "video/mp4")
          .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
          )
          .setAllowedOverMetered(allowMetered)
          .setAllowedOverRoaming(allowMetered)
          .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "DecanMovie/$fileName",
          )

      stream.headers.forEach { (k, v) -> request.addRequestHeader(k, v) }
      if (!stream.cookie.isNullOrBlank()) {
        request.addRequestHeader("Cookie", stream.cookie)
      }

      dm.enqueue(request)
    }

  fun enqueueSubtitleDownload(
    context: Context,
    movieTitle: String,
    subtitle: MovieBoxSubtitle,
    season: Int = 0,
    episode: Int = 0,
    allowMetered: Boolean = true,
  ): Result<Long> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val safeName = sanitizeFilename(movieTitle)
      val epTag =
        if (season > 0 && episode > 0)
          "_S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        else ""
      val langTag = sanitizeFilename(subtitle.language).take(24)
      val fileName = "${safeName}${epTag}_$langTag.srt"
      val labelEp = if (season > 0 && episode > 0) " • S${season}E${episode}" else ""

      val request =
        DownloadManager.Request(Uri.parse(subtitle.url))
          .setTitle("$movieTitle$labelEp • ${subtitle.language} (.srt)")
          .setDescription("Decan Movie • Subtitle track")
          .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
          )
          .setAllowedOverMetered(allowMetered)
          .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "DecanMovie/$fileName",
          )

      dm.enqueue(request)
    }

  fun enqueueMultiSelection(
    context: Context,
    movieTitle: String,
    streams: List<MovieBoxStream>,
    subtitles: List<MovieBoxSubtitle>,
    season: Int = 0,
    episode: Int = 0,
    allowMetered: Boolean = true,
  ): BatchEnqueueSummary {
    var queued = 0
    var failed = 0
    streams.forEach { st ->
      if (st.isPlayableDirect) {
        enqueueStreamDownload(context, movieTitle, st, season, episode, allowMetered)
          .onSuccess { queued++ }
          .onFailure { failed++ }
      }
    }
    subtitles.forEach { sub ->
      enqueueSubtitleDownload(context, movieTitle, sub, season, episode, allowMetered)
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
        parseCursorRow(dm, c)
      }
    }.getOrNull()

  fun queryAllDownloads(context: Context): List<DownloadTaskInfo> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val cursor = dm.query(DownloadManager.Query()) ?: return emptyList()
      cursor.use { c ->
        val items = mutableListOf<DownloadTaskInfo>()
        while (c.moveToNext()) {
          val task = parseCursorRow(dm, c) ?: continue
          items.add(task)
        }
        items.sortedByDescending { it.id }
      }
    }.getOrDefault(emptyList())

  private fun parseCursorRow(dm: DownloadManager, c: Cursor): DownloadTaskInfo? {
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
    )
  }

  fun removeDownload(context: Context, vararg ids: Long): Int =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      dm.remove(*ids)
    }.getOrDefault(0)

  private fun sanitizeFilename(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9._\\-]+"), "_").trim('_').ifBlank { "decan_movie" }.take(64)
}
