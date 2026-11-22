package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.download.StreamDownloadManager
import com.example.data.model.Movie
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.notifications.DecanNotificationManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatchDownloadSheet(
  movie: Movie,
  seasons: List<MovieBoxSeason>,
  initialSeason: Int,
  currentStreams: List<MovieBoxStream>,
  currentSubtitles: List<MovieBoxSubtitle>,
  catalogRepository: MovieCatalogRepository,
  allowMetered: Boolean,
  defaultIncludeSubs: Boolean,
  notifyDownloads: Boolean = true,
  onDismiss: () -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val isSeriesMode = seasons.isNotEmpty()
  var selectedSeason by remember {
    mutableIntStateOf(if (initialSeason > 0) initialSeason else (seasons.firstOrNull()?.season ?: 1))
  }
  val activeSeasonObj = seasons.firstOrNull { it.season == selectedSeason } ?: seasons.firstOrNull()
  var selectedEpisodes by remember(selectedSeason) { mutableStateOf<Set<Int>>(emptySet()) }
  var targetQuality by remember { mutableIntStateOf(1080) }
  var includeSubtitles by remember { mutableStateOf(defaultIncludeSubs) }

  val playableStreams = remember(currentStreams) { currentStreams.filter { it.isPlayableDirect } }
  var selectedStreamUrls by remember(playableStreams) {
    mutableStateOf(playableStreams.firstOrNull()?.let { setOf(it.url) } ?: emptySet())
  }
  var selectedSubUrls by remember(currentSubtitles) {
    val eng = currentSubtitles.firstOrNull { it.language.contains("English", true) }
    mutableStateOf(eng?.let { setOf(it.url) } ?: emptySet())
  }

  var isQueueing by remember { mutableStateOf(false) }
  var progressDone by remember { mutableIntStateOf(0) }
  var progressTotal by remember { mutableIntStateOf(0) }

  ModalBottomSheet(
    onDismissRequest = { if (!isQueueing) onDismiss() },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Batch Download Manager",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = movie.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = { if (!isQueueing) onDismiss() }) {
          Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
        }
      }

      if (isSeriesMode && activeSeasonObj != null) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(seasons, key = { it.season }) { season ->
            FilterChip(
              selected = season.season == selectedSeason,
              onClick = { if (!isQueueing) selectedSeason = season.season },
              label = { Text("Season ${season.season} (${season.episodeCount} eps)") },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Select Episodes (${selectedEpisodes.size}/${activeSeasonObj.episodeCount})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(
              onClick = {
                val allEps = (1..activeSeasonObj.episodeCount).toSet()
                selectedEpisodes = if (selectedEpisodes.size == allEps.size) emptySet() else allEps
              },
              enabled = !isQueueing,
            ) {
              Icon(
                imageVector = Icons.Filled.SelectAll,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (selectedEpisodes.size == activeSeasonObj.episodeCount) "Clear All" else "Select All")
            }
          }
        }

        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 72.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        ) {
          items((1..activeSeasonObj.episodeCount).toList(), key = { it }) { ep ->
            val checked = ep in selectedEpisodes
            Surface(
              color =
                if (checked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
              shape = MaterialTheme.shapes.small,
              border =
                BorderStroke(
                  width = 1.dp,
                  color =
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
              modifier =
                Modifier.clickable(enabled = !isQueueing) {
                  selectedEpisodes =
                    if (checked) selectedEpisodes - ep else selectedEpisodes + ep
                }.testTag("batch_ep_$ep"),
            ) {
              Box(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  if (checked) {
                    Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                  Text(
                    text = "Ep $ep",
                    style = MaterialTheme.typography.labelLarge,
                    color =
                      if (checked) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "Preferred Quality",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1080 to "1080p HD", 720 to "720p", 480 to "480p", 0 to "Best Available").forEach { (q, label) ->
              FilterChip(
                selected = targetQuality == q,
                onClick = { if (!isQueueing) targetQuality = q },
                label = { Text(label) },
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Subtitles,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Also download .srt subtitles",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Switch(
            checked = includeSubtitles,
            onCheckedChange = { if (!isQueueing) includeSubtitles = it },
          )
        }
      } else {
        Column(
          modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Hd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Video Streams (${playableStreams.size})",
                style = MaterialTheme.typography.titleSmall,
              )
            }
            TextButton(
              onClick = {
                val all = playableStreams.map { it.url }.toSet()
                selectedStreamUrls = if (selectedStreamUrls.size == all.size) emptySet() else all
              }
            ) {
              Text(if (selectedStreamUrls.size == playableStreams.size) "Clear" else "Select All")
            }
          }

          playableStreams.forEach { st ->
            val checked = st.url in selectedStreamUrls
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    selectedStreamUrls =
                      if (checked) selectedStreamUrls - st.url else selectedStreamUrls + st.url
                  }
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = {
                  selectedStreamUrls =
                    if (checked) selectedStreamUrls - st.url else selectedStreamUrls + st.url
                },
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${st.quality} • ${st.format}",
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                  text = "${st.source}${if (st.sizeMb > 0) " • ${st.sizeMb} MB" else ""}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          if (currentSubtitles.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Subtitle Tracks (.srt)",
                style = MaterialTheme.typography.titleSmall,
              )
              TextButton(
                onClick = {
                  val all = currentSubtitles.map { it.url }.toSet()
                  selectedSubUrls = if (selectedSubUrls.size == all.size) emptySet() else all
                }
              ) {
                Text(if (selectedSubUrls.size == currentSubtitles.size) "Clear" else "Select All")
              }
            }
            currentSubtitles.forEach { sub ->
              val checked = sub.url in selectedSubUrls
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      selectedSubUrls =
                        if (checked) selectedSubUrls - sub.url else selectedSubUrls + sub.url
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = checked,
                  onCheckedChange = {
                    selectedSubUrls =
                      if (checked) selectedSubUrls - sub.url else selectedSubUrls + sub.url
                  },
                )
                Text(
                  text = "${sub.language} (${sub.code.uppercase().ifBlank { "SRT" }})",
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }
        }
      }

      if (isQueueing && progressTotal > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          LinearProgressIndicator(
            progress = { progressDone.toFloat() / progressTotal.toFloat().coerceAtLeast(1f) },
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            text = "Resolving & queueing $progressDone / $progressTotal…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      val totalToQueue =
        if (isSeriesMode) selectedEpisodes.size else (selectedStreamUrls.size + selectedSubUrls.size)

      Button(
        onClick = {
          if (isSeriesMode) {
            val eps = selectedEpisodes.sorted()
            if (eps.isEmpty()) return@Button
            scope.launch {
              isQueueing = true
              progressTotal = eps.size
              progressDone = 0
              var queuedCount = 0
              var failedCount = 0

              for (ep in eps) {
                val streamsDef = async {
                  catalogRepository.getStreams(movie.subjectId, selectedSeason, ep, movie.detailPath)
                }
                val subsDef = async {
                  if (includeSubtitles) {
                    catalogRepository.getSubtitles(movie.subjectId, selectedSeason, ep)
                  } else emptyList()
                }
                val epStreams = streamsDef.await().filter { it.isPlayableDirect }
                val epSubs = subsDef.await()

                val chosenStream =
                  if (targetQuality > 0) {
                    epStreams.firstOrNull { it.qualityValue == targetQuality }
                      ?: epStreams.minByOrNull { kotlin.math.abs(it.qualityValue - targetQuality) }
                      ?: epStreams.firstOrNull()
                  } else {
                    epStreams.firstOrNull()
                  }

                if (chosenStream != null) {
                  StreamDownloadManager.enqueueStreamDownload(
                    context = context,
                    movieTitle = movie.title,
                    stream = chosenStream,
                    season = selectedSeason,
                    episode = ep,
                    allowMetered = allowMetered,
                  ).onSuccess { queuedCount++ }.onFailure { failedCount++ }
                } else {
                  failedCount++
                }

                if (includeSubtitles && epSubs.isNotEmpty()) {
                  val sub =
                    epSubs.firstOrNull { it.language.contains("English", true) } ?: epSubs.first()
                  StreamDownloadManager.enqueueSubtitleDownload(
                    context = context,
                    movieTitle = movie.title,
                    subtitle = sub,
                    season = selectedSeason,
                    episode = ep,
                    allowMetered = allowMetered,
                  ).onSuccess { queuedCount++ }
                }
                progressDone++
              }
              isQueueing = false
              if (queuedCount > 0) {
                DecanNotificationManager.notifyBatchQueued(
                  context = context,
                  movieTitle = movie.title,
                  fileCount = queuedCount,
                  systemEnabled = notifyDownloads,
                )
              }
              onNotify("Queued $queuedCount files to Downloads/DecanMovie (${if (failedCount > 0) "$failedCount unavailable" else "ready"})")
              onDismiss()
            }
          } else {
            val streams = playableStreams.filter { it.url in selectedStreamUrls }
            val subs = currentSubtitles.filter { it.url in selectedSubUrls }
            val result =
              StreamDownloadManager.enqueueMultiSelection(
                context = context,
                movieTitle = movie.title,
                streams = streams,
                subtitles = subs,
                season = 0,
                episode = 0,
                allowMetered = allowMetered,
              )
            if (result.queuedFiles > 0) {
              DecanNotificationManager.notifyBatchQueued(
                context = context,
                movieTitle = movie.title,
                fileCount = result.queuedFiles,
                systemEnabled = notifyDownloads,
              )
            }
            onNotify("Queued ${result.queuedFiles} file(s) to Downloads/DecanMovie")
            onDismiss()
          }
        },
        enabled = totalToQueue > 0 && !isQueueing,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("batch_download_confirm_button"),
      ) {
        if (isQueueing) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        } else {
          Icon(imageVector = Icons.Filled.Download, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (isSeriesMode) "Download $totalToQueue Episode${if (totalToQueue == 1) "" else "s"}"
              else "Download $totalToQueue Selected File${if (totalToQueue == 1) "" else "s"}",
            style = MaterialTheme.typography.titleSmall,
          )
        }
      }
    }
  }
}
