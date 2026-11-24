package com.decanmovie.desktop.data.remote

import com.decanmovie.desktop.data.remote.moviebox.MovieBoxEngine
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxRichDetail
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSeason
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxStream
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSubtitle
import com.decanmovie.desktop.data.remote.moviebox.MovieBoxSuggestion
import com.decanmovie.desktop.data.remote.moviebox.SubtitleCue
import com.decanmovie.desktop.data.store.DesktopAppStorage
import com.decanmovie.desktop.model.AnimeSortMode
import com.decanmovie.desktop.model.CategorySortMode
import com.decanmovie.desktop.model.DramaExperienceDetector
import com.decanmovie.desktop.model.Movie
import com.decanmovie.desktop.model.MovieRail
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

data class DesktopHomeCatalogResult(
  val spotlight: List<Movie>,
  val trending: List<Movie>,
  val recentlyAdded: List<Movie>,
  val popularMovies: List<Movie>,
  val popularSeries: List<Movie>,
  val popularAnime: List<Movie>,
  val popularDramas: List<Movie>,
  val recommended: List<Movie>,
  val rails: List<MovieRail>,
  val allMovies: List<Movie>,
  val trendingKeywords: List<String>,
  val isOfflineFallback: Boolean,
)

class DesktopMovieCatalogRepository(
  private val itunes: ItunesMovieClient = ItunesMovieClient(),
  private val movieBox: MovieBoxEngine = MovieBoxEngine(),
  private val jikanTvMaze: JikanTvMazeService = JikanTvMazeService(),
  private val storage: DesktopAppStorage? = null,
) {
  private val cache = ConcurrentHashMap<String, Movie>()
  @Volatile private var cachedHome: Pair<Long, DesktopHomeCatalogResult>? = null
  private val richDetailCache = ConcurrentHashMap<String, Pair<Long, MovieBoxRichDetail>>()
  private val streamCache = ConcurrentHashMap<String, Pair<Long, List<MovieBoxStream>>>()
  private val subtitleCache = ConcurrentHashMap<String, Pair<Long, List<MovieBoxSubtitle>>>()
  private val subtitleCuesCache = ConcurrentHashMap<String, Pair<Long, List<SubtitleCue>>>()
  private val recCache = ConcurrentHashMap<String, Pair<Long, List<Movie>>>()

  fun getCachedHomeSnapshot(): DesktopHomeCatalogResult? {
    cachedHome?.second?.takeIf { !it.isOfflineFallback && it.allMovies.isNotEmpty() }?.let {
      return it
    }
    val raw = storage?.readSnapshotJson() ?: return null
    return runCatching {
      val obj = JSONObject(raw)
      val savedAt = obj.optLong("savedAt", 0L)
      if (System.currentTimeMillis() - savedAt > SNAPSHOT_MAX_AGE_MS) return null
      val all = parseMovies(obj.optJSONArray("allMovies")).filter(::isValidCinemaEntry)
      if (all.isEmpty()) return null
      all.forEach { cache[it.id] = it }
      val spotlight = parseMovies(obj.optJSONArray("spotlight")).filter(::isValidCinemaEntry)
      val trending = parseMovies(obj.optJSONArray("trending")).filter(::isValidCinemaEntry)
      val recentlyAdded = parseMovies(obj.optJSONArray("recentlyAdded")).filter(::isValidCinemaEntry)
      val popularMovies = parseMovies(obj.optJSONArray("popularMovies")).filter(::isValidCinemaEntry)
      val popularSeries = parseMovies(obj.optJSONArray("popularSeries")).filter(::isValidCinemaEntry)
      val popularAnime = parseMovies(obj.optJSONArray("popularAnime")).filter(::isGenuineAnime)
      val popularDramas = parseMovies(obj.optJSONArray("popularDramas")).filter(::isValidCinemaEntry)
      val recommended = parseMovies(obj.optJSONArray("recommended")).filter(::isValidCinemaEntry)
      val rails = parseRails(obj.optJSONArray("rails"))
      val keywords = buildList {
        val arr = obj.optJSONArray("trendingKeywords") ?: JSONArray()
        for (i in 0 until arr.length()) {
          val s = arr.optString(i)
          if (s.isNotBlank()) add(s)
        }
      }
      DesktopHomeCatalogResult(
        spotlight = spotlight.ifEmpty { all.take(6) },
        trending = trending.ifEmpty { all.take(15) },
        recentlyAdded = recentlyAdded.ifEmpty { all.take(12) },
        popularMovies = popularMovies,
        popularSeries = popularSeries,
        popularAnime = popularAnime,
        popularDramas = popularDramas,
        recommended = recommended.ifEmpty { all.take(12) },
        rails = rails,
        allMovies = all,
        trendingKeywords = keywords,
        isOfflineFallback = false,
      )
    }.getOrNull()
  }

  suspend fun loadHomeCatalog(forceRefresh: Boolean = false): DesktopHomeCatalogResult = coroutineScope {
    val cached = cachedHome
    if (!forceRefresh && cached != null && !cached.second.isOfflineFallback &&
      (System.currentTimeMillis() - cached.first) < HOME_CACHE_TTL_MS
    ) {
      return@coroutineScope cached.second
    }

    try {
      val tokenDef = async { withTimeoutOrNull(4500L) { runCatching { movieBox.bootstrapToken() }.getOrNull() } }
      val homeDef = async { withTimeoutOrNull(5500L) { runCatching { movieBox.getHome() }.getOrNull() } }
      val rankingsDef = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getRankings(page = 1, perPage = 24) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val moviesDef = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getMovies(page = 1, perPage = 20, sort = "RECOMMEND") }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val newestDef = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getMovies(page = 1, perPage = 16, sort = "LATEST") }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val seriesDef = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getTvSeries(page = 1, perPage = 20) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val dramasDef = async {
        withTimeoutOrNull(6000L) {
          runCatching { movieBox.getDramas(page = 1, perPage = 20) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val mboxAnimeDef = async {
        withTimeoutOrNull(5500L) {
          runCatching { movieBox.getAnime(page = 1, perPage = 18) }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val jikanAnimeDef = async {
        withTimeoutOrNull(4500L) {
          runCatching { jikanTvMaze.browseAnime("All", AnimeSortMode.TOP_RANKED, page = 1, limit = 18) }
            .getOrDefault(emptyList())
        }.orEmpty()
      }
      val keywordsDef = async {
        withTimeoutOrNull(4000L) {
          runCatching { movieBox.getTrendingKeywords() }.getOrDefault(emptyList())
        }.orEmpty()
      }
      val itunesDef = async {
        withTimeoutOrNull(4500L) {
          runCatching { itunes.getTopMovies() }.getOrDefault(emptyList())
        }.orEmpty()
      }

      tokenDef.await()
      val mboxHome = homeDef.await()
      val trending = rankingsDef.await().filter(::isValidCinemaEntry)
      val popularMovies = moviesDef.await().filter(::isValidCinemaEntry)
      val recentMovies = newestDef.await().filter(::isValidCinemaEntry)
      val popularSeries = seriesDef.await().filter(::isValidCinemaEntry)
      val popularDramas = dramasDef.await().filter(::isValidCinemaEntry)
      val mboxAnime = mboxAnimeDef.await().filter(::isGenuineAnime)
      val jikanAnime = jikanAnimeDef.await().filter(::isGenuineAnime)
      val trendingKeywords = keywordsDef.await()
      val topItunes = itunesDef.await().filter(::isValidCinemaEntry)

      val homeSpotlight = mboxHome?.spotlight.orEmpty().filter(::isValidCinemaEntry)
      val homeAll = mboxHome?.allMovies.orEmpty().filter(::isValidCinemaEntry)
      val homeRails =
        mboxHome?.rails.orEmpty().mapNotNull { r ->
          val clean = r.movies.filter(::isValidCinemaEntry)
          if (clean.isEmpty()) null else r.copy(movies = clean)
        }

      val mboxAnimeByTitle = mboxAnime.associateBy { normalizeTitleKey(it.title) }
      val enrichedAnime =
        jikanAnime.map { a ->
          val m = mboxAnimeByTitle[normalizeTitleKey(a.title)]
          if (m != null) a.copy(subjectId = m.subjectId, detailPath = m.detailPath) else a
        }

      val seenAnime = HashSet<String>()
      val effectiveAnime =
        (enrichedAnime + mboxAnime).filter(::isGenuineAnime).filter {
          seenAnime.add(normalizeTitleKey(it.title))
        }

      val combined =
        (homeSpotlight +
            trending +
            popularDramas +
            popularMovies +
            recentMovies +
            popularSeries +
            effectiveAnime +
            homeAll +
            topItunes)
          .filter(::isValidCinemaEntry)
          .distinctBy { it.id }

      if (combined.isEmpty()) {
        return@coroutineScope buildFallbackResult()
      }

      combined.forEach { cache[it.id] = it }

      val effectiveTrending =
        if (trending.isNotEmpty()) trending.mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) }
        else combined.sortedByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }.take(15)

      val effectiveDramas =
        (popularDramas + combined.filter { DramaExperienceDetector.isEpisodicDrama(it) })
          .distinctBy { it.id }
          .take(18)

      val recentlyAdded =
        (recentMovies + combined.sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 })
          .distinctBy { it.id }
          .take(16)

      val spotlight =
        (homeSpotlight + effectiveTrending.take(3) + effectiveDramas.take(2))
          .distinctBy { it.id }
          .take(8)

      val recommended =
        (homeRails.firstOrNull()?.movies.orEmpty() + effectiveTrending.take(6) + popularMovies.take(6))
          .distinctBy { it.id }
          .take(16)

      val result =
        DesktopHomeCatalogResult(
          spotlight = spotlight,
          trending = effectiveTrending,
          recentlyAdded = recentlyAdded,
          popularMovies = popularMovies.ifEmpty { combined.filter { it.mediaType == "Movie" }.take(16) },
          popularSeries = popularSeries.ifEmpty { combined.filter { it.mediaType == "Series" }.take(16) },
          popularAnime = effectiveAnime,
          popularDramas = effectiveDramas,
          recommended = recommended,
          rails = homeRails,
          allMovies = combined,
          trendingKeywords =
            trendingKeywords.ifEmpty {
              listOf("Avatar", "Breaking Bad", "Queen of Tears", "Attack on Titan", "Dune")
            },
          isOfflineFallback = false,
        )
      cachedHome = System.currentTimeMillis() to result
      persistHomeSnapshot(result)
      result
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      buildFallbackResult()
    }
  }

  private fun buildFallbackResult(): DesktopHomeCatalogResult {
    val fallback = DesktopOfflineCatalogFallback.movies
    fallback.forEach { cache[it.id] = it }
    return DesktopHomeCatalogResult(
      spotlight = fallback.take(5),
      trending = fallback.mapIndexed { idx, m -> m.copy(rankPosition = idx + 1) },
      recentlyAdded = fallback,
      popularMovies = fallback.filter { it.mediaType.equals("Movie", true) },
      popularSeries = fallback.filter { it.mediaType.equals("Series", true) },
      popularAnime = fallback.filter { it.mediaType.equals("Anime", true) },
      popularDramas = fallback.filter { it.mediaType.equals("Drama", true) },
      recommended = fallback,
      rails = emptyList(),
      allMovies = fallback,
      trendingKeywords = listOf("Avatar", "Breaking Bad", "Queen of Tears", "Attack on Titan", "Oppenheimer"),
      isOfflineFallback = true,
    )
  }

  suspend fun loadMoviesCatalog(
    genre: String = "All",
    sortMode: CategorySortMode = CategorySortMode.FEATURED,
    page: Int = 1,
  ): List<Movie> {
    val mboxSort = if (sortMode == CategorySortMode.NEWEST) "LATEST" else "RECOMMEND"
    val mboxGenre = if (genre.equals("All", ignoreCase = true)) "ALL" else genre
    val hits =
      withTimeoutOrNull(6000L) {
        runCatching { movieBox.getMovies(page = page, perPage = 28, sort = mboxSort, genre = mboxGenre) }
          .getOrDefault(emptyList())
      }.orEmpty().filter(::isValidCinemaEntry)
    val effective =
      if (hits.isNotEmpty()) hits
      else cache.values.filter { it.mediaType.equals("Movie", true) }
        .ifEmpty { DesktopOfflineCatalogFallback.movies.filter { it.mediaType.equals("Movie", true) } }
    effective.forEach { cache[it.id] = it }
    return sortCatalogMovies(effective, sortMode)
  }

  suspend fun loadSeriesCatalog(
    genre: String = "All",
    sortMode: CategorySortMode = CategorySortMode.FEATURED,
    page: Int = 1,
  ): List<Movie> {
    val mboxSort = if (sortMode == CategorySortMode.NEWEST) "LATEST" else "RECOMMEND"
    val mboxGenre = if (genre.equals("All", ignoreCase = true)) "ALL" else genre
    val hits =
      withTimeoutOrNull(6000L) {
        runCatching { movieBox.getTvSeries(page = page, perPage = 28, sort = mboxSort, genre = mboxGenre) }
          .getOrDefault(emptyList())
      }.orEmpty().filter(::isValidCinemaEntry)
    val effective =
      if (hits.isNotEmpty()) hits
      else cache.values.filter { it.mediaType.equals("Series", true) }
        .ifEmpty { DesktopOfflineCatalogFallback.movies.filter { it.mediaType.equals("Series", true) } }
    effective.forEach { cache[it.id] = it }
    return sortCatalogMovies(effective, sortMode)
  }

  suspend fun loadDramasCatalog(
    regionFilter: String = "All",
    sortMode: CategorySortMode = CategorySortMode.FEATURED,
    page: Int = 1,
  ): List<Movie> {
    val mboxSort = if (sortMode == CategorySortMode.NEWEST) "LATEST" else "RECOMMEND"
    val country =
      when (regionFilter) {
        "K-Drama" -> "South Korea"
        "C-Drama" -> "China"
        "J-Drama" -> "Japan"
        "Western" -> "United States"
        else -> "ALL"
      }
    val hits =
      withTimeoutOrNull(6500L) {
        runCatching { movieBox.getDramas(page = page, perPage = 28, sort = mboxSort, country = country) }
          .getOrDefault(emptyList())
      }.orEmpty().filter(::isValidCinemaEntry)
    val effective =
      if (hits.isNotEmpty()) hits
      else (cache.values + DesktopOfflineCatalogFallback.movies).filter {
        DramaExperienceDetector.isEpisodicDrama(it)
      }
    effective.forEach { cache[it.id] = it }
    return sortCatalogMovies(effective, sortMode)
  }

  suspend fun loadAnimeCatalog(
    genreLabel: String = "All",
    sortMode: AnimeSortMode = AnimeSortMode.TOP_RANKED,
    page: Int = 1,
  ): List<Movie> = coroutineScope {
    val jikanDef = async {
      withTimeoutOrNull(5500L) {
        runCatching { jikanTvMaze.browseAnime(genreLabel, sortMode, page, limit = 24) }
          .getOrDefault(emptyList())
      }.orEmpty()
    }
    val mboxDef = async {
      withTimeoutOrNull(5500L) {
        runCatching {
          val mboxSort = if (sortMode == AnimeSortMode.NEWEST) "LATEST" else "RECOMMEND"
          val mboxGenre = if (genreLabel.equals("All", ignoreCase = true)) "Animation" else genreLabel
          movieBox.getFilteredFeed(
            tabId = 5,
            page = page,
            perPage = 20,
            sort = mboxSort,
            genre = mboxGenre,
            country = "Japan",
          )
        }.getOrDefault(emptyList())
      }.orEmpty()
    }

    val jikanHits = jikanDef.await().filter(::isGenuineAnime)
    val mboxHits =
      mboxDef.await().filter { m ->
        isGenuineAnime(m) &&
          (genreLabel.equals("All", ignoreCase = true) || m.genre.contains(genreLabel, ignoreCase = true))
      }

    val cachedStreams = cache.values.filter { it.subjectId.isNotBlank() && isGenuineAnime(it) }
    val mboxByTitle = (cachedStreams + mboxHits).associateBy { normalizeTitleKey(it.title) }
    val enriched =
      jikanHits.map { a ->
        val m = mboxByTitle[normalizeTitleKey(a.title)]
        if (m != null) a.copy(subjectId = m.subjectId, detailPath = m.detailPath) else a
      }
    val seen = enriched.mapTo(HashSet()) { normalizeTitleKey(it.title) }
    val merged =
      sortAnimeMovies(
        (enriched + mboxHits.filter { seen.add(normalizeTitleKey(it.title)) })
          .filter(::isGenuineAnime)
          .distinctBy { it.id },
        sortMode,
      )
    merged.forEach { cache[it.id] = it }
    merged.ifEmpty { DesktopOfflineCatalogFallback.movies.filter(::isGenuineAnime) }
  }

  suspend fun searchCatalogWithSuggestions(
    query: String,
  ): Triple<List<Movie>, List<MovieBoxSuggestion>, Boolean> = coroutineScope {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) {
      val current = (cache.values + DesktopOfflineCatalogFallback.movies).distinctBy { it.id }
      return@coroutineScope Triple(current, emptyList(), current.size <= DesktopOfflineCatalogFallback.movies.size)
    }

    try {
      val directDef =
        if (cleanQuery.length >= 10 && cleanQuery.all(Char::isDigit)) {
          async { runCatching { getMovieBoxRichDetail(cleanQuery)?.movie }.getOrNull() }
        } else null
      val mboxDef = async { runCatching { movieBox.search(cleanQuery, 1, 28) }.getOrDefault(emptyList()) }
      val suggestDef = async {
        runCatching { movieBox.searchSuggestFull(cleanQuery) }
          .getOrDefault(emptyList<MovieBoxSuggestion>() to emptyList())
      }
      val itunesDef = async { runCatching { itunes.searchMovies(cleanQuery, 20) }.getOrDefault(emptyList()) }
      val tvDef = async { runCatching { jikanTvMaze.searchTvShows(cleanQuery, 15) }.getOrDefault(emptyList()) }
      val animeDef = async { runCatching { jikanTvMaze.searchAnime(cleanQuery, 15) }.getOrDefault(emptyList()) }

      val direct = directDef?.await()
      val mboxHits = mboxDef.await()
      val (suggestions, suggestMovies) = suggestDef.await()
      val itunesHits = itunesDef.await()
      val tvHits = tvDef.await()
      val animeHits = animeDef.await()

      val raw =
        (listOfNotNull(direct) + mboxHits + suggestMovies + tvHits + animeHits + itunesHits)
          .filter(::isValidCinemaEntry)

      val deduped =
        raw.distinctBy { it.id }
          .groupBy { normalizeTitleKey(it.title) }
          .values
          .mapNotNull { group ->
            group.firstOrNull { it.subjectId.isNotBlank() && it.posterUrl.isNotBlank() }
              ?: group.firstOrNull { it.subjectId.isNotBlank() }
              ?: group.firstOrNull { it.posterUrl.isNotBlank() }
              ?: group.firstOrNull()
          }

      deduped.forEach { cache[it.id] = it }
      val cacheExtras =
        cache.values.filter { cached ->
          isValidCinemaEntry(cached) &&
            deduped.none { normalizeTitleKey(it.title) == normalizeTitleKey(cached.title) } &&
            matchesQueryTokens(cleanQuery, cached)
        }
      val ranked = rankSearchResults(cleanQuery, (deduped + cacheExtras).distinctBy { it.id })
      Triple(ranked, suggestions, deduped.isEmpty() && ranked.isEmpty())
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      val local = rankSearchResults(cleanQuery, searchLocalCache(cleanQuery))
      Triple(local, emptyList(), local.isEmpty())
    }
  }

  suspend fun getMovieDetail(movieId: String): Movie? {
    val cached = cache[movieId]
    if (movieId.startsWith("mbox_")) {
      val sid = movieId.removePrefix("mbox_")
      return getMovieBoxRichDetail(sid)?.movie ?: cached
    }
    val trackId = cached?.trackId ?: movieId.removePrefix("itunes_").toLongOrNull()
    if (cached != null && !cached.previewUrl.isNullOrBlank() && cached.runtimeMinutes > 0) {
      return cached
    }
    if (trackId != null && trackId > 0L) {
      runCatching { itunes.lookupMovie(trackId) }.getOrNull()?.let { fetched ->
        cache[fetched.id] = fetched
        return fetched
      }
    }
    return cached ?: DesktopOfflineCatalogFallback.movies.firstOrNull { it.id == movieId }
  }

  suspend fun getMovieBoxRichDetail(subjectId: String): MovieBoxRichDetail? {
    val hit = richDetailCache[subjectId]
    if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.getDetails(subjectId).also { detail ->
        cache[detail.movie.id] = detail.movie
        richDetailCache[subjectId] = System.currentTimeMillis() to detail
      }
    }.getOrNull()
  }

  suspend fun resolvePlayableSubject(movie: Movie): Movie? {
    if (movie.subjectId.isNotBlank()) return movie
    val cacheHit = cache[movie.id]
    if (cacheHit != null && cacheHit.subjectId.isNotBlank()) return cacheHit

    val targetNorm = normalizeTitleKey(movie.title)
    val candidates =
      runCatching { movieBox.search(movie.title, 1, 10) }.getOrDefault(emptyList()) +
        runCatching { movieBox.searchSuggestFull(movie.title).second }.getOrDefault(emptyList())

    val best =
      candidates.firstOrNull { normalizeTitleKey(it.title) == targetNorm && it.subjectId.isNotBlank() }
        ?: candidates.firstOrNull {
          normalizeTitleKey(it.title).contains(targetNorm) && it.subjectId.isNotBlank()
        }
        ?: candidates.firstOrNull { it.subjectId.isNotBlank() }
        ?: return null

    val updated = movie.copy(subjectId = best.subjectId, detailPath = best.detailPath ?: movie.detailPath)
    cache[movie.id] = updated
    cache[best.id] = best
    return updated
  }

  suspend fun getRecommendations(subjectId: String, genreHint: String? = null): List<Movie> {
    if (subjectId.isNotBlank()) {
      val hit = recCache[subjectId]
      if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
        return hit.second
      }
      val remote = runCatching { movieBox.getRecommendations(subjectId) }.getOrDefault(emptyList())
      remote.forEach { cache[it.id] = it }
      if (remote.isNotEmpty()) {
        recCache[subjectId] = System.currentTimeMillis() to remote
        return remote
      }
    }
    val pool = (cache.values + DesktopOfflineCatalogFallback.movies).distinctBy { it.id }
    val matching =
      if (!genreHint.isNullOrBlank()) {
        pool.filter { it.genre.contains(genreHint, ignoreCase = true) && it.subjectId != subjectId }
      } else emptyList()
    return (matching + pool.filter { it.subjectId != subjectId }).distinctBy { it.id }.take(12)
  }

  suspend fun getStreams(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
    detailPath: String? = null,
    forceRefresh: Boolean = false,
  ): List<MovieBoxStream> {
    val key = "${subjectId}_${season}_${episode}"
    val hit = streamCache[key]
    if (!forceRefresh && hit != null && (System.currentTimeMillis() - hit.first) < STREAM_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.getStreams(subjectId, season, episode, detailPath).also { resolved ->
        if (resolved.isNotEmpty()) streamCache[key] = System.currentTimeMillis() to resolved
      }
    }.getOrDefault(emptyList())
  }

  suspend fun getSubtitles(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
  ): List<MovieBoxSubtitle> {
    val key = "${subjectId}_${season}_${episode}"
    val hit = subtitleCache[key]
    if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.getSubtitles(subjectId, season = season, episode = episode).also { subs ->
        if (subs.isNotEmpty()) subtitleCache[key] = System.currentTimeMillis() to subs
      }
    }.getOrDefault(emptyList())
  }

  suspend fun fetchSubtitleCues(subtitleUrl: String): List<SubtitleCue> {
    if (subtitleUrl.isBlank()) return emptyList()
    val hit = subtitleCuesCache[subtitleUrl]
    if (hit != null && (System.currentTimeMillis() - hit.first) < DETAIL_CACHE_TTL_MS) {
      return hit.second
    }
    return runCatching {
      movieBox.fetchSubtitleCues(subtitleUrl).also { cues ->
        if (cues.isNotEmpty()) subtitleCuesCache[subtitleUrl] = System.currentTimeMillis() to cues
      }
    }.getOrDefault(emptyList())
  }

  suspend fun preloadEpisodeResources(
    subjectId: String,
    season: Int,
    episode: Int,
    detailPath: String? = null,
    preloadEnglishCues: Boolean = true,
  ) = coroutineScope {
    if (subjectId.isBlank() || season <= 0 || episode <= 0) return@coroutineScope
    val streamsDef = async { runCatching { getStreams(subjectId, season, episode, detailPath) }.getOrNull() }
    val subsDef = async { runCatching { getSubtitles(subjectId, season, episode) }.getOrDefault(emptyList()) }
    streamsDef.await()
    val subs = subsDef.await()
    if (preloadEnglishCues && subs.isNotEmpty()) {
      val eng = subs.firstOrNull { it.language.contains("English", ignoreCase = true) } ?: subs.first()
      runCatching { fetchSubtitleCues(eng.url) }
    }
  }

  fun getCachedMovie(movieId: String): Movie? =
    cache[movieId] ?: DesktopOfflineCatalogFallback.movies.firstOrNull { it.id == movieId }

  fun cacheMovie(movie: Movie) {
    cache[movie.id] = movie
  }

  private fun searchLocalCache(cleanQuery: String): List<Movie> =
    (cache.values + DesktopOfflineCatalogFallback.movies)
      .distinctBy { it.id }
      .filter { matchesQueryTokens(cleanQuery, it) }

  private fun matchesQueryTokens(cleanQuery: String, movie: Movie): Boolean {
    if (
      movie.title.contains(cleanQuery, ignoreCase = true) ||
        movie.director.contains(cleanQuery, ignoreCase = true) ||
        movie.genre.contains(cleanQuery, ignoreCase = true) ||
        movie.mediaType.contains(cleanQuery, ignoreCase = true)
    ) {
      return true
    }
    val tokens = cleanQuery.split(Regex("\\s+")).filter { it.length >= 2 }
    if (tokens.size <= 1) return false
    val haystack = "${movie.title} ${movie.director} ${movie.genre} ${movie.mediaType}"
    return tokens.all { haystack.contains(it, ignoreCase = true) }
  }

  private fun normalizeTitleKey(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9]"), "")

  private fun rankSearchResults(query: String, movies: List<Movie>): List<Movie> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return movies
    val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    return movies.sortedByDescending { movie ->
      val titleLower = movie.title.trim().lowercase()
      val baseScore =
        when {
          titleLower == q -> 10_000
          titleLower.startsWith(q) -> 7_500
          titleLower.contains(q) -> 5_000
          tokens.isNotEmpty() && tokens.all { titleLower.contains(it) } -> 3_500
          tokens.any { titleLower.contains(it) } -> 1_500
          else -> 200
        }
      val streamBonus = if (movie.subjectId.isNotBlank()) 500 else 0
      val posterBonus = if (movie.posterUrl.isNotBlank()) 50 else 0
      baseScore + streamBonus + posterBonus
    }
  }

  internal fun isValidCinemaEntry(movie: Movie): Boolean {
    val typeLower = movie.mediaType.trim().lowercase()
    if (typeLower in setOf("music", "song", "songs", "audio", "podcast", "pv", "cm", "karaoke")) {
      return false
    }
    val genreTokens =
      movie.genre.split(",", "/", "&", "|").map { it.trim().lowercase() }.filter { it.isNotBlank() }
    val bannedGenres = setOf("music", "song", "songs", "music video", "mv", "audio", "podcast", "karaoke", "single", "ost", "soundtrack", "radio")
    if (genreTokens.any { it in bannedGenres }) return false
    return true
  }

  internal fun isGenuineAnime(movie: Movie): Boolean {
    if (!isValidCinemaEntry(movie)) return false
    if (movie.id.startsWith("mal_") || movie.id.startsWith("anilist_")) return true
    if (movie.sourceProvider.equals("Decan Anime", ignoreCase = true)) return true
    val nonAnimeLive =
      setOf("united states", "usa", "us", "south africa", "nigeria", "united kingdom", "uk", "canada", "australia", "france", "spain", "india", "brazil", "mexico", "turkey")
    val countryLower = movie.country.orEmpty().trim().lowercase()
    if (countryLower in nonAnimeLive) return false
    val hasAnimeGenre = movie.genre.contains("Anime", ignoreCase = true)
    val hasAnimation = movie.genre.contains("Animation", ignoreCase = true)
    val isEastAsian =
      countryLower in setOf("japan", "china", "south korea") ||
        movie.language.orEmpty().contains("Japanese", ignoreCase = true)
    return (hasAnimeGenre && (countryLower.isBlank() || isEastAsian)) || (hasAnimation && isEastAsian)
  }

  private fun sortAnimeMovies(movies: List<Movie>, sortMode: AnimeSortMode): List<Movie> =
    when (sortMode) {
      AnimeSortMode.TOP_RANKED ->
        movies.sortedWith(
          compareBy<Movie> { it.rankPosition ?: Int.MAX_VALUE }
            .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
        )
      AnimeSortMode.POPULAR ->
        movies.sortedWith(
          compareByDescending<Movie> { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .thenBy { it.rankPosition ?: Int.MAX_VALUE }
        )
      AnimeSortMode.AIRING ->
        movies.sortedWith(
          compareByDescending<Movie> { (it.releaseYear.toIntOrNull() ?: 0) >= 2024 }
            .thenByDescending { it.releaseYear.toIntOrNull() ?: 0 }
        )
      AnimeSortMode.NEWEST ->
        movies.sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 }
    }

  private fun sortCatalogMovies(movies: List<Movie>, sortMode: CategorySortMode): List<Movie> =
    when (sortMode) {
      CategorySortMode.FEATURED -> movies
      CategorySortMode.TOP_RANKED ->
        movies.sortedWith(
          compareBy<Movie> { it.rankPosition ?: Int.MAX_VALUE }
            .thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
        )
      CategorySortMode.HIGHEST_RATED ->
        movies.sortedWith(
          compareByDescending<Movie> { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
            .thenBy { it.rankPosition ?: Int.MAX_VALUE }
        )
      CategorySortMode.NEWEST ->
        movies.sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 }
    }

  private fun persistHomeSnapshot(feed: DesktopHomeCatalogResult) {
    val store = storage ?: return
    runCatching {
      val root =
        JSONObject().apply {
          put("savedAt", System.currentTimeMillis())
          put("spotlight", encodeMovies(feed.spotlight.take(8)))
          put("trending", encodeMovies(feed.trending.take(24)))
          put("recentlyAdded", encodeMovies(feed.recentlyAdded.take(16)))
          put("popularMovies", encodeMovies(feed.popularMovies.take(20)))
          put("popularSeries", encodeMovies(feed.popularSeries.take(20)))
          put("popularAnime", encodeMovies(feed.popularAnime.take(24)))
          put("popularDramas", encodeMovies(feed.popularDramas.take(20)))
          put("recommended", encodeMovies(feed.recommended.take(16)))
          put("allMovies", encodeMovies(feed.allMovies.take(80)))
          put(
            "rails",
            JSONArray().apply {
              feed.rails.take(5).forEach { r ->
                put(
                  JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("subtitle", r.subtitle)
                    put("movies", encodeMovies(r.movies.take(16)))
                  }
                )
              }
            },
          )
          put("trendingKeywords", JSONArray().apply { feed.trendingKeywords.forEach { put(it) } })
        }
      store.writeSnapshotJson(root.toString())
    }
  }

  private fun encodeMovies(movies: List<Movie>): JSONArray =
    JSONArray().apply { movies.forEach { put(DesktopAppStorage.movieToJson(it)) } }

  private fun parseMovies(arr: JSONArray?): List<Movie> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        DesktopAppStorage.movieFromJson(o)?.let(::add)
      }
    }
  }

  private fun parseRails(arr: JSONArray?): List<MovieRail> {
    if (arr == null) return emptyList()
    return buildList {
      for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val movies = parseMovies(o.optJSONArray("movies")).filter(::isValidCinemaEntry)
        if (movies.isNotEmpty()) {
          add(MovieRail(o.optString("id"), o.optString("title"), o.optString("subtitle"), movies))
        }
      }
    }
  }

  companion object {
    private const val SNAPSHOT_MAX_AGE_MS = 12 * 3600_000L
    private const val HOME_CACHE_TTL_MS = 5 * 60_000L
    private const val DETAIL_CACHE_TTL_MS = 5 * 60_000L
    private const val STREAM_CACHE_TTL_MS = 3 * 60_000L
  }
}

private object DesktopOfflineCatalogFallback {
  val movies =
    listOf(
      Movie(
        id = "mbox_8906247916759695608",
        trackId = 8906247916759695608L,
        title = "Avatar",
        director = "James Cameron",
        genre = "Sci-Fi, Adventure",
        releaseYear = "2009",
        releaseDateLabel = "2009-12-18",
        synopsis = "A paraplegic Marine dispatched to the moon Pandora becomes torn between following orders and protecting his new home.",
        runtimeMinutes = 162,
        contentRating = "PG-13",
        subjectId = "8906247916759695608",
        mediaType = "Movie",
        imdbRating = "7.9",
        sourceProvider = "Decan",
      ),
      Movie(
        id = "mbox_6207982430134357800",
        trackId = 6207982430134357800L,
        title = "Breaking Bad",
        director = "Vince Gilligan",
        genre = "Crime, Thriller",
        releaseYear = "2008",
        releaseDateLabel = "2008-01-20",
        synopsis = "A chemistry teacher turns to manufacturing methamphetamine to secure his family's future.",
        runtimeMinutes = 47,
        contentRating = "TV-MA",
        subjectId = "6207982430134357800",
        mediaType = "Series",
        imdbRating = "9.5",
        sourceProvider = "Decan",
      ),
      Movie(
        id = "mbox_queen_of_tears",
        trackId = 991001L,
        title = "Queen of Tears",
        director = "South Korea • Korean",
        genre = "Drama, Romance",
        releaseYear = "2024",
        releaseDateLabel = "2024-03-09",
        synopsis = "A married couple from vastly different backgrounds rekindles love through an emotional crisis.",
        runtimeMinutes = 75,
        contentRating = "DRAMA",
        subjectId = "",
        mediaType = "Drama",
        imdbRating = "8.3",
        country = "South Korea",
        language = "Korean",
        sourceProvider = "Decan",
      ),
      Movie(
        id = "mal_16498",
        trackId = 16498L,
        title = "Attack on Titan",
        director = "Wit Studio • 25 eps",
        genre = "Action, Drama, Supernatural",
        releaseYear = "2013",
        releaseDateLabel = "2013-04-07",
        synopsis = "Eren Yeager vows to cleanse the world of giant humanoid Titans.",
        runtimeMinutes = 24,
        contentRating = "TV • ANIME",
        mediaType = "Anime",
        imdbRating = "9.1",
        country = "Japan",
        language = "Japanese",
        sourceProvider = "Decan Anime",
      ),
      Movie(
        id = "itunes_1695817025",
        trackId = 1695817025L,
        title = "Oppenheimer",
        director = "Christopher Nolan",
        genre = "Drama",
        releaseYear = "2023",
        releaseDateLabel = "2023-07-21",
        synopsis = "The story of J. Robert Oppenheimer and the development of the atomic bomb.",
        runtimeMinutes = 180,
        contentRating = "R",
        mediaType = "Movie",
        imdbRating = "8.4",
      ),
    )
}
