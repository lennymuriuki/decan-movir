# Decan Movie

**Decan Movie** is a Material 3 Android application built with Kotlin and Jetpack Compose for discovering movies and TV series, resolving multi-quality streams and subtitles, previewing trailers, downloading videos/subtitles locally, and syncing watchlists and star reviews to Cloud Firestore.

## Download APK via GitHub Actions

1. Push this repository to GitHub (or use AI Studio's **Push to GitHub** menu).
2. Open the **Actions** tab in your GitHub repository and select **Build Decan Movie APK** (it triggers automatically on every push to `main`/`master` or manually via **Run workflow**).
3. Once the workflow finishes, download **`decan-movie-debug-apk`** from the **Artifacts** section at the bottom of the run summary, unzip it, and install `app-debug.apk` on your Android device.

> **Note on Google Sign-In:** If you generate a new keystore locally or in CI, register its SHA-1/SHA-256 fingerprint in your Firebase project (`app/google-services.json`) so Google Sign-In via Credential Manager remains authorized.

## Integrated APIs & Capabilities

- **MovieBox Mobile & H5 BFF APIs** (implemented natively in Kotlin with `OkHttp` + `HmacMD5` request signing and `x-user` token bootstrapping):
  - Home banners, hot sections, and curated feeds (`/wefeed-h5api-bff/home`)
  - Paginated Movies (`tabId=2`) & TV Series (`tabId=5`) feeds (`/wefeed-h5api-bff/subject/filter`)
  - Catalog Search (`/wefeed-mobile-bff/subject-api/search`) & Real-Time Autocomplete Suggestions (`/wefeed-h5api-bff/subject/search-suggest`)
  - Deep Metadata & Audio Dubs (`/wefeed-mobile-bff/subject-api/get`)
  - TV Seasons & Episode Counts (`/wefeed-mobile-bff/subject-api/season-info`)
  - 1080p/720p/480p Web CDN & Mobile Native Stream Resolution + DASH Policy Extraction (`/wefeed-h5api-bff/subject/play`, `/wefeed-mobile-bff/subject-api/play-info`)
  - Stream & External `.srt` Subtitle Extraction (`/wefeed-mobile-bff/subject-api/get-stream-captions`, `/wefeed-mobile-bff/subject-api/get-ext-captions`)
- **iTunes Movies API**: Top Box Office RSS, search fallback, and trailer previews.
- **In-App Stream Player & Downloader**: Embedded `VideoView` stream/trailer playback with HTTP header forwarding and native Android `DownloadManager` downloads for MP4 videos and `.srt` subtitles.
- **Firebase Auth & Cloud Firestore**: Google Sign-In via Jetpack Credential Manager and per-user cloud-synced Watchlist, Watching, Watched, Favorites, and personal star ratings/reviews.
