package com.decanmovie.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.data.remote.DesktopMovieCatalogRepository
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSeason
import com.decanmovie.desktop.data.remote.moviebox.SrtParser
import com.decanmovie.desktop.data.remote.moviebox.SubtitleCue
import com.decanmovie.desktop.data.store.DesktopAppStorage
import com.decanmovie.desktop.media.DesktopMediaController
import com.decanmovie.desktop.media.DesktopPlayerTelemetry
import com.decanmovie.desktop.media.DesktopVideoSurface
import com.decanmovie.desktop.model.DesktopSettings
import com.decanmovie.desktop.model.DramaEpisodeItem
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.PlaybackAspectMode
import com.decanmovie.desktop.model.WatchStatus
import com.decanmovie.desktop.ui.components.AsyncDesktopImage
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import com.decanmovie.desktop.viewmodel.ActiveDesktopOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopVerticalDramaPlayerScreen(
  request: ActiveDesktopOverlay.VerticalDramaPlayer,
  catalogRepository: DesktopMovieCatalogRepository,
  storage: DesktopAppStorage,
  settings: DesktopSettings,
  isInWatchlist: Boolean,
  isFullscreen: Boolean,
  onToggleFullscreen: () -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  onSwitchToStandardPlayer: (movieId: String, season: Int, episode: Int, posMs: Long) -> Unit,
  onBack: () -> Unit,
  onNotify: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val focusRequester = remember { FocusRequester() }
  val mediaController = remember { DesktopMediaController() }

  var movie by remember(request.movieId) { mutableStateOf(catalogRepository.getCachedMovie(request.movieId)) }
  var seasons by remember(request.movieId) { mutableStateOf<List<MovieBoxSeason>>(emptyList()) }
  var episodes by remember {
    mutableStateOf(
      DramaExperienceDetector.buildOrderedDramaEpisodes(movie, seasons)
    )
  }
  var activePageIndex by remember(request.movieId) { mutableIntStateOf(0) }
  var telemetry by remember { mutableStateOf(DesktopPlayerTelemetry()) }
  var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
  var captionsEnabled by remember { mutableStateOf(settings.autoLoadSubtitles) }
  var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
  var volume by remember { mutableFloatStateOf(0.95f) }
  var muted by remember { mutableStateOf(false) }
  var liked by remember { mutableStateOf(storage.getWatchlistEntry(request.movieId)?.favorite == true) }
  var expandSynopsis by remember { mutableStateOf(false) }
  var showEpisodeDrawer by remember { mutableStateOf(false) }
  var isLoadingStream by remember { mutableStateOf(true) }
  var errorNotice by remember { mutableStateOf<String?>(null) }

  var lastWheelStepMs by remember { mutableStateOf(0L) }
  var dragAccumulator by remember { mutableFloatStateOf(0f) }

  val currentItem: DramaEpisodeItem? = episodes.getOrNull(activePageIndex)

  fun saveActiveEpisodeProgress() {
    val m = movie ?: return
    val item = episodes.getOrNull(activePageIndex) ?: return
    if (telemetry.currentPositionMs > 0L) {
      storage.savePlaybackProgress(m, item.season, item.episode, telemetry.currentPositionMs, telemetry.durationMs)
    }
  }

  fun stepEpisode(delta: Int) {
    val targetIdx = (activePageIndex + delta).coerceIn(0, (episodes.lastIndex).coerceAtLeast(0))
    if (targetIdx == activePageIndex) return
    saveActiveEpisodeProgress()
    mediaController.stopAndDispose()
    activePageIndex = targetIdx
  }

  // Initial movie & season metadata load
  LaunchedEffect(request.movieId) {
    val loaded = catalogRepository.getMovieDetail(request.movieId) ?: movie
    val playable = if (loaded != null) catalogRepository.resolvePlayableSubject(loaded) ?: loaded else null
    movie = playable
    if (playable != null && playable.subjectId.isNotBlank()) {
      val rich = catalogRepository.getMovieBoxRichDetail(playable.subjectId)
      if (rich != null) {
        movie = rich.movie
        seasons = rich.seasons
      }
    }
    val ordered = DramaExperienceDetector.buildOrderedDramaEpisodes(movie, seasons)
    episodes = ordered
    activePageIndex =
      DramaExperienceDetector.findEpisodePageIndex(ordered, request.season, request.episode)
  }

  DisposableEffect(mediaController) {
    mediaController.onTelemetry = { telemetry = it }
    mediaController.onCompleted = {
      if (settings.autoPlayNextEpisode && activePageIndex < episodes.lastIndex) {
        stepEpisode(1)
      }
    }
    onDispose {
      saveActiveEpisodeProgress()
      mediaController.stopAndDispose()
    }
  }

  LaunchedEffect(telemetry.isPlaying, activePageIndex) {
    while (telemetry.isPlaying) {
      delay(3000L)
      saveActiveEpisodeProgress()
    }
  }

  // Load exact stream & subtitles whenever activePageIndex or movie changes
  LaunchedEffect(movie?.subjectId, activePageIndex, episodes) {
    val m = movie ?: return@LaunchedEffect
    val item = episodes.getOrNull(activePageIndex) ?: return@LaunchedEffect
    isLoadingStream = true
    errorNotice = null
    subtitleCues = emptyList()

    mediaController.stopAndDispose()

    val sid = m.subjectId
    if (sid.isBlank()) {
      errorNotice = "No playable drama stream found for ${m.title}."
      isLoadingStream = false
      return@LaunchedEffect
    }

    val streams = catalogRepository.getStreams(sid, item.season, item.episode, m.detailPath)
    val best =
      streams.firstOrNull { it.isPlayableDirect && it.qualityValue <= settings.preferredQuality }
        ?: streams.firstOrNull { it.isPlayableDirect }
        ?: streams.firstOrNull()

    val subs = catalogRepository.getSubtitles(sid, item.season, item.episode)
    if (subs.isNotEmpty()) {
      val eng = subs.firstOrNull { it.language.contains("English", true) } ?: subs.first()
      subtitleCues = catalogRepository.fetchSubtitleCues(eng.url)
    }

    if (best == null) {
      errorNotice = "Episode stream unavailable for ${item.subtitleLabel}."
    } else {
      val savedPos =
        if (item.season == request.season && item.episode == request.episode && request.startPositionMs > 0L) {
          request.startPositionMs
        } else {
          storage.getEpisodeProgress(m.id, item.season, item.episode)
        }
      mediaController.loadAndPlay(
        sourceUrlOrPath = best.url,
        startPositionMs = savedPos,
        volume = volume,
        muted = muted,
        playbackRate = playbackSpeed,
        aspectMode = PlaybackAspectMode.FIT,
      )
      // Preload next episode
      val nextItem = episodes.getOrNull(activePageIndex + 1)
      if (nextItem != null) {
        scope.launch {
          catalogRepository.preloadEpisodeResources(sid, nextItem.season, nextItem.episode, m.detailPath)
        }
      }
    }
    isLoadingStream = false
  }

  LaunchedEffect(Unit) {
    runCatching { focusRequester.requestFocus() }
  }

  val activeCue =
    remember(telemetry.currentPositionMs, subtitleCues, captionsEnabled) {
      if (captionsEnabled) SrtParser.findActiveCue(subtitleCues, telemetry.currentPositionMs) else null
    }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(DecanDesktopColors.ObsidianDeep)
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { ev ->
          if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (ev.key) {
            Key.DirectionUp, Key.PageDown -> {
              // Swipe UP / Next Episode
              stepEpisode(1)
              true
            }
            Key.DirectionDown, Key.PageUp -> {
              // Swipe DOWN / Previous Episode
              stepEpisode(-1)
              true
            }
            Key.Spacebar, Key.K -> {
              mediaController.togglePlayPause()
              true
            }
            Key.DirectionLeft -> {
              mediaController.seekTo((telemetry.currentPositionMs - 10_000L).coerceAtLeast(0L))
              true
            }
            Key.DirectionRight -> {
              mediaController.seekTo(telemetry.currentPositionMs + 10_000L)
              true
            }
            Key.C -> {
              captionsEnabled = !captionsEnabled
              true
            }
            Key.M -> {
              muted = !muted
              mediaController.setVolume(volume, muted)
              true
            }
            Key.F, Key.F11 -> {
              onToggleFullscreen()
              true
            }
            Key.Escape -> {
              if (isFullscreen) onToggleFullscreen() else onBack()
              true
            }
            else -> false
          }
        }
        .pointerInput(episodes.size, activePageIndex) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              if (event.type == PointerEventType.Scroll) {
                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                val now = System.currentTimeMillis()
                if (now - lastWheelStepMs > 380L && kotlin.math.abs(deltaY) > 0.2f) {
                  lastWheelStepMs = now
                  if (deltaY > 0f) stepEpisode(1) else stepEpisode(-1)
                }
              }
            }
          }
        }
        .testTag("desktop_vertical_drama_player"),
  ) {
    // Atmospheric backdrop behind portrait frame
    AsyncDesktopImage(
      imageUrl = movie?.backdropUrl?.ifBlank { movie?.posterUrl.orEmpty() }.orEmpty(),
      contentDescription = null,
      modifier = Modifier.fillMaxSize().alpha(0.18f),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.radialGradient(
              listOf(
                Color.Black.copy(alpha = 0.55f),
                DecanDesktopColors.ObsidianDeep.copy(alpha = 0.96f),
              )
            )
          )
    )

    // Top bar
    Row(
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.clip(CircleShape).background(DecanDesktopColors.SurfaceGlassStrong).testTag("drama_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Surface(
          color = DecanDesktopColors.Crimson,
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            text = "VERTICAL DRAMA MODE",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          )
        }
        Text(
          text = currentItem?.subtitleLabel ?: "Episode 1",
          style = MaterialTheme.typography.titleSmall,
          color = DecanDesktopColors.Mist,
          modifier = Modifier.testTag("drama_episode_label"),
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
          onClick = {
            val item = currentItem
            if (movie != null && item != null) {
              saveActiveEpisodeProgress()
              onSwitchToStandardPlayer(movie!!.id, item.season, item.episode, telemetry.currentPositionMs)
            }
          },
          border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
          Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Theater Mode")
        }

        IconButton(onClick = onToggleFullscreen) {
          Icon(
            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = "Fullscreen",
            tint = Color.White,
          )
        }
      }
    }

    // Center Portrait 9:16 Drama Stage + Right Rail + Navigation Step Controls
    Row(
      modifier = Modifier.align(Alignment.Center).fillMaxHeight(0.86f).padding(top = 24.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      // Portrait 9:16 Stage
      Surface(
        color = Color.Black,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, DecanDesktopColors.OutlineGlass),
        modifier =
          Modifier.fillMaxHeight()
            .aspectRatio(9f / 16f)
            .pointerInput(episodes.size, activePageIndex) {
              detectVerticalDragGestures(
                onDragStart = { dragAccumulator = 0f },
                onVerticalDrag = { _, dragAmount -> dragAccumulator += dragAmount },
                onDragEnd = {
                  if (dragAccumulator <= -70f) stepEpisode(1)
                  else if (dragAccumulator >= 70f) stepEpisode(-1)
                  dragAccumulator = 0f
                },
              )
            },
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          DesktopVideoSurface(
            controller = mediaController,
            aspectMode = PlaybackAspectMode.FIT,
            modifier = Modifier.fillMaxSize(),
          )

          if (isLoadingStream || (telemetry.isBuffering && errorNotice == null && telemetry.errorMessage == null)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = DecanDesktopColors.Crimson)
            }
          }

          val err = errorNotice ?: telemetry.errorMessage
          if (err != null) {
            Box(
              modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).padding(20.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(err, color = DecanDesktopColors.Mist, textAlign = TextAlign.Center)
            }
          }

          if (activeCue != null) {
            Box(
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 148.dp, start = 16.dp, end = 16.dp),
            ) {
              Surface(color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp)) {
                Text(
                  text = activeCue.text,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = Color.White,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }
          }

          // Bottom Episode Overlay inside Portrait Frame
          Column(
            modifier =
              Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = currentItem?.title ?: movie?.title.orEmpty(),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = currentItem?.description ?: movie?.synopsis.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = DecanDesktopColors.Mist.copy(alpha = 0.85f),
              maxLines = if (expandSynopsis) 6 else 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.clickable { expandSynopsis = !expandSynopsis },
            )

            val dur = telemetry.durationMs.coerceAtLeast(1L)
            Slider(
              value = (telemetry.currentPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
              onValueChange = { pct -> mediaController.seekTo((pct * dur).toLong()) },
              colors = SliderDefaults.colors(
                thumbColor = DecanDesktopColors.Crimson,
                activeTrackColor = DecanDesktopColors.Crimson,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
              ),
              modifier = Modifier.fillMaxWidth().height(22.dp),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { mediaController.togglePlayPause() }, modifier = Modifier.size(34.dp)) {
                  Icon(
                    imageVector = if (telemetry.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or Pause",
                    tint = Color.White,
                  )
                }
                Text(
                  text = "${formatTimeMs(telemetry.currentPositionMs)} / ${formatTimeMs(telemetry.durationMs)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color.White,
                )
              }

              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {
                  muted = !muted
                  mediaController.setVolume(volume, muted)
                }, modifier = Modifier.size(32.dp)) {
                  Icon(
                    imageVector = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                  )
                }
                IconButton(onClick = { captionsEnabled = !captionsEnabled }, modifier = Modifier.size(32.dp)) {
                  Icon(
                    imageVector = Icons.Filled.ClosedCaption,
                    contentDescription = "Captions",
                    tint = if (captionsEnabled) DecanDesktopColors.CrimsonBright else Color.White,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Surface(
                  color = DecanDesktopColors.SurfaceGlass,
                  shape = RoundedCornerShape(6.dp),
                  modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                    playbackSpeed = if (playbackSpeed >= 2.0f) 1.0f else playbackSpeed + 0.25f
                    mediaController.setPlaybackRate(playbackSpeed)
                  },
                ) {
                  Text(
                    text = "${playbackSpeed}x",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  )
                }
              }
            }
          }
        }
      }

      // TikTok-inspired Right Action Rail + Episode Step Buttons
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          color = DecanDesktopColors.SurfaceElevated,
          shape = CircleShape,
          border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
          modifier =
            Modifier.size(48.dp)
              .clip(CircleShape)
              .clickable(enabled = activePageIndex > 0) { stepEpisode(-1) }
              .testTag("drama_prev_episode_btn"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Filled.KeyboardArrowUp,
              contentDescription = "Previous Episode (Swipe Down)",
              tint = if (activePageIndex > 0) Color.White else DecanDesktopColors.Slate.copy(alpha = 0.3f),
            )
          }
        }

        Surface(
          color = DecanDesktopColors.Crimson,
          shape = CircleShape,
          modifier =
            Modifier.size(48.dp)
              .clip(CircleShape)
              .clickable(enabled = activePageIndex < episodes.lastIndex) { stepEpisode(1) }
              .testTag("drama_next_episode_btn"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Filled.KeyboardArrowDown,
              contentDescription = "Next Episode (Swipe Up)",
              tint = Color.White,
            )
          }
        }

        Spacer(Modifier.height(6.dp))

        DramaActionButton(
          icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
          label = "Like",
          active = liked,
          onClick = {
            liked = !liked
            movie?.let { m ->
              val existing = storage.getWatchlistEntry(m.id)
              storage.saveEntry(
                movie = m,
                watchStatus = existing?.watchStatus ?: WatchStatus.WATCHING,
                favorite = liked,
                personalRating = existing?.personalRating ?: 0,
                personalNote = existing?.personalNote ?: "",
              )
            }
          },
        )

        DramaActionButton(
          icon = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
          label = if (isInWatchlist) "Saved" else "Save",
          active = isInWatchlist,
          onClick = { movie?.let(onToggleWatchlist) },
        )

        DramaActionButton(
          icon = Icons.Filled.List,
          label = "Eps (${episodes.size})",
          active = showEpisodeDrawer,
          onClick = { showEpisodeDrawer = !showEpisodeDrawer },
        )

        DramaActionButton(
          icon = Icons.Filled.Share,
          label = "Share",
          active = false,
          onClick = {
            val label = "${movie?.title ?: "Drama"} (${currentItem?.subtitleLabel ?: "Ep 1"})"
            clipboard.setText(AnnotatedString("Watch $label on Decan Movie Desktop"))
            onNotify("Copied drama share info to clipboard!")
          },
        )
      }
    }

    // Drama Episodes Drawer
    if (showEpisodeDrawer) {
      Surface(
        color = DecanDesktopColors.SurfaceGlassStrong,
        border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
        modifier = Modifier.align(Alignment.CenterEnd).width(320.dp).fillMaxHeight(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Drama Episodes", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
            OutlinedButton(onClick = { showEpisodeDrawer = false }) { Text("Close") }
          }
          LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val grouped = episodes.groupBy { it.season }.toSortedMap()
            items(grouped.keys.toList()) { seasonNum ->
              val seasonEps = grouped[seasonNum].orEmpty()
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Season $seasonNum", style = MaterialTheme.typography.titleSmall, color = DecanDesktopColors.Slate)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  seasonEps.forEach { epItem ->
                    val active = epItem.globalIndex == activePageIndex
                    Surface(
                      color = if (active) DecanDesktopColors.Crimson else DecanDesktopColors.SurfaceElevated,
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.size(width = 48.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        showEpisodeDrawer = false
                        saveActiveEpisodeProgress()
                        mediaController.stopAndDispose()
                        activePageIndex = epItem.globalIndex
                      },
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("${epItem.episode}", color = Color.White, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DramaActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  active: Boolean,
  onClick: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Surface(
      color = DecanDesktopColors.SurfaceGlass,
      shape = CircleShape,
      border = BorderStroke(1.dp, if (active) DecanDesktopColors.CrimsonBright else DecanDesktopColors.OutlineGlass),
      modifier = Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = if (active) DecanDesktopColors.CrimsonBright else Color.White,
          modifier = Modifier.size(21.dp),
        )
      }
    }
    Text(label, style = MaterialTheme.typography.bodySmall, color = DecanDesktopColors.Mist)
  }
}
