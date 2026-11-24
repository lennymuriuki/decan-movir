package com.decanmovie.desktop.data.download

import com.decanmovie.desktop.data.remote.moviebox.MovieBoxStream
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSubtitle
import com.decanmovie.desktop.model.DownloadTaskState
import com.decanmovie.desktop.model.DownloadTaskStatus
import com.decanmovie.desktop.model.LocalMediaItem
import com.decanmovie.desktop.model.Movie
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

class DesktopDownloadManager(
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .retryOnConnectionFailure(true)
      .build(),
  private val onDownloadCompleted: (DownloadTaskState) -> Unit = {},
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val concurrencyLimiter = Semaphore(2)
  private val jobs = ConcurrentHashMap<String, Job>()

  private val _tasks = MutableStateFlow<List<DownloadTaskState>>(emptyList())
  val tasks: StateFlow<List<DownloadTaskState>> = _tasks.asStateFlow()

  private val _localLibrary = MutableStateFlow<List<LocalMediaItem>>(emptyList())
  val localLibrary: StateFlow<List<LocalMediaItem>> = _localLibrary.asStateFlow()

  fun enqueueVideoDownload(
    rootDir: String,
    movie: Movie,
    stream: MovieBoxStream,
    season: Int = 0,
    episode: Int = 0,
    subtitle: MovieBoxSubtitle? = null,
  ): Result<String> {
    if (!stream.isPlayableDirect) {
      return Result.failure(IllegalArgumentException("Adaptive DASH streams cannot be saved directly."))
    }
    val destFolder = buildStructuredFolder(rootDir, movie, season, episode)
    destFolder.mkdirs()

    val safeTitle = sanitizeFileName(movie.title)
    val ext = guessExtension(stream.format, stream.url)
    val qualitySuffix = stream.quality.replace(" ", "_")
    val baseName =
      if (season > 0 && episode > 0) {
        "${safeTitle}_S%02dE%02d_%s".format(season, episode, qualitySuffix)
      } else {
        "${safeTitle}_$qualitySuffix"
      }
    val destFile = File(destFolder, "$baseName.$ext")
    val epBadge = if (season > 0 && episode > 0) "S%02dE%02d • ".format(season, episode) else ""

    val taskId = UUID.randomUUID().toString()
    val state =
      DownloadTaskState(
        id = taskId,
        title = movie.title,
        displayLabel = "$epBadge${stream.quality} ($ext)",
        url = stream.url,
        destinationPath = destFile.absolutePath,
        folderPath = destFolder.absolutePath,
        isSubtitle = false,
        season = season,
        episode = episode,
        status = DownloadTaskStatus.QUEUED,
      )
    addOrUpdateTask(state)
    startDownloadJob(state, stream.headers, rootDir)

    if (subtitle != null) {
      enqueueSubtitleDownload(
        rootDir = rootDir,
        movie = movie,
        subtitle = subtitle,
        season = season,
        episode = episode,
      )
    }
    return Result.success(taskId)
  }

  fun enqueueSubtitleDownload(
    rootDir: String,
    movie: Movie,
    subtitle: MovieBoxSubtitle,
    season: Int = 0,
    episode: Int = 0,
  ): Result<String> {
    val destFolder = buildStructuredFolder(rootDir, movie, season, episode)
    destFolder.mkdirs()

    val safeTitle = sanitizeFileName(movie.title)
    val langTag = sanitizeFileName(subtitle.code.ifBlank { subtitle.language }).take(16)
    val baseName =
      if (season > 0 && episode > 0) {
        "${safeTitle}_S%02dE%02d_$langTag.srt".format(season, episode)
      } else {
        "${safeTitle}_$langTag.srt"
      }
    val destFile = File(destFolder, baseName)
    val epBadge = if (season > 0 && episode > 0) "S%02dE%02d • ".format(season, episode) else ""

    val taskId = UUID.randomUUID().toString()
    val state =
      DownloadTaskState(
        id = taskId,
        title = movie.title,
        displayLabel = "$epBadge${subtitle.language} (.srt)",
        url = subtitle.url,
        destinationPath = destFile.absolutePath,
        folderPath = destFolder.absolutePath,
        isSubtitle = true,
        season = season,
        episode = episode,
        status = DownloadTaskStatus.QUEUED,
      )
    addOrUpdateTask(state)
    startDownloadJob(state, emptyMap(), rootDir)
    return Result.success(taskId)
  }

  fun pauseDownload(taskId: String) {
    jobs.remove(taskId)?.cancel()
    updateTask(taskId) { it.copy(status = DownloadTaskStatus.PAUSED) }
  }

  fun resumeDownload(taskId: String, rootDir: String) {
    val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
    if (task.status == DownloadTaskStatus.DOWNLOADING || task.status == DownloadTaskStatus.COMPLETED) {
      return
    }
    val updated = task.copy(status = DownloadTaskStatus.QUEUED, errorMessage = null)
    addOrUpdateTask(updated)
    startDownloadJob(updated, emptyMap(), rootDir)
  }

  fun cancelDownload(taskId: String) {
    jobs.remove(taskId)?.cancel()
    val task = _tasks.value.firstOrNull { it.id == taskId }
    if (task != null) {
      runCatching { File("${task.destinationPath}.part").delete() }
    }
    updateTask(taskId) { it.copy(status = DownloadTaskStatus.CANCELED) }
  }

  fun removeTask(taskId: String) {
    jobs.remove(taskId)?.cancel()
    _tasks.value = _tasks.value.filterNot { it.id == taskId }
  }

  fun refreshLocalLibrary(rootDir: String) {
    scope.launch {
      _localLibrary.value = scanLocalLibrary(rootDir)
    }
  }

  fun shutdown() {
    jobs.values.forEach { it.cancel() }
    jobs.clear()
  }

  private fun startDownloadJob(
    initial: DownloadTaskState,
    headers: Map<String, String>,
    rootDir: String,
  ) {
    val job =
      scope.launch {
        concurrencyLimiter.withPermit {
          try {
            val destFile = File(initial.destinationPath)
            val partFile = File("${initial.destinationPath}.part")
            destFile.parentFile?.mkdirs()

            val existingBytes = if (partFile.exists()) partFile.length().coerceAtLeast(0L) else 0L
            updateTask(initial.id) {
              it.copy(status = DownloadTaskStatus.DOWNLOADING, bytesDownloaded = existingBytes)
            }

            val reqBuilder =
              Request.Builder()
                .url(initial.url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            if (existingBytes > 0L) {
              reqBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
              if (!resp.isSuccessful && resp.code != 206) {
                throw IllegalStateException("Download failed (HTTP ${resp.code})")
              }
              val body = resp.body ?: throw IllegalStateException("Empty download response")
              val appendMode = resp.code == 206 && existingBytes > 0L
              val startOffset = if (appendMode) existingBytes else 0L
              val contentLen = body.contentLength()
              val totalLen =
                if (contentLen > 0L) startOffset + contentLen else -1L

              var downloaded = startOffset
              var lastEmitMs = 0L
              val buffer = ByteArray(32 * 1024)

              RandomAccessFile(partFile, "rw").use { raf ->
                if (appendMode) raf.seek(startOffset) else raf.setLength(0L)
                body.byteStream().use { input ->
                  while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    raf.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 200L) {
                      lastEmitMs = now
                      updateTask(initial.id) {
                        it.copy(
                          bytesDownloaded = downloaded,
                          totalBytes = totalLen.coerceAtLeast(downloaded),
                          status = DownloadTaskStatus.DOWNLOADING,
                        )
                      }
                    }
                  }
                }
              }

              if (destFile.exists()) destFile.delete()
              if (!partFile.renameTo(destFile)) {
                partFile.copyTo(destFile, overwrite = true)
                partFile.delete()
              }

              val doneState =
                initial.copy(
                  bytesDownloaded = destFile.length(),
                  totalBytes = destFile.length(),
                  status = DownloadTaskStatus.COMPLETED,
                )
              addOrUpdateTask(doneState)
              _localLibrary.value = scanLocalLibrary(rootDir)
              if (!doneState.isSubtitle) {
                onDownloadCompleted(doneState)
              }
            }
          } catch (e: CancellationException) {
            throw e
          } catch (t: Throwable) {
            updateTask(initial.id) {
              it.copy(
                status = DownloadTaskStatus.FAILED,
                errorMessage = t.message ?: "Download failed",
              )
            }
          } finally {
            jobs.remove(initial.id)
          }
        }
      }
    jobs[initial.id] = job
  }

  private fun addOrUpdateTask(task: DownloadTaskState) {
    val current = _tasks.value.toMutableList()
    val idx = current.indexOfFirst { it.id == task.id }
    if (idx >= 0) current[idx] = task else current.add(0, task)
    _tasks.value = current
  }

  private fun updateTask(id: String, transform: (DownloadTaskState) -> DownloadTaskState) {
    _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
  }

  companion object {
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "m4v", "mov", "webm", "avi", "ts")

    fun categoryFolderName(movie: Movie): String =
      when {
        movie.mediaType.equals("Anime", ignoreCase = true) -> "Anime"
        movie.mediaType.equals("Drama", ignoreCase = true) -> "Dramas"
        movie.mediaType.equals("Series", ignoreCase = true) -> "Series"
        else -> "Movies"
      }

    fun buildStructuredFolder(
      rootDir: String,
      movie: Movie,
      season: Int = 0,
      episode: Int = 0,
    ): File {
      val root = File(rootDir)
      val cat = categoryFolderName(movie)
      val safeTitle = sanitizeFileName(movie.title)
      val base = File(File(root, cat), safeTitle)
      return if (season > 0 && episode > 0) {
        File(base, "Season %02d".format(season))
      } else {
        base
      }
    }

    fun sanitizeFileName(raw: String): String =
      raw.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim().ifBlank { "DecanMedia" }.take(72)

    private fun guessExtension(format: String, url: String): String {
      val lowerUrl = url.lowercase().substringBefore('?')
      return when {
        lowerUrl.endsWith(".mkv") || format.equals("MKV", true) -> "mkv"
        lowerUrl.endsWith(".webm") || format.equals("WEBM", true) -> "webm"
        lowerUrl.endsWith(".m4v") -> "m4v"
        lowerUrl.endsWith(".mov") -> "mov"
        else -> "mp4"
      }
    }

    fun scanLocalLibrary(rootDir: String): List<LocalMediaItem> {
      val root = File(rootDir)
      if (!root.exists() || !root.isDirectory) return emptyList()
      val items = mutableListOf<LocalMediaItem>()
      root.walkTopDown()
        .maxDepth(5)
        .filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
        .forEach { file ->
          val rel = file.relativeTo(root).invariantSeparatorsPath
          val parts = rel.split('/')
          val category = if (parts.size >= 2) parts[0] else "Local"
          val titleFolder = if (parts.size >= 3) parts[1] else file.nameWithoutExtension
          val seasonFolder =
            if (parts.size >= 4 && parts[2].startsWith("Season", ignoreCase = true)) parts[2]
            else null
          val matchingSub = findSiblingSubtitle(file)
          items.add(
            LocalMediaItem(
              name = file.nameWithoutExtension.replace('_', ' '),
              filePath = file.absolutePath,
              sizeBytes = file.length(),
              category = category,
              titleFolder = titleFolder.replace('_', ' '),
              seasonFolder = seasonFolder,
              subtitlePath = matchingSub?.absolutePath,
              modifiedAtMs = file.lastModified(),
            )
          )
        }
      return items.sortedByDescending { it.modifiedAtMs }
    }

    fun findSiblingSubtitle(videoFile: File): File? {
      val parent = videoFile.parentFile ?: return null
      val srts = parent.listFiles { f -> f.isFile && f.extension.equals("srt", ignoreCase = true) }
        ?: return null
      if (srts.isEmpty()) return null
      val episodeMarker = Regex("S\\d{2}E\\d{2}", RegexOption.IGNORE_CASE).find(videoFile.name)?.value
      if (episodeMarker != null) {
        srts.firstOrNull { it.name.contains(episodeMarker, ignoreCase = true) }?.let { return it }
      }
      val stem = videoFile.nameWithoutExtension.substringBeforeLast('_')
      return srts.firstOrNull { it.name.startsWith(stem, ignoreCase = true) } ?: srts.firstOrNull()
    }
  }
}
