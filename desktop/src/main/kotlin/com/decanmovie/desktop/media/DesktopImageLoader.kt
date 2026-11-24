package com.decanmovie.desktop.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.decanmovie.desktop.platform.WindowsAppPaths
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image

object DesktopImageLoader {
  private const val MAX_DISK_CACHE_BYTES = 40L * 1024L * 1024L
  private const val MAX_IN_MEMORY_IMAGES = 140

  private val cacheDir: File by lazy {
    File(WindowsAppPaths.cacheDirectory(), "images").apply { mkdirs() }
  }

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .connectionPool(ConnectionPool(6, 3, TimeUnit.MINUTES))
      .followRedirects(true)
      .build()
  }

  private val fetchGate = Semaphore(6)

  private val memoryCache: MutableMap<String, ImageBitmap> =
    Collections.synchronizedMap(
      object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
          return size > MAX_IN_MEMORY_IMAGES
        }
      }
    )

  suspend fun loadBitmap(url: String): ImageBitmap? {
    val cleanUrl = url.trim()
    if (cleanUrl.isBlank()) return null

    memoryCache[cleanUrl]?.let { return it }

    return withContext(Dispatchers.IO) {
      val diskFile = cacheFileFor(cleanUrl)
      if (diskFile.exists() && diskFile.length() > 128L) {
        decodeToImageBitmap(diskFile.readBytes())?.let { bmp ->
          diskFile.setLastModified(System.currentTimeMillis())
          memoryCache[cleanUrl] = bmp
          return@withContext bmp
        }
      }

      fetchGate.withPermit {
        runCatching {
          val req =
            Request.Builder()
              .url(cleanUrl)
              .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
              .build()
          client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val bytes = resp.body?.bytes() ?: return@use null
            if (bytes.size > 8 * 1024 * 1024) return@use null
            val bmp = decodeToImageBitmap(bytes) ?: return@use null
            runCatching {
              cacheDir.mkdirs()
              diskFile.writeBytes(bytes)
              trimDiskCacheIfNeeded()
            }
            memoryCache[cleanUrl] = bmp
            bmp
          }
        }.getOrNull()
      }
    }
  }

  fun clearAllCaches() {
    memoryCache.clear()
    runCatching {
      cacheDir.listFiles()?.forEach { it.delete() }
    }
  }

  fun cacheUsageBytes(): Long =
    cacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

  private fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching {
      Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()

  private fun cacheFileFor(url: String): File {
    val hash =
      MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it) }
    return File(cacheDir, "$hash.img")
  }

  private fun trimDiskCacheIfNeeded() {
    val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
    var total = files.sumOf { it.length() }
    if (total <= MAX_DISK_CACHE_BYTES) return
    val sorted = files.sortedBy { it.lastModified() }
    for (f in sorted) {
      if (total <= (MAX_DISK_CACHE_BYTES * 3L) / 4L) break
      val len = f.length()
      if (f.delete()) total -= len
    }
  }
}
