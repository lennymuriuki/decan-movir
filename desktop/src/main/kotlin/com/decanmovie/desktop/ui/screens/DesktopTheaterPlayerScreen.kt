package com.decanmovie.desktop.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.decanmovie.desktop.data.download.DesktopDownloadManager
import com.decanmovie.desktop.data.remote.DesktopMovieCatalogRepository
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxDub
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSeason
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxStream
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSubtitle
import com.decanmovie.desktop.data.remote.moviebox.SrtParser
import com.decanmovie.desktop.data.remote.moviebox.SubtitleCue
import com.decanmovie.desktop.data.store.DesktopAppStorage
import com.decanmovie.desktop.media.DesktopMediaController
import com.decanmovie.desktop.media.DesktopPlayerTelemetry
import com.decanmovie.desktop.media.DesktopVideoSurface
import com.decanmovie.desktop.model.DesktopSettings
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.PlaybackAspectMode
import com.decanmovie.desktop.platform.WindowsSystemHelper
import com.decanmovie.desktop.ui.theme.DecanDesktopColors
import com.decanmovie.desktop.viewmodel.ActiveDesktopOverlay
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopTheaterPlayerScreen(
  request: ActiveDesktopOverlay.TheaterPlayer,
  catalogRepository: DesktopMovieCatalogRepository,
  downloadManager: DesktopDownloadManager,
  storage: DesktopAppStorage,
  settings: DesktopSettings,
  isFullscreen: Boolean,
  onToggleFullscreen: () -> Unit,
  onBack: () -> Unit,
  onNotify: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }
  val mediaController = remember { DesktopMediaController() }

  var telemetry by remember { mutableStateOf(DesktopPlayerTelemetry()) }
  var movie by remember(request.movieId) { mutableStateOf(catalogRepository.getCachedMovie(request.movieId)) }
  var currentSeason by remember(request) { mutableIntStateOf(request.season) }
  var currentEpisode by remember(request) { mutableIntStateOf(request.episode) }
  var seasons by remember(request.movieId) { mutableStateOf<List<MovieBoxSeason>>(emptyList()) }
  var dubs by remember(request.movieId) { mutableStateOf<List<MovieBoxDub>>(emptyList()) }
  var activeSubjectId by remember(request.movieId) { mutableStateOf(movie?.subjectId.orEmpty()) }
  var streams by remember { mutableStateOf<List<MovieBoxStream>>(emptyList()) }
  var selectedStream by remember { mutableStateOf<MovieBoxStream?>(null) }
  var subtitles by remember { mutableStateOf<List<MovieBoxSubtitle>>(emptyList()) }
  var selectedSubtitle by remember { mutableStateOf<MovieBoxSubtitle?>(null) }
  var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
  var subtitlesEnabled by remember { mutableStateOf(settings.autoLoadSubtitles) }

  var volume by remember { mutableFloatStateOf(0.95f) }
  var muted by remember { mutableStateOf(false) }
  var playbackSpeed by remember { mutableFloatStateOf(settings.defaultPlaybackSpeed) }
  var aspectMode by remember { mutableStateOf(PlaybackAspectMode.FIT) }
  var isResolving by remember { mutableStateOf(true) }
  var resolveError by remember { mutableStateOf<String?>(null) }

  var showQualityMenu by remember { mutableStateOf(false) }
  var showSubtitleMenu by remember { mutableStateOf(false) }
  var showSpeedMenu by remember { mutableStateOf(false) }
  var showAspectMenu by remember { mutableStateOf(false) }
  var showEpisodeDrawer by remember { mutableStateOf(false) }

  fun persistCurrentPosition() {
    val m = movie ?: return
    if (request.localVideoPath != null) return
    val pos = telemetry.currentPositionMs
    if (pos > 0L) {
      storage.savePlaybackProgress(m, currentSeason, currentEpisode, pos, telemetry.durationMs)
    }
  }

  // Advance helper for episodic series/anime
  val orderedEpisodes =
    remember(seasons) {
      seasons
        .sortedBy { it.season }
        .flatMap { s -> (1..s.episodeCount.coerceAtLeast(1)).map { ep -> s.season to ep } }
    }
  val currentEpIndex =
    orderedEpisodes.indexOfFirst { it.first == currentSeason && it.second == currentEpisode }
  val hasPrevEp = currentEpIndex > 0
  val hasNextEp = currentEpIndex >= 0 && currentEpIndex < orderedEpisodes.lastIndex

  fun jumpEpisode(newSeason: Int, newEpisode: Int) {
    persistCurrentPosition()
    mediaController.stopAndDispose()
    currentSeason = newSeason
    currentEpisode = newEpisode
  }

  DisposableEffect(mediaController) {
    mediaController.onTelemetry = { telemetry = it }
    mediaController.onCompleted = {
      if (settings.autoPlayNextEpisode && hasNextEp) {
        val (ns, ne) = orderedEpisodes[currentEpIndex + 1]
        jumpEpisode(ns, ne)
      }
    }
    onDispose {
      persistCurrentPosition()
      mediaController.stopAndDispose()
    }
  }

  // Periodic playback history autosave
  LaunchedEffect(telemetry.isPlaying, currentSeason, currentEpisode) {
    while (telemetry.isPlaying) {
      delay(4000L)
      persistCurrentPosition()
    }
  }

  // Resolve local or remote stream whenever episode or dub changes
  LaunchedEffect(request.localVideoPath, request.movieId, activeSubjectId, currentSeason, currentEpisode) {
    isResolving = true
    resolveError = null
    subtitleCues = emptyList()

    if (request.localVideoPath != null) {
      val localFile = File(request.localVideoPath)
      if (!localFile.exists()) {
        resolveError = "Local file not found: ${localFile.name}"
        isResolving = false
        return@LaunchedEffect
      }
      val subFile = request.localSubtitlePath?.let(::File)?.takeIf { it.exists() }
        ?: DesktopDownloadManager.findSiblingSubtitle(localFile)
      if (subFile != null) {
        subtitleCues = runCatching { SrtParser.parse(subFile.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
      }
      mediaController.loadAndPlay(
        sourceUrlOrPath = localFile.absolutePath,
        startPositionMs = request.startPositionMs,
        volume = volume,
        muted = muted,
        playbackRate = playbackSpeed,
        aspectMode = aspectMode,
      )
      isResolving = false
      return@LaunchedEffect
    }

    val loadedMovie = movie ?: catalogRepository.getMovieDetail(request.movieId)
    val resolvedMovie =
      if (loadedMovie != null) catalogRepository.resolvePlayableSubject(loadedMovie) ?: loadedMovie
      else null
    movie = resolvedMovie

    val sid = activeSubjectId.ifBlank { resolvedMovie?.subjectId.orEmpty() }
    if (resolvedMovie == null || sid.isBlank()) {
      resolveError = "No playable streaming source was found for this title."
      isResolving = false
      return@LaunchedEffect
    }
    activeSubjectId = sid

    val rich = catalogRepository.getMovieBoxRichDetail(sid)
    if (rich != null) {
      seasons = rich.seasons
      dubs = rich.dubs
    }

    val reqSeason = if (rich?.seasons?.isNotEmpty() == true) currentSeason.coerceAtLeast(1) else currentSeason
    val reqEpisode = if (rich?.seasons?.isNotEmpty() == true) currentEpisode.coerceAtLeast(1) else currentEpisode
    if (reqSeason != currentSeason) currentSeason = reqSeason
    if (reqEpisode != currentEpisode) currentEpisode = reqEpisode

    val fetchedStreams =
      catalogRepository.getStreams(sid, reqSeason, reqEpisode, resolvedMovie.detailPath)
    streams = fetchedStreams
    val best =
      fetchedStreams.firstOrNull { it.isPlayableDirect && it.qualityValue <= settings.preferredQuality }
        ?: fetchedStreams.firstOrNull { it.isPlayableDirect }
        ?: fetchedStreams.firstOrNull()
    selectedStream = best

    val fetchedSubs = catalogRepository.getSubtitles(sid, reqSeason, reqEpisode)
    subtitles = fetchedSubs
    if (subtitlesEnabled && fetchedSubs.isNotEmpty()) {
      val autoSub =
        fetchedSubs.firstOrNull { it.language.contains("English", true) } ?: fetchedSubs.first()
      selectedSubtitle = autoSub
      subtitleCues = catalogRepository.fetchSubtitleCues(autoSub.url)
    }

    if (best == null) {
      resolveError = "No playable stream URL returned for this episode."
    } else {
      val resumeMs =
        if (request.startPositionMs > 0L && currentSeason == request.season && currentEpisode == request.episode) {
          request.startPositionMs
        } else {
          storage.getEpisodeProgress(resolvedMovie.id, reqSeason, reqEpisode)
        }
      mediaController.loadAndPlay(
        sourceUrlOrPath = best.url,
        startPositionMs = resumeMs,
        volume = volume,
        muted = muted,
        playbackRate = playbackSpeed,
        aspectMode = aspectMode,
      )
      // Preload next episode
      val nextIdx = orderedEpisodes.indexOfFirst { it.first == reqSeason && it.second == reqEpisode } + 1
      if (nextIdx in orderedEpisodes.indices) {
        val (ns, ne) = orderedEpisodes[nextIdx]
        scope.launch { catalogRepository.preloadEpisodeResources(sid, ns, ne, resolvedMovie.detailPath) }
      }
    }
    isResolving = false
  }

  LaunchedEffect(Unit) {
    runCatching { focusRequester.requestFocus() }
  }

  val activeCue =
    remember(telemetry.currentPositionMs, subtitleCues, subtitlesEnabled) {
      if (subtitlesEnabled) SrtParser.findActiveCue(subtitleCues, telemetry.currentPositionMs) else null
    }

  val displayTitle =
    request.localTitle
      ?: buildString {
        append(movie?.title ?: "Decan Theater")
        if (currentSeason > 0 && currentEpisode > 0) {
          append(" — S%02d • Ep %02d".format(currentSeason, currentEpisode))
        }
      }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Color.Black)
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { ev ->
          if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (ev.key) {
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
            Key.DirectionUp -> {
              volume = (volume + 0.1f).coerceIn(0f, 1f)
              muted = false
              mediaController.setVolume(volume, muted)
              true
            }
            Key.DirectionDown -> {
              volume = (volume - 0.1f).coerceIn(0f, 1f)
              mediaController.setVolume(volume, muted)
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
            Key.C -> {
              subtitlesEnabled = !subtitlesEnabled
              true
            }
            Key.N -> {
              if (hasNextEp) {
                val (ns, ne) = orderedEpisodes[currentEpIndex + 1]
                jumpEpisode(ns, ne)
                true
              } else false
            }
            Key.P -> {
              if (hasPrevEp) {
                val (ps, pe) = orderedEpisodes[currentEpIndex - 1]
                jumpEpisode(ps, pe)
                true
              } else false
            }
            Key.Escape -> {
              if (isFullscreen) onToggleFullscreen() else onBack()
              true
            }
            else -> false
          }
        }
        .testTag("desktop_theater_player"),
  ) {
    // Hardware-accelerated Desktop JavaFX Video Canvas
    DesktopVideoSurface(
      controller = mediaController,
      aspectMode = aspectMode,
      modifier = Modifier.fillMaxSize(),
    )

    // Loading / Buffering / Codec Error Overlay
    val playerError = resolveError ?: telemetry.errorMessage
    if (isResolving || (telemetry.isBuffering && playerError == null)) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
          color = DecanDesktopColors.SurfaceGlassStrong,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            CircularProgressIndicator(color = DecanDesktopColors.Crimson, modifier = Modifier.size(22.dp))
            Text(
              text = if (isResolving) "Preparing stream…" else "Buffering…",
              color = DecanDesktopColors.Mist,
            )
          }
        }
      }
    } else if (playerError != null) {
      Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center,
      ) {
        Surface(
          color = DecanDesktopColors.SurfaceElevated,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, DecanDesktopColors.Crimson),
          modifier = Modifier.width(480.dp),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Stream Playback Notice", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
            Text(
              text = "$playerError\n\nSome MKV/HEVC containers may play better in your Windows default media player.",
              style = MaterialTheme.typography.bodyMedium,
              color = DecanDesktopColors.Slate,
              textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              val externalTarget = request.localVideoPath ?: selectedStream?.url
              if (!externalTarget.isNullOrBlank()) {
                Button(
                  onClick = { WindowsSystemHelper.openInExternalMediaPlayer(externalTarget) },
                  colors = ButtonDefaults.buttonColors(containerColor = DecanDesktopColors.Crimson),
                ) {
                  Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Open External Player")
                }
              }
              OutlinedButton(onClick = onBack) { Text("Back") }
            }
          }
        }
      }
    }

    // Closed Captions Overlay
    if (activeCue != null) {
      Box(
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp, start = 40.dp, end = 40.dp),
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.78f),
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = activeCue.text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }
    }

    // Top Player Header
    Row(
      modifier =
        Modifier.align(Alignment.TopCenter)
          .fillMaxWidth()
          .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)))
          .padding(horizontal = 20.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.clip(CircleShape).background(DecanDesktopColors.SurfaceGlass).testTag("player_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column {
          Text(displayTitle, style = MaterialTheme.typography.titleMedium, color = Color.White)
          Text(
            text = "Space: Play/Pause • Left/Right: Seek 10s • Up/Down: Volume • F: Fullscreen • C: Subtitles",
            style = MaterialTheme.typography.bodySmall,
            color = DecanDesktopColors.Slate,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dubs.size > 1) {
          OutlinedButton(
            onClick = {
              val curIdx = dubs.indexOfFirst { it.subjectId == activeSubjectId }
              val nextDub = dubs[(curIdx + 1) % dubs.size]
              activeSubjectId = nextDub.subjectId
              onNotify("Switched audio dub to ${nextDub.langName.ifBlank { nextDub.language }}")
            },
            border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
          ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(dubs.firstOrNull { it.subjectId == activeSubjectId }?.langName?.ifBlank { "Audio" } ?: "Audio")
          }
        }

        if (orderedEpisodes.isNotEmpty()) {
          OutlinedButton(
            onClick = { showEpisodeDrawer = !showEpisodeDrawer },
            border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
          ) {
            Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Episodes")
          }
        }

        if (selectedStream != null && movie != null && request.localVideoPath == null) {
          IconButton(
            onClick = {
              downloadManager.enqueueVideoDownload(
                rootDir = settings.downloadDirectory,
                movie = movie!!,
                stream = selectedStream!!,
                season = currentSeason,
                episode = currentEpisode,
                subtitle = if (settings.includeSubtitlesInDownloads) selectedSubtitle else null,
              )
              onNotify("Queued ${movie!!.title} for download")
            },
          ) {
            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
          }
        }
      }
    }

    // Bottom Player Transport Bar
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
          .padding(horizontal = 24.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      val duration = telemetry.durationMs.coerceAtLeast(1L)
      Slider(
        value = (telemetry.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
        onValueChange = { pct -> mediaController.seekTo((pct * duration).toLong()) },
        colors = SliderDefaults.colors(
          thumbColor = DecanDesktopColors.Crimson,
          activeTrackColor = DecanDesktopColors.Crimson,
          inactiveTrackColor = Color.White.copy(alpha = 0.28f),
        ),
        modifier = Modifier.fillMaxWidth().height(26.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (hasPrevEp) {
            IconButton(onClick = {
              val (ps, pe) = orderedEpisodes[currentEpIndex - 1]
              jumpEpisode(ps, pe)
            }) {
              Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous Episode", tint = Color.White)
            }
          }

          IconButton(
            onClick = { mediaController.togglePlayPause() },
            modifier = Modifier.size(42.dp).clip(CircleShape).background(DecanDesktopColors.Crimson).testTag("player_play_pause_btn"),
          ) {
            Icon(
              imageVector = if (telemetry.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (telemetry.isPlaying) "Pause" else "Play",
              tint = Color.White,
            )
          }

          if (hasNextEp) {
            IconButton(onClick = {
              val (ns, ne) = orderedEpisodes[currentEpIndex + 1]
              jumpEpisode(ns, ne)
            }) {
              Icon(Icons.Filled.SkipNext, contentDescription = "Next Episode", tint = Color.White)
            }
          }

          Text(
            text = "${formatTimeMs(telemetry.currentPositionMs)} / ${formatTimeMs(telemetry.durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = DecanDesktopColors.Mist,
            modifier = Modifier.padding(horizontal = 8.dp),
          )

          IconButton(onClick = {
            muted = !muted
            mediaController.setVolume(volume, muted)
          }) {
            Icon(
              imageVector = if (muted || volume <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = "Mute",
              tint = Color.White,
            )
          }

          Slider(
            value = if (muted) 0f else volume,
            onValueChange = {
              volume = it
              muted = it <= 0.01f
              mediaController.setVolume(volume, muted)
            },
            colors = SliderDefaults.colors(
              thumbColor = Color.White,
              activeTrackColor = DecanDesktopColors.CrimsonBright,
              inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.width(110.dp),
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Quality selector
          if (streams.isNotEmpty()) {
            Box {
              OutlinedButton(
                onClick = { showQualityMenu = true },
                border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              ) {
                Text(selectedStream?.quality ?: "Quality")
              }
              DropdownMenu(expanded = showQualityMenu, onDismissRequest = { showQualityMenu = false }) {
                streams.forEach { st ->
                  DropdownMenuItem(
                    text = { Text("${st.quality} (${st.format}) • ${st.sizeMb.toInt()} MB") },
                    onClick = {
                      showQualityMenu = false
                      selectedStream = st
                      mediaController.loadAndPlay(
                        sourceUrlOrPath = st.url,
                        startPositionMs = telemetry.currentPositionMs,
                        volume = volume,
                        muted = muted,
                        playbackRate = playbackSpeed,
                        aspectMode = aspectMode,
                      )
                    },
                  )
                }
              }
            }
          }

          // Subtitle selector
          Box {
            IconButton(onClick = {
              if (subtitles.isEmpty()) {
                subtitlesEnabled = !subtitlesEnabled
              } else {
                showSubtitleMenu = true
              }
            }) {
              Icon(
                imageVector = Icons.Filled.ClosedCaption,
                contentDescription = "Subtitles",
                tint = if (subtitlesEnabled) DecanDesktopColors.CrimsonBright else Color.White,
              )
            }
            DropdownMenu(expanded = showSubtitleMenu, onDismissRequest = { showSubtitleMenu = false }) {
              DropdownMenuItem(
                text = { Text(if (subtitlesEnabled) "Hide Subtitles" else "Show Subtitles") },
                onClick = {
                  showSubtitleMenu = false
                  subtitlesEnabled = !subtitlesEnabled
                },
              )
              subtitles.forEach { sub ->
                DropdownMenuItem(
                  text = { Text(sub.language) },
                  onClick = {
                    showSubtitleMenu = false
                    selectedSubtitle = sub
                    subtitlesEnabled = true
                    scope.launch { subtitleCues = catalogRepository.fetchSubtitleCues(sub.url) }
                  },
                )
              }
            }
          }

          // Playback speed
          Box {
            OutlinedButton(
              onClick = { showSpeedMenu = true },
              border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
              Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("${playbackSpeed}x")
            }
            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
              listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                DropdownMenuItem(
                  text = { Text("${spd}x") },
                  onClick = {
                    showSpeedMenu = false
                    playbackSpeed = spd
                    mediaController.setPlaybackRate(spd)
                  },
                )
              }
            }
          }

          // Aspect Ratio
          Box {
            IconButton(onClick = { showAspectMenu = true }) {
              Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
            }
            DropdownMenu(expanded = showAspectMenu, onDismissRequest = { showAspectMenu = false }) {
              PlaybackAspectMode.entries.forEach { mode ->
                DropdownMenuItem(
                  text = { Text(mode.label) },
                  onClick = {
                    showAspectMenu = false
                    aspectMode = mode
                  },
                )
              }
            }
          }

          IconButton(onClick = onToggleFullscreen, modifier = Modifier.testTag("player_fullscreen_btn")) {
            Icon(
              imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
              contentDescription = "Fullscreen",
              tint = Color.White,
            )
          }
        }
      }
    }

    // Episode Drawer Overlay
    if (showEpisodeDrawer && seasons.isNotEmpty()) {
      Surface(
        color = DecanDesktopColors.SurfaceGlassStrong,
        border = BorderStroke(1.dp, DecanDesktopColors.OutlineGlass),
        modifier = Modifier.align(Alignment.CenterEnd).width(320.dp).fillMaxHeight(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Episodes", style = MaterialTheme.typography.titleLarge, color = DecanDesktopColors.Mist)
            OutlinedButton(onClick = { showEpisodeDrawer = false }) { Text("Close") }
          }
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons.sortedBy { it.season }) { s ->
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Season ${s.season}", style = MaterialTheme.typography.titleSmall, color = DecanDesktopColors.Slate)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  for (ep in 1..s.episodeCount.coerceAtLeast(1)) {
                    val active = s.season == currentSeason && ep == currentEpisode
                    Surface(
                      color = if (active) DecanDesktopColors.Crimson else DecanDesktopColors.SurfaceElevated,
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.size(width = 48.dp, height = 38.dp).clip(RoundedCornerShape(8.dp)).clickable {
                        showEpisodeDrawer = false
                        jumpEpisode(s.season, ep)
                      },
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Text("$ep", color = Color.White, fontWeight = FontWeight.Bold)
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

internal fun formatTimeMs(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val h = totalSec / 3600L
  val m = (totalSec % 3600L) / 60L
  val s = totalSec % 60L
  return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
