package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import com.example.data.settings.DownloadStorageLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DramaExperienceDetector
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.WatchStatus
import com.example.data.remote.MovieCatalogRepository
import com.example.data.remote.moviebox.MovieBoxDub
import com.example.data.remote.moviebox.MovieBoxSeason
import com.example.data.remote.moviebox.MovieBoxStream
import com.example.data.remote.moviebox.MovieBoxSubtitle
import com.example.ui.components.BatchDownloadSheet
import com.example.ui.components.EpisodeNumberGrid
import com.example.ui.components.MetadataChip
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.MoviePosterImage
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
  storageLocation: DownloadStorageLocation = DownloadStorageLocation.PUBLIC_DOWNLOADS,
  onChangeStorageLocation: (DownloadStorageLocation) -> Unit = {},
  notifyDownloads: Boolean = true,
  isSaving: Boolean,
  onBack: () -> Unit,
  onSwitchSubject: (String) -> Unit,
  onOpenPlayer: (Movie, Int, Int) -> Unit,
  onOpenDramaPlayer: (Movie, Int, Int) -> Unit = onOpenPlayer,
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
  var showBatchSheet by remember(movieId) { mutableStateOf(false) }

  suspend fun loadStreamsAndSubs(subjectId: String, se: Int, ep: Int, detailPath: String?) {
    if (subjectId.isBlank()) return
    val streamsDeferred = scope.async { catalogRepository.getStreams(subjectId, se, ep, detailPath) }
    val subsDeferred = scope.async { catalogRepository.getSubtitles(subjectId, se, ep) }
    streams = streamsDeferred.await()
    subtitles = subsDeferred.await()
  }

  LaunchedEffect(movieId) {
    if (movieId.startsWith("mbox_")) {
      val sid = movieId.removePrefix("mbox_")
      val recDeferred =
        scope.async { catalogRepository.getRecommendations(sid, movie?.genre) }
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
        loadStreamsAndSubs(sid, 0, 0, movie?.detailPath)
      }
      recommendations = recDeferred.await().filter { it.id != movieId }
    } else {
      val base = catalogRepository.getMovieDetail(movieId) ?: movie
      if (base != null) movie = base
      val bridged =
        if (base != null && base.subjectId.isBlank()) {
          catalogRepository.resolvePlayableSubject(base) ?: base
        } else base
      if (bridged != null) movie = bridged
      val sid = bridged?.subjectId.orEmpty()
      val recDeferred =
        scope.async { catalogRepository.getRecommendations(sid, bridged?.genre ?: movie?.genre) }
      if (sid.isNotBlank()) {
        val rich = catalogRepository.getMovieBoxRichDetail(sid)
        if (rich != null) {
          dubs = rich.dubs
          seasons = rich.seasons
          val mergedMovie =
            bridged!!.copy(
              detailPath = rich.detailPath.ifBlank { bridged.detailPath },
              posterUrl = bridged.posterUrl.ifBlank { rich.movie.posterUrl },
              backdropUrl = bridged.backdropUrl.ifBlank { rich.movie.backdropUrl },
            )
          movie = mergedMovie
          if (rich.seasons.isNotEmpty()) {
            val firstSeason = rich.seasons.first()
            selectedSeason = firstSeason.season
            selectedEpisode = 1
            loadStreamsAndSubs(sid, firstSeason.season, 1, rich.detailPath)
          } else {
            loadStreamsAndSubs(sid, 0, 0, rich.detailPath)
          }
        } else {
          loadStreamsAndSubs(sid, 0, 0, bridged?.detailPath)
        }
      }
      recommendations = recDeferred.await().filter { it.id != movieId }
    }
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
            text = "Title currently unavailable",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Button(onClick = onBack) { Text("Back") }
        }
      }
    }
    return
  }

  var favorite by remember(savedEntry?.favorite, movieId) {
    mutableStateOf(savedEntry?.favorite ?: false)
  }
  var synopsisExpanded by remember(movieId) { mutableStateOf(false) }

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
            onClick = { onToggleQuickWatchlist(activeMovie) },
            colors =
              IconButtonDefaults.filledTonalIconButtonColors(
                containerColor =
                  if (savedEntry != null) MaterialTheme.colorScheme.primary
                  else Color.Black.copy(alpha = 0.6f),
                contentColor =
                  if (savedEntry != null) MaterialTheme.colorScheme.onPrimary else Color.White,
              ),
            modifier = Modifier.testTag("detail_watchlist_button"),
          ) {
            Icon(
              imageVector =
                if (savedEntry != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
              contentDescription = if (savedEntry != null) "Saved in watchlist" else "Save to watchlist",
            )
          }

          FilledTonalIconButton(
            onClick = {
              val nextFav = !favorite
              favorite = nextFav
              onSaveEntry(
                activeMovie,
                savedEntry?.watchStatusEnum() ?: WatchStatus.WANT_TO_WATCH,
                nextFav,
                savedEntry?.personalRating?.toInt() ?: 0,
                savedEntry?.personalNote.orEmpty(),
              )
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

      val isDramaTitle =
        remember(activeMovie, seasons) {
          DramaExperienceDetector.isEpisodicDrama(activeMovie, seasons)
        }

      // Dedicated Player + Batch Download Action Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = {
            if (isDramaTitle) {
              onOpenDramaPlayer(
                activeMovie,
                selectedSeason.coerceAtLeast(1),
                selectedEpisode.coerceAtLeast(1),
              )
            } else {
              onOpenPlayer(activeMovie, selectedSeason, selectedEpisode)
            }
          },
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
            when {
              isDramaTitle && selectedSeason > 0 && selectedEpisode > 0 ->
                "Swipe Drama S${selectedSeason}E$selectedEpisode"
              isDramaTitle -> "Watch Vertical Drama"
              selectedSeason > 0 && selectedEpisode > 0 ->
                "Watch S${selectedSeason}E$selectedEpisode"
              activeMovie.subjectId.isNotBlank() -> "Watch in Theater"
              else -> "Play Trailer"
            }
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
        EpisodeNumberGrid(
          seasons = seasons,
          selectedSeason = selectedSeason,
          selectedEpisode = selectedEpisode,
          onSelectSeason = { seasonNum ->
            selectedSeason = seasonNum
            selectedEpisode = 1
            scope.launch {
              loadStreamsAndSubs(
                activeMovie.subjectId,
                seasonNum,
                1,
                activeMovie.detailPath,
              )
            }
          },
          onSelectEpisode = { seasonNum, epNum ->
            selectedSeason = seasonNum
            selectedEpisode = epNum
            if (isDramaTitle) {
              onOpenDramaPlayer(activeMovie, seasonNum, epNum)
            } else {
              onOpenPlayer(activeMovie, seasonNum, epNum)
            }
          },
          actionLabel = if (activeMovie.subjectId.isNotBlank()) "Batch DL" else null,
          onActionClick =
            if (activeMovie.subjectId.isNotBlank()) {
              { showBatchSheet = true }
            } else null,
        )
      }

      if (activeMovie.synopsis.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "Overview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = activeMovie.synopsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
          )
          if (activeMovie.synopsis.length > 140) {
            TextButton(
              onClick = { synopsisExpanded = !synopsisExpanded },
              contentPadding = PaddingValues(0.dp),
            ) {
              Text(if (synopsisExpanded) "Show less" else "Read more")
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
      storageLocation = storageLocation,
      onChangeStorageLocation = onChangeStorageLocation,
      notifyDownloads = notifyDownloads,
      onDismiss = { showBatchSheet = false },
      onNotify = onNotify,
    )
  }
}
