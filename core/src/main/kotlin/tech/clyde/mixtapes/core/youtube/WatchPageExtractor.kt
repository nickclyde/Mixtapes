package tech.clyde.mixtapes.core.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VideoMetadata(val title: String, val description: String)

enum class ExtractionError { MARKER_NOT_FOUND, MALFORMED_JSON, MISSING_FIELDS }

sealed interface ExtractionResult {
    data class Success(val metadata: VideoMetadata) : ExtractionResult
    data class Failure(val error: ExtractionError) : ExtractionResult
}

object WatchPageExtractor {
    private val MARKERS = listOf(
        "var ytInitialPlayerResponse = ",
        "ytInitialPlayerResponse = ",
        "ytInitialPlayerResponse=",
    )

    /**
     * Pulls `videoDetails.title` and `videoDetails.shortDescription` out of a
     * YouTube watch-page HTML string by locating the `ytInitialPlayerResponse`
     * assignment and balanced-brace-scanning its JSON object (descriptions can
     * contain `};`, so no regex-to-`};`).
     *
     * If the page layout ever changes and this starts failing in production,
     * the fallback path is the Innertube `youtubei/v1/player` endpoint —
     * deliberately not used in v1 because it needs a hardcoded, Google-rotated
     * API key.
     */
    fun extract(html: String): ExtractionResult {
        val markerEnd = MARKERS.firstNotNullOfOrNull { marker ->
            html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length)
        } ?: return ExtractionResult.Failure(ExtractionError.MARKER_NOT_FOUND)

        val start = html.indexOf('{', markerEnd)
        if (start < 0) return ExtractionResult.Failure(ExtractionError.MALFORMED_JSON)
        val jsonText = extractBalancedObject(html, start)
            ?: return ExtractionResult.Failure(ExtractionError.MALFORMED_JSON)

        val root = try {
            Json.parseToJsonElement(jsonText).jsonObject
        } catch (_: Exception) {
            return ExtractionResult.Failure(ExtractionError.MALFORMED_JSON)
        }

        val videoDetails = root["videoDetails"]?.jsonObject
            ?: return ExtractionResult.Failure(ExtractionError.MISSING_FIELDS)
        val title = (videoDetails["title"] ?: return ExtractionResult.Failure(ExtractionError.MISSING_FIELDS))
            .jsonPrimitive.content
        val description = videoDetails["shortDescription"]?.jsonPrimitive?.content ?: ""

        return ExtractionResult.Success(VideoMetadata(title = title, description = description))
    }

    /** Scans from the `{` at [start] to its matching `}`, string- and escape-aware. */
    private fun extractBalancedObject(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++ // skip the escaped character
                inString && c == '"' -> inString = false
                !inString && c == '"' -> inString = true
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }
}
