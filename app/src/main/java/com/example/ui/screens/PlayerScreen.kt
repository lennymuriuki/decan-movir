package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.download.StreamDownloadManager
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxDub
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.data.remote.moviebox.SrtParser
import com.example.data.remote.moviebox.SubtitleCue
import com.example.data.settings.AppSettings
import com.example.data.settings.PlaybackProgressStore
import com.example.ui.components.BatchDownloadSheet
import com.example.ui.components.MoviePosterImage
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
  movieId: String,
  initialSeason: Int = 0,
  initialEpisode: Int = 0,
  initialPositionMs: Long = 0L,
  localPlayUri: String? = null,
  localTitle: String? = null,
  savedEntry: SavedMovieEntry?,
  catalogRepository: MovieCatalogRepository,
  appSettings: AppSettings,
  progressStore: PlaybackProgressStore? = null,
  onBack: () -> Unit,
  onSwitchMovie: (String) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val activity = context as? Activity
  val scope = rememberCoroutineScope()

  var activeMovieId by remember(movieId) { mutableStateOf(movieId) }
  var movie by remember(activeMovieId) {
    mutableStateOf(catalogRepository.getCachedMovie(activeMovieId) ?: savedEntry?.toMovie())
  }
  var dubs by remember(activeMovieId) { mutableStateOf<List<MovieBoxDub>>(emptyList()) }
  var seasons by remember(activeMovieId) { mutableStateOf<List<MovieBoxSeason>>(emptyList()) }
  var selectedSeason by remember(activeMovieId) { mutableIntStateOf(initialSeason) }
  var selectedEpisode by remember(activeMovieId) { mutableIntStateOf(initialEpisode) }
  var streams by remember(activeMovieId) { mutableStateOf<List<MovieBoxStream>>(emptyList()) }
  var subtitles by remember(activeMovieId) { mutableStateOf<List<MovieBoxSubtitle>>(emptyList()) }
  var recommendations by remember(activeMovieId) { mutableStateOf<List<Movie>>(emptyList()) }

  var activeStream by remember { mutableStateOf<MovieBoxStream?>(null) }
  var activeSubtitle by remember { mutableStateOf<MovieBoxSubtitle?>(null) }
  var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
  var isLoadingMedia by remember { mutableStateOf(localPlayUri.isNullOrBlank()) }

  var isFullscreen by remember { mutableStateOf(false) }
  var isLocked by remember { mutableStateOf(false) }
  var controlsVisible by remember { mutableStateOf(true) }
  var zoomFill by remember { mutableStateOf(false) }
  var playbackSpeed by remember { mutableFloatStateOf(appSettings.defaultPlaybackSpeed) }
  var showSettingsSheet by remember { mutableStateOf(false) }
  var showSubtitleSheet by remember { mutableStateOf(false) }
  var showBatchDownloadSheet by remember { mutableStateOf(false) }

  // MediaPlayer runtime state
  var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var isBuffering by remember { mutableStateOf(true) }
  var playerError by remember { mutableStateOf<String?>(null) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var seekDragMs by remember { mutableStateOf<Long?>(null) }
  var pendingSeekMs by remember(movieId) {
    val savedResume =
      if (initialPositionMs > 0L) initialPositionMs
      else progressStore?.getProgress(movieId)?.positionMs ?: 0L
    mutableLongStateOf(savedResume)
  }
  var videoWidth by remember { mutableIntStateOf(16) }
  var videoHeight by remember { mutableIntStateOf(9) }
  var seekFeedbackText by remember { mutableStateOf<String?>(null) }
  var surfaceHolderRef by remember { mutableStateOf<SurfaceHolder?>(null) }
  var upNextDismissed by remember(selectedSeason, selectedEpisode, activeMovieId) { mutableStateOf(false) }

  // Fullscreen orientation & immersive system bar management
  LaunchedEffect(isFullscreen) {
    if (activity != null) {
      activity.requestedOrientation =
        if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      val window = activity.window
      if (window != null) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
          controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
          controller.show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      val currentM = movie
      if (localPlayUri.isNullOrBlank() && currentM != null && currentPositionMs > 3_000L) {
        progressStore?.saveProgress(
          movieId = currentM.id,
          title = currentM.title,
          posterUrl = currentM.posterUrl,
          mediaType = currentM.mediaType,
          season = selectedSeason,
          episode = selectedEpisode,
          positionMs = currentPositionMs,
          durationMs = durationMs,
        )
      }
      if (activity != null) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.window?.let { window ->
          WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
      }
      runCatching {
        mediaPlayer?.stop()
        mediaPlayer?.release()
      }
      mediaPlayer = null
    }
  }

  BackHandler(enabled = isFullscreen) {
    isFullscreen = false
  }

  suspend fun loadSubtitlesForEpisode(subList: List<MovieBoxSubtitle>) {
    subtitles = subList
    if (appSettings.autoLoadSubtitles && subList.isNotEmpty()) {
      val preferred =
        subList.firstOrNull { it.language.contains("English", ignoreCase = true) } ?: subList.first()
      activeSubtitle = preferred
      subtitleCues = catalogRepository.fetchSubtitleCues(preferred.url)
    } else {
      activeSubtitle = null
      subtitleCues = emptyList()
    }
  }

  suspend fun resolveEpisodeOrMovieStreams(sid: String, se: Int, ep: Int, detailPath: String?) {
    if (sid.isBlank()) return
    isLoadingMedia = true
    playerError = null
    val streamsDef = scope.async { catalogRepository.getStreams(sid, se, ep, detailPath) }
    val subsDef = scope.async { catalogRepository.getSubtitles(sid, se, ep) }
    val resolvedStreams = streamsDef.await()
    val resolvedSubs = subsDef.await()
    streams = resolvedStreams
    val playable = resolvedStreams.filter { it.isPlayableDirect }
    val preferred =
      playable.firstOrNull { it.qualityValue == appSettings.preferredQuality }
        ?: playable.minByOrNull { kotlin.math.abs(it.qualityValue - appSettings.preferredQuality) }
        ?: playable.firstOrNull()
    activeStream = preferred
    loadSubtitlesForEpisode(resolvedSubs)
    isLoadingMedia = false
    if (preferred == null) {
      val trailer = movie?.previewUrl
      if (trailer.isNullOrBlank()) {
        playerError = "No direct MP4 stream returned for this episode/title."
      }
    }
  }

  // Initial catalog, detail, streams, subtitles & recommendations load
  LaunchedEffect(activeMovieId, localPlayUri) {
    if (!localPlayUri.isNullOrBlank()) {
      isLoadingMedia = false
      return@LaunchedEffect
    }
    isLoadingMedia = true
    val enriched = catalogRepository.getMovieDetail(activeMovieId)
    if (enriched != null) movie = enriched

    val sid =
      enriched?.subjectId?.takeIf { it.isNotBlank() }
        ?: activeMovieId.takeIf { it.startsWith("mbox_") }?.removePrefix("mbox_")
        ?: ""

    val recDeferred = scope.async {
      catalogRepository.getRecommendations(sid, enriched?.genre)
    }

    if (sid.isNotBlank()) {
      val rich = catalogRepository.getMovieBoxRichDetail(sid)
      if (rich != null) {
        movie = rich.movie
        dubs = rich.dubs
        seasons = rich.seasons
        val se =
          if (rich.seasons.isNotEmpty()) {
            if (selectedSeason > 0) selectedSeason else rich.seasons.first().season
          } else 0
        val ep =
          if (rich.seasons.isNotEmpty()) {
            if (selectedEpisode > 0) selectedEpisode else 1
          } else 0
        selectedSeason = se
        selectedEpisode = ep
        resolveEpisodeOrMovieStreams(sid, se, ep, rich.detailPath)
      } else {
        resolveEpisodeOrMovieStreams(sid, selectedSeason, selectedEpisode, enriched?.detailPath)
      }
    } else {
      isLoadingMedia = false
    }
    recommendations = recDeferred.await()
  }

  val playbackUrl =
    localPlayUri?.takeIf { it.isNotBlank() }
      ?: activeStream?.url
      ?: movie?.previewUrl

  val playbackHeaders =
    if (!localPlayUri.isNullOrBlank()) emptyMap() else activeStream?.headers.orEmpty()

  fun switchToEpisode(newSeason: Int, newEpisode: Int) {
    val sid = movie?.subjectId.orEmpty()
    if (sid.isBlank()) return
    selectedSeason = newSeason
    selectedEpisode = newEpisode
    pendingSeekMs = 0L
    currentPositionMs = 0L
    scope.launch {
      resolveEpisodeOrMovieStreams(sid, newSeason, newEpisode, movie?.detailPath)
    }
  }

  fun goToNextEpisode() {
    val currentSeasonObj = seasons.firstOrNull { it.season == selectedSeason }
    if (currentSeasonObj != null) {
      if (selectedEpisode < currentSeasonObj.episodeCount) {
        switchToEpisode(selectedSeason, selectedEpisode + 1)
      } else {
        val nextSeason = seasons.firstOrNull { it.season > selectedSeason }
        if (nextSeason != null) {
          switchToEpisode(nextSeason.season, 1)
        } else {
          recommendations.firstOrNull()?.let { onSwitchMovie(it.id) }
        }
      }
    } else {
      recommendations.firstOrNull()?.let { onSwitchMovie(it.id) }
    }
  }

  fun goToPreviousEpisode() {
    val currentSeasonObj = seasons.firstOrNull { it.season == selectedSeason }
    if (currentSeasonObj != null && selectedEpisode > 1) {
      switchToEpisode(selectedSeason, selectedEpisode - 1)
    } else {
      val prevSeason = seasons.lastOrNull { it.season < selectedSeason }
      if (prevSeason != null) {
        switchToEpisode(prevSeason.season, prevSeason.episodeCount)
      } else {
        mediaPlayer?.let { mp ->
          runCatching {
            mp.seekTo(0)
            currentPositionMs = 0L
          }
        }
      }
    }
  }

  // Prepare / reload MediaPlayer whenever playbackUrl or SurfaceHolder changes
  LaunchedEffect(playbackUrl, surfaceHolderRef) {
    val holder = surfaceHolderRef ?: return@LaunchedEffect
    val url = playbackUrl ?: return@LaunchedEffect

    if (currentPositionMs > 2_000L && pendingSeekMs == 0L) {
      pendingSeekMs = currentPositionMs
    }
    runCatching {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    }
    mediaPlayer = null
    isBuffering = true
    playerError = null

    val mp = MediaPlayer()
    mediaPlayer = mp
    try {
      val uri = Uri.parse(url)
      if (playbackHeaders.isNotEmpty()) {
        mp.setDataSource(context, uri, playbackHeaders)
      } else {
        mp.setDataSource(context, uri)
      }
      mp.setDisplay(holder)
      mp.setScreenOnWhilePlaying(true)
      mp.setOnVideoSizeChangedListener { _, w, h ->
        if (w > 0 && h > 0) {
          videoWidth = w
          videoHeight = h
        }
      }
      mp.setOnPreparedListener { prepared ->
        isBuffering = false
        durationMs = prepared.duration.toLong().coerceAtLeast(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
          runCatching {
            prepared.playbackParams = prepared.playbackParams.setSpeed(playbackSpeed)
          }
        }
        if (pendingSeekMs > 0L) {
          runCatching { prepared.seekTo(pendingSeekMs.toInt()) }
          pendingSeekMs = 0L
        }
        prepared.start()
        isPlaying = true
      }
      mp.setOnCompletionListener {
        isPlaying = false
        if (appSettings.autoPlayNextEpisode && seasons.isNotEmpty()) {
          goToNextEpisode()
        } else {
          controlsVisible = true
        }
      }
      mp.setOnInfoListener { _, what, _ ->
        when (what) {
          MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
          MediaPlayer.MEDIA_INFO_BUFFERING_END,
          MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
        }
        false
      }
      mp.setOnErrorListener { _, what, extra ->
        isBuffering = false
        isPlaying = false
        playerError = "Playback failed (code $what/$extra). Try switching quality or external player."
        controlsVisible = true
        true
      }
      mp.prepareAsync()
    } catch (e: Exception) {
      isBuffering = false
      isPlaying = false
      playerError = e.localizedMessage ?: "Unable to initialize stream"
    }
  }

  // Apply playback speed changes live
  LaunchedEffect(playbackSpeed) {
    val mp = mediaPlayer ?: return@LaunchedEffect
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      runCatching {
        val wasPlaying = mp.isPlaying
        mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
        if (!wasPlaying && mp.isPlaying) {
          mp.pause()
        }
      }
    }
  }

  // Poll MediaPlayer currentPosition every 250ms for seekbar + live SRT subtitles + periodic progress save
  LaunchedEffect(mediaPlayer, isPlaying) {
    var tickCount = 0
    while (true) {
      val mp = mediaPlayer
      if (mp != null) {
        runCatching {
          if (mp.isPlaying) {
            currentPositionMs = mp.currentPosition.toLong()
            if (durationMs <= 0L) {
              durationMs = mp.duration.toLong().coerceAtLeast(0L)
            }
            tickCount++
            if (tickCount % 20 == 0 && localPlayUri.isNullOrBlank()) {
              val m = movie
              if (m != null && currentPositionMs > 3_000L) {
                progressStore?.saveProgress(
                  movieId = m.id,
                  title = m.title,
                  posterUrl = m.posterUrl,
                  mediaType = m.mediaType,
                  season = selectedSeason,
                  episode = selectedEpisode,
                  positionMs = currentPositionMs,
                  durationMs = durationMs,
                )
              }
            }
          }
        }
      }
      delay(250L)
    }
  }

  // Auto-hide controls after 3.5s while playing
  LaunchedEffect(controlsVisible, isPlaying, isLocked) {
    if (controlsVisible && isPlaying && !isLocked) {
      delay(3500L)
      controlsVisible = false
    }
  }

  // Clear seek feedback badge after 800ms
  LaunchedEffect(seekFeedbackText) {
    if (seekFeedbackText != null) {
      delay(800L)
      seekFeedbackText = null
    }
  }

  val activeCue =
    remember(subtitleCues, currentPositionMs) {
      SrtParser.findActiveCue(subtitleCues, currentPositionMs)
    }

  val displayTitle = localTitle?.takeIf { it.isNotBlank() } ?: movie?.title ?: "Decan Theater"
  val episodeBadge =
    if (selectedSeason > 0 && selectedEpisode > 0) "S${selectedSeason} E${selectedEpisode}" else null

  Column(
    modifier =
      modifierOrBaseFillMaxSize()
        .background(Color.Black)
  ) {
    // TOP PINNED YOUTUBE/NETFLIX VIDEO WINDOW (16:9 in Portrait, FillMaxSize in Fullscreen Landscape)
    BoxWithConstraints(
      modifier =
        if (isFullscreen) {
          Modifier.fillMaxSize().background(Color.Black)
        } else {
          Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .aspectRatio(16f / 9f)
            .background(Color.Black)
        },
      contentAlignment = Alignment.Center,
    ) {
      val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
      val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
      val videoRatio =
        if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat()
        else 16f / 9f
      val containerRatio = containerW / containerH

      val surfaceModifier =
        if (zoomFill) {
          if (videoRatio > containerRatio) {
            Modifier.fillMaxHeight().aspectRatio(videoRatio)
          } else {
            Modifier.fillMaxWidth().aspectRatio(videoRatio)
          }
        } else {
          if (videoRatio > containerRatio) {
            Modifier.fillMaxWidth().aspectRatio(videoRatio)
          } else {
            Modifier.fillMaxHeight().aspectRatio(videoRatio)
          }
        }

      Box(
        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
      ) {
        if (playbackUrl != null && playerError == null) {
          AndroidView(
            factory = { ctx ->
              SurfaceView(ctx).apply {
                holder.addCallback(
                  object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                      surfaceHolderRef = h
                    }

                    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hgt: Int) {
                      surfaceHolderRef = h
                    }

                    override fun surfaceDestroyed(h: SurfaceHolder) {
                      if (surfaceHolderRef == h) {
                        surfaceHolderRef = null
                      }
                    }
                  }
                )
              }
            },
            modifier = surfaceModifier,
          )
        }
      }

      // Tap / Double-Tap Gesture Surface
      Box(
        modifier =
          Modifier.fillMaxSize().pointerInput(isLocked, mediaPlayer, durationMs) {
            detectTapGestures(
              onTap = { controlsVisible = !controlsVisible },
              onDoubleTap = { offset ->
                if (isLocked) return@detectTapGestures
                val mp = mediaPlayer ?: return@detectTapGestures
                val delta = if (offset.x < size.width / 2f) -10_000L else 10_000L
                val target = (currentPositionMs + delta).coerceIn(0L, durationMs.coerceAtLeast(0L))
                runCatching {
                  mp.seekTo(target.toInt())
                  currentPositionMs = target
                }
                seekFeedbackText = if (delta < 0) "-10s" else "+10s"
              },
            )
          }
      )

      // Loading / Error States
      if (isLoadingMedia || isBuffering) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(44.dp),
        )
      }

      if (playerError != null) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(20.dp),
        ) {
          Text(
            text = playerError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (playbackUrl != null) {
              OutlinedButton(
                onClick = {
                  runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playbackUrl)))
                  }
                }
              ) {
                Text("Open External")
              }
            }
            FilledTonalButton(onClick = { showSettingsSheet = true }) {
              Text("Switch Quality")
            }
          }
        }
      }

      // Synchronized On-Screen .srt Caption Renderer
      if (activeCue != null) {
        Surface(
          color = Color.Black.copy(alpha = 0.72f),
          shape = MaterialTheme.shapes.extraSmall,
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .padding(
                start = 24.dp,
                end = 24.dp,
                bottom = if (controlsVisible) 68.dp else 18.dp,
              ),
        ) {
          Text(
            text = activeCue.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Double-tap Seek Visual Badge
      androidx.compose.animation.AnimatedVisibility(
        visible = seekFeedbackText != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.72f),
          shape = CircleShape,
        ) {
          Text(
            text = seekFeedbackText.orEmpty(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
          )
        }
      }

      // Up-Next Episode Countdown Pill (last 15s when auto-play enabled)
      val currentSeasonObjForNext = seasons.firstOrNull { it.season == selectedSeason }
      val hasNextEpInSeries =
        seasons.isNotEmpty() &&
          ((currentSeasonObjForNext != null && selectedEpisode < currentSeasonObjForNext.episodeCount) ||
            seasons.any { it.season > selectedSeason })
      val remainingSec = ((durationMs - currentPositionMs) / 1000L).toInt()
      if (
        appSettings.autoPlayNextEpisode &&
          hasNextEpInSeries &&
          !upNextDismissed &&
          durationMs > 30_000L &&
          remainingSec in 1..15 &&
          isPlaying
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.85f),
          shape = MaterialTheme.shapes.large,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(end = 14.dp, bottom = if (controlsVisible) 66.dp else 16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Next Ep in ${remainingSec}s",
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
            )
            TextButton(onClick = { goToNextEpisode() }) {
              Text("Play Now", color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { upNextDismissed = true }) {
              Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
          }
        }
      }

      // Screen Lock Floating Unlock Button
      if (isLocked && controlsVisible) {
        FilledTonalButton(
          onClick = { isLocked = false },
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Unlock screen",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Screen Locked • Tap to Unlock")
        }
      }

      // Full Netflix / YouTube Playback Controls Overlay
      androidx.compose.animation.AnimatedVisibility(
        visible = controlsVisible && !isLocked,
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
                      Color.Black.copy(alpha = 0.72f),
                      Color.Black.copy(alpha = 0.25f),
                      Color.Black.copy(alpha = 0.82f),
                    )
                )
              )
              .padding(horizontal = if (isFullscreen) 20.dp else 10.dp, vertical = 8.dp)
        ) {
          // TOP CONTROL BAR (Back, Title + S/E, CC, Settings/Quality/Speed, Zoom, External)
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
                  if (isFullscreen) isFullscreen = false else onBack()
                },
                modifier = Modifier.testTag("player_back_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = if (isFullscreen) "Exit fullscreen" else "Back",
                  tint = Color.White,
                )
              }

              Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                  text = displayTitle,
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val subLine =
                  listOfNotNull(
                    episodeBadge,
                    activeStream?.quality,
                    if (playbackSpeed != 1.0f) "${playbackSpeed}x" else null,
                  ).joinToString(" • ")
                if (subLine.isNotBlank()) {
                  Text(
                    text = subLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                  )
                }
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { showSubtitleSheet = true },
                modifier = Modifier.testTag("player_cc_button"),
              ) {
                Icon(
                  imageVector =
                    if (activeSubtitle != null) Icons.Filled.ClosedCaption
                    else Icons.Filled.ClosedCaptionOff,
                  contentDescription = "Subtitles (CC)",
                  tint = if (activeSubtitle != null) MaterialTheme.colorScheme.primary else Color.White,
                )
              }

              IconButton(
                onClick = { showSettingsSheet = true },
                modifier = Modifier.testTag("player_settings_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Settings,
                  contentDescription = "Quality & Speed settings",
                  tint = Color.White,
                )
              }

              if (isFullscreen) {
                IconButton(onClick = { zoomFill = !zoomFill }) {
                  Icon(
                    imageVector = Icons.Filled.AspectRatio,
                    contentDescription = "Toggle aspect fit/fill",
                    tint = if (zoomFill) MaterialTheme.colorScheme.primary else Color.White,
                  )
                }
              }

              if (playbackUrl != null) {
                IconButton(
                  onClick = {
                    runCatching {
                      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playbackUrl)))
                    }
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

          // CENTER TRANSPORT CONTROLS (Previous, -10s, Play/Pause, +10s, Next)
          Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 26.dp else 10.dp),
          ) {
            IconButton(
              onClick = { goToPreviousEpisode() },
              enabled = seasons.isNotEmpty() || currentPositionMs > 2000L,
              modifier = Modifier.testTag("player_prev_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
              )
            }

            IconButton(
              onClick = {
                val mp = mediaPlayer ?: return@IconButton
                val target = (currentPositionMs - 10_000L).coerceAtLeast(0L)
                runCatching {
                  mp.seekTo(target.toInt())
                  currentPositionMs = target
                }
                seekFeedbackText = "-10s"
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
                val mp = mediaPlayer ?: return@FilledTonalIconButton
                runCatching {
                  if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                  } else {
                    mp.start()
                    isPlaying = true
                  }
                }
              },
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.size(56.dp).testTag("player_play_pause_button"),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp),
              )
            }

            IconButton(
              onClick = {
                val mp = mediaPlayer ?: return@IconButton
                val target = (currentPositionMs + 10_000L).coerceAtMost(durationMs.coerceAtLeast(0L))
                runCatching {
                  mp.seekTo(target.toInt())
                  currentPositionMs = target
                }
                seekFeedbackText = "+10s"
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
              onClick = { goToNextEpisode() },
              enabled = seasons.isNotEmpty() || recommendations.isNotEmpty(),
              modifier = Modifier.testTag("player_next_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
              )
            }
          }

          // BOTTOM SEEKBAR & ACTIONS (Time, Slider, Lock, Download, Fullscreen Landscape Toggle)
          Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
          ) {
            val shownPos = seekDragMs ?: currentPositionMs
            val sliderValue =
              if (durationMs > 0L) (shownPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
              else 0f

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = formatPlaybackTime(shownPos),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )

              Slider(
                value = sliderValue,
                onValueChange = { fraction ->
                  if (durationMs > 0L) {
                    seekDragMs = (fraction * durationMs).toLong()
                  }
                },
                onValueChangeFinished = {
                  val target = seekDragMs
                  seekDragMs = null
                  if (target != null) {
                    runCatching {
                      mediaPlayer?.seekTo(target.toInt())
                      currentPositionMs = target
                    }
                  }
                },
                colors =
                  SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                  ),
                modifier = Modifier.weight(1f).height(26.dp),
              )

              Text(
                text = formatPlaybackTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
              )

              IconButton(
                onClick = {
                  isLocked = true
                  controlsVisible = true
                },
                modifier = Modifier.size(36.dp).testTag("player_lock_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.LockOpen,
                  contentDescription = "Lock controls",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp),
                )
              }

              if (movie != null && localPlayUri.isNullOrBlank()) {
                IconButton(
                  onClick = { showBatchDownloadSheet = true },
                  modifier = Modifier.size(36.dp).testTag("player_download_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Batch download",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }

              IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier.size(36.dp).testTag("player_fullscreen_button"),
              ) {
                Icon(
                  imageVector =
                    if (isFullscreen) Icons.Filled.FullscreenExit
                    else Icons.Filled.Fullscreen,
                  contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen landscape",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp),
                )
              }
            }
          }
        }
      }
    }

    // PORTRAIT CONTENT AREA BELOW PINNED VIDEO (Movie Info, Episodes, Dubs, Recommendations)
    if (!isFullscreen) {
      val currentMovie = movie
      LazyColumn(
        modifier =
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top,
            ) {
              Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = displayTitle,
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                val metaParts =
                  listOfNotNull(
                    episodeBadge,
                    currentMovie?.releaseYear?.takeIf { it.isNotBlank() },
                    currentMovie?.genre?.takeIf { it.isNotBlank() },
                    currentMovie?.imdbRating?.let { "IMDb $it" },
                  )
                if (metaParts.isNotEmpty()) {
                  Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            // Quick YouTube/Netflix Action Chips Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              item {
                FilledTonalButton(
                  onClick = { showSettingsSheet = true },
                  shape = MaterialTheme.shapes.large,
                ) {
                  Icon(imageVector = Icons.Filled.Hd, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(activeStream?.quality ?: "Quality")
                }
              }
              item {
                FilledTonalButton(
                  onClick = { showSubtitleSheet = true },
                  shape = MaterialTheme.shapes.large,
                ) {
                  Icon(imageVector = Icons.Filled.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(activeSubtitle?.language ?: "CC (${subtitles.size})")
                }
              }
              if (currentMovie != null && localPlayUri.isNullOrBlank()) {
                item {
                  FilledTonalButton(
                    onClick = { showBatchDownloadSheet = true },
                    shape = MaterialTheme.shapes.large,
                  ) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (seasons.isNotEmpty()) "Batch Download" else "Download")
                  }
                }
                item {
                  OutlinedButton(
                    onClick = { onToggleWatchlist(currentMovie) },
                    shape = MaterialTheme.shapes.large,
                  ) {
                    Icon(
                      imageVector =
                        if (savedEntry != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (savedEntry != null) "Saved" else "Watchlist")
                  }
                }
                item {
                  OutlinedButton(
                    onClick = {
                      val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                          type = "text/plain"
                          putExtra(Intent.EXTRA_TEXT, "Watching \"$displayTitle\" on Decan Movie!")
                        }
                      context.startActivity(Intent.createChooser(shareIntent, "Share"))
                    },
                    shape = MaterialTheme.shapes.large,
                  ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                  }
                }
              }
            }

            if (!currentMovie?.synopsis.isNullOrBlank()) {
              Text(
                text = currentMovie?.synopsis.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        // Dub switcher
        if (dubs.isNotEmpty()) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                text = "Audio Dubs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dubs, key = { it.subjectId }) { dub ->
                  FilterChip(
                    selected = dub.subjectId == currentMovie?.subjectId,
                    onClick = { activeMovieId = "mbox_${dub.subjectId}" },
                    label = { Text(dub.langName) },
                  )
                }
              }
            }
          }
        }

        // Episodes Selector Strip
        if (seasons.isNotEmpty()) {
          item {
            val activeSeasonObj =
              seasons.firstOrNull { it.season == selectedSeason } ?: seasons.first()
            Card(
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
              shape = MaterialTheme.shapes.large,
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Episodes • Season ${activeSeasonObj.season}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  TextButton(onClick = { showBatchDownloadSheet = true }) {
                    Text("Multi-Download")
                  }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items(seasons, key = { it.season }) { seasonObj ->
                    FilterChip(
                      selected = seasonObj.season == selectedSeason,
                      onClick = { switchToEpisode(seasonObj.season, 1) },
                      label = { Text("S${seasonObj.season}") },
                      colors =
                        FilterChipDefaults.filterChipColors(
                          selectedContainerColor = MaterialTheme.colorScheme.primary,
                          selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                  }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  items((1..activeSeasonObj.episodeCount).toList(), key = { it }) { ep ->
                    val current = ep == selectedEpisode
                    Surface(
                      color =
                        if (current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                      shape = MaterialTheme.shapes.small,
                      modifier =
                        Modifier.clickable { switchToEpisode(selectedSeason, ep) },
                    ) {
                      Text(
                        text = if (current) "▶ Ep $ep" else "Ep $ep",
                        style = MaterialTheme.typography.labelLarge,
                        color =
                          if (current) MaterialTheme.colorScheme.onPrimary
                          else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Up Next & Recommendations
        if (recommendations.isNotEmpty()) {
          item {
            Text(
              text = "Recommended Next",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
          }
          items(recommendations, key = { "rec_${it.id}" }) { rec ->
            Card(
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
              shape = MaterialTheme.shapes.medium,
              modifier =
                Modifier.fillMaxWidth().clickable {
                  pendingSeekMs = 0L
                  currentPositionMs = 0L
                  selectedSeason = 0
                  selectedEpisode = 0
                  activeMovieId = rec.id
                },
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                MoviePosterImage(
                  imageUrl = rec.posterUrl,
                  contentDescription = rec.title,
                  modifier =
                    Modifier.width(72.dp).aspectRatio(16f / 10f).clip(MaterialTheme.shapes.small),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(
                    text = rec.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = listOfNotNull(rec.mediaType, rec.genre, rec.releaseYear).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                  )
                }
                Icon(
                  imageVector = Icons.Filled.PlayArrow,
                  contentDescription = "Play recommendation",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }

  // Quality, Speed & Aspect Ratio Settings Bottom Sheet
  if (showSettingsSheet) {
    ModalBottomSheet(
      onDismissRequest = { showSettingsSheet = false },
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Playback Settings",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )

        if (streams.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(imageVector = Icons.Filled.Hd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text("Video Quality", style = MaterialTheme.typography.titleSmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              streams.filter { it.isPlayableDirect }.forEach { st ->
                val selected = activeStream?.url == st.url
                FilterChip(
                  selected = selected,
                  onClick = {
                    pendingSeekMs = currentPositionMs
                    activeStream = st
                    showSettingsSheet = false
                    onNotify("Switched to ${st.quality}")
                  },
                  label = {
                    Text("${st.quality}${if (st.sizeMb > 0) " (${st.sizeMb}MB)" else ""}")
                  },
                )
              }
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(imageVector = Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Playback Speed", style = MaterialTheme.typography.titleSmall)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
              FilterChip(
                selected = playbackSpeed == spd,
                onClick = {
                  playbackSpeed = spd
                  showSettingsSheet = false
                },
                label = { Text("${spd}x") },
              )
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Video Scaling", style = MaterialTheme.typography.titleSmall)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = !zoomFill,
              onClick = { zoomFill = false },
              label = { Text("Fit") },
            )
            FilterChip(
              selected = zoomFill,
              onClick = { zoomFill = true },
              label = { Text("Zoom Fill") },
            )
          }
        }
      }
    }
  }

  // CC / Subtitles Track Picker Sheet
  if (showSubtitleSheet) {
    ModalBottomSheet(
      onDismissRequest = { showSubtitleSheet = false },
      containerColor = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Subtitles / Closed Captions (CC)",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
          color =
            if (activeSubtitle == null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.medium,
          border =
            if (activeSubtitle == null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            else null,
          modifier =
            Modifier.fillMaxWidth().clickable {
              activeSubtitle = null
              subtitleCues = emptyList()
              showSubtitleSheet = false
            },
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Subtitles Off", style = MaterialTheme.typography.titleSmall)
            if (activeSubtitle == null) {
              Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
          }
        }

        subtitles.forEach { sub ->
          val selected = activeSubtitle?.url == sub.url
          Surface(
            color =
              if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            modifier =
              Modifier.fillMaxWidth().clickable {
                activeSubtitle = sub
                showSubtitleSheet = false
                scope.launch {
                  subtitleCues = catalogRepository.fetchSubtitleCues(sub.url)
                  onNotify("Loaded ${sub.language} captions (${subtitleCues.size} lines)")
                }
              },
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(sub.language, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = sub.code.uppercase().ifBlank { "SRT" },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = {
                    StreamDownloadManager.enqueueSubtitleDownload(
                      context = context,
                      movieTitle = displayTitle,
                      subtitle = sub,
                      season = selectedSeason,
                      episode = selectedEpisode,
                      allowMetered = appSettings.allowMeteredDownloads,
                    ).onSuccess {
                      onNotify("Downloading ${sub.language} (.srt)")
                    }
                  }
                ) {
                  Icon(imageVector = Icons.Filled.Download, contentDescription = "Download subtitle")
                }
                if (selected) {
                  Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }

        if (subtitles.isEmpty()) {
          Text(
            text = "No .srt subtitle tracks were returned for this stream.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  val sheetMovie = movie
  if (showBatchDownloadSheet && sheetMovie != null) {
    BatchDownloadSheet(
      movie = sheetMovie,
      seasons = seasons,
      initialSeason = selectedSeason,
      currentStreams = streams,
      currentSubtitles = subtitles,
      catalogRepository = catalogRepository,
      allowMetered = appSettings.allowMeteredDownloads,
      defaultIncludeSubs = appSettings.includeSubtitlesInBatch,
      notifyDownloads = appSettings.enableDownloadNotifications,
      onDismiss = { showBatchDownloadSheet = false },
      onNotify = onNotify,
    )
  }
}

private fun modifierOrBaseFillMaxSize(): Modifier = Modifier.fillMaxSize()

private fun formatPlaybackTime(ms: Long): String {
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
