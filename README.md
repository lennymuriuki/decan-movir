# Decan Movie

**Decan Movie** is a Material 3 Android cinema application built with Kotlin and Jetpack Compose for streaming movies, TV series, and anime in a dedicated Netflix/YouTube-style Theater Player, batch-downloading episodes with synchronized `.srt` subtitles, tracking Top Rankings and recommendations, checking GitHub Releases for updates, and syncing personal watchlists and star reviews to Cloud Firestore.

## Download APK & Automated GitHub Releases

1. Push this repository to GitHub (or use AI Studio's **Push to GitHub** menu).
2. **CI Builds on Every Push**: Open the **Actions** tab and select **Build Decan Movie APK** to download **`decan-movie-debug-apk`** from the workflow summary artifacts.
3. **Publish GitHub Releases (Downloadable APKs)**:
   - Tag and push a version (`git tag v1.1.0 && git push origin v1.1.0`), **or**
   - Open **Actions → Android Release Publisher → Run workflow** and enter a `version_tag` (e.g. `v1.1.0`).
   - `.github/workflows/android-release.yml` automatically compiles `decan-movie-<tag>-debug.apk` (plus signed Release APK & AAB when `SIGNING_KEY_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` secrets are configured) and publishes a GitHub Release that the in-app **Settings → GitHub Updates & Releases** checker detects automatically.

> **Note on Firebase & Google Sign-In:** Pushing from AI Studio omits `app/google-services.json` by default. The GitHub Actions workflows still compile and produce working APKs for discovery, Theater playback, and batch downloads without it. To also enable Google Sign-In and Cloud Firestore watchlist sync in GitHub-built APKs, add a repository secret named **`GOOGLE_SERVICES_JSON`** containing your `app/google-services.json`.

## Features & Integrated APIs

- **Dedicated Theater Player Window**: Top-pinned 16:9 playback in portrait and full-screen landscape toggle (`WindowCompat` immersive bars), double-tap `-10s` / `+10s`, Next/Previous Episode, live synchronized `.srt` Closed Captions (`CC`), resolution switcher (`1080p`/`720p`/`480p`), speed control (`0.5x–2.0x`), Fit/Fill aspect mode, screen lock, audio dubs, and Up-Next recommendations.
- **MovieBox-Style Multi-Select Batch Downloads & Live Queue**: Select multiple TV/anime episodes (or multiple resolutions + `.srt` subtitle tracks) at once into `Downloads/DecanMovie/`, track live MB progress in the **Downloads** tab, and play downloaded videos offline inside the Theater Player.
- **MovieBox Mobile & H5 BFF APIs**:
  - Home banners & curated rails (`/wefeed-h5api-bff/home`)
  - Top Rankings (`/wefeed-mobile-bff/tab/ranking-list`)
  - Movies, TV Series, Anime & Genre pagination (`/wefeed-h5api-bff/subject/filter`)
  - Recommendations (`/wefeed-mobile-bff/subject-api/detail-rec`)
  - Catalog Search, Autocomplete & Hot Trending Keywords (`/wefeed-mobile-bff/subject-api/search`, `/wefeed-h5api-bff/subject/search-suggest`, `/wefeed-h5api-bff/subject/hot-search`)
  - Deep Metadata, Audio Dubs & Seasons (`/wefeed-mobile-bff/subject-api/get`, `/wefeed-mobile-bff/subject-api/season-info`)
  - 1080p/720p/480p Stream Resolution & External `.srt` Subtitles (`/wefeed-h5api-bff/subject/play`, `/wefeed-mobile-bff/subject-api/play-info`, `/wefeed-mobile-bff/subject-api/get-stream-captions`, `/wefeed-mobile-bff/subject-api/get-ext-captions`)
- **Settings, Account & GitHub Updater**: Switch between `Cinema Dark` and `Editorial Light`, configure default quality/speed/subtitles, manage Google Sign-In/Sign-Out, and check `api.github.com/repos/{owner}/{repo}/releases/latest` for new releases.
