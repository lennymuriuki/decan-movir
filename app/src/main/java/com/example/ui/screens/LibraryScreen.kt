package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.GoogleSignInButton
import com.example.data.model.SavedMovieEntry
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.example.ui.components.MoviePosterImage
import com.example.ui.viewmodel.LibraryFilter
import com.example.ui.viewmodel.LibraryUiState

@Composable
fun LibraryScreen(
  libraryState: LibraryUiState,
  userProfile: UserProfile?,
  userDisplayName: String,
  userEmail: String,
  userPhotoUrl: String?,
  isGuestMode: Boolean = false,
  isFirebaseConfigured: Boolean = true,
  selectedFilter: LibraryFilter,
  onSelectFilter: (LibraryFilter) -> Unit,
  onOpenMovie: (SavedMovieEntry) -> Unit,
  onToggleFavorite: (SavedMovieEntry) -> Unit,
  onRemoveEntry: (SavedMovieEntry) -> Unit,
  onExploreMovies: () -> Unit,
  onSignOut: () -> Unit,
  onAuthSuccess: () -> Unit = {},
  onAuthError: (String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("library_list"),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f),
            ) {
              if (!userPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                  model = userPhotoUrl,
                  contentDescription = userDisplayName,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(CircleShape),
                )
              } else {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = CircleShape,
                  modifier = Modifier.size(48.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = userDisplayName.take(1).uppercase(),
                      style = MaterialTheme.typography.titleLarge,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }

              Column {
                Text(
                  text =
                    if (isGuestMode) "Guest Cinema"
                    else userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName,
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                val subtitleText =
                  when {
                    !isGuestMode && userEmail.isNotBlank() -> userEmail
                    isGuestMode && isFirebaseConfigured ->
                      "Saved locally on device • Sign in anytime to sync"
                    else -> "Saved locally on this device"
                  }
                Text(
                  text = subtitleText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }

            if (!isGuestMode) {
              OutlinedButton(
                onClick = onSignOut,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.testTag("sign_out_button"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Logout,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.sign_out), style = MaterialTheme.typography.labelLarge)
              }
            }
          }

          if (isGuestMode && isFirebaseConfigured) {
            GoogleSignInButton(
              onAuthSuccess = onAuthSuccess,
              onAuthError = onAuthError,
              modifier = Modifier.testTag("library_sign_in_button"),
            )
          }

          if (libraryState is LibraryUiState.Success) {
            val entries = libraryState.entries
            val watchlistCount =
              entries.count { it.watchStatusEnum() == WatchStatus.WANT_TO_WATCH }
            val watchedCount = entries.count { it.watchStatusEnum() == WatchStatus.WATCHED }
            val favCount = entries.count { it.favorite }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              StatTile(
                label = "Watchlist",
                value = watchlistCount.toString(),
                modifier = Modifier.weight(1f),
              )
              StatTile(
                label = "Watched",
                value = watchedCount.toString(),
                modifier = Modifier.weight(1f),
              )
              StatTile(
                label = "Favorites",
                value = favCount.toString(),
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(LibraryFilter.entries, key = { it.name }) { filter ->
          FilterChip(
            selected = filter == selectedFilter,
            onClick = { onSelectFilter(filter) },
            label = { Text(filter.label, style = MaterialTheme.typography.labelLarge) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.testTag("library_filter_${filter.name}"),
          )
        }
      }
    }

    when (libraryState) {
      LibraryUiState.Loading -> {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          }
        }
      }
      is LibraryUiState.Error -> {
        item {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = libraryState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }
      is LibraryUiState.Success -> {
        val filtered =
          when (selectedFilter) {
            LibraryFilter.ALL -> libraryState.entries
            LibraryFilter.WATCHLIST ->
              libraryState.entries.filter {
                it.watchStatusEnum() == WatchStatus.WANT_TO_WATCH
              }
            LibraryFilter.WATCHING ->
              libraryState.entries.filter { it.watchStatusEnum() == WatchStatus.WATCHING }
            LibraryFilter.WATCHED ->
              libraryState.entries.filter { it.watchStatusEnum() == WatchStatus.WATCHED }
            LibraryFilter.FAVORITES -> libraryState.entries.filter { it.favorite }
          }

        if (filtered.isEmpty()) {
          item {
            Card(
              colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Image(
                  painter = painterResource(id = R.drawable.img_empty_watchlist),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(170.dp)
                      .clip(MaterialTheme.shapes.medium),
                )
                Text(
                  text = stringResource(R.string.empty_library_title),
                  style = MaterialTheme.typography.headlineMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = stringResource(R.string.empty_library_subtitle),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                  onClick = onExploreMovies,
                  shape = MaterialTheme.shapes.large,
                  modifier = Modifier.testTag("explore_movies_button"),
                ) {
                  Text(stringResource(R.string.explore_movies))
                }
              }
            }
          }
        } else {
          items(filtered, key = { it.movieId.ifBlank { it.id } }) { entry ->
            SavedLibraryRowCard(
              entry = entry,
              onClick = { onOpenMovie(entry) },
              onToggleFavorite = { onToggleFavorite(entry) },
              onRemove = { onRemoveEntry(entry) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StatTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(vertical = 10.dp, horizontal = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = value,
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SavedLibraryRowCard(
  entry: SavedMovieEntry,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRemove: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .clickable(onClick = onClick)
        .testTag("library_card_${entry.movieId}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MoviePosterImage(
        imageUrl = entry.posterUrl,
        contentDescription = entry.title,
        modifier =
          Modifier.width(82.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium),
      )

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.extraSmall,
          ) {
            Text(
              text = entry.watchStatusEnum().label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
          }
          if (entry.releaseYear.isNotBlank()) {
            Text(
              text = entry.releaseYear,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Text(
          text = entry.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Text(
          text = "${entry.genre} • ${entry.director}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        if (entry.personalRating > 0) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            repeat(entry.personalRating.toInt().coerceIn(0, 5)) {
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
              )
            }
            Text(
              text = "${entry.personalRating}/5",
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 4.dp),
            )
          }
        }

        if (!entry.personalNote.isNullOrBlank()) {
          Text(
            text = "“${entry.personalNote}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onToggleFavorite) {
          Icon(
            imageVector =
              if (entry.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Toggle favorite",
            tint =
              if (entry.favorite) MaterialTheme.colorScheme.secondary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onRemove) {
          Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = "Remove from library",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
