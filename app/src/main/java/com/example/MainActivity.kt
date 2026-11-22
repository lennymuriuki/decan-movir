package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.settings.AppSettingsRepository
import com.example.data.settings.AppThemeMode
import com.example.notifications.DecanNotificationManager
import com.example.ui.navigation.DecanApp
import com.example.ui.theme.DecanMovieTheme

data class NotificationDeepLink(
  val tab: String? = null,
  val movieId: String? = null,
  val nonce: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {
  private var deepLinkState by mutableStateOf<NotificationDeepLink?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DecanNotificationManager.ensureInitialized(applicationContext)
    consumeDeepLink(intent)
    enableEdgeToEdge()
    setContent {
      val settingsRepository =
        remember(applicationContext) { AppSettingsRepository(applicationContext) }
      val appSettings by settingsRepository.settings.collectAsStateWithLifecycle()
      DecanMovieTheme(darkTheme = appSettings.themeMode == AppThemeMode.DARK) {
        DecanApp(
          settingsRepository = settingsRepository,
          pendingDeepLink = deepLinkState,
          onConsumedDeepLink = { deepLinkState = null },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeDeepLink(intent)
  }

  private fun consumeDeepLink(intent: Intent?) {
    if (intent == null) return
    val tab = intent.getStringExtra(DecanNotificationManager.EXTRA_NAV_TAB)
    val movieId = intent.getStringExtra(DecanNotificationManager.EXTRA_MOVIE_ID)
    if (!tab.isNullOrBlank() || !movieId.isNullOrBlank()) {
      deepLinkState = NotificationDeepLink(tab = tab, movieId = movieId)
      intent.removeExtra(DecanNotificationManager.EXTRA_NAV_TAB)
      intent.removeExtra(DecanNotificationManager.EXTRA_MOVIE_ID)
    }
  }
}
