package com.example.data.cache

import android.app.ActivityManager
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * Manages a bounded, adaptive ~40 MB media & artwork cache budget rather than
 * allocating large fixed memory blocks. Splits disk budget cleanly between
 * episode/poster artwork (~24 MB) and network stream/subtitle metadata (~16 MB),
 * scaling safely down if device free storage is constrained.
 */
object MediaResourceCacheManager {
  const val TARGET_TOTAL_MEDIA_CACHE_BYTES = 40L * 1024L * 1024L
  private const val MIN_MEDIA_CACHE_BYTES = 8L * 1024L * 1024L
  private const val IMAGE_DISK_SHARE = 0.60
  private const val MAX_MEMORY_CACHE_PERCENT = 0.12

  @Volatile private var sharedImageLoader: ImageLoader? = null

  fun computeAdaptiveDiskBudgetBytes(cacheDir: File): Long {
    val usable = runCatching { cacheDir.usableSpace }.getOrDefault(TARGET_TOTAL_MEDIA_CACHE_BYTES)
    if (usable <= 0L) return MIN_MEDIA_CACHE_BYTES
    val safeCeiling = (usable / 8L).coerceAtLeast(MIN_MEDIA_CACHE_BYTES)
    return minOf(TARGET_TOTAL_MEDIA_CACHE_BYTES, safeCeiling)
  }

  fun computeMemoryCacheBytes(context: Context): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memClassMb = am?.memoryClass?.coerceAtLeast(32) ?: 64
    return (memClassMb.toLong() * 1024L * 1024L * MAX_MEMORY_CACHE_PERCENT).toLong()
      .coerceIn(4L * 1024L * 1024L, 24L * 1024L * 1024L)
  }

  fun installCoilImageLoader(context: Context): ImageLoader {
    sharedImageLoader?.let { return it }
    synchronized(this) {
      sharedImageLoader?.let { return it }
      val appCtx = context.applicationContext
      val rootCacheDir = File(appCtx.cacheDir, "decan_media_cache").apply { mkdirs() }
      val totalBudget = computeAdaptiveDiskBudgetBytes(appCtx.cacheDir)
      val imageDiskBytes = (totalBudget * IMAGE_DISK_SHARE).toLong()
      val httpDiskBytes = (totalBudget - imageDiskBytes).coerceAtLeast(2L * 1024L * 1024L)

      val okHttpClient =
        OkHttpClient.Builder()
          .cache(Cache(File(rootCacheDir, "http_cache"), httpDiskBytes))
          .connectTimeout(10, TimeUnit.SECONDS)
          .readTimeout(15, TimeUnit.SECONDS)
          .build()

      val loader =
        ImageLoader.Builder(appCtx)
          .okHttpClient(okHttpClient)
          .memoryCache {
            MemoryCache.Builder(appCtx)
              .maxSizeBytes(computeMemoryCacheBytes(appCtx).toInt())
              .build()
          }
          .diskCache {
            DiskCache.Builder()
              .directory(File(rootCacheDir, "image_cache"))
              .maxSizeBytes(imageDiskBytes)
              .build()
          }
          .crossfade(true)
          .build()

      Coil.setImageLoader(loader)
      sharedImageLoader = loader
      return loader
    }
  }
}
