package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
)

object DecanNotificationManager {
  const val CHANNEL_DOWNLOADS = "decan_downloads"
  const val CHANNEL_RELEASES = "decan_releases"
  const val CHANNEL_PICKS = "decan_picks"

  const val EXTRA_NAV_TAB = "decan_nav_tab"
  const val EXTRA_MOVIE_ID = "decan_movie_id"

  private const val PREFS_NAME = "decan_in_app_notifications"
  private const val KEY_LIST = "notifications_json"

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
  ) {
    ensureInitialized(context)
    val title = "Batch Download Queued ($fileCount files)"
    val msg = "$movieTitle is downloading to Downloads/DecanMovie."
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
      )
    }
  }

  fun notifyDownloadCompleted(
    context: Context,
    downloadTitle: String,
    systemEnabled: Boolean,
  ) {
    ensureInitialized(context)
    val title = "Download Complete"
    val msg = "$downloadTitle is ready to watch offline in Theater Player."
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
      )
    }
  }

  fun notifyReleaseAvailable(
    context: Context,
    tagName: String,
    releaseTitle: String,
    releaseUrl: String,
    systemEnabled: Boolean,
  ) {
    ensureInitialized(context)
    val title = "Decan Movie $tagName Available"
    val msg = "$releaseTitle is ready to download. Tap to view update details."
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
      )
    }
  }

  fun notifyTrendingPick(
    context: Context,
    movieTitle: String,
    movieId: String,
    subtitle: String,
    systemEnabled: Boolean,
  ) {
    ensureInitialized(context)
    val title = "Trending Spotlight: $movieTitle"
    recordInApp(
      context = context,
      item =
        InAppNotification(
          id = "pick_${movieId}_${System.currentTimeMillis() / 60000}",
          title = title,
          message = subtitle,
          category = NotificationCategory.TRENDING,
          timestampMs = System.currentTimeMillis(),
          targetMovieId = movieId,
        ),
    )
    if (systemEnabled) {
      postSystemNotification(
        context = context,
        channelId = CHANNEL_PICKS,
        notificationId = 5301,
        title = title,
        body = subtitle,
        targetMovieId = movieId,
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
  ) {
    if (!hasPostPermission(context)) return
    val intent =
      if (!externalUrl.isNullOrBlank() && targetTab == null) {
        Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
      } else {
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
          if (targetTab != null) putExtra(EXTRA_NAV_TAB, targetTab)
          if (targetMovieId != null) putExtra(EXTRA_MOVIE_ID, targetMovieId)
        }
      }
    val pendingIntent =
      PendingIntent.getActivity(
        context,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val builder =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    runCatching {
      NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
  }

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
