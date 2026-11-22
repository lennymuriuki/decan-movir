package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.example.ui.components.PlayerControlsOverlay
import com.example.ui.components.PlayerGestureLayer
import com.example.ui.components.PlayerHudGestureType
import com.example.ui.components.PlayerOverlayState
import com.example.ui.components.PlayerRecommendationPosterCard
import com.example.ui.components.PlayerSettingsSheet
import com.example.ui.components.PlayerSubtitlesSheet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MediaPlayerScreen(
  movieId: String,
  initialSeason: Int = 0,
  initialEpisode: Int = 0,
  initialPositionMs: Long = 0L,
  localPlayUri: String? = null,
  localTitle: String? = null,
  savedEntry: SavedMovieEntry?,
  savedLibraryMap: Map<String, SavedMovieEntry> = emptyMap(),
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
  val audioManager =
    remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
  val maxSystemVol =
    remember(audioManager) {
      audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }

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
  var interactionBump by remember { mutableIntStateOf(0) }
  var zoomFill by remember { mutableStateOf(false) }
  var playbackSpeed by remember { mutableFloatStateOf(appSettings.defaultPlaybackSpeed) }
  var showSettingsSheet by remember { mutableStateOf(false) }
  var showSubtitleSheet by remember { mutableStateOf(false) }
  var showBatchDownloadSheet by remember { mutableStateOf(false) }

  // Volume & brightness state
  var volume by remember {
    val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxSystemVol
    mutableFloatStateOf((cur.toFloat() / maxSystemVol.toFloat()).coerceIn(0f, 1f))
  }
  var lastNonZeroVolume by remember { mutableFloatStateOf(if (volume > 0.05f) volume else 0.7f) }
  var isMuted by remember { mutableStateOf(volume <= 0.01f) }
  var brightness by remember {
    val attr = activity?.window?.attributes?.screenBrightness ?: -1f
    mutableFloatStateOf(if (attr >= 0f) attr.coerceIn(0.05f, 1f) else 0.55f)
  }
  var gestureHudType by remember { mutableStateOf<PlayerHudGestureType?>(null) }
  var gestureHudFraction by remember { mutableFloatStateOf(0f) }

  // MediaPlayer runtime state
  var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var isBuffering by remember { mutableStateOf(true) }
  var bufferedFraction by remember { mutableFloatStateOf(0f) }
  var playerError by remember { mutableStateOf<String?>(null) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
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
  var upNextDismissed by remember(selectedSeason, selectedEpisode, activeMovieId) {
    mutableStateOf(false)
  }

  fun applyVolume(nextVolume: Float, syncSystemStream: Boolean) {
    val clamped = nextVolume.coerceIn(0f, 1f)
    volume = clamped
    isMuted = clamped <= 0.01f
    if (clamped > 0.05f) lastNonZeroVolume = clamped
    runCatching {
      if (syncSystemStream) {
        audioManager?.setStreamVolume(
          AudioManager.STREAM_MUSIC,
          (clamped * maxSystemVol).roundToInt(),
          0,
        )
      }
      val outVol = if (isMuted) 0f else clamped
      mediaPlayer?.setVolume(outVol, outVol)
    }
  }

  fun toggleMute() {
    if (isMuted) {
      val target = lastNonZeroVolume.coerceAtLeast(0.35f)
      applyVolume(target, syncSystemStream = true)
    } else {
      if (volume > 0.05f) lastNonZeroVolume = volume
      isMuted = true
      runCatching { mediaPlayer?.setVolume(0f, 0f) }
    }
  }

  fun applyBrightness(nextBrightness: Float) {
    val clamped = nextBrightness.coerceIn(0.05f, 1f)
    brightness = clamped
    activity?.window?.let { win ->
      val params = win.attributes
      params.screenBrightness = clamped
      win.attributes = params
    }
  }

  // Fullscreen orientation & immersive bars
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
          val lp = window.attributes
          lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
          window.attributes = lp
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

  BackHandler(enabled = isFullscreen) { isFullscreen = false }

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
        ?: playable.minByOrNull { abs(it.qualityValue - appSettings.preferredQuality) }
        ?: playable.firstOrNull()
    activeStream = preferred
    loadSubtitlesForEpisode(resolvedSubs)
    isLoadingMedia = false
    if (preferred == null && movie?.previewUrl.isNullOrBlank()) {
      playerError = "Stream unavailable right now."
    }
  }

  // Initial load for movie detail, streams, subtitles & recommendations
  LaunchedEffect(activeMovieId, localPlayUri) {
    if (!localPlayUri.isNullOrBlank()) {
      isLoadingMedia = false
      return@LaunchedEffect
    }
    isLoadingMedia = true
    if (activeMovieId.startsWith("mbox_")) {
      val sid = activeMovieId.removePrefix("mbox_")
      val recDeferred = scope.async { catalogRepository.getRecommendations(sid, movie?.genre) }
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
        resolveEpisodeOrMovieStreams(sid, selectedSeason, selectedEpisode, movie?.detailPath)
      }
      recommendations = recDeferred.await().filter { it.id != activeMovieId }
    } else {
      val enriched = catalogRepository.getMovieDetail(activeMovieId)
      if (enriched != null) movie = enriched
      val sid = enriched?.subjectId.orEmpty()
      val recDeferred = scope.async {
        catalogRepository.getRecommendations(sid, enriched?.genre ?: movie?.genre)
      }
      if (sid.isNotBlank()) {
        resolveEpisodeOrMovieStreams(sid, selectedSeason, selectedEpisode, enriched?.detailPath)
      } else {
        isLoadingMedia = false
      }
      recommendations = recDeferred.await().filter { it.id != activeMovieId }
    }
  }

  val playbackUrl =
    localPlayUri?.takeIf { it.isNotBlank() } ?: activeStream?.url ?: movie?.previewUrl
  val playbackHeaders =
    if (!localPlayUri.isNullOrBlank()) emptyMap() else activeStream?.headers.orEmpty()

  fun switchToEpisode(newSeason: Int, newEpisode: Int) {
    val sid = movie?.subjectId.orEmpty()
    if (sid.isBlank()) return
    selectedSeason = newSeason
    selectedEpisode = newEpisode
    pendingSeekMs = 0L
    currentPositionMs = 0L
    scope.launch { resolveEpisodeOrMovieStreams(sid, newSeason, newEpisode, movie?.detailPath) }
  }

  fun goToNextEpisode() {
    val currentSeasonObj = seasons.firstOrNull { it.season == selectedSeason }
    if (currentSeasonObj != null) {
      if (selectedEpisode < currentSeasonObj.episodeCount) {
        switchToEpisode(selectedSeason, selectedEpisode + 1)
      } else {
        val nextSeason = seasons.firstOrNull { it.season > selectedSeason }
        if (nextSeason != null) switchToEpisode(nextSeason.season, 1)
        else recommendations.firstOrNull()?.let { onSwitchMovie(it.id) }
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
        runCatching {
          mediaPlayer?.seekTo(0)
          currentPositionMs = 0L
        }
      }
    }
  }

  fun seekTo(targetMs: Long) {
    val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    runCatching {
      mediaPlayer?.seekTo(clamped.toInt())
      currentPositionMs = clamped
    }
  }

  // Prepare MediaPlayer when URL or SurfaceHolder changes
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
    bufferedFraction = 0f
    playerError = null

    val mp = MediaPlayer()
    mediaPlayer = mp
    try {
      val uri = Uri.parse(url)
      if (playbackHeaders.isNotEmpty()) mp.setDataSource(context, uri, playbackHeaders)
      else mp.setDataSource(context, uri)
      mp.setDisplay(holder)
      mp.setScreenOnWhilePlaying(true)
      val outVol = if (isMuted) 0f else volume
      mp.setVolume(outVol, outVol)
      mp.setOnBufferingUpdateListener { _, percent ->
        bufferedFraction = (percent / 100f).coerceIn(0f, 1f)
      }
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
        if (appSettings.autoPlayNextEpisode && seasons.isNotEmpty()) goToNextEpisode()
        else controlsVisible = true
      }
      mp.setOnInfoListener { _, what, _ ->
        when (what) {
          MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
          MediaPlayer.MEDIA_INFO_BUFFERING_END,
          MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
        }
        false
      }
      mp.setOnErrorListener { _, _, _ ->
        isBuffering = false
        isPlaying = false
        playerError = "Playback unavailable on this stream. Try switching quality."
        controlsVisible = true
        true
      }
      mp.prepareAsync()
    } catch (_: Exception) {
      isBuffering = false
      isPlaying = false
      playerError = "Stream unavailable right now."
    }
  }

  LaunchedEffect(playbackSpeed) {
    val mp = mediaPlayer ?: return@LaunchedEffect
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      runCatching {
        val wasPlaying = mp.isPlaying
        mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
        if (!wasPlaying && mp.isPlaying) mp.pause()
      }
    }
  }

  LaunchedEffect(mediaPlayer, isPlaying) {
    var tickCount = 0
    while (true) {
      val mp = mediaPlayer
      if (mp != null) {
        runCatching {
          if (mp.isPlaying) {
            currentPositionMs = mp.currentPosition.toLong()
            if (durationMs <= 0L) durationMs = mp.duration.toLong().coerceAtLeast(0L)
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

  LaunchedEffect(controlsVisible, isPlaying, isLocked, interactionBump) {
    if (controlsVisible && isPlaying && !isLocked) {
      delay(3500L)
      controlsVisible = false
    }
  }

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

  val currentSeasonObjForNext = seasons.firstOrNull { it.season == selectedSeason }
  val hasNextEpInSeries =
    seasons.isNotEmpty() &&
      ((currentSeasonObjForNext != null && selectedEpisode < currentSeasonObjForNext.episodeCount) ||
        seasons.any { it.season > selectedSeason })
  val remainingSec = ((durationMs - currentPositionMs) / 1000L).toInt()
  val upNextSeconds =
    if (
      appSettings.autoPlayNextEpisode &&
        hasNextEpInSeries &&
        !upNextDismissed &&
        durationMs > 30_000L &&
        remainingSec in 1..15 &&
        isPlaying
    ) remainingSec
    else null

  val overlayState =
    PlayerOverlayState(
      title = displayTitle,
      subtitleLine =
        listOfNotNull(
          episodeBadge,
          activeStream?.quality,
          if (playbackSpeed != 1.0f) "${playbackSpeed}x" else null,
        ).joinToString(" • "),
      isPlaying = isPlaying,
      isBuffering = isBuffering,
      isLoadingMedia = isLoadingMedia,
      isFullscreen = isFullscreen,
      isLocked = isLocked,
      controlsVisible = controlsVisible,
      zoomFill = zoomFill,
      hasPlaybackUrl = playbackUrl != null,
      canGoPrevious = seasons.isNotEmpty() || currentPositionMs > 2000L,
      canGoNext = seasons.isNotEmpty() || recommendations.isNotEmpty(),
      canDownload = movie != null && localPlayUri.isNullOrBlank(),
      ccActive = activeSubtitle != null,
      currentPositionMs = currentPositionMs,
      durationMs = durationMs,
      bufferedFraction = bufferedFraction,
      volume = volume,
      isMuted = isMuted,
      activeCaptionText = activeCue?.text,
      seekFeedbackText = seekFeedbackText,
      upNextSeconds = upNextSeconds,
      gestureHudType = gestureHudType,
      gestureHudFraction = gestureHudFraction,
    )

  Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
          if (videoRatio > containerRatio) Modifier.fillMaxHeight().aspectRatio(videoRatio)
          else Modifier.fillMaxWidth().aspectRatio(videoRatio)
        } else {
          if (videoRatio > containerRatio) Modifier.fillMaxWidth().aspectRatio(videoRatio)
          else Modifier.fillMaxHeight().aspectRatio(videoRatio)
        }

      Box(
        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
      ) {
        val backdropArt = movie?.backdropUrl?.ifBlank { movie?.posterUrl }.orEmpty()
        if (
          localPlayUri.isNullOrBlank() &&
            backdropArt.isNotBlank() &&
            (isLoadingMedia || isBuffering || playbackUrl == null || playerError != null)
        ) {
          MoviePosterImage(
            imageUrl = backdropArt,
            contentDescription = displayTitle,
            modifier = Modifier.fillMaxSize(),
          )
          Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)))
        }

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
                      if (surfaceHolderRef == h) surfaceHolderRef = null
                    }
                  }
                )
              }
            },
            modifier = surfaceModifier,
          )
        }
      }

      PlayerGestureLayer(
        isLocked = isLocked,
        onToggleControls = { controlsVisible = !controlsVisible },
        onDoubleTapSeek = { delta ->
          seekTo(currentPositionMs + delta)
          seekFeedbackText = if (delta < 0) "-10s" else "+10s"
        },
        onVerticalVolumeDrag = { delta ->
          val next = (volume + delta).coerceIn(0f, 1f)
          applyVolume(next, syncSystemStream = true)
          gestureHudType = PlayerHudGestureType.VOLUME
          gestureHudFraction = next
        },
        onVerticalBrightnessDrag = { delta ->
          val next = (brightness + delta).coerceIn(0.05f, 1f)
          applyBrightness(next)
          gestureHudType = PlayerHudGestureType.BRIGHTNESS
          gestureHudFraction = next
        },
        onGestureDragEnd = { gestureHudType = null },
      )

      if ((isLoadingMedia || isBuffering) && !controlsVisible) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
          )
          if (isLoadingMedia) {
            Surface(color = Color.Black.copy(alpha = 0.65f), shape = MaterialTheme.shapes.small) {
              Text(
                text = "Loading stream…",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              )
            }
          }
        }
      }

      if (playerError != null) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(20.dp),
        ) {
          Text(
            text = playerError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
            textAlign = TextAlign.Center,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val sid = movie?.subjectId.orEmpty()
            if (sid.isNotBlank()) {
              FilledTonalButton(
                onClick = {
                  scope.launch {
                    resolveEpisodeOrMovieStreams(sid, selectedSeason, selectedEpisode, movie?.detailPath)
                  }
                }
              ) { Text("Retry") }
            }
            if (streams.isNotEmpty()) {
              FilledTonalButton(onClick = { showSettingsSheet = true }) { Text("Switch Quality") }
            }
            if (playbackUrl != null) {
              OutlinedButton(
                onClick = {
                  runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playbackUrl)))
                  }
                }
              ) { Text("Open External") }
            }
          }
        }
      }

      PlayerControlsOverlay(
        state = overlayState,
        onBack = { if (isFullscreen) isFullscreen = false else onBack() },
        onPlayPause = {
          val mp = mediaPlayer ?: return@PlayerControlsOverlay
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
        onPrevious = { goToPreviousEpisode() },
        onNext = { goToNextEpisode() },
        onSeekBy = { delta ->
          seekTo(currentPositionMs + delta)
          seekFeedbackText = if (delta < 0) "-10s" else "+10s"
        },
        onSeekTo = { target -> seekTo(target) },
        onToggleMute = { toggleMute() },
        onVolumeChange = { next -> applyVolume(next, syncSystemStream = true) },
        onToggleFullscreen = { isFullscreen = !isFullscreen },
        onToggleLock = { locked ->
          isLocked = locked
          controlsVisible = true
        },
        onToggleZoomFill = { zoomFill = !zoomFill },
        onOpenSubtitles = { showSubtitleSheet = true },
        onOpenSettings = { showSettingsSheet = true },
        onOpenDownload = { showBatchDownloadSheet = true },
        onOpenExternal = {
          playbackUrl?.let { url ->
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
          }
        },
        onPlayNextNow = { goToNextEpisode() },
        onDismissUpNext = { upNextDismissed = true },
        onControlInteraction = { interactionBump++ },
      )
    }

    // PORTRAIT CONTENT BELOW PINNED VIDEO
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
                  Icon(
                    imageVector = Icons.Filled.ClosedCaption,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(activeSubtitle?.language ?: "CC (${subtitles.size})")
                }
              }
              item {
                FilledTonalButton(
                  onClick = { toggleMute() },
                  shape = MaterialTheme.shapes.large,
                ) {
                  Icon(
                    imageVector =
                      if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                      else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (isMuted) "Muted" else "${(volume * 100f).roundToInt()}%")
                }
              }
              if (currentMovie != null && localPlayUri.isNullOrBlank()) {
                item {
                  FilledTonalButton(
                    onClick = { showBatchDownloadSheet = true },
                    shape = MaterialTheme.shapes.large,
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Download,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
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
                      modifier = Modifier.clickable { switchToEpisode(selectedSeason, ep) },
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

        if (recommendations.isNotEmpty()) {
          item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = "Recommended Next",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = "Poster picks with synopsis — tap any card to play",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          items(
            recommendations.chunked(2),
            key = { row -> "rec_row_${row.joinToString("_") { it.id }}" },
          ) { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              for (rec in rowItems) {
                PlayerRecommendationPosterCard(
                  movie = rec,
                  isSaved = savedLibraryMap.containsKey(rec.id),
                  onPlay = {
                    pendingSeekMs = 0L
                    currentPositionMs = 0L
                    selectedSeason = 0
                    selectedEpisode = 0
                    activeMovieId = rec.id
                  },
                  onToggleWatchlist = { onToggleWatchlist(rec) },
                  modifier = Modifier.weight(1f),
                )
              }
              if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }

  if (showSettingsSheet) {
    PlayerSettingsSheet(
      streams = streams,
      activeStream = activeStream,
      playbackSpeed = playbackSpeed,
      zoomFill = zoomFill,
      volume = volume,
      isMuted = isMuted,
      onSelectStream = { st ->
        pendingSeekMs = currentPositionMs
        activeStream = st
        showSettingsSheet = false
        onNotify("Switched to ${st.quality}")
      },
      onSelectSpeed = { spd ->
        playbackSpeed = spd
        showSettingsSheet = false
      },
      onSelectZoomFill = { fill -> zoomFill = fill },
      onVolumeChange = { next -> applyVolume(next, syncSystemStream = true) },
      onDismiss = { showSettingsSheet = false },
    )
  }

  if (showSubtitleSheet) {
    PlayerSubtitlesSheet(
      subtitles = subtitles,
      activeSubtitle = activeSubtitle,
      onDisableSubtitles = {
        activeSubtitle = null
        subtitleCues = emptyList()
        showSubtitleSheet = false
      },
      onSelectSubtitle = { sub ->
        activeSubtitle = sub
        showSubtitleSheet = false
        scope.launch {
          subtitleCues = catalogRepository.fetchSubtitleCues(sub.url)
          onNotify("Loaded ${sub.language} captions (${subtitleCues.size} lines)")
        }
      },
      onDownloadSubtitle = { sub ->
        StreamDownloadManager.enqueueSubtitleDownload(
          context = context,
          movieTitle = displayTitle,
          subtitle = sub,
          season = selectedSeason,
          episode = selectedEpisode,
          allowMetered = appSettings.allowMeteredDownloads,
        ).onSuccess { onNotify("Downloading ${sub.language} (.srt)") }
      },
      onDismiss = { showSubtitleSheet = false },
    )
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
