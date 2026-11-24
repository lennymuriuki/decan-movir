package com.example.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

class DownloadTask(
  val id: String,
  val title: String,
  val fileName: String,
  val target: File,
  val stream: MovieBoxStream?,
  val subtitle: MovieBoxSubtitle?,
) {
  var status by mutableStateOf(DownloadStatus.QUEUED)
  var bytes by mutableStateOf(0L)
  var total by mutableStateOf(0L)
  var error by mutableStateOf<String?>(null)
  internal var job: Job? = null

  val progress: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Downloads videos (and optional .srt files) into the user's download folder, 2 at a time. */
class DownloadManager(private val store: LocalStore) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val gate = Semaphore(2)
  private val client =
    OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

  val tasks = mutableStateListOf<DownloadTask>()

  fun downloadDir(): File =
    store.settings.value.downloadDir.takeIf { it.isNotBlank() }?.let(::File) ?: AppPaths.defaultDownloadDir

  fun enqueue(title: String, stream: MovieBoxStream, subtitle: MovieBoxSubtitle? = null, label: String = "") {
    val base = sanitize(listOf(title, label, stream.resolutionTag.ifBlank { stream.quality }).filter { it.isNotBlank() }.joinToString(" - "))
    val dir = downloadDir().apply { mkdirs() }
    val video = uniqueFile(dir, base, "mp4")
    enqueueInternal(DownloadTask("${System.nanoTime()}", title, video.name, video, stream, null))
    if (subtitle != null) {
      val srt = File(dir, video.nameWithoutExtension + ".srt")
      enqueueInternal(DownloadTask("${System.nanoTime()}", "$title (subtitles)", srt.name, srt, null, subtitle))
    }
  }

  private fun enqueueInternal(task: DownloadTask) {
    tasks.add(0, task)
    task.job = scope.launch { gate.withPermit { run(task) } }
  }

  private suspend fun run(task: DownloadTask) {
    task.status = DownloadStatus.RUNNING
    val part = File(task.target.parentFile, task.target.name + ".part")
    try {
      withContext(Dispatchers.IO) {
        val url = task.stream?.url ?: task.subtitle?.url ?: error("Nothing to download")
        val rb = Request.Builder().url(url)
        task.stream?.headers?.forEach { (k, v) -> rb.header(k, v) }
        task.stream?.cookie?.takeIf { it.isNotBlank() }?.let { rb.header("Cookie", it) }
        client.newCall(rb.build()).execute().use { resp ->
          if (!resp.isSuccessful) error("Server returned ${resp.code}")
          val body = resp.body ?: error("Empty response")
          task.total = body.contentLength().coerceAtLeast(0L)
          part.parentFile?.mkdirs()
          part.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            body.byteStream().use { input ->
              while (true) {
                ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                task.bytes += n
              }
            }
          }
        }
        if (task.target.exists()) task.target.delete()
        if (!part.renameTo(task.target)) error("Could not save file")
      }
      task.status = DownloadStatus.DONE
    } catch (ce: CancellationException) {
      part.delete()
      task.status = DownloadStatus.CANCELLED
    } catch (t: Throwable) {
      part.delete()
      task.error = t.message ?: "Download failed"
      task.status = DownloadStatus.FAILED
    }
  }

  fun cancel(task: DownloadTask) {
    task.job?.cancel()
    if (task.status == DownloadStatus.QUEUED) task.status = DownloadStatus.CANCELLED
  }

  fun remove(task: DownloadTask, deleteFile: Boolean = false) {
    cancel(task)
    if (deleteFile) runCatching { task.target.delete() }
    tasks.remove(task)
  }

  /** Video files already in the download folder (from previous sessions). */
  fun listLocalVideos(): List<File> =
    downloadDir().listFiles { f -> f.isFile && f.extension.lowercase() in VIDEO_EXT }
      ?.sortedByDescending { it.lastModified() }
      .orEmpty()

  companion object {
    val VIDEO_EXT = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi")
    fun sanitize(raw: String): String = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(120).ifBlank { "video" }

    private fun uniqueFile(dir: File, base: String, ext: String): File {
      var f = File(dir, "$base.$ext")
      var i = 1
      while (f.exists()) f = File(dir, "$base ($i).$ext").also { i++ }
      return f
    }
  }
}
