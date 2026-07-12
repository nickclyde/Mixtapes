package tech.clyde.mixtapes.core.transcript

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Turns a YouTube timedtext `fmt=json3` body into plain text for LLM consumption.
 *
 * json3 shape: {"events":[{"tStartMs":1234,"dDurationMs":...,"segs":[{"utf8":"..."}]}]}.
 * Some events carry window/styling data and no `segs`; ASR tracks use `"\n"`-only
 * segs as line separators. Neither contributes text.
 */
object Json3TranscriptParser {

    /** A silence gap longer than this becomes a paragraph break — light structure for the LLM. */
    private const val PARAGRAPH_GAP_MS = 5_000L

    /** Returns null when the body is not json3 or contains no text events. */
    fun parse(json3: String): String? {
        val events = try {
            Json.parseToJsonElement(json3).jsonObject["events"]?.jsonArray ?: return null
        } catch (_: Exception) {
            return null
        }

        val out = StringBuilder()
        var lastEndMs: Long? = null
        for (element in events) {
            val event = try {
                element.jsonObject
            } catch (_: Exception) {
                continue
            }
            val segs = event["segs"]?.jsonArray ?: continue
            val text = segs
                .mapNotNull { seg ->
                    try {
                        seg.jsonObject["utf8"]?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        null
                    }
                }
                .joinToString("")
                .replace('\n', ' ')
                .trim()
            if (text.isEmpty()) continue

            val startMs = event["tStartMs"]?.jsonPrimitive?.longOrNull
            if (out.isNotEmpty()) {
                // Silence between the previous event's end and this one's start.
                // ASR events can overlap (rolling captions); a negative gap is just "no break".
                val gap = if (startMs != null && lastEndMs != null) startMs - lastEndMs else 0L
                out.append(if (gap > PARAGRAPH_GAP_MS) "\n\n" else " ")
            }
            out.append(text.replace(Regex("\\s+"), " "))
            if (startMs != null) {
                val durationMs = event["dDurationMs"]?.jsonPrimitive?.longOrNull ?: 0L
                lastEndMs = startMs + durationMs
            }
        }

        return out.toString().takeIf { it.isNotBlank() }
    }
}
