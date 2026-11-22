package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.download.StreamDownloadManager
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.WatchStatus
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxDub
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.ui.components.BatchDownloadSheet
import com.example.ui.components.MetadataChip
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.MoviePosterImage
import com.example.ui.components.StarRatingBar
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(
  movieId: String,
  savedEntry: SavedMovieEntry?,
  savedLibraryMap: Map<String, SavedMovieEntry>,
  catalogRepository: MovieCatalogRepository,
  allowMeteredDownloads: Boolean,
  includeSubtitlesInBatch: Boolean,
  notifyDownloads: Boolean = true,
  isSaving: Boolean,
  onBack: () -> Unit,
  onSwitchSubject: (String) -> Unit,
  onOpenPlayer: (Movie, Int, Int) -> Unit,
  onOpenMovie: (Movie) -> Unit,
  onToggleQuickWatchlist: (Movie) -> Unit,
  onNotify: (String) -> Unit,
  onSaveEntry: (Movie, WatchStatus, Boolean, Int, String) -> Unit,
  onRemoveEntry: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var movie by remember(movieId) {
    mutableStateOf(catalogRepository.getCachedMovie(movieId) ?: savedEntry?.toMovie())
  }
  var isEnriching by remember(movieId) { mutableStateOf(movie == null) }
  var dubs by remember(movieId) { mutableStateOf<List<MovieBoxDub>>(emptyList()) }
  var seasons by remember(movieId) { mutableStateOf<List<MovieBoxSeason>>(emptyList()) }
  var selectedSeason by remember(movieId) { mutableIntStateOf(0) }
  var selectedEpisode by remember(movieId) { mutableIntStateOf(0) }
  var streams by remember(movieId) { mutableStateOf<List<MovieBoxStream>>(emptyList()) }
  var subtitles by remember(movieId) { mutableStateOf<List<MovieBoxSubtitle>>(emptyList()) }
  var recommendations by remember(movieId) { mutableStateOf<List<Movie>>(emptyList()) }
  var isResolvingStreams by remember(movieId) { mutableStateOf(false) }
  var showBatchSheet by remember(movieId) { mutableStateOf(false) }

  suspend fun loadStreamsAndSubs(subjectId: String, se: Int, ep: Int, detailPath: String?) {
    if (subjectId.isBlank()) return
    isResolvingStreams = true
    val streamsDeferred = scope.async { catalogRepository.getStreams(subjectId, se, ep, detailPath) }
    val subsDeferred = scope.async { catalogRepository.getSubtitles(subjectId, se, ep) }
    streams = streamsDeferred.await()
    subtitles = subsDeferred.await()
    isResolvingStreams = false
  }

  LaunchedEffect(movieId) {
    val enriched = catalogRepository.getMovieDetail(movieId)
    if (enriched != null) {
      movie = enriched
    }
    val sid =
      enriched?.subjectId?.takeIf { it.isNotBlank() }
        ?: movieId.takeIf { it.startsWith("mbox_") }?.removePrefix("mbox_")
        ?: ""
    val recDeferred =
      scope.async { catalogRepository.getRecommendations(sid, enriched?.genre ?: movie?.genre) }
    if (sid.isNotBlank()) {
      val rich = catalogRepository.getMovieBoxRichDetail(sid)
      if (rich != null) {
        movie = rich.movie
        dubs = rich.dubs
        seasons = rich.seasons
        if (rich.seasons.isNotEmpty()) {
          val firstSeason = rich.seasons.first()
          selectedSeason = firstSeason.season
          selectedEpisode = 1
          loadStreamsAndSubs(sid, firstSeason.season, 1, rich.detailPath)
        } else {
          loadStreamsAndSubs(sid, 0, 0, rich.detailPath)
        }
      } else {
        loadStreamsAndSubs(sid, 0, 0, enriched?.detailPath)
      }
    }
    recommendations = recDeferred.await().filter { it.id != movieId }
    isEnriching = false
  }

  val activeMovie = movie
  if (activeMovie == null) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      if (isEnriching) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Film details unavailable",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Button(onClick = onBack) { Text("Back") }
        }
      }
    }
    return
  }

  var watchStatus by remember(savedEntry?.watchStatus, movieId) {
    mutableStateOf(savedEntry?.watchStatusEnum() ?: WatchStatus.WANT_TO_WATCH)
  }
  var favorite by remember(savedEntry?.favorite, movieId) {
    mutableStateOf(savedEntry?.favorite ?: false)
  }
  var personalRating by remember(savedEntry?.personalRating, movieId) {
    mutableIntStateOf(savedEntry?.personalRating?.toInt() ?: 0)
  }
  var personalNote by remember(savedEntry?.personalNote, movieId) {
    mutableStateOf(savedEntry?.personalNote.orEmpty())
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .windowInsetsPadding(WindowInsets.navigationBars)
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
      MoviePosterImage(
        imageUrl = activeMovie.backdropUrl.ifBlank { activeMovie.posterUrl },
        contentDescription = activeMovie.title,
        modifier = Modifier.fillMaxSize(),
      )

      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color.Black.copy(alpha = 0.55f),
                    Color.Black.copy(alpha = 0.25f),
                    MaterialTheme.colorScheme.background,
                  )
              )
            )
      )

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalIconButton(
          onClick = onBack,
          colors =
            IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = Color.Black.copy(alpha = 0.6f),
              contentColor = Color.White,
            ),
          modifier = Modifier.testTag("detail_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilledTonalIconButton(
            onClick = {
              val shareText =
                "Check out \"${activeMovie.title}\" (${activeMovie.releaseYear}) on Decan Movie!"
              val intent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, shareText)
                }
              context.startActivity(Intent.createChooser(intent, "Share film"))
            },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
              ),
            modifier = Modifier.testTag("detail_share_button"),
          ) {
            Icon(imageVector = Icons.Filled.Share, contentDescription = "Share movie")
          }

          FilledTonalIconButton(
            onClick = {
              val nextFav = !favorite
              favorite = nextFav
              onSaveEntry(activeMovie, watchStatus, nextFav, personalRating, personalNote)
            },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor =
                  if (favorite) MaterialTheme.colorScheme.secondary else Color.White,
              ),
            modifier = Modifier.testTag("detail_favorite_button"),
          ) {
            Icon(
              imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
              contentDescription = if (favorite) "Unfavorite" else "Favorite",
            )
          }
        }
      }

      Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        MoviePosterImage(
          imageUrl = activeMovie.posterUrl,
          contentDescription = activeMovie.title,
          modifier =
            Modifier.width(106.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium),
        )

        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(bottom = 4.dp),
        ) {
          Text(
            text = activeMovie.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = activeMovie.director,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 760.dp)
          .align(Alignment.CenterHorizontally)
          .padding(horizontal = 20.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MetadataChip(text = activeMovie.genre)
        if (activeMovie.releaseYear.isNotBlank()) MetadataChip(text = activeMovie.releaseYear)
        if (activeMovie.runtimeMinutes > 0) MetadataChip(text = "${activeMovie.runtimeMinutes} min")
        if (!activeMovie.imdbRating.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = "IMDb ${activeMovie.imdbRating}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
        if (activeMovie.contentRating.isNotBlank()) MetadataChip(text = activeMovie.contentRating)
        if (!activeMovie.priceFormatted.isNullOrBlank()) {
          MetadataChip(text = activeMovie.priceFormatted)
        }
      }

      // Dedicated Theater Player + Batch Download Action Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = { onOpenPlayer(activeMovie, selectedSeason, selectedEpisode) },
          shape = MaterialTheme.shapes.large,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          modifier = Modifier.weight(1.3f).height(52.dp).testTag("detail_watch_theater_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          val playLabel =
            if (selectedSeason > 0 && selectedEpisode > 0)
              "Watch S${selectedSeason}E$selectedEpisode"
            else if (activeMovie.subjectId.isNotBlank()) "Watch in Theater"
            else "Play Trailer"
          Text(text = playLabel, style = MaterialTheme.typography.labelLarge)
        }

        if (activeMovie.subjectId.isNotBlank()) {
          FilledTonalButton(
            onClick = { showBatchSheet = true },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.weight(1f).height(52.dp).testTag("detail_batch_download_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DownloadForOffline,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Batch DL", style = MaterialTheme.typography.labelLarge)
          }
        }
      }

      if (dubs.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Audio Dubs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dubs, key = { it.subjectId }) { dub ->
              FilterChip(
                selected = dub.subjectId == activeMovie.subjectId,
                onClick = { onSwitchSubject("mbox_${dub.subjectId}") },
                label = { Text(dub.langName) },
              )
            }
          }
        }
      }

      if (seasons.isNotEmpty()) {
        val currentSeasonObj =
          seasons.firstOrNull { it.season == selectedSeason } ?: seasons.first()
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Seasons & Episodes",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "${currentSeasonObj.episodeCount} eps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(seasons, key = { it.season }) { seasonObj ->
                FilterChip(
                  selected = seasonObj.season == selectedSeason,
                  onClick = {
                    selectedSeason = seasonObj.season
                    selectedEpisode = 1
                    scope.launch {
                      loadStreamsAndSubs(
                        activeMovie.subjectId,
                        seasonObj.season,
                        1,
                        activeMovie.detailPath,
                      )
                    }
                  },
                  label = { Text("Season ${seasonObj.season}") },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.primary,
                      selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
              }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items((1..currentSeasonObj.episodeCount).toList(), key = { it }) { epNum ->
                FilterChip(
                  selected = epNum == selectedEpisode,
                  onClick = {
                    selectedEpisode = epNum
                    scope.launch {
                      loadStreamsAndSubs(
                        activeMovie.subjectId,
                        selectedSeason,
                        epNum,
                        activeMovie.detailPath,
                      )
                    }
                  },
                  label = { Text("Ep $epNum") },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                      selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
              }
            }
          }
        }
      }

      if (activeMovie.subjectId.isNotBlank()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
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
                  imageVector = Icons.Filled.Hd,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text(
                    text = "Streams & Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  if (selectedSeason > 0 && selectedEpisode > 0) {
                    Text(
                      text = "Season $selectedSeason • Episode $selectedEpisode",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              IconButton(
                onClick = {
                  scope.launch {
                    loadStreamsAndSubs(
                      activeMovie.subjectId,
                      selectedSeason,
                      selectedEpisode,
                      activeMovie.detailPath,
                    )
                  }
                },
                modifier = Modifier.testTag("refresh_streams_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Resolve streams",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }

            if (isResolvingStreams) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "Resolving 1080p streams & subtitle tracks…",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            } else if (streams.isEmpty()) {
              Text(
                text = "No direct CDN streams returned for this title right now. Tap refresh to retry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              streams.forEachIndexed { idx, stream ->
                if (idx > 0) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                StreamOptionRow(
                  stream = stream,
                  onPlay = {
                    if (stream.isPlayableDirect) {
                      onOpenPlayer(activeMovie, selectedSeason, selectedEpisode)
                    } else {
                      runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stream.url)))
                      }
                    }
                  },
                  onDownload = {
                    StreamDownloadManager.enqueueStreamDownload(
                      context = context,
                      movieTitle = activeMovie.title,
                      stream = stream,
                      season = selectedSeason,
                      episode = selectedEpisode,
                      allowMetered = allowMeteredDownloads,
                    ).onSuccess {
                      onNotify("Downloading ${activeMovie.title} (${stream.quality}) to Downloads/DecanMovie")
                    }.onFailure { err ->
                      onNotify(err.localizedMessage ?: "Download failed to start")
                    }
                  },
                  onCopy = {
                    copyToClipboard(context, stream.url)
                    onNotify("Stream URL copied")
                  },
                )
              }
            }

            if (subtitles.isNotEmpty()) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                  text = "Subtitles (.srt) • ${subtitles.size} tracks",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }

              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
              ) {
                items(subtitles, key = { "${it.code}_${it.url}" }) { sub ->
                  FilledTonalButton(
                    onClick = {
                      StreamDownloadManager.enqueueSubtitleDownload(
                        context = context,
                        movieTitle = activeMovie.title,
                        subtitle = sub,
                        season = selectedSeason,
                        episode = selectedEpisode,
                        allowMetered = allowMeteredDownloads,
                      ).onSuccess {
                        onNotify("Downloading ${sub.language} (.srt) subtitles")
                      }.onFailure { err ->
                        onNotify(err.localizedMessage ?: "Subtitle download failed")
                      }
                    },
                    shape = MaterialTheme.shapes.medium,
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Download,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(sub.language, style = MaterialTheme.typography.labelLarge)
                  }
                }
              }
            }
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Synopsis",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = activeMovie.synopsis,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
      }

      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
            text = "My Screening Log",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchStatus.entries.forEach { status ->
              FilterChip(
                selected = watchStatus == status,
                onClick = { watchStatus = status },
                label = { Text(status.label, style = MaterialTheme.typography.labelLarge) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.testTag("status_chip_${status.name}"),
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Your Rating",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StarRatingBar(
              rating = personalRating,
              onRatingChanged = { personalRating = it },
            )
          }

          OutlinedTextField(
            value = personalNote,
            onValueChange = { if (it.length <= 1000) personalNote = it },
            label = { Text("Personal review or viewing note") },
            placeholder = { Text("What stood out about the score, pacing, or ending?") },
            minLines = 3,
            maxLines = 5,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().testTag("review_note_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = {
                onSaveEntry(activeMovie, watchStatus, favorite, personalRating, personalNote)
              },
              enabled = !isSaving,
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.weight(1f).height(50.dp).testTag("save_library_button"),
            ) {
              if (isSaving) {
                CircularProgressIndicator(
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(20.dp),
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Filled.BookmarkAdded,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (savedEntry != null) "Update Library" else "Save to Library")
              }
            }

            if (savedEntry != null) {
              OutlinedButton(
                onClick = { onRemoveEntry(activeMovie.id, activeMovie.title) },
                enabled = !isSaving,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.height(50.dp).testTag("remove_library_button"),
              ) {
                Icon(
                  imageVector = Icons.Filled.DeleteOutline,
                  contentDescription = "Remove from library",
                )
              }
            }
          }
        }
      }

      if (recommendations.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
            text = "Recommended Similar Titles",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(recommendations, key = { it.id }) { rec ->
              MoviePosterCard(
                movie = rec,
                savedEntry = savedLibraryMap[rec.id],
                onClick = { onOpenMovie(rec) },
                onToggleWatchlist = { onToggleQuickWatchlist(rec) },
                modifier = Modifier.width(136.dp),
              )
            }
          }
        }
      }
    }
  }

  if (showBatchSheet) {
    BatchDownloadSheet(
      movie = activeMovie,
      seasons = seasons,
      initialSeason = selectedSeason,
      currentStreams = streams,
      currentSubtitles = subtitles,
      catalogRepository = catalogRepository,
      allowMetered = allowMeteredDownloads,
      defaultIncludeSubs = includeSubtitlesInBatch,
      notifyDownloads = notifyDownloads,
      onDismiss = { showBatchSheet = false },
      onNotify = onNotify,
    )
  }
}

@Composable
private fun StreamOptionRow(
  stream: MovieBoxStream,
  onPlay: () -> Unit,
  onDownload: () -> Unit,
  onCopy: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          color =
            if (stream.quality.contains("1080") || stream.quality.contains("2160"))
              MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.extraSmall,
        ) {
          Text(
            text = stream.quality,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color =
              if (stream.quality.contains("1080") || stream.quality.contains("2160"))
                MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
          )
        }
        Text(
          text = stream.format,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (stream.sizeMb > 0.0) {
          Text(
            text = "${stream.sizeMb} MB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Text(
        text = buildString {
          append(stream.source)
          if (!stream.codec.isNullOrBlank()) append(" • ${stream.codec}")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
      FilledTonalIconButton(
        onClick = onPlay,
        colors =
          IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
      ) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Watch stream in player")
      }

      if (stream.isPlayableDirect) {
        FilledTonalIconButton(onClick = onDownload) {
          Icon(imageVector = Icons.Filled.Download, contentDescription = "Download MP4")
        }
      }

      IconButton(onClick = onCopy) {
        Icon(
          imageVector = Icons.Filled.ContentCopy,
          contentDescription = "Copy stream URL",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun copyToClipboard(context: Context, value: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("Stream URL", value))
}
