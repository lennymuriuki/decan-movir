package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DramaEpisodeItem
import com.example.data.remote.moviebox.MovieBoxDub
import com.example.data.remote.moviebox.MovieBoxSeason

@Immutable
data class DramaPlayerUiState(
  val dramaTitle: String,
  val episode: DramaEpisodeItem,
  val nextEpisode: DramaEpisodeItem?,
  val isPlaying: Boolean,
  val isBuffering: Boolean,
  val isLoadingStreams: Boolean,
  val errorMessage: String?,
  val controlsVisible: Boolean,
  val isLocked: Boolean,
  val isLiked: Boolean,
  val isInWatchlist: Boolean,
  val ccActive: Boolean,
  val activeCaptionText: String?,
  val currentPositionMs: Long,
  val durationMs: Long,
  val qualityLabel: String?,
  val playbackSpeed: Float,
  val fillScreen: Boolean,
)

@Composable
fun DramaPlayerOverlay(
  state: DramaPlayerUiState,
  onBack: () -> Unit,
  onPlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onToggleLike: () -> Unit,
  onToggleWatchlist: () -> Unit,
  onShare: () -> Unit,
  onOpenMoreOptions: () -> Unit,
  onOpenSubtitles: () -> Unit,
  onOpenSettings: () -> Unit,
  onNextEpisode: () -> Unit,
  onRetryStream: () -> Unit,
  onUnlock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var descriptionExpanded by remember(state.episode.globalIndex) { mutableStateOf(false) }
  var scrubbingMs by remember { mutableStateOf<Long?>(null) }

  Box(modifier = modifier.fillMaxSize()) {
    // Persistent edge-to-edge protective gradient scrims when controls are visible
    if (state.controlsVisible && !state.isLocked) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(170.dp)
            .align(Alignment.TopCenter)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)
              )
            )
      )
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(290.dp)
            .align(Alignment.BottomCenter)
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.62f),
                    Color.Black.copy(alpha = 0.90f),
                  )
              )
            )
      )
    }

    // Center buffering / error / paused badge
    if (state.errorMessage != null) {
      Surface(
        color = Color.Black.copy(alpha = 0.84f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = state.errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
              onClick = onRetryStream,
              modifier = Modifier.testTag("drama_retry_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Retry")
            }
            if (state.nextEpisode != null) {
              OutlinedButton(onClick = onNextEpisode) { Text("Next Ep") }
            }
          }
        }
      }
    } else if (state.isLoadingStreams || state.isBuffering) {
      Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(46.dp),
        )
        Surface(
          color = Color.Black.copy(alpha = 0.65f),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            text = if (state.isLoadingStreams) "Preparing ${state.episode.subtitleLabel}…" else "Buffering…",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
    } else if (!state.isPlaying && !state.controlsVisible) {
      FilledTonalIconButton(
        onClick = onPlayPause,
        colors =
          IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.65f),
            contentColor = Color.White,
          ),
        modifier = Modifier.align(Alignment.Center).size(68.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.PlayArrow,
          contentDescription = "Play episode",
          modifier = Modifier.size(40.dp),
        )
      }
    }

    // Lock screen unlock pill
    if (state.isLocked && state.controlsVisible) {
      FilledTonalButton(
        onClick = onUnlock,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 28.dp)
            .testTag("drama_unlock_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = "Unlock screen",
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Player Locked • Tap to Unlock")
      }
    }

    // Main Top / Right Rail / Bottom Overlay
    AnimatedVisibility(
      visible = state.controlsVisible && !state.isLocked,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // TOP SECTION: Back button, Drama title, Season & Episode, More options
        Row(
          modifier =
            Modifier.align(Alignment.TopCenter)
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.statusBars)
              .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("drama_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
              )
            }

            Column(modifier = Modifier.padding(horizontal = 6.dp)) {
              Text(
                text = state.dramaTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("drama_title_text"),
              )
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                  shape = MaterialTheme.shapes.extraSmall,
                ) {
                  Text(
                    text = state.episode.subtitleLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier =
                      Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("drama_episode_badge"),
                  )
                }
                if (!state.qualityLabel.isNullOrBlank()) {
                  Text(
                    text = state.qualityLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                  )
                }
              }
            }
          }

          IconButton(
            onClick = onOpenMoreOptions,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("drama_top_more_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.MoreVert,
              contentDescription = "More options",
              tint = Color.White,
            )
          }
        }

        // RIGHT-SIDE ACTION CONTROLS: Like, Add to Watchlist, Share, More Options
        Column(
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .windowInsetsPadding(WindowInsets.navigationBars)
              .padding(end = 10.dp, bottom = 190.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          DramaSideActionButton(
            icon = if (state.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = if (state.isLiked) "Liked" else "Like",
            tint = if (state.isLiked) MaterialTheme.colorScheme.secondary else Color.White,
            onClick = onToggleLike,
            testTag = "drama_like_button",
          )
          DramaSideActionButton(
            icon = if (state.isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
            label = if (state.isInWatchlist) "Saved" else "Watchlist",
            tint = if (state.isInWatchlist) MaterialTheme.colorScheme.primary else Color.White,
            onClick = onToggleWatchlist,
            testTag = "drama_watchlist_button",
          )
          DramaSideActionButton(
            icon = Icons.Filled.Share,
            label = "Share",
            tint = Color.White,
            onClick = onShare,
            testTag = "drama_share_button",
          )
          DramaSideActionButton(
            icon = Icons.Filled.MoreVert,
            label = "More",
            tint = Color.White,
            onClick = onOpenMoreOptions,
            testTag = "drama_side_more_button",
          )
        }

        // BOTTOM SECTION: Caption, Episode title & description, Seek bar, Play/Pause, CC, Settings, Next Ep
        Column(
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.navigationBars)
              .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (!state.activeCaptionText.isNullOrBlank()) {
            Surface(
              color = Color.Black.copy(alpha = 0.78f),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.align(Alignment.CenterHorizontally),
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

          Column(
            modifier =
              Modifier.fillMaxWidth(0.84f)
                .clickable { descriptionExpanded = !descriptionExpanded },
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              text = state.episode.title,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("drama_episode_title"),
            )
            Text(
              text = state.episode.description,
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.86f),
              maxLines = if (descriptionExpanded) 5 else 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("drama_episode_description"),
            )
          }

          val shownPosMs = scrubbingMs ?: state.currentPositionMs
          val safeDuration = state.durationMs.coerceAtLeast(1L)
          val sliderValue = (shownPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Slider(
              value = sliderValue,
              onValueChange = { frac ->
                if (state.durationMs > 0L) {
                  scrubbingMs = (frac * state.durationMs).toLong()
                }
              },
              onValueChangeFinished = {
                val target = scrubbingMs
                scrubbingMs = null
                if (target != null) onSeekTo(target)
              },
              colors =
                SliderDefaults.colors(
                  thumbColor = MaterialTheme.colorScheme.primary,
                  activeTrackColor = MaterialTheme.colorScheme.primary,
                  inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                ),
              modifier = Modifier.fillMaxWidth().height(28.dp).testTag("drama_seek_bar"),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  "${formatPlaybackDuration(shownPosMs)} / ${formatPlaybackDuration(state.durationMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
              )

              Text(
                text =
                  "Ep ${state.episode.globalIndex + 1} of ${state.episode.totalCount}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f),
              )
            }
          }

          // Bottom Action Row: Play/Pause, CC, Settings, Next Episode Indicator
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              FilledTonalIconButton(
                onClick = onPlayPause,
                colors =
                  IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.size(48.dp).testTag("drama_play_pause_button"),
              ) {
                Icon(
                  imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                  contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
              }

              IconButton(
                onClick = onOpenSubtitles,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("drama_cc_button"),
              ) {
                Icon(
                  imageVector =
                    if (state.ccActive) Icons.Filled.ClosedCaption
                    else Icons.Filled.ClosedCaptionOff,
                  contentDescription = "Subtitles",
                  tint = if (state.ccActive) MaterialTheme.colorScheme.primary else Color.White,
                )
              }

              IconButton(
                onClick = onOpenSettings,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("drama_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Settings,
                  contentDescription = "Playback settings",
                  tint = Color.White,
                )
              }
            }

            if (state.nextEpisode != null) {
              Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clickable(onClick = onNextEpisode)
                    .testTag("drama_next_indicator"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text =
                      "Next S%02dE%02d".format(state.nextEpisode.season, state.nextEpisode.episode),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                  )
                  Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Swipe up for next episode",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            } else {
              Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("drama_next_indicator"),
              ) {
                Text(
                  text = "Final Episode",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White.copy(alpha = 0.85f),
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
              }
            }
          }
        }
      }
    }

    // Closed captions when controls are hidden
    if (!state.controlsVisible && !state.activeCaptionText.isNullOrBlank()) {
      Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.small,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
      ) {
        Text(
          text = state.activeCaptionText,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          color = Color.White,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
private fun DramaSideActionButton(
  icon: ImageVector,
  label: String,
  tint: Color,
  onClick: () -> Unit,
  testTag: String,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Surface(
      color = Color.Black.copy(alpha = 0.48f),
      shape = CircleShape,
      modifier = Modifier.size(48.dp),
    ) {
      IconButton(
        onClick = onClick,
        modifier = Modifier.fillMaxSize().testTag(testTag),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = tint,
          modifier = Modifier.size(24.dp),
        )
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
      color = Color.White,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DramaMoreOptionsSheet(
  seasons: List<MovieBoxSeason>,
  currentSeason: Int,
  currentEpisode: Int,
  dubs: List<MovieBoxDub>,
  activeSubjectId: String?,
  fillScreen: Boolean,
  onSelectEpisode: (season: Int, episode: Int) -> Unit,
  onSelectDub: (subjectId: String) -> Unit,
  onToggleScaling: (Boolean) -> Unit,
  onDownloadEpisode: () -> Unit,
  onLockControls: () -> Unit,
  onOpenStandardTheater: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text(
        text = "Drama Options & Episodes",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (seasons.isNotEmpty()) {
        EpisodeNumberGrid(
          seasons = seasons,
          selectedSeason = currentSeason,
          selectedEpisode = currentEpisode,
          onSelectSeason = { s -> onSelectEpisode(s, 1) },
          onSelectEpisode = { s, ep -> onSelectEpisode(s, ep) },
        )
      }

      if (dubs.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Audio Languages", style = MaterialTheme.typography.titleSmall)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dubs.forEach { dub ->
              FilterChip(
                selected = dub.subjectId == activeSubjectId,
                onClick = { onSelectDub(dub.subjectId) },
                label = { Text(dub.langName) },
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(imageVector = Icons.Filled.AspectRatio, contentDescription = null)
          Text("Video Display", style = MaterialTheme.typography.titleSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = fillScreen,
            onClick = { onToggleScaling(true) },
            label = { Text("Fill") },
          )
          FilterChip(
            selected = !fillScreen,
            onClick = { onToggleScaling(false) },
            label = { Text("Fit") },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDownloadEpisode,
          modifier = Modifier.weight(1f),
        ) {
          Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Download")
        }
        OutlinedButton(
          onClick = onLockControls,
          modifier = Modifier.weight(1f),
        ) {
          Icon(imageVector = Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Lock")
        }
      }

      FilledTonalButton(
        onClick = onOpenStandardTheater,
        modifier = Modifier.fillMaxWidth().testTag("drama_switch_standard_player_button"),
      ) {
        Icon(imageVector = Icons.Filled.Theaters, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Open in Standard Theater Player")
      }
    }
  }
}
