package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.WatchStatus
import com.example.data.remote.MovieCatalogRepository
import com.example.ui.components.MetadataChip
import com.example.ui.components.MoviePosterImage
import com.example.ui.components.StarRatingBar
import com.example.ui.components.TrailerVideoPlayer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(
  movieId: String,
  savedEntry: SavedMovieEntry?,
  catalogRepository: MovieCatalogRepository,
  isSaving: Boolean,
  onBack: () -> Unit,
  onSaveEntry: (Movie, WatchStatus, Boolean, Int, String) -> Unit,
  onRemoveEntry: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var movie by remember(movieId) {
    mutableStateOf(catalogRepository.getCachedMovie(movieId) ?: savedEntry?.toMovie())
  }
  var isEnriching by remember(movieId) { mutableStateOf(movie == null) }

  LaunchedEffect(movieId) {
    val enriched = catalogRepository.getMovieDetail(movieId)
    if (enriched != null) {
      movie = enriched
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
            text = "Directed by ${activeMovie.director}",
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
        if (activeMovie.contentRating.isNotBlank()) MetadataChip(text = activeMovie.contentRating)
        if (!activeMovie.priceFormatted.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = activeMovie.priceFormatted,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      if (!activeMovie.previewUrl.isNullOrBlank()) {
        TrailerVideoPlayer(
          previewUrl = activeMovie.previewUrl,
          movieTitle = activeMovie.title,
        )
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
    }
  }
}
