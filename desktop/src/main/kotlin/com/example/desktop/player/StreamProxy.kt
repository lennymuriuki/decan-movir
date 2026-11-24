package com.example.desktop.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Local loop-back HTTP proxy. Some stream CDNs require custom headers / cookies that VLC cannot
 * send, so the player is pointed at http://127.0.0.1:<port>/s/<id> and this proxy forwards the
 * request (including Range requests for seeking) with the required headers attached.
 */
object StreamProxy {
  private class Target(val url: String, val headers: Map<String, String>)

  private val targets = ConcurrentHashMap<String, Target>()
  private val client =
    OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

  private val server: HttpServer by lazy {
    HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
      executor = Executors.newCachedThreadPool { r -> Thread(r, "stream-proxy").apply { isDaemon = true } }
      createContext("/s/") { ex -> runCatching { handle(ex) }.onFailure { runCatching { ex.close() } } }
      start()
    }
  }

  fun register(url: String, headers: Map<String, String>, cookie: String?): String {
    val all = LinkedHashMap(headers)
    if (!cookie.isNullOrBlank()) all["Cookie"] = cookie
    val id = (url + all.toString()).hashCode().toUInt().toString(16)
    targets[id] = Target(url, all)
    return "http://127.0.0.1:${server.address.port}/s/$id"
  }

  private fun handle(ex: HttpExchange) {
    val id = ex.requestURI.path.removePrefix("/s/")
    val target = targets[id]
    if (target == null) {
      ex.sendResponseHeaders(404, -1)
      ex.close()
      return
    }
    val rb = Request.Builder().url(target.url)
    target.headers.forEach { (k, v) -> rb.header(k, v) }
    ex.requestHeaders.getFirst("Range")?.let { rb.header("Range", it) }
    if (ex.requestMethod.equals("HEAD", true)) rb.head() else rb.get()

    client.newCall(rb.build()).execute().use { resp ->
      listOf("Content-Type", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag").forEach { h ->
        resp.header(h)?.let { ex.responseHeaders.add(h, it) }
      }
      val body = resp.body
      val len = body?.contentLength() ?: -1L
      if (ex.requestMethod.equals("HEAD", true) || body == null) {
        if (len >= 0) ex.responseHeaders.add("Content-Length", len.toString())
        ex.sendResponseHeaders(resp.code, -1)
      } else {
        ex.sendResponseHeaders(resp.code, if (len >= 0) len else 0L)
        ex.responseBody.use { out -> body.byteStream().copyTo(out, 64 * 1024) }
      }
    }
    ex.close()
  }
}
