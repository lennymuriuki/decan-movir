package com.example.ui.components

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.Movie
import com.example.data.model.SavedMovieEntry
import com.example.data.model.WatchStatus

@Composable
fun MoviePosterImage(
  imageUrl: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  if (imageUrl.isBlank()) {
    Image(
      painter = painterResource(id = R.drawable.img_hero_fallback),
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier,
    )
  } else {
    SubcomposeAsyncImage(
      model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier,
      loading = {
        Box(
          modifier =
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(32.dp),
          )
        }
      },
      error = {
        Image(
          painter = painterResource(id = R.drawable.img_hero_fallback),
          contentDescription = contentDescription,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      },
    )
  }
}

@Composable
fun MoviePosterCard(
  movie: Movie,
  savedEntry: SavedMovieEntry?,
  onClick: () -> Unit,
  onToggleWatchlist: () -> Unit,
  rankOverride: Int? = null,
  compact: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.96f else 1f,
    label = "card_press_scale",
  )

  Column(
    modifier =
      modifier
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }
        .clip(MaterialTheme.shapes.medium)
        .clickable(
          interactionSource = interactionSource,
          indication = androidx.compose.material3.ripple(),
          onClick = onClick,
        )
        .testTag("movie_card_${movie.id}"),
    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
  ) {
    val badgePadding = if (compact) 5.dp else 8.dp
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(2f / 3f)
          .clip(MaterialTheme.shapes.medium)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
      MoviePosterImage(
        imageUrl = movie.posterUrl,
        contentDescription = movie.title,
        modifier = Modifier.fillMaxSize(),
      )

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(if (compact) 54.dp else 68.dp)
            .align(Alignment.BottomCenter)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
              )
            )
      )

      FilledTonalIconButton(
        onClick = onToggleWatchlist,
        colors =
          IconButtonDefaults.filledTonalIconButtonColors(
            containerColor =
              if (savedEntry != null) MaterialTheme.colorScheme.primary
              else Color.Black.copy(alpha = 0.58f),
            contentColor =
              if (savedEntry != null) MaterialTheme.colorScheme.onPrimary else Color.White,
          ),
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(badgePadding)
            .size(if (compact) 30.dp else 36.dp)
            .minimumInteractiveComponentSize()
            .testTag("quick_watchlist_${movie.id}"),
      ) {
        Icon(
          imageVector =
            when {
              savedEntry?.watchStatusEnum() == WatchStatus.WATCHED -> Icons.Filled.CheckCircle
              savedEntry != null -> Icons.Filled.Bookmark
              else -> Icons.Filled.BookmarkAdd
            },
          contentDescription =
            if (savedEntry != null) "Saved in library" else "Add to watchlist",
          modifier = Modifier.size(if (compact) 15.dp else 18.dp),
        )
      }

      val displayRank = rankOverride ?: movie.rankPosition
      if (displayRank != null) {
        Surface(
          color =
            if (displayRank <= 3) MaterialTheme.colorScheme.primary
            else Color.Black.copy(alpha = 0.84f),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.align(Alignment.TopStart).padding(badgePadding),
        ) {
          Text(
            text = "#$displayRank",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color =
              if (displayRank <= 3) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 2.dp),
          )
        }
      } else if (savedEntry?.favorite == true) {
        Surface(
          color = MaterialTheme.colorScheme.secondary,
          shape = CircleShape,
          modifier = Modifier.align(Alignment.TopStart).padding(badgePadding),
        ) {
          Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Favorite",
            tint = Color.White,
            modifier = Modifier.padding(4.dp).size(11.dp),
          )
        }
      } else if (!movie.imdbRating.isNullOrBlank()) {
        Surface(
          color = Color.Black.copy(alpha = 0.76f),
          shape = MaterialTheme.shapes.extraSmall,
          modifier = Modifier.align(Alignment.TopStart).padding(badgePadding),
        ) {
          Text(
            text = "★ ${movie.imdbRating}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
          )
        }
      }

      Row(
        modifier = Modifier.align(Alignment.BottomStart).padding(badgePadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.68f),
          shape = MaterialTheme.shapes.extraSmall,
        ) {
          Text(
            text = movie.releaseYear,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp),
          )
        }
        if (movie.mediaType.equals("Anime", ignoreCase = true)) {
          Surface(
            color = MaterialTheme.colorScheme.secondary,
            shape = MaterialTheme.shapes.extraSmall,
          ) {
            Text(
              text = "ANIME",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondary,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
          }
        } else if (movie.mediaType.equals("Series", ignoreCase = true)) {
          Surface(
            color = MaterialTheme.colorScheme.tertiary,
            shape = MaterialTheme.shapes.extraSmall,
          ) {
            Text(
              text = "SERIES",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onTertiary,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
          }
        } else if (movie.subjectId.isNotBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.extraSmall,
          ) {
            Text(
              text = "HD",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
          }
        } else if (!compact && movie.previewUrl != null) {
          Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.extraSmall,
          ) {
            Text(
              text = "TRAILER",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
          }
        }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)) {
      Text(
        text = movie.title,
        style =
          if (compact) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
          else MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = if (compact) 1 else 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = movie.genre,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun HeroSpotlightCard(
  movie: Movie,
  savedEntry: SavedMovieEntry?,
  onOpenDetail: () -> Unit,
  onToggleWatchlist: () -> Unit,
  onPlayNow: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Card(
    onClick = onOpenDetail,
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = modifier.fillMaxWidth().height(268.dp).testTag("hero_spotlight_${movie.id}"),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      MoviePosterImage(
        imageUrl = movie.backdropUrl.ifBlank { movie.posterUrl },
        contentDescription = movie.title,
        modifier = Modifier.fillMaxSize(),
      )

      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    Color.Black.copy(alpha = 0.14f),
                    Color.Black.copy(alpha = 0.56f),
                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                  )
              )
            )
      )

      Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
      ) {
        Text(
          text =
            when {
              movie.mediaType.equals("Anime", ignoreCase = true) -> "FEATURED ANIME"
              movie.mediaType.equals("Series", ignoreCase = true) -> "FEATURED SERIES"
              else -> "FEATURED SPOTLIGHT"
            },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
      }

      Column(
        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          MetadataChip(text = movie.genre)
          MetadataChip(text = movie.releaseYear)
          if (!movie.imdbRating.isNullOrBlank()) {
            MetadataChip(text = "★ ${movie.imdbRating}")
          } else if (movie.runtimeMinutes > 0) {
            MetadataChip(text = "${movie.runtimeMinutes}m")
          }
        }

        Text(
          text = movie.title,
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Text(
          text = movie.synopsis,
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.82f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Button(
            onClick = { onPlayNow?.invoke() ?: onOpenDetail() },
            shape = MaterialTheme.shapes.large,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.testTag("hero_play_button_${movie.id}"),
          ) {
            Icon(
              imageVector = Icons.Filled.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                if (onPlayNow != null) "Watch Now"
                else if (movie.subjectId.isNotBlank()) "Streams & Download"
                else if (movie.previewUrl != null) "Watch Trailer"
                else "View Details",
              style = MaterialTheme.typography.labelLarge,
            )
          }

          OutlinedButton(
            onClick = onToggleWatchlist,
            shape = MaterialTheme.shapes.large,
            colors =
              ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                containerColor = Color.Black.copy(alpha = 0.35f),
              ),
          ) {
            Icon(
              imageVector =
                if (savedEntry != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkAdd,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (savedEntry != null) savedEntry.watchStatusEnum().label else "Watchlist",
              style = MaterialTheme.typography.labelLarge,
            )
          }
        }
      }
    }
  }
}

@Composable
fun MetadataChip(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color.White.copy(alpha = 0.14f),
    shape = MaterialTheme.shapes.small,
    modifier = modifier,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = Color.White,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun StarRatingBar(
  rating: Int,
  onRatingChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (star in 1..5) {
      val selected = star <= rating
      val tint by
        animateColorAsState(
          targetValue =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
          label = "starTint",
        )
      IconButton(
        onClick = { onRatingChanged(if (rating == star) 0 else star) },
        modifier = Modifier.size(48.dp).testTag("star_rating_$star"),
      ) {
        Icon(
          imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
          contentDescription = "Rate $star stars",
          tint = tint,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}

@Composable
fun TrailerVideoPlayer(
  previewUrl: String,
  movieTitle: String,
  sectionTitle: String = "Official Trailer Preview",
  headers: Map<String, String> = emptyMap(),
  autoPlay: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var isPlaying by remember(previewUrl) { mutableStateOf(autoPlay) }
  var isMuted by remember(previewUrl) { mutableStateOf(false) }
  var isBuffering by remember(previewUrl) { mutableStateOf(autoPlay) }
  var hasError by remember(previewUrl) { mutableStateOf(false) }
  var videoViewRef by remember(previewUrl) { mutableStateOf<VideoView?>(null) }
  var mediaPlayerRef by remember(previewUrl) { mutableStateOf<MediaPlayer?>(null) }

  DisposableEffect(previewUrl) {
    onDispose {
      runCatching { videoViewRef?.stopPlayback() }
      mediaPlayerRef = null
      videoViewRef = null
    }
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = sectionTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = movieTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = {
            runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl)))
            }
          },
          modifier = Modifier.testTag("open_external_trailer_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open externally",
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
      ) {
        if (!hasError) {
          AndroidView(
            factory = { ctx ->
              VideoView(ctx).apply {
                tag = previewUrl
                val uri = Uri.parse(previewUrl)
                if (headers.isNotEmpty()) {
                  setVideoURI(uri, headers)
                } else {
                  setVideoURI(uri)
                }
                setOnPreparedListener { mp ->
                  mediaPlayerRef = mp
                  isBuffering = false
                  mp.isLooping = true
                  val vol = if (isMuted) 0f else 1f
                  mp.setVolume(vol, vol)
                  if (isPlaying) {
                    start()
                  }
                }
                setOnErrorListener { _, _, _ ->
                  isBuffering = false
                  isPlaying = false
                  hasError = true
                  true
                }
                setOnInfoListener { _, what, _ ->
                  when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> isBuffering = true
                    MediaPlayer.MEDIA_INFO_BUFFERING_END,
                    MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> isBuffering = false
                  }
                  false
                }
                videoViewRef = this
              }
            },
            update = { view ->
              if (view.tag != previewUrl) {
                view.tag = previewUrl
                val uri = Uri.parse(previewUrl)
                if (headers.isNotEmpty()) {
                  view.setVideoURI(uri, headers)
                } else {
                  view.setVideoURI(uri)
                }
                if (autoPlay) {
                  view.start()
                }
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        }

        if (isBuffering && !hasError) {
          CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
          )
        }

        if (hasError) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
          ) {
            Text(
              text = "Stream preview unavailable in embedded player",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
              color = Color.White,
            )
            OutlinedButton(
              onClick = {
                runCatching {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(previewUrl)))
                }
              }
            ) {
              Text("Open Externally")
            }
          }
        } else {
          Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilledTonalIconButton(
              onClick = {
                val vv = videoViewRef
                if (vv != null) {
                  if (isPlaying) {
                    vv.pause()
                    isPlaying = false
                  } else {
                    isBuffering = mediaPlayerRef == null
                    vv.start()
                    isPlaying = true
                  }
                }
              },
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
              modifier = Modifier.testTag("trailer_play_button"),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause video" else "Play video",
              )
            }

            FilledTonalIconButton(
              onClick = {
                isMuted = !isMuted
                val vol = if (isMuted) 0f else 1f
                runCatching { mediaPlayerRef?.setVolume(vol, vol) }
              },
              colors =
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = Color.Black.copy(alpha = 0.65f),
                  contentColor = Color.White,
                ),
              modifier = Modifier.testTag("trailer_mute_button"),
            ) {
              Icon(
                imageVector =
                  if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                  else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute video" else "Mute video",
              )
            }
          }
        }
      }
    }
  }
}
