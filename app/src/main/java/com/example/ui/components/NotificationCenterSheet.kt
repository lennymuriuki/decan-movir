package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.notifications.InAppNotification
import com.example.notifications.NotificationCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterSheet(
  notifications: List<InAppNotification>,
  onMarkAllRead: () -> Unit,
  onClearAll: () -> Unit,
  onNotificationClick: (InAppNotification) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Notifications & Cinema News",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          val unreadCount = notifications.count { !it.isRead }
          Text(
            text =
              if (unreadCount > 0) "$unreadCount unread alert(s)"
              else "Movie drops, offline folders & app updates",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (notifications.any { !it.isRead }) {
            IconButton(
              onClick = onMarkAllRead,
              modifier = Modifier.testTag("notifications_mark_read_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DoneAll,
                contentDescription = "Mark all read",
                tint = MaterialTheme.colorScheme.primary,
              )
            }
          }
          if (notifications.isNotEmpty()) {
            IconButton(
              onClick = onClearAll,
              modifier = Modifier.testTag("notifications_clear_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = "Clear all notifications",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
          }
        }
      }

      if (notifications.isEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsNone,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(38.dp),
            )
            Text(
              text = "All Caught Up",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Poster previews for new drops, folder downloads, and updates will appear here.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
          contentPadding = PaddingValues(vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(notifications, key = { it.id }) { item ->
            val icon =
              when (item.category) {
                NotificationCategory.DOWNLOAD -> Icons.Filled.DownloadDone
                NotificationCategory.RELEASE -> Icons.Filled.NewReleases
                NotificationCategory.TRENDING -> Icons.Filled.TrendingUp
              }
            val defaultCta =
              when (item.category) {
                NotificationCategory.DOWNLOAD -> "Open"
                NotificationCategory.RELEASE -> "Update"
                NotificationCategory.TRENDING -> "Watch"
              }
            val ctaText = item.actionLabel ?: defaultCta

            Card(
              colors =
                CardDefaults.cardColors(
                  containerColor =
                    if (!item.isRead) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
              shape = MaterialTheme.shapes.medium,
              modifier =
                Modifier.fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { onNotificationClick(item) }
                  .testTag("notification_item_${item.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                if (!item.imageUrl.isNullOrBlank()) {
                  Box(
                    modifier =
                      Modifier.width(56.dp)
                        .height(80.dp)
                        .clip(MaterialTheme.shapes.small)
                  ) {
                    MoviePosterImage(
                      imageUrl = item.imageUrl,
                      contentDescription = item.title,
                      modifier = Modifier.fillMaxWidth().height(80.dp),
                    )
                    Surface(
                      color = MaterialTheme.colorScheme.primary,
                      shape = CircleShape,
                      modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Filled.LocalMovies,
                          contentDescription = "Decan Movie",
                          tint = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.size(12.dp),
                        )
                      }
                    }
                  }
                } else {
                  Box(modifier = Modifier.size(48.dp)) {
                    Surface(
                      color = MaterialTheme.colorScheme.primaryContainer,
                      shape = MaterialTheme.shapes.medium,
                      modifier = Modifier.size(46.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = icon,
                          contentDescription = item.category.label,
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(22.dp),
                        )
                      }
                    }
                    Surface(
                      color = MaterialTheme.colorScheme.primary,
                      shape = CircleShape,
                      modifier =
                        Modifier.align(Alignment.BottomEnd)
                          .size(18.dp)
                          .background(MaterialTheme.colorScheme.surface, CircleShape),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Filled.LocalMovies,
                          contentDescription = "Decan Movie",
                          tint = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.size(11.dp),
                        )
                      }
                    }
                  }
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "DECAN • ${item.category.label.uppercase()}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    if (!item.isRead) {
                      Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp),
                      ) {}
                    }
                  }
                  Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      color = MaterialTheme.colorScheme.primaryContainer,
                      shape = MaterialTheme.shapes.small,
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.PlayArrow,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onPrimaryContainer,
                          modifier = Modifier.size(12.dp),
                        )
                        Text(
                          text = ctaText,
                          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                          color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
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
