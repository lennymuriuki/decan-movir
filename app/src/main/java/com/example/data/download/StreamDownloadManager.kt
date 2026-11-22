package com.example.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle

object StreamDownloadManager {
  fun enqueueStreamDownload(
    context: Context,
    movieTitle: String,
    stream: MovieBoxStream,
    season: Int = 0,
    episode: Int = 0,
  ): Result<Long> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val safeName = sanitizeFilename(movieTitle)
      val epTag =
        if (season > 0 && episode > 0) "_S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        else ""
      val ext =
        when {
          stream.format.equals("MKV", ignoreCase = true) -> "mkv"
          else -> "mp4"
        }
      val fileName = "${safeName}${epTag}_${stream.quality.replace(" ", "_")}.$ext"

      val request =
        DownloadManager.Request(Uri.parse(stream.url))
          .setTitle("$movieTitle (${stream.quality})")
          .setDescription("Downloading ${stream.format} via Decan Movie")
          .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
          )
          .setAllowedOverMetered(true)
          .setAllowedOverRoaming(true)
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
  ): Result<Long> =
    runCatching {
      val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val safeName = sanitizeFilename(movieTitle)
      val epTag =
        if (season > 0 && episode > 0) "_S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        else ""
      val langTag = sanitizeFilename(subtitle.language).take(24)
      val fileName = "${safeName}${epTag}_$langTag.srt"

      val request =
        DownloadManager.Request(Uri.parse(subtitle.url))
          .setTitle("$movieTitle • ${subtitle.language} Subtitles")
          .setDescription("Downloading .srt subtitle track")
          .setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
          )
          .setAllowedOverMetered(true)
          .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS,
            "DecanMovie/$fileName",
          )

      dm.enqueue(request)
    }

  private fun sanitizeFilename(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9._\\-]+"), "_").trim('_').ifBlank { "decan_movie" }.take(64)
}
