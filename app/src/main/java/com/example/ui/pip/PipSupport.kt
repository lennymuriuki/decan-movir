package com.example.ui.pip

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer

object PipSupport {
  const val ACTION_PIP_CONTROL = "com.example.decanmovie.PIP_CONTROL"
  const val EXTRA_CONTROL_TYPE = "control_type"
  const val CONTROL_PLAY = 1
  const val CONTROL_PAUSE = 2
  const val CONTROL_REPLAY_10 = 3
  const val CONTROL_FORWARD_10 = 4

  fun findComponentActivity(context: Context): ComponentActivity? {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
      if (ctx is ComponentActivity) return ctx
      ctx = ctx.baseContext
    }
    return ctx as? ComponentActivity
  }

  fun isPipSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    return runCatching {
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }.getOrDefault(false)
  }

  fun enterPictureInPicture(
    context: Context,
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    sourceRect: Rect?,
    autoEnterEnabled: Boolean,
    includeSeekActions: Boolean = true,
  ): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val activity = findComponentActivity(context) ?: return false
    if (!isPipSupported(activity)) return false
    return runCatching {
      val params =
        buildPipParams(
          context = activity,
          isPlaying = isPlaying,
          videoWidth = videoWidth,
          videoHeight = videoHeight,
          sourceRect = sourceRect,
          autoEnterEnabled = autoEnterEnabled,
          includeSeekActions = includeSeekActions,
        )
      activity.enterPictureInPictureMode(params)
    }.getOrDefault(false)
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun buildPipParams(
    context: Context,
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    sourceRect: Rect?,
    autoEnterEnabled: Boolean,
    includeSeekActions: Boolean = true,
  ): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
    val safeRatio = computeSafeAspectRatio(videoWidth, videoHeight)
    builder.setAspectRatio(safeRatio)

    if (sourceRect != null && !sourceRect.isEmpty && sourceRect.width() > 0 && sourceRect.height() > 0) {
      builder.setSourceRectHint(sourceRect)
    }

    val actions = buildList {
      if (includeSeekActions) {
        add(
          RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_media_rew),
            "Rewind 10s",
            "Rewind 10 seconds",
            createControlPendingIntent(context, CONTROL_REPLAY_10),
          )
        )
      }
      add(
        RemoteAction(
          Icon.createWithResource(
            context,
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
          ),
          if (isPlaying) "Pause" else "Play",
          if (isPlaying) "Pause playback" else "Resume playback",
          createControlPendingIntent(context, if (isPlaying) CONTROL_PAUSE else CONTROL_PLAY),
        )
      )
      if (includeSeekActions) {
        add(
          RemoteAction(
            Icon.createWithResource(context, android.R.drawable.ic_media_ff),
            "Forward 10s",
            "Forward 10 seconds",
            createControlPendingIntent(context, CONTROL_FORWARD_10),
          )
        )
      }
    }
    builder.setActions(actions)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      builder.setAutoEnterEnabled(autoEnterEnabled)
      builder.setSeamlessResizeEnabled(true)
    }

    return builder.build()
  }

  internal fun computeSafeAspectRatio(videoWidth: Int, videoHeight: Int): Rational {
    val rawRatio =
      if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat()
      else 16f / 9f
    // Android enforces aspect ratio between 1:2.39 and 2.39:1
    val clamped = rawRatio.coerceIn(0.45f, 2.35f)
    return Rational((clamped * 1000f).toInt().coerceAtLeast(450), 1000)
  }

  private fun createControlPendingIntent(context: Context, controlType: Int): PendingIntent {
    val intent =
      Intent(ACTION_PIP_CONTROL)
        .setPackage(context.packageName)
        .putExtra(EXTRA_CONTROL_TYPE, controlType)
    return PendingIntent.getBroadcast(
      context,
      controlType,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}

@Composable
fun rememberIsInPictureInPictureMode(): Boolean {
  val context = LocalContext.current
  val activity = remember(context) { PipSupport.findComponentActivity(context) }
  var isInPip by remember(activity) {
    mutableStateOf(
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity?.isInPictureInPictureMode == true
    )
  }

  DisposableEffect(activity) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      onDispose {}
    } else {
      isInPip = activity.isInPictureInPictureMode
      val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
        isInPip = info.isInPictureInPictureMode
      }
      activity.addOnPictureInPictureModeChangedListener(listener)
      onDispose {
        activity.removeOnPictureInPictureModeChangedListener(listener)
      }
    }
  }
  return isInPip
}

@Composable
fun PlayerPipController(
  shouldAutoEnterPip: Boolean,
  isPlaying: Boolean,
  videoWidth: Int,
  videoHeight: Int,
  sourceRect: Rect?,
  includeSeekActions: Boolean = true,
  onPlay: () -> Unit,
  onPause: () -> Unit,
  onSeekBy: ((Long) -> Unit)? = null,
) {
  val context = LocalContext.current
  val activity = remember(context) { PipSupport.findComponentActivity(context) }

  val latestShouldEnter by rememberUpdatedState(shouldAutoEnterPip)
  val latestIsPlaying by rememberUpdatedState(isPlaying)
  val latestWidth by rememberUpdatedState(videoWidth)
  val latestHeight by rememberUpdatedState(videoHeight)
  val latestRect by rememberUpdatedState(sourceRect)
  val latestOnPlay by rememberUpdatedState(onPlay)
  val latestOnPause by rememberUpdatedState(onPause)
  val latestOnSeekBy by rememberUpdatedState(onSeekBy)

  LaunchedEffect(
    activity,
    shouldAutoEnterPip,
    isPlaying,
    videoWidth,
    videoHeight,
    sourceRect,
    includeSeekActions,
  ) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@LaunchedEffect
    if (!PipSupport.isPipSupported(activity)) return@LaunchedEffect
    runCatching {
      val params =
        PipSupport.buildPipParams(
          context = activity,
          isPlaying = isPlaying,
          videoWidth = videoWidth,
          videoHeight = videoHeight,
          sourceRect = sourceRect,
          autoEnterEnabled = shouldAutoEnterPip,
          includeSeekActions = includeSeekActions,
        )
      activity.setPictureInPictureParams(params)
    }
  }

  // Register PiP remote action broadcast receiver while player is active
  DisposableEffect(activity) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      onDispose {}
    } else {
      val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != PipSupport.ACTION_PIP_CONTROL) return
            when (intent.getIntExtra(PipSupport.EXTRA_CONTROL_TYPE, 0)) {
              PipSupport.CONTROL_PLAY -> latestOnPlay()
              PipSupport.CONTROL_PAUSE -> latestOnPause()
              PipSupport.CONTROL_REPLAY_10 -> latestOnSeekBy?.invoke(-10_000L)
              PipSupport.CONTROL_FORWARD_10 -> latestOnSeekBy?.invoke(10_000L)
            }
          }
        }
      ContextCompat.registerReceiver(
        activity,
        receiver,
        IntentFilter(PipSupport.ACTION_PIP_CONTROL),
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      onDispose {
        runCatching { activity.unregisterReceiver(receiver) }
      }
    }
  }

  // Pre-Android 12 auto-enter PiP on Home/leave hint
  DisposableEffect(activity) {
    if (
      activity == null ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
      onDispose {
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && PipSupport.isPipSupported(activity)) {
          runCatching {
            activity.setPictureInPictureParams(
              PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
            )
          }
        }
      }
    } else {
      val userLeaveListener = Runnable {
        if (latestShouldEnter && PipSupport.isPipSupported(activity)) {
          runCatching {
            val params =
              PipSupport.buildPipParams(
                context = activity,
                isPlaying = latestIsPlaying,
                videoWidth = latestWidth,
                videoHeight = latestHeight,
                sourceRect = latestRect,
                autoEnterEnabled = false,
                includeSeekActions = includeSeekActions,
              )
            activity.enterPictureInPictureMode(params)
          }
        }
      }
      activity.addOnUserLeaveHintListener(userLeaveListener)
      onDispose {
        activity.removeOnUserLeaveHintListener(userLeaveListener)
      }
    }
  }
}
