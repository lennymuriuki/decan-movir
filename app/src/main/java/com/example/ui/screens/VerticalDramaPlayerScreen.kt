package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.download.StreamDownloadManager
import com.example.data.model.DramaEpisodeItem
import com.example.data.model.DramaExperienceDetector
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.WatchStatus
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
import com.example.ui.components.DramaMoreOptionsSheet
import com.example.ui.components.DramaPlayerOverlay
import com.example.ui.components.DramaPlayerUiState
import com.example.ui.components.MoviePosterImage
import com.example.ui.components.PlayerSettingsSheet
import com.example.ui.components.PlayerSubtitlesSheet
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VerticalDramaPlayerScreen(
  movieId: String,
  initialSeason: Int = 1,
  initialEpisode: Int = 1,
  initialPositionMs: Long = 0L,
  savedEntry: SavedMovieEntry?,
  catalogRepository: MovieCatalogRepository,
  appSettings: AppSettings,
  progressStore: PlaybackProgressStore? = null,
  onBack: () -> Unit,
  onOpenStandardTheater: (movieId: String, season: Int, episode: Int, positionMs: Long) -> Unit,
  onToggleWatchlist: (Movie) -> Unit,
  onSaveFavorite: (Movie, WatchStatus, Boolean, Int, String) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val view = LocalView.current
  val activity = context as? Activity
  val lifecycleOwner = LocalLifecycleOwner.current
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
  var isDetailReady by remember(activeMovieId) { mutableStateOf(false) }

  val episodes =
    remember(movie, seasons) {
      DramaExperienceDetector.buildOrderedDramaEpisodes(movie, seasons)
    }

  val pagerState =
    rememberPagerState(
      initialPage = 0,
      pageCount = { episodes.size.coerceAtLeast(1) },
    )

  var appliedInitialTarget by remember(activeMovieId) { mutableStateOf(false) }
  var currentPageIndex by remember { mutableIntStateOf(0) }

  var streams by remember { mutableStateOf<List<MovieBoxStream>>(emptyList()) }
  var subtitles by remember { mutableStateOf<List<MovieBoxSubtitle>>(emptyList()) }
  var activeStream by remember { mutableStateOf<MovieBoxStream?>(null) }
  var activeSubtitle by remember { mutableStateOf<MovieBoxSubtitle?>(null) }
  var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
  var isLoadingStreams by remember { mutableStateOf(true) }
  var playerError by remember { mutableStateOf<String?>(null) }

  // Active player state (strictly single MediaPlayer instance at any time)
  var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
  var surfaceHolderRef by remember { mutableStateOf<SurfaceHolder?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var isBuffering by remember { mutableStateOf(true) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  var durationMs by remember { mutableLongStateOf(0L) }
  var pendingSeekMs by remember { mutableLongStateOf(initialPositionMs) }
  var videoWidth by remember { mutableIntStateOf(9) }
  var videoHeight by remember { mutableIntStateOf(16) }

  // Controls & customization state
  var controlsVisible by remember { mutableStateOf(true) }
  var isLocked by remember { mutableStateOf(false) }
  var fillScreen by remember { mutableStateOf(true) }
  var playbackSpeed by remember { mutableFloatStateOf(appSettings.defaultPlaybackSpeed) }
  var interactionBump by remember { mutableIntStateOf(0) }
  var isLiked by remember(savedEntry?.favorite, movieId) {
    mutableStateOf(savedEntry?.favorite ?: false)
  }

  var volume by remember {
    val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxSystemVol
    mutableFloatStateOf((cur.toFloat() / maxSystemVol.toFloat()).coerceIn(0f, 1f))
  }
  var isMuted by remember { mutableStateOf(volume <= 0.01f) }

  var showMoreSheet by remember { mutableStateOf(false) }
  var showSettingsSheet by remember { mutableStateOf(false) }
  var showSubtitleSheet by remember { mutableStateOf(false) }
  var showBatchDownloadSheet by remember { mutableStateOf(false) }

  // Audio focus integration
  val audioFocusListener = remember {
    AudioManager.OnAudioFocusChangeListener { change ->
      when (change) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
          runCatching { mediaPlayer?.pause() }
          isPlaying = false
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          runCatching { mediaPlayer?.setVolume(0.25f, 0.25f) }
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
          val outVol = if (isMuted) 0f else volume
          runCatching { mediaPlayer?.setVolume(outVol, outVol) }
        }
      }
    }
  }
  var audioFocusRequest by remember { mutableStateOf<AudioFocusRequest?>(null) }

  fun requestPlaybackAudioFocus(): Boolean {
    val am = audioManager ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val req =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
              .build()
          )
          .setOnAudioFocusChangeListener(audioFocusListener)
          .build()
      audioFocusRequest = req
      am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
      @Suppress("DEPRECATION")
      am.requestAudioFocus(
        audioFocusListener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
  }

  fun abandonPlaybackAudioFocus() {
    val am = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
    } else {
      @Suppress("DEPRECATION")
      am.abandonAudioFocus(audioFocusListener)
    }
  }

  fun persistCurrentEpisodeProgress(ep: DramaEpisodeItem?) {
    val currentM = movie ?: return
    if (ep == null) return
    if (currentPositionMs >= 2_000L) {
      progressStore?.saveProgress(
        movieId = currentM.id,
        title = currentM.title,
        posterUrl = currentM.posterUrl,
        mediaType = currentM.mediaType,
        season = ep.season,
        episode = ep.episode,
        positionMs = currentPositionMs,
        durationMs = durationMs,
      )
    }
  }

  fun releaseActivePlayer() {
    runCatching {
      mediaPlayer?.stop()
      mediaPlayer?.release()
    }
    mediaPlayer = null
    isPlaying = false
  }

  fun togglePlayPause() {
    val mp = mediaPlayer ?: return
    runCatching {
      if (mp.isPlaying) {
        mp.pause()
        isPlaying = false
        persistCurrentEpisodeProgress(episodes.getOrNull(pagerState.currentPage))
      } else {
        if (requestPlaybackAudioFocus()) {
          mp.start()
          isPlaying = true
        }
      }
      interactionBump++
    }
  }

  // Enforce portrait orientation & immersive bars for TikTok-style Drama viewing
  DisposableEffect(activity) {
    if (activity != null) {
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      activity.window?.let { window ->
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
          WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
      }
    }
    onDispose {
      persistCurrentEpisodeProgress(episodes.getOrNull(pagerState.currentPage))
      releaseActivePlayer()
      abandonPlaybackAudioFocus()
      if (activity != null) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity.window?.let { window ->
          WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
      }
    }
  }

  // Pause safely when app goes to background
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        persistCurrentEpisodeProgress(episodes.getOrNull(pagerState.currentPage))
        runCatching {
          if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
          }
        }
        isPlaying = false
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  BackHandler {
    when {
      showBatchDownloadSheet -> showBatchDownloadSheet = false
      showSubtitleSheet -> showSubtitleSheet = false
      showSettingsSheet -> showSettingsSheet = false
      showMoreSheet -> showMoreSheet = false
      else -> {
        persistCurrentEpisodeProgress(episodes.getOrNull(pagerState.currentPage))
        releaseActivePlayer()
        onBack()
      }
    }
  }

  // Load drama metadata & full ordered season/episode catalog once
  LaunchedEffect(activeMovieId) {
    isDetailReady = false
    if (activeMovieId.startsWith("mbox_")) {
      val sid = activeMovieId.removePrefix("mbox_")
      val rich = catalogRepository.getMovieBoxRichDetail(sid)
      if (rich != null) {
        movie = rich.movie
        dubs = rich.dubs
        seasons = rich.seasons
      }
    } else {
      val base = catalogRepository.getMovieDetail(activeMovieId) ?: movie
      val bridged =
        if (base != null && base.subjectId.isBlank()) {
          catalogRepository.resolvePlayableSubject(base) ?: base
        } else {
          base
        }
      if (bridged != null) movie = bridged
      val sid = bridged?.subjectId.orEmpty()
      if (sid.isNotBlank()) {
        val rich = catalogRepository.getMovieBoxRichDetail(sid)
        if (rich != null) {
          movie =
            bridged!!.copy(
              detailPath = rich.detailPath.ifBlank { bridged.detailPath },
              posterUrl = bridged.posterUrl.ifBlank { rich.movie.posterUrl },
              backdropUrl = bridged.backdropUrl.ifBlank { rich.movie.backdropUrl },
            )
          dubs = rich.dubs
          seasons = rich.seasons
        }
      }
    }
    isDetailReady = true
  }

  // Align pager to requested initial season/episode once seasons are loaded
  LaunchedEffect(isDetailReady, episodes.size) {
    if (!isDetailReady || appliedInitialTarget) return@LaunchedEffect
    appliedInitialTarget = true
    val targetPage =
      DramaExperienceDetector.findEpisodePageIndex(
        episodes = episodes,
        season = initialSeason,
        episode = initialEpisode,
      )
    if (targetPage > 0 && targetPage < episodes.size) {
      currentPageIndex = targetPage
      pagerState.scrollToPage(targetPage)
    }
  }

  suspend fun loadEpisodeStreamAndSubs(ep: DramaEpisodeItem) {
    val currentM = movie
    val sid = currentM?.subjectId.orEmpty()
    if (sid.isBlank()) {
      isLoadingStreams = false
      if (currentM?.previewUrl.isNullOrBlank()) {
        playerError = "Stream unavailable right now."
      }
      return
    }
    isLoadingStreams = true
    playerError = null

    val streamsDef = scope.async {
      catalogRepository.getStreams(sid, ep.season, ep.episode, currentM?.detailPath)
    }
    val subsDef = scope.async { catalogRepository.getSubtitles(sid, ep.season, ep.episode) }
    val resolvedStreams = streamsDef.await()
    val resolvedSubs = subsDef.await()
    streams = resolvedStreams
    subtitles = resolvedSubs

    val playable = resolvedStreams.filter { it.isPlayableDirect }
    val preferred =
      playable.firstOrNull { it.qualityValue == appSettings.preferredQuality }
        ?: playable.minByOrNull { abs(it.qualityValue - appSettings.preferredQuality) }
        ?: playable.firstOrNull()
    activeStream = preferred

    if (appSettings.autoLoadSubtitles && resolvedSubs.isNotEmpty()) {
      val prefSub =
        resolvedSubs.firstOrNull { it.language.contains("English", ignoreCase = true) }
          ?: resolvedSubs.first()
      activeSubtitle = prefSub
      subtitleCues = catalogRepository.fetchSubtitleCues(prefSub.url)
    } else {
      activeSubtitle = null
      subtitleCues = emptyList()
    }

    isLoadingStreams = false
    if (preferred == null && currentM?.previewUrl.isNullOrBlank()) {
      playerError = "Episode stream unavailable. Retry or swipe for next."
    }

    // Preload next episode metadata/streams/captions smoothly without allocating heavy buffers
    val nextEp = episodes.getOrNull(ep.globalIndex + 1)
    if (nextEp != null) {
      scope.launch {
        catalogRepository.preloadEpisodeResources(
          subjectId = sid,
          season = nextEp.season,
          episode = nextEp.episode,
          detailPath = currentM?.detailPath,
          preloadEnglishCues = appSettings.autoLoadSubtitles,
        )
      }
    }
  }

  // Handle vertical page transition: save previous progress, stop old player, load & resume new ep
  LaunchedEffect(pagerState.currentPage, isDetailReady, episodes.size) {
    if (!isDetailReady && movie == null) return@LaunchedEffect
    val newPage = pagerState.currentPage.coerceIn(0, episodes.lastIndex)
    if (newPage != currentPageIndex) {
      persistCurrentEpisodeProgress(episodes.getOrNull(currentPageIndex))
      currentPageIndex = newPage
    }
    releaseActivePlayer()
    currentPositionMs = 0L
    durationMs = 0L
    activeStream = null
    subtitleCues = emptyList()

    val ep = episodes.getOrNull(newPage) ?: return@LaunchedEffect
    val savedEpPos =
      if (newPage == DramaExperienceDetector.findEpisodePageIndex(episodes, initialSeason, initialEpisode) &&
        initialPositionMs > 0L &&
        pendingSeekMs == initialPositionMs
      ) {
        initialPositionMs
      } else {
        movie?.id?.let { progressStore?.getEpisodeProgress(it, ep.season, ep.episode) } ?: 0L
      }
    pendingSeekMs = savedEpPos
    controlsVisible = true
    interactionBump++
    loadEpisodeStreamAndSubs(ep)
  }

  val activeEp = episodes.getOrNull(pagerState.currentPage) ?: episodes.first()
  val playbackUrl = activeStream?.url ?: movie?.previewUrl
  val playbackHeaders = activeStream?.headers.orEmpty()

  // Prepare MediaPlayer strictly for the currently visible page + active stream
  LaunchedEffect(playbackUrl, surfaceHolderRef, pagerState.currentPage) {
    val holder = surfaceHolderRef ?: return@LaunchedEffect
    val url = playbackUrl ?: return@LaunchedEffect

    releaseActivePlayer()
    isBuffering = true
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
          currentPositionMs = pendingSeekMs
          pendingSeekMs = 0L
        }
        if (requestPlaybackAudioFocus()) {
          prepared.start()
          isPlaying = true
        }
      }
      mp.setOnCompletionListener {
        isPlaying = false
        val currentIdx = pagerState.currentPage
        movie?.let { m ->
          progressStore?.saveEpisodeProgress(
            movieId = m.id,
            season = activeEp.season,
            episode = activeEp.episode,
            positionMs = durationMs,
            durationMs = durationMs,
          )
        }
        if (appSettings.autoPlayNextEpisode && currentIdx < episodes.lastIndex) {
          scope.launch { pagerState.animateScrollToPage(currentIdx + 1) }
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
      mp.setOnErrorListener { _, _, _ ->
        isBuffering = false
        isPlaying = false
        playerError = "Playback interrupted. Tap Retry or swipe to another episode."
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

  // Playback progress polling & periodic persistence
  LaunchedEffect(mediaPlayer, isPlaying, pagerState.currentPage) {
    var ticks = 0
    while (true) {
      val mp = mediaPlayer
      if (mp != null) {
        runCatching {
          if (mp.isPlaying) {
            currentPositionMs = mp.currentPosition.toLong()
            if (durationMs <= 0L) durationMs = mp.duration.toLong().coerceAtLeast(0L)
            ticks++
            if (ticks % 16 == 0) {
              persistCurrentEpisodeProgress(episodes.getOrNull(pagerState.currentPage))
            }
          }
        }
      }
      delay(250L)
    }
  }

  // Auto-hide controls after inactivity while playing
  LaunchedEffect(controlsVisible, isPlaying, isLocked, interactionBump) {
    if (controlsVisible && isPlaying && !isLocked &&
      !showMoreSheet && !showSettingsSheet && !showSubtitleSheet && !showBatchDownloadSheet
    ) {
      delay(4000L)
      controlsVisible = false
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

  val activeCue =
    remember(subtitleCues, currentPositionMs) {
      SrtParser.findActiveCue(subtitleCues, currentPositionMs)
    }

  val dramaTitle = movie?.title?.ifBlank { "Decan Drama" } ?: "Decan Drama"
  val posterArt = movie?.backdropUrl?.ifBlank { movie?.posterUrl }.orEmpty()

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black)
        .testTag("vertical_drama_player_root")
  ) {
    VerticalPager(
      state = pagerState,
      userScrollEnabled = !isLocked,
      beyondViewportPageCount = 1,
      key = { idx ->
        val ep = episodes.getOrNull(idx)
        "drama_page_${ep?.season ?: 1}_${ep?.episode ?: (idx + 1)}"
      },
      modifier = Modifier.fillMaxSize().testTag("drama_vertical_pager"),
    ) { page ->
      val pageEp = episodes[page]
      val isCurrentPage = page == pagerState.currentPage

      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .pointerInput(isLocked) {
              detectTapGestures(
                onTap = {
                  controlsVisible = !controlsVisible
                  interactionBump++
                },
                onDoubleTap = {
                  if (!isLocked) togglePlayPause()
                },
              )
            },
        contentAlignment = Alignment.Center,
      ) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val videoRatio =
          if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat()
          else 9f / 16f
        val containerRatio = containerW / containerH

        val surfaceModifier =
          if (fillScreen) {
            if (videoRatio > containerRatio) Modifier.fillMaxHeight().aspectRatio(videoRatio)
            else Modifier.fillMaxWidth().aspectRatio(videoRatio)
          } else {
            if (videoRatio > containerRatio) Modifier.fillMaxWidth().aspectRatio(videoRatio)
            else Modifier.fillMaxHeight().aspectRatio(videoRatio)
          }

        if (posterArt.isNotBlank() && (!isCurrentPage || isLoadingStreams || isBuffering || playbackUrl == null || playerError != null)) {
          MoviePosterImage(
            imageUrl = posterArt,
            contentDescription = pageEp.title,
            modifier = Modifier.fillMaxSize(),
          )
          Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }

        if (isCurrentPage && playbackUrl != null && playerError == null) {
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

        if (isCurrentPage) {
          val overlayUiState =
            DramaPlayerUiState(
              dramaTitle = dramaTitle,
              episode = pageEp,
              nextEpisode = episodes.getOrNull(page + 1),
              isPlaying = isPlaying,
              isBuffering = isBuffering,
              isLoadingStreams = isLoadingStreams,
              errorMessage = playerError,
              controlsVisible = controlsVisible,
              isLocked = isLocked,
              isLiked = isLiked,
              isInWatchlist = savedEntry != null,
              ccActive = activeSubtitle != null,
              activeCaptionText = activeCue?.text,
              currentPositionMs = currentPositionMs,
              durationMs = durationMs,
              qualityLabel = activeStream?.quality,
              playbackSpeed = playbackSpeed,
              fillScreen = fillScreen,
            )

          DramaPlayerOverlay(
            state = overlayUiState,
            onBack = {
              persistCurrentEpisodeProgress(pageEp)
              releaseActivePlayer()
              onBack()
            },
            onPlayPause = { togglePlayPause() },
            onSeekTo = { targetMs ->
              val clamped = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
              runCatching {
                mediaPlayer?.seekTo(clamped.toInt())
                currentPositionMs = clamped
              }
              interactionBump++
            },
            onToggleLike = {
              val m = movie ?: return@DramaPlayerOverlay
              val nextFav = !isLiked
              isLiked = nextFav
              onSaveFavorite(
                m,
                savedEntry?.watchStatusEnum() ?: WatchStatus.WATCHING,
                nextFav,
                savedEntry?.personalRating?.toInt() ?: 0,
                savedEntry?.personalNote.orEmpty(),
              )
              interactionBump++
            },
            onToggleWatchlist = {
              val m = movie ?: return@DramaPlayerOverlay
              onToggleWatchlist(m)
              interactionBump++
            },
            onShare = {
              val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(
                    Intent.EXTRA_TEXT,
                    "Watching $dramaTitle (${pageEp.subtitleLabel}) on Decan Movie!",
                  )
                }
              context.startActivity(Intent.createChooser(shareIntent, "Share drama"))
              interactionBump++
            },
            onOpenMoreOptions = {
              showMoreSheet = true
              interactionBump++
            },
            onOpenSubtitles = {
              showSubtitleSheet = true
              interactionBump++
            },
            onOpenSettings = {
              showSettingsSheet = true
              interactionBump++
            },
            onNextEpisode = {
              if (page < episodes.lastIndex) {
                scope.launch { pagerState.animateScrollToPage(page + 1) }
              }
            },
            onRetryStream = {
              scope.launch { loadEpisodeStreamAndSubs(pageEp) }
            },
            onUnlock = {
              isLocked = false
              controlsVisible = true
              interactionBump++
            },
          )
        }
      }
    }
  }

  if (showMoreSheet) {
    DramaMoreOptionsSheet(
      seasons = seasons,
      currentSeason = activeEp.season,
      currentEpisode = activeEp.episode,
      dubs = dubs,
      activeSubjectId = movie?.subjectId,
      fillScreen = fillScreen,
      onSelectEpisode = { s, ep ->
        showMoreSheet = false
        val targetIdx = DramaExperienceDetector.findEpisodePageIndex(episodes, s, ep)
        scope.launch { pagerState.animateScrollToPage(targetIdx) }
      },
      onSelectDub = { dubSubjectId ->
        showMoreSheet = false
        activeMovieId = "mbox_$dubSubjectId"
      },
      onToggleScaling = { nextFill ->
        fillScreen = nextFill
      },
      onDownloadEpisode = {
        showMoreSheet = false
        showBatchDownloadSheet = true
      },
      onLockControls = {
        showMoreSheet = false
        isLocked = true
        controlsVisible = true
      },
      onOpenStandardTheater = {
        showMoreSheet = false
        persistCurrentEpisodeProgress(activeEp)
        releaseActivePlayer()
        onOpenStandardTheater(
          movie?.id ?: activeMovieId,
          activeEp.season,
          activeEp.episode,
          currentPositionMs,
        )
      },
      onDismiss = { showMoreSheet = false },
    )
  }

  if (showSettingsSheet) {
    PlayerSettingsSheet(
      streams = streams,
      activeStream = activeStream,
      playbackSpeed = playbackSpeed,
      zoomFill = fillScreen,
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
      onSelectZoomFill = { fill -> fillScreen = fill },
      onVolumeChange = { next ->
        val clamped = next.coerceIn(0f, 1f)
        volume = clamped
        isMuted = clamped <= 0.01f
        runCatching {
          audioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (clamped * maxSystemVol).roundToInt(),
            0,
          )
          val outVol = if (isMuted) 0f else clamped
          mediaPlayer?.setVolume(outVol, outVol)
        }
      },
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
          movieTitle = dramaTitle,
          subtitle = sub,
          season = activeEp.season,
          episode = activeEp.episode,
          isSeries = true,
          allowMetered = appSettings.allowMeteredDownloads,
          storageLocation = appSettings.storageLocation,
        )
          .onSuccess { onNotify("Downloading ${sub.language} (.srt)") }
          .onFailure { err -> onNotify(err.message ?: "Subtitle download unavailable") }
      },
      onDismiss = { showSubtitleSheet = false },
    )
  }

  val downloadMovie = movie
  if (showBatchDownloadSheet && downloadMovie != null) {
    BatchDownloadSheet(
      movie = downloadMovie,
      seasons = seasons,
      initialSeason = activeEp.season,
      currentStreams = streams,
      currentSubtitles = subtitles,
      catalogRepository = catalogRepository,
      allowMetered = appSettings.allowMeteredDownloads,
      defaultIncludeSubs = appSettings.includeSubtitlesInBatch,
      storageLocation = appSettings.storageLocation,
      notifyDownloads = appSettings.enableDownloadNotifications,
      onDismiss = { showBatchDownloadSheet = false },
      onNotify = onNotify,
    )
  }
}
