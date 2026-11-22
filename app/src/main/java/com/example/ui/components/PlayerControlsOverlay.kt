package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class PlayerHudGestureType {
  VOLUME,
  BRIGHTNESS,
}

data class PlayerOverlayState(
  val title: String,
  val subtitleLine: String,
  val isPlaying: Boolean,
  val isBuffering: Boolean,
  val isLoadingMedia: Boolean,
  val isFullscreen: Boolean,
  val isLocked: Boolean,
  val controlsVisible: Boolean,
  val zoomFill: Boolean,
  val hasPlaybackUrl: Boolean,
  val canGoPrevious: Boolean,
  val canGoNext: Boolean,
  val canDownload: Boolean,
  val ccActive: Boolean,
  val currentPositionMs: Long,
  val durationMs: Long,
  val bufferedFraction: Float,
  val volume: Float,
  val isMuted: Boolean,
  val activeCaptionText: String?,
  val seekFeedbackText: String?,
  val upNextSeconds: Int?,
  val gestureHudType: PlayerHudGestureType?,
  val gestureHudFraction: Float,
)

@Composable
fun PlayerGestureLayer(
  isLocked: Boolean,
  onToggleControls: () -> Unit,
  onDoubleTapSeek: (deltaMs: Long) -> Unit,
  onVerticalVolumeDrag: (deltaFraction: Float) -> Unit,
  onVerticalBrightnessDrag: (deltaFraction: Float) -> Unit,
  onGestureDragEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var widthPx by remember { mutableFloatStateOf(1f) }
  var heightPx by remember { mutableFloatStateOf(1f) }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .onSizeChanged { size ->
          widthPx = size.width.toFloat().coerceAtLeast(1f)
          heightPx = size.height.toFloat().coerceAtLeast(1f)
        }
        .pointerInput(isLocked) {
          detectTapGestures(
            onTap = { onToggleControls() },
            onDoubleTap = { offset ->
              if (isLocked) return@detectTapGestures
              val delta = if (offset.x < widthPx / 2f) -10_000L else 10_000L
              onDoubleTapSeek(delta)
            },
          )
        }
        .pointerInput(isLocked) {
          if (isLocked) return@pointerInput
          var dragOnRight = true
          detectVerticalDragGestures(
            onDragStart = { startOffset ->
              dragOnRight = startOffset.x >= widthPx / 2f
            },
            onVerticalDrag = { _, dragAmount ->
              val deltaFraction = -dragAmount / (heightPx * 0.75f)
              if (dragOnRight) {
                onVerticalVolumeDrag(deltaFraction)
              } else {
                onVerticalBrightnessDrag(deltaFraction)
              }
            },
            onDragEnd = { onGestureDragEnd() },
            onDragCancel = { onGestureDragEnd() },
          )
        }
  )
}

@Composable
fun PlayerControlsOverlay(
  state: PlayerOverlayState,
  onBack: () -> Unit,
  onPlayPause: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onSeekBy: (deltaMs: Long) -> Unit,
  onSeekTo: (positionMs: Long) -> Unit,
  onToggleMute: () -> Unit,
  onVolumeChange: (Float) -> Unit,
  onToggleFullscreen: () -> Unit,
  onToggleLock: (Boolean) -> Unit,
  onToggleZoomFill: () -> Unit,
  onOpenSubtitles: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenDownload: () -> Unit,
  onOpenExternal: () -> Unit,
  onPlayNextNow: () -> Unit,
  onDismissUpNext: () -> Unit,
  onControlInteraction: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showVolumeSlider by remember { mutableStateOf(false) }
  var scrubbingPositionMs by remember { mutableStateOf<Long?>(null) }

  Box(modifier = modifier.fillMaxSize()) {
    // Synchronized .srt closed captions
    if (!state.activeCaptionText.isNullOrBlank()) {
      Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .padding(
              start = 24.dp,
              end = 24.dp,
              bottom = if (state.controlsVisible && !state.isLocked) 88.dp else 18.dp,
            ),
      ) {
        Text(
          text = state.activeCaptionText,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = Color.White,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
      }
    }

    // Gesture Volume / Brightness HUD
    AnimatedVisibility(
      visible = state.gestureHudType != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center),
    ) {
      val type = state.gestureHudType
      val pct = (state.gestureHudFraction * 100f).roundToInt().coerceIn(0, 100)
      Surface(
        color = Color.Black.copy(alpha = 0.80f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector =
              when (type) {
                PlayerHudGestureType.VOLUME ->
                  volumeIconFor(volume = state.gestureHudFraction, isMuted = pct == 0)
                PlayerHudGestureType.BRIGHTNESS -> Icons.Filled.BrightnessHigh
                null -> Icons.AutoMirrored.Filled.VolumeUp
              },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
          Text(
            text =
              when (type) {
                PlayerHudGestureType.VOLUME -> "Volume $pct%"
                PlayerHudGestureType.BRIGHTNESS -> "Brightness $pct%"
                null -> ""
              },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
          )
        }
      }
    }

    // Double-tap seek feedback pill
    AnimatedVisibility(
      visible = state.seekFeedbackText != null && state.gestureHudType == null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.Center),
    ) {
      Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
      ) {
        Text(
          text = state.seekFeedbackText.orEmpty(),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
      }
    }

    // Up-Next Episode countdown pill
    val upNextSec = state.upNextSeconds
    if (upNextSec != null) {
      Surface(
        color = Color.Black.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(end = 14.dp, bottom = if (state.controlsVisible) 88.dp else 16.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Next Ep in ${upNextSec}s",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
          )
          TextButton(onClick = onPlayNextNow) {
            Text("Play Now", color = MaterialTheme.colorScheme.primary)
          }
          TextButton(onClick = onDismissUpNext) {
            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
          }
        }
      }
    }

    // Lock floating button
    if (state.isLocked && state.controlsVisible) {
      FilledTonalButton(
        onClick = { onToggleLock(false) },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = "Unlock player controls",
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Screen Locked • Tap to Unlock")
      }
    }

    // Main Streaming Overlay
    AnimatedVisibility(
      visible = state.controlsVisible && !state.isLocked,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color.Black.copy(alpha = 0.78f),
                    Color.Black.copy(alpha = 0.22f),
                    Color.Black.copy(alpha = 0.86f),
                  )
              )
            )
            .padding(horizontal = if (state.isFullscreen) 20.dp else 10.dp, vertical = 8.dp)
      ) {
        // TOP BAR
        Row(
          modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
          ) {
            IconButton(
              onClick = {
                onControlInteraction()
                onBack()
              },
              modifier = Modifier.testTag("player_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (state.isFullscreen) "Exit fullscreen" else "Back",
                tint = Color.White,
              )
            }

            Column(modifier = Modifier.padding(end = 8.dp)) {
              Text(
                text = state.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (state.subtitleLine.isNotBlank()) {
                Text(
                  text = state.subtitleLine,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                onControlInteraction()
                onOpenSubtitles()
              },
              modifier = Modifier.testTag("player_cc_button"),
            ) {
              Icon(
                imageVector =
                  if (state.ccActive) Icons.Filled.ClosedCaption
                  else Icons.Filled.ClosedCaptionOff,
                contentDescription = "Closed Captions (CC)",
                tint =
                  if (state.ccActive) MaterialTheme.colorScheme.primary else Color.White,
              )
            }

            IconButton(
              onClick = {
                onControlInteraction()
                onOpenSettings()
              },
              modifier = Modifier.testTag("player_settings_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Playback Settings",
                tint = Color.White,
              )
            }

            if (state.isFullscreen) {
              IconButton(
                onClick = {
                  onControlInteraction()
                  onToggleZoomFill()
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.AspectRatio,
                  contentDescription = "Toggle fit or zoom fill",
                  tint =
                    if (state.zoomFill) MaterialTheme.colorScheme.primary else Color.White,
                )
              }
            }

            if (state.hasPlaybackUrl) {
              IconButton(
                onClick = {
                  onControlInteraction()
                  onOpenExternal()
                }
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                  contentDescription = "Open external player",
                  tint = Color.White,
                )
              }
            }
          }
        }

        // CENTER TRANSPORT CONTROLS
        Row(
          modifier = Modifier.align(Alignment.Center),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (state.isFullscreen) 24.dp else 10.dp),
        ) {
          IconButton(
            onClick = {
              onControlInteraction()
              onPrevious()
            },
            enabled = state.canGoPrevious,
            modifier = Modifier.testTag("player_prev_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.SkipPrevious,
              contentDescription = "Previous",
              tint = Color.White.copy(alpha = if (state.canGoPrevious) 1f else 0.35f),
              modifier = Modifier.size(30.dp),
            )
          }

          IconButton(
            onClick = {
              onControlInteraction()
              onSeekBy(-10_000L)
            },
            modifier = Modifier.testTag("player_replay10_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Replay10,
              contentDescription = "Rewind 10 seconds",
              tint = Color.White,
              modifier = Modifier.size(32.dp),
            )
          }

          FilledTonalIconButton(
            onClick = {
              onControlInteraction()
              onPlayPause()
            },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.size(58.dp).testTag("player_play_pause_button"),
          ) {
            if (state.isLoadingMedia || state.isBuffering) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(26.dp),
              )
            } else {
              Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(34.dp),
              )
            }
          }

          IconButton(
            onClick = {
              onControlInteraction()
              onSeekBy(10_000L)
            },
            modifier = Modifier.testTag("player_forward10_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Forward10,
              contentDescription = "Forward 10 seconds",
              tint = Color.White,
              modifier = Modifier.size(32.dp),
            )
          }

          IconButton(
            onClick = {
              onControlInteraction()
              onNext()
            },
            enabled = state.canGoNext,
            modifier = Modifier.testTag("player_next_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.SkipNext,
              contentDescription = "Next",
              tint = Color.White.copy(alpha = if (state.canGoNext) 1f else 0.35f),
              modifier = Modifier.size(30.dp),
            )
          }
        }

        // BOTTOM SCRUBBER & UTILITY ROW (Seek bar, Time, Volume slider, CC, Lock, Download, Fullscreen)
        Column(
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          // Floating Volume Slider Pill when expanded
          AnimatedVisibility(
            visible = showVolumeSlider,
            enter = fadeIn(),
            exit = fadeOut(),
          ) {
            Surface(
              color = Color.Black.copy(alpha = 0.85f),
              shape = RoundedCornerShape(999.dp),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
              modifier = Modifier.padding(bottom = 4.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                IconButton(
                  onClick = {
                    onControlInteraction()
                    onToggleMute()
                  },
                  modifier = Modifier.size(32.dp),
                ) {
                  Icon(
                    imageVector = volumeIconFor(state.volume, state.isMuted),
                    contentDescription = if (state.isMuted) "Unmute" else "Mute",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }

                Slider(
                  value = if (state.isMuted) 0f else state.volume,
                  onValueChange = { next ->
                    onControlInteraction()
                    onVolumeChange(next)
                  },
                  colors =
                    SliderDefaults.colors(
                      thumbColor = MaterialTheme.colorScheme.primary,
                      activeTrackColor = MaterialTheme.colorScheme.primary,
                      inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                  modifier = Modifier.width(136.dp).height(28.dp).testTag("player_volume_slider"),
                )

                Text(
                  text = "${((if (state.isMuted) 0f else state.volume) * 100).roundToInt()}%",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                )
              }
            }
          }

          val shownPos = scrubbingPositionMs ?: state.currentPositionMs
          val progressFraction =
            if (state.durationMs > 0L) (shownPos.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            else 0f

          // Custom Streaming Seek Bar with buffered track and drag preview pill
          StreamingSeekBar(
            progressFraction = progressFraction,
            bufferedFraction = state.bufferedFraction,
            durationMs = state.durationMs,
            isDragging = scrubbingPositionMs != null,
            previewTimeText = formatPlaybackDuration(shownPos),
            onScrub = { frac ->
              onControlInteraction()
              if (state.durationMs > 0L) {
                scrubbingPositionMs = (frac * state.durationMs).toLong()
              }
            },
            onScrubFinished = { frac ->
              val target =
                if (state.durationMs > 0L) (frac * state.durationMs).toLong()
                else scrubbingPositionMs
              scrubbingPositionMs = null
              if (target != null) {
                onSeekTo(target)
              }
            },
            modifier = Modifier.fillMaxWidth().testTag("player_seek_bar"),
          )

          // Bottom Control Row: Timestamp | Volume | CC | Lock | Download | Fullscreen
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = formatPlaybackDuration(shownPos),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
              )
              Text(
                text = "/ ${formatPlaybackDuration(state.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              // Volume button (tap to toggle slider, long-press or muted indicator)
              IconButton(
                onClick = {
                  onControlInteraction()
                  showVolumeSlider = !showVolumeSlider
                },
                modifier = Modifier.size(40.dp).testTag("player_volume_button"),
              ) {
                Icon(
                  imageVector = volumeIconFor(state.volume, state.isMuted),
                  contentDescription = "Volume control",
                  tint =
                    if (showVolumeSlider || state.isMuted) MaterialTheme.colorScheme.primary
                    else Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }

              IconButton(
                onClick = {
                  onControlInteraction()
                  onOpenSubtitles()
                },
                modifier = Modifier.size(40.dp),
              ) {
                Icon(
                  imageVector =
                    if (state.ccActive) Icons.Filled.ClosedCaption
                    else Icons.Filled.ClosedCaptionOff,
                  contentDescription = "Closed Captions",
                  tint = if (state.ccActive) MaterialTheme.colorScheme.primary else Color.White,
                  modifier = Modifier.size(20.dp),
                )
              }

              IconButton(
                onClick = { onToggleLock(true) },
                modifier = Modifier.size(40.dp).testTag("player_lock_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.LockOpen,
                  contentDescription = "Lock screen controls",
                  tint = Color.White,
                  modifier = Modifier.size(19.dp),
                )
              }

              if (state.canDownload) {
                IconButton(
                  onClick = {
                    onControlInteraction()
                    onOpenDownload()
                  },
                  modifier = Modifier.size(40.dp).testTag("player_download_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }

              IconButton(
                onClick = {
                  onControlInteraction()
                  onToggleFullscreen()
                },
                modifier = Modifier.size(40.dp).testTag("player_fullscreen_button"),
              ) {
                Icon(
                  imageVector =
                    if (state.isFullscreen) Icons.Filled.FullscreenExit
                    else Icons.Filled.Fullscreen,
                  contentDescription =
                    if (state.isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StreamingSeekBar(
  progressFraction: Float,
  bufferedFraction: Float,
  durationMs: Long,
  isDragging: Boolean,
  previewTimeText: String,
  onScrub: (Float) -> Unit,
  onScrubFinished: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val trackHeight by animateDpAsState(targetValue = if (isDragging) 6.dp else 4.dp, label = "trackH")
  val thumbRadius by animateDpAsState(targetValue = if (isDragging) 9.dp else 6.5.dp, label = "thumbR")
  var latestFraction by remember { mutableFloatStateOf(progressFraction) }
  val density = LocalDensity.current

  BoxWithConstraints(
    modifier = modifier.height(34.dp),
    contentAlignment = Alignment.Center,
  ) {
    val totalWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }

    if (isDragging) {
      val thumbX = (progressFraction.coerceIn(0f, 1f) * totalWidthPx).roundToInt()
      Surface(
        color = Color.Black.copy(alpha = 0.88f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.7f)),
        modifier =
          Modifier.align(Alignment.TopStart)
            .offset {
              val pillOffset = (thumbX - 42).coerceIn(0, (totalWidthPx - 84).toInt().coerceAtLeast(0))
              IntOffset(pillOffset, -44)
            },
      ) {
        Text(
          text = previewTimeText,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = primaryColor,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }

    Box(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(durationMs) {
            if (durationMs <= 0L) return@pointerInput
            detectTapGestures { offset ->
              val frac = (offset.x / totalWidthPx).coerceIn(0f, 1f)
              latestFraction = frac
              onScrubFinished(frac)
            }
          }
          .pointerInput(durationMs) {
            if (durationMs <= 0L) return@pointerInput
            detectDragGestures(
              onDragStart = { start ->
                val frac = (start.x / totalWidthPx).coerceIn(0f, 1f)
                latestFraction = frac
                onScrub(frac)
              },
              onDrag = { change, _ ->
                change.consume()
                val frac = (change.position.x / totalWidthPx).coerceIn(0f, 1f)
                latestFraction = frac
                onScrub(frac)
              },
              onDragEnd = { onScrubFinished(latestFraction) },
              onDragCancel = { onScrubFinished(latestFraction) },
            )
          },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
        val barH = trackHeight.toPx()
        val cy = size.height / 2f
        val topY = cy - barH / 2f
        val corner = CornerRadius(barH / 2f, barH / 2f)

        // Base inactive track
        drawRoundRect(
          color = Color.White.copy(alpha = 0.25f),
          topLeft = Offset(0f, topY),
          size = Size(size.width, barH),
          cornerRadius = corner,
        )

        // Buffered track
        val bufW = (bufferedFraction.coerceIn(0f, 1f) * size.width)
        if (bufW > 0f) {
          drawRoundRect(
            color = Color.White.copy(alpha = 0.45f),
            topLeft = Offset(0f, topY),
            size = Size(bufW, barH),
            cornerRadius = corner,
          )
        }

        // Active played track
        val activeW = (progressFraction.coerceIn(0f, 1f) * size.width)
        if (activeW > 0f) {
          drawRoundRect(
            color = primaryColor,
            topLeft = Offset(0f, topY),
            size = Size(activeW, barH),
            cornerRadius = corner,
          )
        }

        // Thumb circle
        drawCircle(
          color = primaryColor,
          radius = thumbRadius.toPx(),
          center = Offset(activeW.coerceIn(0f, size.width), cy),
        )
      }
    }
  }
}

private fun volumeIconFor(volume: Float, isMuted: Boolean) =
  when {
    isMuted || volume <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
    volume < 0.35f -> Icons.AutoMirrored.Filled.VolumeMute
    volume < 0.7f -> Icons.AutoMirrored.Filled.VolumeDown
    else -> Icons.AutoMirrored.Filled.VolumeUp
  }

fun formatPlaybackDuration(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val hours = totalSec / 3600L
  val mins = (totalSec % 3600L) / 60L
  val secs = totalSec % 60L
  return if (hours > 0L) {
    "%d:%02d:%02d".format(hours, mins, secs)
  } else {
    "%02d:%02d".format(mins, secs)
  }
}
