package com.example.desktop.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.data.remote.moviebox.SrtParser
import com.example.data.remote.moviebox.SubtitleCue
import com.example.desktop.AppState
import com.example.desktop.PlayRequest
import com.example.desktop.pickStream
import com.example.desktop.ui.formatTime
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent

private val SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun PlayerScreen(app: AppState, req: PlayRequest) {
  var season by remember(req) { mutableStateOf(req.season) }
  var episode by remember(req) { mutableStateOf(req.episode) }
  var seasons by remember(req) { mutableStateOf<List<MovieBoxSeason>>(emptyList()) }
  var movie by remember(req) { mutableStateOf(req.movie) }

  var streams by remember { mutableStateOf<List<MovieBoxStream>>(emptyList()) }
  var subs by remember { mutableStateOf<List<MovieBoxSubtitle>>(emptyList()) }
  var cues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
  var subIndex by remember { mutableStateOf(-1) }
  var stream by remember { mutableStateOf<MovieBoxStream?>(null) }
  var status by remember { mutableStateOf<String?>("Loading…") }

  var positionMs by remember { mutableStateOf(0L) }
  var lengthMs by remember { mutableStateOf(0L) }
  var playing by remember { mutableStateOf(false) }
  var dragging by remember { mutableStateOf(false) }
  var dragFrac by remember { mutableStateOf(0f) }
  var speed by remember { mutableStateOf(1.0f) }
  var volume by remember { mutableStateOf(100f) }
  var ended by remember { mutableStateOf(false) }
  var playerError by remember { mutableStateOf(false) }
  var qualityMenu by remember { mutableStateOf(false) }
  var speedMenu by remember { mutableStateOf(false) }
  var subMenu by remember { mutableStateOf(false) }

  val vlcOk = Vlc.available
  val component = remember { if (vlcOk) EmbeddedMediaPlayerComponent().apply { isFocusable = false } else null }
  val focus = remember { FocusRequester() }
  val settings = app.store.settings.value

  fun mrlFor(s: MovieBoxStream): String =
    if (s.headers.isNotEmpty() || !s.cookie.isNullOrBlank()) StreamProxy.register(s.url, s.headers, s.cookie) else s.url

  fun startPlayback(mrl: String, startMs: Long) {
    val c = component ?: return
    ended = false
    playerError = false
    val opts = if (startMs > 3000) arrayOf(":start-time=${startMs / 1000}") else emptyArray()
    c.mediaPlayer().media().play(mrl, *opts)
    c.mediaPlayer().controls().setRate(speed)
  }

  DisposableEffect(component) {
    component?.mediaPlayer()?.events()?.addMediaPlayerEventListener(
      object : MediaPlayerEventAdapter() {
        override fun error(mediaPlayer: MediaPlayer) { playerError = true }
        override fun finished(mediaPlayer: MediaPlayer) { ended = true }
      }
    )
    onDispose {
      val m = movie
      if (m != null && positionMs > 0 && !ended) app.store.savePosition(m.id, season, episode, positionMs)
      component?.let { c -> Thread { runCatching { c.mediaPlayer().controls().stop(); c.release() } }.start() }
    }
  }

  // Load streams + subtitles whenever the episode changes.
  LaunchedEffect(req, season, episode) {
    positionMs = 0; lengthMs = 0; cues = emptyList(); subIndex = -1; subs = emptyList(); streams = emptyList(); stream = null

    val local = req.localFile
    if (local != null) {
      status = null
      val srt = java.io.File(local.parentFile, local.nameWithoutExtension + ".srt")
      if (srt.exists()) { cues = runCatching { SrtParser.parse(srt.readText()) }.getOrDefault(emptyList()); subIndex = 0 }
      startPlayback(local.absolutePath, 0)
      return@LaunchedEffect
    }

    var m = movie ?: return@LaunchedEffect
    status = "Finding streams…"
    if (m.subjectId.isBlank()) {
      m = app.repo.resolvePlayableSubject(m) ?: m
      movie = m
    }
    if (m.subjectId.isBlank()) { status = "No streamable source found for this title."; return@LaunchedEffect }
    if (seasons.isEmpty() && season > 0) seasons = app.repo.getSeriesSeasons(m.subjectId)

    val all = app.repo.getStreams(m.subjectId, season, episode, m.detailPath)
    streams = all.filter { it.isPlayableDirect && it.url.isNotBlank() }.sortedByDescending { it.qualityValue }
    subs = app.repo.getSubtitles(m.subjectId, season, episode)
    val pick = pickStream(all, settings.defaultQuality)
    if (pick == null) { status = "No playable stream available (try downloading or another episode)."; return@LaunchedEffect }
    stream = pick
    status = null

    val en = subs.indexOfFirst { it.code.startsWith("en", true) || it.language.contains("English", true) }
    if (settings.subtitlesOnByDefault && en >= 0) {
      subIndex = en
      cues = app.repo.fetchSubtitleCues(subs[en].url)
    }
    startPlayback(mrlFor(pick), app.store.savedPosition(m.id, season, episode))
  }

  // Poll player state.
  LaunchedEffect(component) {
    val c = component ?: return@LaunchedEffect
    var tick = 0
    while (true) {
      delay(400)
      val st = c.mediaPlayer().status()
      if (!dragging) positionMs = st.time().coerceAtLeast(0)
      lengthMs = st.length().coerceAtLeast(0)
      playing = st.isPlaying
      if (++tick % 25 == 0) movie?.let { app.store.savePosition(it.id, season, episode, positionMs) }
    }
  }

  fun seasonEpisodes(s: Int) = seasons.firstOrNull { it.season == s }?.episodeCount ?: 0
  fun goNext() {
    if (req.localFile != null || season == 0) return
    if (episode < seasonEpisodes(season)) episode += 1
    else seasons.firstOrNull { it.season > season }?.let { season = it.season; episode = 1 }
  }
  fun goPrev() {
    if (req.localFile != null || season == 0) return
    if (episode > 1) episode -= 1
    else seasons.lastOrNull { it.season < season }?.let { season = it.season; episode = it.episodeCount }
  }

  LaunchedEffect(ended) {
    if (ended) {
      movie?.let { app.store.clearPosition(it.id, season, episode) }
      if (settings.autoPlayNext) goNext()
    }
  }
  LaunchedEffect(Unit) { focus.requestFocus() }

  fun seekBy(ms: Long) { component?.mediaPlayer()?.controls()?.setTime((positionMs + ms).coerceAtLeast(0)) }
  fun togglePlay() { component?.mediaPlayer()?.controls()?.pause() }

  Column(
    Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
      if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
      when (e.key) {
        Key.Spacebar -> { togglePlay(); true }
        Key.DirectionRight -> { seekBy(10_000); true }
        Key.DirectionLeft -> { seekBy(-10_000); true }
        Key.F -> { app.fullscreen = !app.fullscreen; true }
        Key.Escape -> { if (app.fullscreen) app.fullscreen = false else app.back(); true }
        else -> false
      }
    },
  ) {
    if (!app.fullscreen) {
      Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(app::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        Text(
          req.title + if (req.localFile == null && season > 0) "  •  S$season E$episode" else "",
          color = Color.White, style = MaterialTheme.typography.titleMedium,
        )
      }
    }

    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      if (component != null) {
        SwingPanel(factory = { component }, modifier = Modifier.fillMaxSize())
      } else {
        NoVlc(stream?.url)
      }
      status?.let { Text(it, color = Color.White, modifier = Modifier.background(Color(0xAA000000)).padding(12.dp)) }
      if (playerError) Text("Playback error — try another quality.", color = Color(0xFFFFB4AB), modifier = Modifier.background(Color(0xAA000000)).padding(12.dp))
    }

    // Subtitles are drawn below the video (a native video surface cannot be overlaid by Compose).
    val active = SrtParser.findActiveCue(cues, positionMs)
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(Color(0xFF05070C)).padding(horizontal = 24.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
      if (active != null) Text(active.text, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
    }

    Column(Modifier.fillMaxWidth().background(Color(0xFF0A0E17)).padding(horizontal = 16.dp, vertical = 6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(if (dragging) (dragFrac * lengthMs).toLong() else positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
        Slider(
          value = if (dragging) dragFrac else if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f,
          onValueChange = { dragging = true; dragFrac = it },
          onValueChangeFinished = { component?.mediaPlayer()?.controls()?.setTime((dragFrac * lengthMs).toLong()); positionMs = (dragFrac * lengthMs).toLong(); dragging = false },
          modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(formatTime(lengthMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (req.localFile == null && season > 0) TextButton(::goPrev) { Text("⏮") }
        TextButton({ seekBy(-10_000) }) { Text("⏪ 10s") }
        Button(::togglePlay) { Text(if (playing) "⏸ Pause" else "▶ Play") }
        TextButton({ seekBy(10_000) }) { Text("10s ⏩") }
        if (req.localFile == null && season > 0) TextButton(::goNext) { Text("⏭") }

        Box(Modifier.weight(1f)) {}

        Box {
          OutlinedButton({ speedMenu = true }) { Text("${speed}x") }
          DropdownMenu(speedMenu, { speedMenu = false }) {
            SPEEDS.forEach { sp -> DropdownMenuItem({ Text("${sp}x") }, { speed = sp; component?.mediaPlayer()?.controls()?.setRate(sp); speedMenu = false }) }
          }
        }
        if (streams.isNotEmpty()) Box {
          OutlinedButton({ qualityMenu = true }) { Text(stream?.let { it.resolutionTag.ifBlank { it.quality } } ?: "Quality") }
          DropdownMenu(qualityMenu, { qualityMenu = false }) {
            streams.forEach { st ->
              DropdownMenuItem({ Text(st.resolutionTag.ifBlank { st.quality }) }, {
                qualityMenu = false; stream = st
                startPlayback(mrlFor(st), positionMs)
              })
            }
          }
        }
        if (subs.isNotEmpty() || cues.isNotEmpty()) Box {
          OutlinedButton({ subMenu = true }) { Text(if (subIndex >= 0 && subIndex < subs.size) "CC: ${subs[subIndex].language}" else if (cues.isNotEmpty()) "CC: On" else "CC: Off") }
          DropdownMenu(subMenu, { subMenu = false }) {
            DropdownMenuItem({ Text("Off") }, { subIndex = -1; cues = emptyList(); subMenu = false })
            subs.forEachIndexed { i, sb ->
              DropdownMenuItem({ Text(sb.language) }, {
                subMenu = false; subIndex = i
                app.scope.launch { cues = app.repo.fetchSubtitleCues(sb.url) }
              })
            }
          }
        }
        Text("🔊", color = Color.White)
        Slider(volume, { volume = it; component?.mediaPlayer()?.audio()?.setVolume(it.toInt()) }, Modifier.width(100.dp), valueRange = 0f..120f)
        TextButton({ app.fullscreen = !app.fullscreen }) { Text(if (app.fullscreen) "Exit full screen" else "⛶ Full screen") }
      }
    }
  }
}

@Composable
private fun NoVlc(url: String?) {
  Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("VLC media player was not found", color = Color.White, style = MaterialTheme.typography.titleLarge)
    Text(
      "Decan Movie uses VLC for in-app playback. Install VLC (64-bit) and restart the app, or open the video in your browser.",
      color = Color(0xFFC8D4EA), textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button({ runCatching { Desktop.getDesktop().browse(URI("https://www.videolan.org/vlc/")) } }) { Text("Get VLC") }
      if (url != null) OutlinedButton({ runCatching { Desktop.getDesktop().browse(URI(url)) } }) { Text("Open in browser") }
    }
  }
}
