package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class NotificationCategory(val label: String) {
  DOWNLOAD("Downloads"),
  RELEASE("App Update"),
  TRENDING("Cinema Pick"),
}

@Immutable
data class InAppNotification(
  val id: String,
  val title: String,
  val message: String,
  val category: NotificationCategory,
  val timestampMs: Long,
  val isRead: Boolean = false,
  val targetTab: String? = null,
  val targetMovieId: String? = null,
  val externalUrl: String? = null,
  val imageUrl: String? = null,
  val actionLabel: String? = null,
)

object DecanNotificationManager {
  const val CHANNEL_DOWNLOADS = "decan_downloads"
  const val CHANNEL_RELEASES = "decan_releases"
  const val CHANNEL_PICKS = "decan_picks"

  const val EXTRA_NAV_TAB = "decan_nav_tab"
  const val EXTRA_MOVIE_ID = "decan_movie_id"

  private const val PREFS_NAME = "decan_in_app_notifications"
  private const val KEY_LIST = "notifications_json"

  private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val bitmapClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(4, TimeUnit.SECONDS)
      .build()
  }

  private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
  val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

  private var initialized = false

  fun ensureInitialized(context: Context) {
    createChannels(context)
    if (!initialized) {
      initialized = true
      _notifications.value = loadSaved(context)
    }
  }

  fun createChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channels =
      listOf(
        NotificationChannel(
          CHANNEL_DOWNLOADS,
          "Downloads & Batch Queue",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Notifies when movies, series episodes, or subtitles finish downloading."
        },
        NotificationChannel(
          CHANNEL_RELEASES,
          "App Updates",
          NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
          description = "Alerts when a new Decan Movie update is ready."
        },
        NotificationChannel(
          CHANNEL_PICKS,
          "Trending Cinema & Recommendations",
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "Top chart spotlights, anime drops, and personalized picks."
        },
      )
    nm.createNotificationChannels(channels)
  }

  fun hasPostPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  fun notifyBatchQueued(
    context: Context,
    movieTitle: String,
    fileCount: Int,
    systemEnabled: Boolean,
    imageUrl: String? = null,
    targetMovieId: String? = null,
  ) {
    ensureInitialized(context)
    val title = "Queued $fileCount File${if (fileCount == 1) "" else "s"} • $movieTitle"
    val msg = "Saving to organized DecanMovie folders. Open Downloads folder to track progress."
    recordInApp(
      context = context,
      item =
        InAppNotification(
          id = "batch_${System.currentTimeMillis()}",
          title = title,
          message = msg,
          category = NotificationCategory.DOWNLOAD,
          timestampMs = System.currentTimeMillis(),
          targetTab = "downloads",
          targetMovieId = targetMovieId,
          imageUrl = imageUrl,
          actionLabel = "Open Folder",
        ),
    )
    if (systemEnabled) {
      postSystemNotification(
        context = context,
        channelId = CHANNEL_DOWNLOADS,
        notificationId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
        title = title,
        body = msg,
        targetTab = "downloads",
        targetMovieId = targetMovieId,
        imageUrl = imageUrl,
        ctaLabel = "Open Folder",
      )
    }
  }

  fun notifyDownloadCompleted(
    context: Context,
    downloadTitle: String,
    systemEnabled: Boolean,
    imageUrl: String? = null,
  ) {
    ensureInitialized(context)
    val title = "Ready Offline: $downloadTitle"
    val msg = "Saved to your DecanMovie folder with matched CC support. Tap to watch now!"
    recordInApp(
      context = context,
      item =
        InAppNotification(
          id = "dl_done_${downloadTitle.hashCode()}_${System.currentTimeMillis() / 1000}",
          title = title,
          message = msg,
          category = NotificationCategory.DOWNLOAD,
          timestampMs = System.currentTimeMillis(),
          targetTab = "downloads",
          imageUrl = imageUrl,
          actionLabel = "Watch Offline",
        ),
    )
    if (systemEnabled) {
      postSystemNotification(
        context = context,
        channelId = CHANNEL_DOWNLOADS,
        notificationId = 3100 + (downloadTitle.hashCode() and 0xFF),
        title = title,
        body = msg,
        targetTab = "downloads",
        imageUrl = imageUrl,
        ctaLabel = "Watch Offline",
      )
    }
  }

  fun notifyReleaseAvailable(
    context: Context,
    tagName: String,
    releaseTitle: String,
    releaseUrl: String,
    systemEnabled: Boolean,
    imageUrl: String? = null,
  ) {
    ensureInitialized(context)
    val title = "Decan Movie $tagName Update Ready"
    val msg = "$releaseTitle is live — faster search, anime genres & folder downloads. Tap to update."
    recordInApp(
      context = context,
      item =
        InAppNotification(
          id = "rel_$tagName",
          title = title,
          message = msg,
          category = NotificationCategory.RELEASE,
          timestampMs = System.currentTimeMillis(),
          targetTab = "settings",
          externalUrl = releaseUrl,
          imageUrl = imageUrl,
          actionLabel = "Update Now",
        ),
    )
    if (systemEnabled) {
      postSystemNotification(
        context = context,
        channelId = CHANNEL_RELEASES,
        notificationId = 4201,
        title = title,
        body = msg,
        targetTab = "settings",
        externalUrl = releaseUrl,
        imageUrl = imageUrl,
        ctaLabel = "Update App",
      )
    }
  }

  fun notifyTrendingPick(
    context: Context,
    movieTitle: String,
    movieId: String,
    subtitle: String,
    systemEnabled: Boolean,
    imageUrl: String? = null,
  ) {
    ensureInitialized(context)
    val title = "Now Streaming: $movieTitle"
    val body =
      subtitle.ifBlank { "Top trending pick on Decan Movie — tap to watch in HD Theater Player." }
    recordInApp(
      context = context,
      item =
        InAppNotification(
          id = "pick_${movieId}_${System.currentTimeMillis() / 60000}",
          title = title,
          message = body,
          category = NotificationCategory.TRENDING,
          timestampMs = System.currentTimeMillis(),
          targetMovieId = movieId.takeIf { it.isNotBlank() },
          imageUrl = imageUrl,
          actionLabel = "Watch Now",
        ),
    )
    if (systemEnabled) {
      postSystemNotification(
        context = context,
        channelId = CHANNEL_PICKS,
        notificationId = 5301 + (movieId.hashCode() and 0xFF),
        title = title,
        body = body,
        targetMovieId = movieId.takeIf { it.isNotBlank() },
        imageUrl = imageUrl,
        ctaLabel = "Watch Now",
      )
    }
  }

  fun markAllRead(context: Context) {
    _notifications.update { list -> list.map { it.copy(isRead = true) } }
    saveList(context, _notifications.value)
  }

  fun markRead(context: Context, id: String) {
    _notifications.update { list ->
      list.map { if (it.id == id) it.copy(isRead = true) else it }
    }
    saveList(context, _notifications.value)
  }

  fun clearAll(context: Context) {
    _notifications.value = emptyList()
    saveList(context, emptyList())
  }

  private fun postSystemNotification(
    context: Context,
    channelId: String,
    notificationId: Int,
    title: String,
    body: String,
    targetTab: String? = null,
    targetMovieId: String? = null,
    externalUrl: String? = null,
    imageUrl: String? = null,
    ctaLabel: String = "Open",
  ) {
    if (!hasPostPermission(context)) return
    val appCtx = context.applicationContext
    notificationScope.launch {
      val appLogoBmp = loadAppLogoBitmap(appCtx)
      val posterBmp = if (!imageUrl.isNullOrBlank()) fetchBitmap(imageUrl) else null

      val intent =
        if (!externalUrl.isNullOrBlank() && targetTab == null && targetMovieId == null) {
          Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
        } else {
          Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!targetTab.isNullOrBlank()) putExtra(EXTRA_NAV_TAB, targetTab)
            if (!targetMovieId.isNullOrBlank()) putExtra(EXTRA_MOVIE_ID, targetMovieId)
          }
        }
      val pendingIntent =
        PendingIntent.getActivity(
          appCtx,
          notificationId,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val builder =
        NotificationCompat.Builder(appCtx, channelId)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setLargeIcon(posterBmp ?: appLogoBmp)
          .setContentTitle(title)
          .setContentText(body)
          .setAutoCancel(true)
          .setContentIntent(pendingIntent)
          .addAction(0, ctaLabel, pendingIntent)
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)

      if (posterBmp != null) {
        builder.setStyle(
          NotificationCompat.BigPictureStyle()
            .bigPicture(posterBmp)
            .bigLargeIcon(appLogoBmp)
            .setSummaryText(body)
        )
      } else {
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
      }

      runCatching {
        NotificationManagerCompat.from(appCtx).notify(notificationId, builder.build())
      }
    }
  }

  private fun loadAppLogoBitmap(context: Context): Bitmap? =
    runCatching {
      val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
      if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return@runCatching drawable.bitmap
      }
      val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
      val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
      val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      bmp
    }.getOrNull()

  private fun fetchBitmap(url: String): Bitmap? =
    runCatching {
      val req =
        Request.Builder()
          .url(url)
          .addHeader("User-Agent", "DecanMovie-Android")
          .build()
      bitmapClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val bytes = resp.body?.bytes() ?: return@use null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }
    }.getOrNull()

  private fun recordInApp(context: Context, item: InAppNotification) {
    _notifications.update { current ->
      (listOf(item) + current.filterNot { it.id == item.id }).take(30)
    }
    saveList(context, _notifications.value)
  }

  private fun loadSaved(context: Context): List<InAppNotification> {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val o = arr.optJSONObject(i) ?: continue
          val catName = o.optString("category", NotificationCategory.TRENDING.name)
          val cat =
            NotificationCategory.entries.firstOrNull { it.name == catName }
              ?: NotificationCategory.TRENDING
          add(
            InAppNotification(
              id = o.optString("id"),
              title = o.optString("title"),
              message = o.optString("message"),
              category = cat,
              timestampMs = o.optLong("timestampMs", 0L),
              isRead = o.optBoolean("isRead", false),
              targetTab = o.optString("targetTab").takeIf { it.isNotBlank() },
              targetMovieId = o.optString("targetMovieId").takeIf { it.isNotBlank() },
              externalUrl = o.optString("externalUrl").takeIf { it.isNotBlank() },
              imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
              actionLabel = o.optString("actionLabel").takeIf { it.isNotBlank() },
            )
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  private fun saveList(context: Context, list: List<InAppNotification>) {
    val arr = JSONArray()
    list.forEach { n ->
      arr.put(
        JSONObject().apply {
          put("id", n.id)
          put("title", n.title)
          put("message", n.message)
          put("category", n.category.name)
          put("timestampMs", n.timestampMs)
          put("isRead", n.isRead)
          put("targetTab", n.targetTab.orEmpty())
          put("targetMovieId", n.targetMovieId.orEmpty())
          put("externalUrl", n.externalUrl.orEmpty())
          put("imageUrl", n.imageUrl.orEmpty())
          put("actionLabel", n.actionLabel.orEmpty())
        }
      )
    }
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_LIST, arr.toString())
      .apply()
  }
}
