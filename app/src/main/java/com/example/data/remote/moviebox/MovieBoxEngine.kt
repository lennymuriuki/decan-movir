package com.example.data.remote.moviebox

import com.example.data.model.Movie
import com.example.data.model.MovieRail
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MovieBoxEngine(
  private val client: OkHttpClient = createDefaultClient(),
) {
  private val deviceId = MovieBoxCrypto.generateDeviceId()
  private val tokenMutex = Mutex()
  private val streamIdCache = ConcurrentHashMap<String, String>()
  @Volatile private var bearerToken: String? = null

  suspend fun bootstrapToken(force: Boolean = false): String =
    tokenMutex.withLock {
      val current = bearerToken
      if (!current.isNullOrBlank() && !force) return@withLock current

      withContext(Dispatchers.IO) {
        val headers =
          MovieBoxCrypto.buildAuthHeaders("GET", BOOTSTRAP_URL, deviceId = deviceId)
        val reqBuilder = Request.Builder().url(BOOTSTRAP_URL).get()
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

        client.newCall(reqBuilder.build()).execute().use { resp ->
          val xUser = resp.header("x-user")
            ?: throw IllegalStateException("Service temporarily unavailable")
          val token = JSONObject(xUser).optString("token")
          if (token.isBlank()) {
            throw IllegalStateException("Service temporarily unavailable")
          }
          bearerToken = token
          token
        }
      }
    }

  private suspend fun apiRequest(
    method: String,
    pathOrUrl: String,
    bodyJson: JSONObject? = null,
    useToken: Boolean = true,
    retryAuth: Boolean = true,
  ): JSONObject {
    val fullUrl =
      if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl
      else "$NATIVE_API_BASE$pathOrUrl"

    val token = if (useToken) bootstrapToken(force = false) else null
    val bodyStr = bodyJson?.toString()
    val headers =
      MovieBoxCrypto.buildAuthHeaders(
        method = method,
        url = fullUrl,
        body = bodyStr,
        token = token,
        deviceId = deviceId,
      )

    val (statusCode, responseText) =
      withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder().url(fullUrl)
        headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
        if (method.equals("POST", ignoreCase = true)) {
          val mediaType = "application/json; charset=utf-8".toMediaType()
          reqBuilder.post((bodyStr ?: "{}").toRequestBody(mediaType))
        } else {
          reqBuilder.get()
        }
        client.newCall(reqBuilder.build()).execute().use { resp ->
          resp.code to (resp.body?.string().orEmpty())
        }
      }

    if ((statusCode == 401 || statusCode == 441) && retryAuth) {
      bootstrapToken(force = true)
      return apiRequest(method, pathOrUrl, bodyJson, useToken, retryAuth = false)
    }
    if (statusCode != 200) {
      throw IllegalStateException("Catalog service unavailable")
    }
    return JSONObject(responseText)
  }

  suspend fun getHome(): MovieBoxHomeFeed =
    withContext(Dispatchers.IO) {
      val url = "$API_BASE/home?host=moviebox.ph"
      val req =
        Request.Builder()
          .url(url)
          .get()
          .addHeader("User-Agent", MovieBoxCrypto.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .build()

      val root =
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) throw IllegalStateException("Catalog service unavailable")
          JSONObject(resp.body?.string().orEmpty())
        }

      val data = root.optJSONObject("data") ?: JSONObject()
      val spotlight = mutableListOf<Movie>()
      val rails = mutableListOf<MovieRail>()
      val allMovies = LinkedHashMap<String, Movie>()

      val operatingList = data.optJSONArray("operatingList") ?: JSONArray()
      for (i in 0 until operatingList.length()) {
        val op = operatingList.optJSONObject(i) ?: continue
        val type = op.optString("type")
        val title = op.optString("title").ifBlank { "Featured Selection" }

        if (type.equals("BANNER", ignoreCase = true)) {
          val bannerItems = op.optJSONObject("banner")?.optJSONArray("items") ?: JSONArray()
          for (j in 0 until bannerItems.length()) {
            val b = bannerItems.optJSONObject(j) ?: continue
            val sub = b.optJSONObject("subject") ?: b
            parseSubjectToMovie(sub)?.let { m ->
              spotlight.add(m)
              allMovies[m.id] = m
            }
          }
        } else {
          val subjects =
            op.optJSONArray("subjects")
              ?: op.optJSONObject("subjectList")?.optJSONArray("items")
              ?: op.optJSONArray("items")
              ?: JSONArray()
          val list = mutableListOf<Movie>()
          for (j in 0 until subjects.length()) {
            val item = subjects.optJSONObject(j) ?: continue
            val sub = item.optJSONObject("subject") ?: item
            parseSubjectToMovie(sub)?.let { m ->
              list.add(m)
              allMovies[m.id] = m
            }
          }
          if (list.isNotEmpty()) {
            rails.add(
              MovieRail(
                id = "mbox_op_$i",
                title = title,
                subtitle = "Curated collection",
                movies = list,
              )
            )
          }
        }
      }

      MovieBoxHomeFeed(
        spotlight = spotlight.distinctBy { it.id }.take(8),
        rails = rails,
        allMovies = allMovies.values.toList(),
      )
    }

  suspend fun getRankings(page: Int = 1, perPage: Int = 20): List<Movie> {
    val path =
      "/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=$page&perPage=$perPage"
    val data = apiRequest("GET", path).optJSONObject("data") ?: JSONObject()
    val raw =
      data.optJSONArray("items")
        ?: data.optJSONArray("subjects")
        ?: data.optJSONArray("list")
        ?: JSONArray()
    val ranked = mutableListOf<Movie>()
    for (i in 0 until raw.length()) {
      val item = raw.optJSONObject(i) ?: continue
      val sub = item.optJSONObject("subject") ?: item
      parseSubjectToMovie(sub)?.let { movie ->
        ranked.add(movie.copy(rankPosition = ((page - 1) * perPage) + ranked.size + 1))
      }
    }
    return ranked
  }

  suspend fun getFilteredFeed(
    tabId: Int,
    page: Int = 1,
    perPage: Int = 24,
    sort: String = "RECOMMEND",
    genre: String = "ALL",
    country: String = "ALL",
    year: String = "ALL",
    language: String = "ALL",
    overrideMediaType: String? = null,
  ): List<Movie> =
    withContext(Dispatchers.IO) {
      val url = "$API_BASE/subject/filter"
      val payload =
        JSONObject().apply {
          put("tabId", tabId)
          put(
            "filter",
            JSONObject().apply {
              put("sort", sort)
              put("genre", genre)
              put("country", country)
              put("year", year)
              put("language", language)
            },
          )
          put("page", page)
          put("perPage", perPage)
        }
      val req =
        Request.Builder()
          .url(url)
          .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
          .addHeader("User-Agent", MovieBoxCrypto.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .build()

      val root =
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) throw IllegalStateException("Catalog service unavailable")
          JSONObject(resp.body?.string().orEmpty())
        }

      val data = root.optJSONObject("data") ?: JSONObject()
      val raw = data.optJSONArray("items") ?: data.optJSONArray("subjects") ?: JSONArray()
      val out = mutableListOf<Movie>()
      for (i in 0 until raw.length()) {
        val obj = raw.optJSONObject(i) ?: continue
        parseSubjectToMovie(
          sub = obj,
          defaultIsSeries = (tabId == 5),
          overrideMediaType = overrideMediaType,
        )?.let { out.add(it) }
      }
      out
    }

  suspend fun getMovies(
    page: Int = 1,
    perPage: Int = 24,
    sort: String = "RECOMMEND",
    genre: String = "ALL",
  ): List<Movie> = getFilteredFeed(tabId = 2, page = page, perPage = perPage, sort = sort, genre = genre)

  suspend fun getTvSeries(
    page: Int = 1,
    perPage: Int = 24,
    sort: String = "RECOMMEND",
    genre: String = "ALL",
  ): List<Movie> = getFilteredFeed(tabId = 5, page = page, perPage = perPage, sort = sort, genre = genre)

  suspend fun getAnime(page: Int = 1, perPage: Int = 24): List<Movie> {
    val animeSeries =
      runCatching {
        getFilteredFeed(
          tabId = 5,
          page = page,
          perPage = perPage,
          sort = "RECOMMEND",
          genre = "Animation",
          overrideMediaType = "Anime",
        )
      }.getOrDefault(emptyList())
    val animeMovies =
      runCatching {
        getFilteredFeed(
          tabId = 2,
          page = page,
          perPage = perPage / 2,
          sort = "RECOMMEND",
          genre = "Animation",
          overrideMediaType = "Anime",
        )
      }.getOrDefault(emptyList())
    return (animeSeries + animeMovies).distinctBy { it.id }
  }

  suspend fun getRecommendations(subjectId: String, page: Int = 1, perPage: Int = 12): List<Movie> {
    val path =
      "/wefeed-mobile-bff/subject-api/detail-rec?subjectId=$subjectId&page=$page&perPage=$perPage"
    val data = apiRequest("GET", path).optJSONObject("data") ?: JSONObject()
    val raw =
      data.optJSONArray("items")
        ?: data.optJSONArray("subjects")
        ?: data.optJSONArray("list")
        ?: JSONArray()
    val list = mutableListOf<Movie>()
    for (i in 0 until raw.length()) {
      val item = raw.optJSONObject(i) ?: continue
      val sub = item.optJSONObject("subject") ?: item
      parseSubjectToMovie(sub)?.let { list.add(it) }
    }
    return list
  }

  suspend fun getTrendingKeywords(): List<String> =
    withContext(Dispatchers.IO) {
      val url = "$API_BASE/subject/hot-search"
      val req =
        Request.Builder()
          .url(url)
          .get()
          .addHeader("User-Agent", MovieBoxCrypto.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        val root = JSONObject(resp.body?.string().orEmpty())
        val data = root.optJSONObject("data") ?: JSONObject()
        val arr =
          data.optJSONArray("everyoneSearch")
            ?: data.optJSONArray("items")
            ?: data.optJSONArray("hot")
            ?: JSONArray()
        buildList {
          for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            val word =
              if (obj != null) obj.optString("title").ifBlank { obj.optString("word") }
              else arr.optString(i)
            if (word.isNotBlank()) add(word)
          }
        }.distinct().take(10)
      }
    }

  suspend fun search(query: String, page: Int = 1, perPage: Int = 20): List<Movie> {
    val payload =
      JSONObject().apply {
        put("keyword", query)
        put("page", page)
        put("perPage", perPage)
      }
    val data =
      apiRequest("POST", "/wefeed-mobile-bff/subject-api/search", bodyJson = payload)
        .optJSONObject("data") ?: JSONObject()
    val raw = data.optJSONArray("items") ?: data.optJSONArray("list") ?: JSONArray()
    val results = mutableListOf<Movie>()
    for (i in 0 until raw.length()) {
      val item = raw.optJSONObject(i) ?: continue
      val sub = item.optJSONObject("subject") ?: item
      parseSubjectToMovie(sub)?.let { results.add(it) }
    }
    return results
  }

  suspend fun searchSuggest(query: String): List<MovieBoxSuggestion> =
    withContext(Dispatchers.IO) {
      val url = "$API_BASE/subject/search-suggest"
      val payload =
        JSONObject().apply {
          put("keyword", query)
          put("perPage", 10)
        }
      val req =
        Request.Builder()
          .url(url)
          .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
          .addHeader("User-Agent", MovieBoxCrypto.WEB_USER_AGENT)
          .addHeader("Referer", "https://moviebox.ph/")
          .build()
      val root =
        client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@withContext emptyList()
          JSONObject(resp.body?.string().orEmpty())
        }
      val raw = root.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
      val suggestions = mutableListOf<MovieBoxSuggestion>()
      for (i in 0 until raw.length()) {
        val item = raw.optJSONObject(i) ?: continue
        val sub = item.optJSONObject("subject") ?: JSONObject()
        val title =
          sub.optString("title").ifBlank {
            item.optString("word").ifBlank { item.optString("title") }
          }
        val sid =
          sub.optString("subjectId").ifBlank { item.optString("subjectId") }
        val slug =
          sub.optString("detailPath").ifBlank { item.optString("detailPath") }
        if (title.isNotBlank() && sid.isNotBlank()) {
          suggestions.add(MovieBoxSuggestion(title = title, slug = slug, subjectId = sid))
        }
      }
      suggestions
    }

  suspend fun getDetails(subjectId: String): MovieBoxRichDetail {
    val data =
      apiRequest("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId")
        .optJSONObject("data") ?: JSONObject()

    val detailUrl = data.optString("detailUrl")
    val detailPath =
      if (detailUrl.contains("/")) detailUrl.substringAfterLast("/") else detailUrl

    val dubsArr = data.optJSONArray("dubs") ?: JSONArray()
    val dubs = mutableListOf<MovieBoxDub>()
    for (i in 0 until dubsArr.length()) {
      val d = dubsArr.optJSONObject(i) ?: continue
      val sid = d.optString("subjectId")
      if (sid.isNotBlank()) {
        dubs.add(
          MovieBoxDub(
            subjectId = sid,
            langName = d.optString("lanName").ifBlank { d.optString("language", "Audio") },
            language = d.optString("language"),
          )
        )
      }
    }

    val movie =
      parseSubjectToMovie(data, overrideDetailPath = detailPath)
        ?: throw IllegalStateException("Title unavailable")

    val seasons =
      if (movie.mediaType.equals("Series", ignoreCase = true) ||
          movie.mediaType.equals("Anime", ignoreCase = true)
      ) {
        runCatching { getEpisodes(subjectId) }.getOrDefault(emptyList())
      } else {
        emptyList()
      }

    return MovieBoxRichDetail(
      movie = movie,
      subjectId = subjectId,
      detailPath = detailPath,
      dubs = dubs,
      seasons = seasons,
    )
  }

  suspend fun getEpisodes(subjectId: String): List<MovieBoxSeason> {
    val res =
      apiRequest("GET", "/wefeed-mobile-bff/subject-api/season-info?subjectId=$subjectId")
    val seasonsArr = res.optJSONObject("data")?.optJSONArray("seasons") ?: JSONArray()
    val list = mutableListOf<MovieBoxSeason>()
    for (i in 0 until seasonsArr.length()) {
      val s = seasonsArr.optJSONObject(i) ?: continue
      val resArr = s.optJSONArray("resolutions") ?: JSONArray()
      val resolutions = mutableListOf<Int>()
      for (r in 0 until resArr.length()) {
        val v = resArr.optInt(r, 0)
        if (v > 0) resolutions.add(v)
      }
      list.add(
        MovieBoxSeason(
          season = s.optInt("se", 1),
          episodeCount = s.optInt("maxEp", 1).coerceAtLeast(1),
          resolutions = resolutions,
        )
      )
    }
    return list
  }

  suspend fun getStreams(
    subjectId: String,
    season: Int = 0,
    episode: Int = 0,
    detailPath: String? = null,
  ): List<MovieBoxStream> = coroutineScope {
    val detailPathDeferred =
      if (!detailPath.isNullOrBlank()) null
      else async { runCatching { getDetails(subjectId).detailPath }.getOrDefault("") }

    val tokenDeferred = async { bootstrapToken() }

    val nativeDeferred = async {
      runCatching {
        val nativeUrl =
          "/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode"
        val nativeData = apiRequest("GET", nativeUrl).optJSONObject("data") ?: JSONObject()
        val rawNative = nativeData.optJSONArray("streams") ?: JSONArray()
        val nativeList = mutableListOf<MovieBoxStream>()
        for (i in 0 until rawNative.length()) {
          val st = rawNative.optJSONObject(i) ?: continue
          val stId = st.optString("id")
          if (stId.isNotBlank()) {
            streamIdCache.putIfAbsent("${subjectId}_${season}_${episode}", stId)
          }
          val rawUrl = st.optString("url")
          if (rawUrl.isBlank() || rawUrl.contains("b164fbfb4347792950bdfbfb563d39d9")) continue
          val cookie = st.optString("signCookie").takeIf { it.isNotBlank() }
          val policyUrl = MovieBoxCrypto.extractPolicyResource(cookie)
          val finalUrl = policyUrl ?: rawUrl
          val resStr = st.optString("resolutions", "")
          val qualityVal = parseQualityValue(resStr)
          val sizeBytes = st.optLong("size", 0L)
          val sizeMb =
            if (sizeBytes > 0L) ((sizeBytes / (1024.0 * 1024.0)) * 10.0).roundToInt() / 10.0
            else 0.0
          val isDash = policyUrl != null || finalUrl.contains(".mpd")
          nativeList.add(
            MovieBoxStream(
              source = "Fast Mirror Stream",
              format = if (isDash) "DASH" else st.optString("format", "MP4").ifBlank { "MP4" },
              quality = if (qualityVal > 0) "${qualityVal}p" else resStr.ifBlank { "Default" },
              qualityValue = qualityVal,
              resolutionTag = resStr,
              url = finalUrl,
              sizeMb = sizeMb,
              codec = st.optString("codecName").takeIf { it.isNotBlank() },
              durationSec = st.optLong("duration", 0L).takeIf { it > 0L },
              cookie = cookie,
              headers =
                mapOf(
                  "User-Agent" to MovieBoxCrypto.MODERN_USER_AGENT,
                  "Referer" to "https://api3.aoneroom.com/",
                ),
            )
          )
        }
        nativeList
      }.getOrElse { err ->
        if (err is CancellationException) throw err
        emptyList()
      }
    }

    val webDeferred = async {
      val resolvedDetailPath = detailPath ?: detailPathDeferred?.await().orEmpty()
      val token = runCatching { tokenDeferred.await() }.getOrDefault("")
      val encodedDetailPath = URLEncoder.encode(resolvedDetailPath, "UTF-8")
      val webList = mutableListOf<MovieBoxStream>()

      withContext(Dispatchers.IO) {
        for (domain in WEB_DOMAINS) {
          val playUrl =
            "$domain/wefeed-h5api-bff/subject/play?" +
              "subjectId=$subjectId&se=$season&ep=$episode&detailPath=$encodedDetailPath&" +
              "streamSignType=1&supportCodecs%5Bhevc%5D=1&supportCodecs%5Bh264%5D=1"
          val referer =
            "$domain/movies/$resolvedDetailPath?id=$subjectId&type=/movie/detail&detailSe=&detailEp=&lang=en"
          val req =
            Request.Builder()
              .url(playUrl)
              .get()
              .addHeader("User-Agent", MovieBoxCrypto.WEB_USER_AGENT)
              .addHeader("Referer", referer)
              .addHeader("Accept", "application/json")
              .addHeader("x-client-info", """{"timezone":"Asia/Calcutta"}""")
              .addHeader("x-request-lang", "en")
              .addHeader("x-vip-restrict", "0")
              .addHeader("x-no-high-risk-restrict", "0")
              .addHeader("x-source", "moviebox")
              .apply { if (token.isNotBlank()) addHeader("Authorization", "Bearer $token") }
              .build()

          val foundOnDomain =
            runCatching {
                client.newCall(req).execute().use { resp ->
                  if (!resp.isSuccessful) return@use false
                  val data =
                    JSONObject(resp.body?.string().orEmpty()).optJSONObject("data")
                      ?: return@use false
                  val rawStreams = data.optJSONArray("streams") ?: JSONArray()
                  val dashItems = data.optJSONArray("dash") ?: JSONArray()
                  if (rawStreams.length() == 0 && dashItems.length() == 0) return@use false

                  for (i in 0 until rawStreams.length()) {
                    val st = rawStreams.optJSONObject(i) ?: continue
                    val url = st.optString("url")
                    if (url.isBlank()) continue
                    val resStr = st.optString("resolutions", "unknown")
                    val qualityVal = parseQualityValue(resStr)
                    val sizeBytes = st.optLong("size", 0L)
                    val sizeMb =
                      if (sizeBytes > 0L) ((sizeBytes / (1024.0 * 1024.0)) * 10.0).roundToInt() / 10.0
                      else 0.0
                    webList.add(
                      MovieBoxStream(
                        source = "Direct HD Stream",
                        format = st.optString("format", "MP4").ifBlank { "MP4" },
                        quality = if (qualityVal > 0) "${qualityVal}p" else resStr,
                        qualityValue = qualityVal,
                        resolutionTag = resStr,
                        url = url,
                        sizeMb = sizeMb,
                        codec = st.optString("codecName").takeIf { it.isNotBlank() },
                        durationSec = st.optLong("duration", 0L).takeIf { it > 0L },
                        headers =
                          mapOf(
                            "User-Agent" to "Mozilla/5.0",
                            "Referer" to "$domain/",
                            "Origin" to domain,
                          ),
                      )
                    )
                  }

                  for (i in 0 until dashItems.length()) {
                    val d = dashItems.optJSONObject(i) ?: continue
                    val rawUrl = d.optString("url")
                    val cookie = d.optString("signCookie").takeIf { it.isNotBlank() }
                    val extracted = MovieBoxCrypto.extractPolicyResource(cookie)
                    val finalUrl = extracted ?: rawUrl
                    if (finalUrl.isBlank()) continue
                    val resTag = d.optString("resolutions", "adaptive")
                    val is1080 = resTag.contains("1080")
                    webList.add(
                      MovieBoxStream(
                        source = "Adaptive HD Stream",
                        format = "DASH",
                        quality = if (is1080) "1080p (Adaptive)" else "Adaptive",
                        qualityValue = if (is1080) 1080 else 999,
                        resolutionTag = resTag,
                        url = finalUrl,
                        sizeMb = 0.0,
                        codec = "hevc/h264",
                        cookie = cookie,
                        headers =
                          mapOf(
                            "User-Agent" to "Mozilla/5.0",
                            "Referer" to "$domain/",
                            "Origin" to domain,
                          ),
                      )
                    )
                  }
                  true
                }
              }
              .getOrDefault(false)

          if (foundOnDomain) break
        }
      }
      webList
    }

    val seenUrls = HashSet<String>()
    (webDeferred.await() + nativeDeferred.await())
      .filter { seenUrls.add(it.url) }
      .sortedByDescending { it.qualityValue }
  }

  suspend fun getSubtitles(
    subjectId: String,
    streamId: String? = null,
    season: Int = 0,
    episode: Int = 0,
  ): List<MovieBoxSubtitle> = coroutineScope {
    val subs = mutableListOf<MovieBoxSubtitle>()
    val seenUrls = HashSet<String>()
    var resolvedStreamId =
      streamId?.takeIf { it.isNotBlank() } ?: streamIdCache["${subjectId}_${season}_${episode}"]

    if (resolvedStreamId.isNullOrBlank()) {
      runCatching {
        val nativeUrl =
          "/wefeed-mobile-bff/subject-api/play-info?subjectId=$subjectId&se=$season&ep=$episode"
        val raw =
          apiRequest("GET", nativeUrl).optJSONObject("data")?.optJSONArray("streams")
        if (raw != null && raw.length() > 0) {
          resolvedStreamId = raw.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
          if (!resolvedStreamId.isNullOrBlank()) {
            streamIdCache["${subjectId}_${season}_${episode}"] = resolvedStreamId!!
          }
        }
      }
    }

    fun addSub(item: JSONObject) {
      val url = item.optString("url")
      if (url.isNotBlank() && seenUrls.add(url)) {
        subs.add(
          MovieBoxSubtitle(
            id = item.optString("id"),
            language =
              item.optString("lanName").ifBlank {
                item.optString("language").ifBlank { "English" }
              },
            code = item.optString("lan"),
            url = url,
            sizeBytes = item.optLong("size", 0L).takeIf { it > 0L },
          )
        )
      }
    }

    val streamCaptionsDef = async {
      if (!resolvedStreamId.isNullOrBlank()) {
        runCatching {
          val url =
            "/wefeed-mobile-bff/subject-api/get-stream-captions?subjectId=$subjectId&streamId=$resolvedStreamId"
          extractCaptionsArray(apiRequest("GET", url))
        }.getOrDefault(emptyList())
      } else {
        emptyList()
      }
    }

    val extCaptionsDef = async {
      runCatching {
        val resId = resolvedStreamId ?: "0"
        val url =
          "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=$resId&episode=$episode"
        extractCaptionsArray(apiRequest("GET", url))
      }.getOrDefault(emptyList())
    }

    (streamCaptionsDef.await() + extCaptionsDef.await()).forEach { addSub(it) }
    subs
  }

  suspend fun fetchSubtitleCues(subtitleUrl: String): List<SubtitleCue> =
    withContext(Dispatchers.IO) {
      val req =
        Request.Builder()
          .url(subtitleUrl)
          .get()
          .addHeader("User-Agent", MovieBoxCrypto.WEB_USER_AGENT)
          .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@withContext emptyList()
        SrtParser.parse(resp.body?.string().orEmpty())
      }
    }

  private fun extractCaptionsArray(root: JSONObject): List<JSONObject> {
    val dataObj = root.optJSONObject("data")
    val arr =
      if (dataObj != null) {
        dataObj.optJSONArray("extCaptions") ?: dataObj.optJSONArray("captions") ?: JSONArray()
      } else {
        root.optJSONArray("data") ?: JSONArray()
      }
    val list = mutableListOf<JSONObject>()
    for (i in 0 until arr.length()) {
      arr.optJSONObject(i)?.let { list.add(it) }
    }
    return list
  }

  private fun parseQualityValue(resStr: String): Int {
    for (q in listOf(2160, 1440, 1080, 720, 480, 360, 240)) {
      if (resStr.contains(q.toString())) return q
    }
    return 0
  }

  private fun parseSubjectToMovie(
    sub: JSONObject,
    defaultIsSeries: Boolean = false,
    overrideDetailPath: String? = null,
    overrideMediaType: String? = null,
  ): Movie? {
    val subjectId = sub.optString("subjectId").trim()
    val title = sub.optString("title").trim()
    if (subjectId.isBlank() || title.isBlank()) return null

    val subjectType = sub.optInt("subjectType", if (defaultIsSeries) 2 else 1)
    val rawGenre = sub.optString("genre").trim()
    val country = sub.optString("countryName").takeIf { it.isNotBlank() }
    val inferredAnime =
      rawGenre.contains("Anime", ignoreCase = true) ||
        (rawGenre.contains("Animation", ignoreCase = true) &&
          (country.equals("Japan", ignoreCase = true) ||
            sub.optString("language").contains("Japanese", ignoreCase = true)))
    val mediaType =
      overrideMediaType
        ?: when {
          inferredAnime -> "Anime"
          subjectType == 1 -> "Movie"
          else -> "Series"
        }
    val coverObj = sub.optJSONObject("cover")
    val coverUrl = coverObj?.optString("url").orEmpty().trim()
    val releaseDate = sub.optString("releaseDate")
    val year =
      releaseDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "2025"
    val primaryGenre =
      rawGenre.split(",", "/").firstOrNull()?.trim()?.ifBlank { "Cinema" } ?: "Cinema"
    val imdb = sub.optString("imdbRatingValue").takeIf { it.isNotBlank() && it != "0" }
    val lang = sub.optString("language").takeIf { it.isNotBlank() }
    val durationRaw = sub.optInt("duration", 0)
    val runtimeMin =
      when {
        durationRaw > 600 -> (durationRaw / 60).coerceIn(0, 1000)
        durationRaw > 0 -> durationRaw.coerceIn(0, 1000)
        else -> 0
      }
    val detailPath =
      overrideDetailPath?.takeIf { it.isNotBlank() } ?: sub.optString("detailPath").takeIf {
        it.isNotBlank()
      }
    val description =
      sub.optString("description").trim().ifBlank {
        "$mediaType streaming in HD on Decan Movie."
      }
    val directorLabel =
      listOfNotNull(country, lang).joinToString(" • ").ifBlank { "Decan Cinema • $mediaType" }

    return Movie(
      id = Movie.sanitizeId("mbox_$subjectId"),
      trackId = subjectId.toLongOrNull() ?: 0L,
      title = title.take(200),
      director = directorLabel.take(120),
      genre = primaryGenre.take(60),
      releaseYear = year,
      releaseDateLabel = releaseDate.take(10).ifBlank { year },
      posterUrl = coverUrl.take(500),
      backdropUrl = coverUrl.take(500),
      previewUrl = null,
      synopsis = description.take(2000),
      runtimeMinutes = runtimeMin,
      contentRating = mediaType.uppercase(),
      subjectId = subjectId,
      mediaType = mediaType,
      imdbRating = imdb,
      country = country,
      language = lang,
      detailPath = detailPath,
      sourceProvider = "Decan",
    )
  }

  companion object {
    private const val API_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private const val NATIVE_API_BASE = "https://api3.aoneroom.com"
    private const val BOOTSTRAP_HOST = "https://apig.inmoviebox.com"
    private const val BOOTSTRAP_URL =
      "$BOOTSTRAP_HOST/wefeed-mobile-bff/tab/ranking-list?tabId=0&categoryType=4516404531735022304&page=1&perPage=1"

    private val WEB_DOMAINS =
      listOf(
        "https://123moviesfree.club",
        "https://movie-box.co",
        "https://movieboxonline.net",
        "https://sflix.film",
        "https://h5-api.aoneroom.com",
        "https://netnaija.film",
        "https://movieboxhd.net",
      )

    fun createDefaultClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .build()
  }
}
