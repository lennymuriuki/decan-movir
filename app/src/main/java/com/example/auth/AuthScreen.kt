package com.example.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  onSkipSignIn: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    Image(
      painter = painterResource(id = R.drawable.img_cinema_auth_backdrop),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors =
                listOf(
                  MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                  MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                  MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                )
            )
          )
    )

    Column(
      modifier =
        Modifier.fillMaxSize()
          .verticalScroll(rememberScrollState())
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 24.dp, vertical = 28.dp)
          .widthIn(max = 520.dp)
          .align(Alignment.Center),
      verticalArrangement = Arrangement.SpaceBetween,
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
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.LocalMovies,
                contentDescription = stringResource(R.string.app_name),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
              )
            }
          }
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = stringResource(R.string.tagline_cinema),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        TextButton(
          onClick = onSkipSignIn,
          modifier = Modifier.testTag("skip_sign_in_top_button"),
        ) {
          Text("Skip", style = MaterialTheme.typography.labelLarge)
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Cinema, Curated\nFor Every Mood.",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            text = stringResource(R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          FeatureHighlightRow(
            icon = Icons.Filled.MovieFilter,
            text = "Live box office releases, genre rails & HD trailer previews",
          )
          FeatureHighlightRow(
            icon = Icons.Filled.Star,
            text = "Cloud-synced watchlist, watched log & 5-star reviews",
          )
        }

        AnimatedVisibility(visible = errorMessage != null) {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
              ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text =
                  "Sign-in is optional — tap \"Skip & Continue as Guest\" below to unlock everything immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
              )
            }
          }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          GoogleSignInButton(
            onAuthSuccess = {
              errorMessage = null
              onAuthSuccess()
            },
            onAuthError = { msg -> errorMessage = msg },
          )

          OutlinedButton(
            onClick = onSkipSignIn,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("skip_sign_in_button"),
          ) {
            Text(
              text = stringResource(R.string.skip_sign_in),
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow(
  icon: ImageVector,
  text: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(32.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp),
      )
    }
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      color = Color.White.copy(alpha = 0.9f),
    )
  }
}
