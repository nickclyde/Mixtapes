package tech.clyde.mixtapes.core.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One entry of `captions.playerCaptionsTracklistRenderer.captionTracks`. */
data class CaptionTrack(
    val baseUrl: String,
    val languageCode: String,
    /** "asr" for auto-generated tracks; null for uploader-provided ones. */
    val kind: String? = null,
    val name: String? = null,
)

data class VideoMetadata(
    val title: String,
    val description: String,
    val captionTracks: List<CaptionTrack> = emptyList(),
)

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

        return ExtractionResult.Success(
            VideoMetadata(title = title, description = description, captionTracks = captionTracks(root)),
        )
    }

    /**
     * Parses caption tracks out of a raw player-response JSON body (no HTML markers) —
     * the shape returned by the Innertube `youtubei/v1/player` endpoint. Returns an
     * empty list for missing captions or unparseable JSON; captions are always optional.
     */
    fun captionTracksFromPlayerResponse(json: String): List<CaptionTrack> {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return emptyList()
        }
        return captionTracks(root)
    }

    private fun captionTracks(root: JsonObject): List<CaptionTrack> = try {
        root["captions"]?.jsonObject
            ?.get("playerCaptionsTracklistRenderer")?.jsonObject
            ?.get("captionTracks")?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val track = element.jsonObject
                val baseUrl = track["baseUrl"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val languageCode = track["languageCode"]?.jsonPrimitive?.content ?: return@mapNotNull null
                CaptionTrack(
                    baseUrl = baseUrl,
                    languageCode = languageCode,
                    kind = track["kind"]?.jsonPrimitive?.content,
                    name = trackName(track),
                )
            }
    } catch (_: Exception) {
        emptyList()
    }

    /** Track names appear as {"simpleText": ...} on older pages and {"runs": [{"text": ...}]} on newer ones. */
    private fun trackName(track: JsonObject): String? {
        val name = track["name"]?.jsonObject ?: return null
        name["simpleText"]?.jsonPrimitive?.content?.let { return it }
        return name["runs"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
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
