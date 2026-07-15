package tech.clyde.mixtapes.core.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.clyde.mixtapes.core.match.SystemHint

/**
 * Parses the raw HTTP response body of an OpenAI-compatible chat/completions call.
 * Providers differ in how faithfully they honor "output only a JSON object", so the
 * content is parsed through a defensive ladder rather than trusted.
 */
object LlmResponseParser {

    sealed interface Parsed {
        /**
         * The extraction; an empty title list is a valid (if useless) model answer.
         * [system] is the video's detected system as a canonical [SystemHint] id,
         * or null when absent, unrecognized, or the content was a bare title array.
         */
        data class Extraction(val titles: List<String>, val system: String? = null) : Parsed

        /** The gateway returned a structured error ({"error":{"message":...}}). */
        data class ApiError(val message: String) : Parsed

        data object Unparseable : Parsed
    }

    fun parse(responseBody: String): Parsed {
        val root = try {
            Json.parseToJsonElement(responseBody).jsonObject
        } catch (_: Exception) {
            return Parsed.Unparseable
        }

        apiErrorMessage(root["error"])?.let { return Parsed.ApiError(it) }

        val content = try {
            root["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        } ?: return Parsed.Unparseable

        return extractionFromContent(content) ?: Parsed.Unparseable
    }

    private fun apiErrorMessage(error: JsonElement?): String? = try {
        error?.jsonObject?.get("message")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    /**
     * The ladder, cheapest first:
     * 1. content is exactly a JSON array of strings (older prompt/model shape; no system)
     * 2. same, after stripping markdown code fences
     * 3. content is a JSON object — a "games" array of strings (falling back to a
     *    single array-of-strings value) plus an optional sibling "system"
     * 4. first balanced [...] substring that parses as an array of strings
     *    (prose-wrapped output degrades to titles-only)
     */
    private fun extractionFromContent(content: String): Parsed.Extraction? {
        val trimmed = content.trim()

        stringArray(trimmed)?.let { return Parsed.Extraction(it) }
        val unfenced = stripCodeFences(trimmed)
        stringArray(unfenced)?.let { return Parsed.Extraction(it) }
        objectExtraction(unfenced)?.let { return it }
        firstBalancedArray(trimmed)?.let { array ->
            stringArray(array)?.let { return Parsed.Extraction(it) }
        }

        return null
    }

    private fun objectExtraction(json: String): Parsed.Extraction? {
        val obj = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return null
        }
        val titles = (obj["games"] as? JsonArray)?.let { stringArray(it.toString()) }
            ?: singleArrayValue(obj)
            ?: return null
        val system = (obj["system"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return Parsed.Extraction(titles, system?.let { SystemHint.canonical(it) })
    }

    /** Parses [json] iff it is a JSON array whose elements are all strings; blanks filtered. */
    private fun stringArray(json: String): List<String>? {
        val array = try {
            Json.parseToJsonElement(json) as? JsonArray ?: return null
        } catch (_: Exception) {
            return null
        }
        val titles = array.map { element ->
            val primitive = element as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            primitive.content
        }
        return titles.map(String::trim).filter(String::isNotEmpty)
    }

    private fun stripCodeFences(content: String): String {
        val fenced = Regex("^```[a-zA-Z]*\\s*(.*?)\\s*```$", RegexOption.DOT_MATCHES_ALL)
        return fenced.matchEntire(content)?.groupValues?.get(1)?.trim() ?: content
    }

    private fun singleArrayValue(obj: JsonObject): List<String>? {
        val single = obj.values.filterIsInstance<JsonArray>().singleOrNull() ?: return null
        return stringArray(single.toString())
    }

    /** Scans for the first balanced, string-aware [...] region. */
    private fun firstBalancedArray(text: String): String? {
        val start = text.indexOf('[')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                inString && c == '\\' -> i++
                inString && c == '"' -> inString = false
                !inString && c == '"' -> inString = true
                !inString && c == '[' -> depth++
                !inString && c == ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }
}
