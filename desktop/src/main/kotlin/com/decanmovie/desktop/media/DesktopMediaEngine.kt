package com.decanmovie.desktop.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.decanmovie.desktop.model.PlaybackAspectMode
import java.awt.BorderLayout
import java.awt.Color
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.AudioTrack
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color as FxColor
import javafx.util.Duration
import javax.swing.JPanel

@Immutable
data class DesktopAudioTrackInfo(
  val trackId: Long,
  val label: String,
  val language: String,
)

@Immutable
data class DesktopPlayerTelemetry(
  val isReady: Boolean = false,
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val currentPositionMs: Long = 0L,
  val durationMs: Long = 0L,
  val videoWidth: Int = 0,
  val videoHeight: Int = 0,
  val audioTracks: List<DesktopAudioTrackInfo> = emptyList(),
  val errorMessage: String? = null,
)

object DesktopFxToolkit {
  private val initialized = AtomicBoolean(false)
  @Volatile private var available = false

  fun ensureInitialized(): Boolean {
    if (java.awt.GraphicsEnvironment.isHeadless()) return false
    if (initialized.compareAndSet(false, true)) {
      available =
        runCatching {
          Platform.setImplicitExit(false)
          // Creating JFXPanel initializes the JavaFX runtime cleanly on Swing/Desktop
          JFXPanel()
          true
        }.getOrDefault(false)
    }
    return available
  }

  fun runOnFxThread(block: () -> Unit) {
    if (!ensureInitialized()) return
    runCatching { Platform.runLater(block) }
  }
}

class DesktopMediaController {
  private var activePlayer: MediaPlayer? = null
  private var activeMediaView: MediaView? = null
  private var currentAspectMode: PlaybackAspectMode = PlaybackAspectMode.FIT

  var onTelemetry: ((DesktopPlayerTelemetry) -> Unit)? = null
  var onCompleted: (() -> Unit)? = null
  private var telemetry = DesktopPlayerTelemetry()

  fun attachMediaView(mediaView: MediaView) {
    activeMediaView = mediaView
  }

  fun loadAndPlay(
    sourceUrlOrPath: String,
    startPositionMs: Long = 0L,
    volume: Float = 1f,
    muted: Boolean = false,
    playbackRate: Float = 1f,
    aspectMode: PlaybackAspectMode = currentAspectMode,
    autoPlay: Boolean = true,
  ) {
    currentAspectMode = aspectMode
    val resolvedUri = resolvePlayableUri(sourceUrlOrPath)

    if (!DesktopFxToolkit.ensureInitialized()) {
      emitTelemetry(
        DesktopPlayerTelemetry(
          isReady = false,
          isPlaying = false,
          isBuffering = false,
          errorMessage = "JavaFX video surface unavailable in headless mode.",
        )
      )
      return
    }

    DesktopFxToolkit.runOnFxThread {
      disposeActiveOnFxThread()
      emitTelemetry(
        DesktopPlayerTelemetry(
          isReady = false,
          isPlaying = false,
          isBuffering = true,
          currentPositionMs = startPositionMs,
          errorMessage = null,
        )
      )

      try {
        val media = Media(resolvedUri)
        val player = MediaPlayer(media)
        activePlayer = player
        activeMediaView?.mediaPlayer = player
        applyAspectRatioOnFxThread(aspectMode)

        player.volume = volume.toDouble().coerceIn(0.0, 1.0)
        player.isMute = muted
        player.rate = playbackRate.toDouble().coerceIn(0.25, 2.5)

        media.setOnError {
          val msg = media.error?.message ?: "Unsupported video codec or stream format."
          emitTelemetry(
            telemetry.copy(
              isBuffering = false,
              isPlaying = false,
              errorMessage = msg,
            )
          )
        }

        player.setOnError {
          val msg = player.error?.message ?: "Stream interrupted or unsupported format."
          emitTelemetry(
            telemetry.copy(
              isBuffering = false,
              isPlaying = false,
              errorMessage = msg,
            )
          )
        }

        player.setOnReady {
          val durMs =
            player.totalDuration?.takeIf { !it.isUnknown && !it.isIndefinite }?.toMillis()?.toLong()
              ?: 0L
          val tracks =
            media.tracks.filterIsInstance<AudioTrack>().map { t ->
              DesktopAudioTrackInfo(
                trackId = t.trackID,
                label = t.name?.takeIf { it.isNotBlank() } ?: "Audio #${t.trackID}",
                language = t.locale?.displayLanguage ?: "",
              )
            }
          if (startPositionMs > 0L) {
            runCatching { player.seek(Duration.millis(startPositionMs.toDouble())) }
          }
          emitTelemetry(
            telemetry.copy(
              isReady = true,
              isBuffering = false,
              durationMs = durMs,
              videoWidth = media.width,
              videoHeight = media.height,
              audioTracks = tracks,
              errorMessage = null,
            )
          )
          applyAspectRatioOnFxThread(currentAspectMode)
          if (autoPlay) {
            player.play()
          }
        }

        player.setOnPlaying {
          emitTelemetry(telemetry.copy(isPlaying = true, isBuffering = false))
        }
        player.setOnPaused {
          emitTelemetry(telemetry.copy(isPlaying = false, isBuffering = false))
        }
        player.setOnStalled {
          emitTelemetry(telemetry.copy(isBuffering = true))
        }
        player.setOnEndOfMedia {
          emitTelemetry(telemetry.copy(isPlaying = false, currentPositionMs = telemetry.durationMs))
          onCompleted?.invoke()
        }
        player.currentTimeProperty().addListener { _, _, newTime ->
          val pos = newTime?.toMillis()?.toLong() ?: return@addListener
          val dur =
            player.totalDuration?.takeIf { !it.isUnknown && !it.isIndefinite }?.toMillis()?.toLong()
              ?: telemetry.durationMs
          emitTelemetry(
            telemetry.copy(
              currentPositionMs = pos,
              durationMs = dur.coerceAtLeast(pos),
            )
          )
        }
      } catch (t: Throwable) {
        emitTelemetry(
          DesktopPlayerTelemetry(
            isReady = false,
            isPlaying = false,
            isBuffering = false,
            errorMessage =
              t.message ?: "Unable to initialize desktop media player for this stream.",
          )
        )
      }
    }
  }

  fun play() {
    DesktopFxToolkit.runOnFxThread { runCatching { activePlayer?.play() } }
  }

  fun pause() {
    DesktopFxToolkit.runOnFxThread { runCatching { activePlayer?.pause() } }
  }

  fun togglePlayPause() {
    DesktopFxToolkit.runOnFxThread {
      val p = activePlayer ?: return@runOnFxThread
      if (p.status == MediaPlayer.Status.PLAYING) p.pause() else p.play()
    }
  }

  fun seekTo(positionMs: Long) {
    DesktopFxToolkit.runOnFxThread {
      runCatching {
        activePlayer?.seek(Duration.millis(positionMs.coerceAtLeast(0L).toDouble()))
        emitTelemetry(telemetry.copy(currentPositionMs = positionMs.coerceAtLeast(0L)))
      }
    }
  }

  fun setVolume(volume: Float, muted: Boolean) {
    DesktopFxToolkit.runOnFxThread {
      runCatching {
        activePlayer?.volume = volume.toDouble().coerceIn(0.0, 1.0)
        activePlayer?.isMute = muted
      }
    }
  }

  fun setPlaybackRate(rate: Float) {
    DesktopFxToolkit.runOnFxThread {
      runCatching { activePlayer?.rate = rate.toDouble().coerceIn(0.25, 2.5) }
    }
  }

  fun setAspectMode(mode: PlaybackAspectMode) {
    currentAspectMode = mode
    DesktopFxToolkit.runOnFxThread { applyAspectRatioOnFxThread(mode) }
  }

  fun stopAndDispose() {
    if (activePlayer == null) {
      emitTelemetry(DesktopPlayerTelemetry())
      return
    }
    DesktopFxToolkit.runOnFxThread {
      disposeActiveOnFxThread()
      emitTelemetry(DesktopPlayerTelemetry())
    }
  }

  private fun applyAspectRatioOnFxThread(mode: PlaybackAspectMode) {
    val mv = activeMediaView ?: return
    val scene = mv.scene ?: return
    mv.fitWidthProperty().unbind()
    mv.fitHeightProperty().unbind()
    when (mode) {
      PlaybackAspectMode.FIT -> {
        mv.isPreserveRatio = true
        mv.fitWidthProperty().bind(scene.widthProperty())
        mv.fitHeightProperty().bind(scene.heightProperty())
      }
      PlaybackAspectMode.FILL -> {
        mv.isPreserveRatio = false
        mv.fitWidthProperty().bind(scene.widthProperty())
        mv.fitHeightProperty().bind(scene.heightProperty())
      }
      PlaybackAspectMode.RATIO_16_9 -> {
        mv.isPreserveRatio = false
        mv.fitWidthProperty().bind(scene.widthProperty())
        mv.fitHeightProperty().bind(
          Bindings.min(scene.heightProperty(), scene.widthProperty().multiply(9.0 / 16.0))
        )
      }
      PlaybackAspectMode.RATIO_21_9 -> {
        mv.isPreserveRatio = false
        mv.fitWidthProperty().bind(scene.widthProperty())
        mv.fitHeightProperty().bind(
          Bindings.min(scene.heightProperty(), scene.widthProperty().multiply(9.0 / 21.0))
        )
      }
    }
  }

  private fun disposeActiveOnFxThread() {
    val existing = activePlayer ?: return
    activePlayer = null
    activeMediaView?.mediaPlayer = null
    runCatching { existing.stop() }
    runCatching { existing.dispose() }
  }

  private fun emitTelemetry(next: DesktopPlayerTelemetry) {
    telemetry = next
    onTelemetry?.invoke(next)
  }

  private fun resolvePlayableUri(sourceUrlOrPath: String): String {
    val trimmed = sourceUrlOrPath.trim()
    if (
      trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("file:/", ignoreCase = true)
    ) {
      return trimmed
    }
    return File(trimmed).toURI().toString()
  }
}

val LocalEnableDesktopVideoSurface = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun DesktopVideoSurface(
  controller: DesktopMediaController,
  aspectMode: PlaybackAspectMode,
  modifier: Modifier = Modifier,
) {
  val surfaceEnabled = LocalEnableDesktopVideoSurface.current
  val isHeadless = remember { java.awt.GraphicsEnvironment.isHeadless() }
  var jfxPanelReady by remember { mutableStateOf(false) }

  LaunchedEffect(aspectMode, jfxPanelReady) {
    if (jfxPanelReady) {
      controller.setAspectMode(aspectMode)
    }
  }

  DisposableEffect(controller) {
    onDispose {
      controller.stopAndDispose()
    }
  }

  if (!surfaceEnabled || isHeadless) {
    androidx.compose.foundation.layout.Box(
      modifier = modifier,
    )
    return
  }

  SwingPanel(
    factory = {
      DesktopFxToolkit.ensureInitialized()
      val container = JPanel(BorderLayout()).apply { background = Color.BLACK }
      val fxPanel = JFXPanel().apply { background = Color.BLACK }
      Platform.runLater {
        val mv = MediaView().apply { isPreserveRatio = true }
        val root =
          StackPane(mv).apply {
            style = "-fx-background-color: #000000;"
          }
        val scene = Scene(root, FxColor.BLACK)
        fxPanel.scene = scene
        controller.attachMediaView(mv)
        controller.setAspectMode(aspectMode)
        jfxPanelReady = true
      }
      container.add(fxPanel, BorderLayout.CENTER)
      container
    },
    modifier = modifier,
  )
}
