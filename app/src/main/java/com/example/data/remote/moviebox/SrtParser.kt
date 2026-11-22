package com.example.data.remote.moviebox

import androidx.compose.runtime.Immutable

@Immutable
data class SubtitleCue(
  val startMs: Long,
  val endMs: Long,
  val text: String,
)

object SrtParser {
  private val TIMESTAMP_LINE =
    Regex("""(\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}\s*-->\s*(\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}""")
  private val HTML_TAG = Regex("""<[^>]+>""")

  fun parse(rawContent: String): List<SubtitleCue> {
    if (rawContent.isBlank()) return emptyList()
    val normalized = rawContent.replace("\r\n", "\n").replace("\r", "\n").trim()
    val blocks = normalized.split(Regex("\n{2,}"))
    val cues = ArrayList<SubtitleCue>(blocks.size)

    for (block in blocks) {
      val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.isEmpty()) continue
      val timeLineIdx = lines.indexOfFirst { TIMESTAMP_LINE.containsMatchIn(it) }
      if (timeLineIdx < 0) continue

      val timeParts = lines[timeLineIdx].split("-->")
      if (timeParts.size < 2) continue
      val startMs = parseTimestampMs(timeParts[0].trim())
      val endMs = parseTimestampMs(timeParts[1].trim().substringBefore(" "))
      if (startMs < 0 || endMs <= startMs) continue

      val captionText =
        lines
          .drop(timeLineIdx + 1)
          .joinToString("\n") { line -> HTML_TAG.replace(line, "").trim() }
          .trim()
      if (captionText.isNotEmpty()) {
        cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = captionText))
      }
    }
    return cues.sortedBy { it.startMs }
  }

  fun findActiveCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
    if (cues.isEmpty()) return null
    var low = 0
    var high = cues.lastIndex
    while (low <= high) {
      val mid = (low + high) ushr 1
      val cue = cues[mid]
      when {
        positionMs < cue.startMs -> high = mid - 1
        positionMs > cue.endMs -> low = mid + 1
        else -> return cue
      }
    }
    return null
  }

  private fun parseTimestampMs(token: String): Long {
    val clean = token.replace(',', '.').trim()
    val secAndMillis = clean.split('.')
    val hms = secAndMillis[0].split(':')
    val millis =
      secAndMillis.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
    return when (hms.size) {
      3 -> {
        val h = hms[0].toLongOrNull() ?: return -1L
        val m = hms[1].toLongOrNull() ?: return -1L
        val s = hms[2].toLongOrNull() ?: return -1L
        ((h * 3600) + (m * 60) + s) * 1000L + millis
      }
      2 -> {
        val m = hms[0].toLongOrNull() ?: return -1L
        val s = hms[1].toLongOrNull() ?: return -1L
        ((m * 60) + s) * 1000L + millis
      }
      else -> -1L
    }
  }
}
