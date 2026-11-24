package com.example.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Tiny in-memory image cache + loader (OkHttp -> Skia decode). */
object Images {
  private val client =
    OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

  private val cache =
    object : LinkedHashMap<String, ImageBitmap>(256, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 320
    }

  fun peek(url: String): ImageBitmap? = synchronized(cache) { cache[url] }

  suspend fun load(url: String): ImageBitmap? {
    peek(url)?.let { return it }
    return withContext(Dispatchers.IO) {
      runCatching {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 DecanMovieDesktop").build()
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@use null
          val bytes = resp.body?.bytes() ?: return@use null
          org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
      }.getOrNull()?.also { bmp -> synchronized(cache) { cache[url] = bmp } }
    }
  }
}

@Composable
fun RemoteImage(
  url: String?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  contentDescription: String? = null,
) {
  var bitmap by remember(url) { mutableStateOf(url?.let { Images.peek(it) }) }
  LaunchedEffect(url) {
    if (bitmap == null && !url.isNullOrBlank() && url.startsWith("http")) bitmap = Images.load(url)
  }
  val bmp = bitmap
  if (bmp != null) {
    Image(bmp, contentDescription, modifier, contentScale = contentScale)
  } else {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
  }
}
