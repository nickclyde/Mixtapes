package tech.clyde.mixtapes.core.collection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** What we remember about a collection beyond its cfg file. */
data class MixtapeInfo(
    val videoUrl: String,
    val videoTitle: String? = null,
    /** ISO-8601 UTC; informational only, never parsed back. */
    val createdAt: String? = null,
)

/**
 * The app's metadata sidecar, `collections/mixtapes.json`: cfg filename →
 * [MixtapeInfo]. The cfg files are the source of truth — this index is
 * best-effort (stale or missing entries are fine) and parsing is defensive:
 * malformed input yields an empty map, never an exception.
 */
object MixtapesIndex {
    const val FILE_NAME = "mixtapes.json"
    private const val VERSION = 1

    fun parse(json: String): Map<String, MixtapeInfo> {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return emptyMap()
        }
        val collections = root["collections"] as? JsonObject ?: return emptyMap()
        return collections.mapNotNull { (fileName, value) ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            val videoUrl = obj.string("videoUrl") ?: return@mapNotNull null
            fileName to MixtapeInfo(
                videoUrl = videoUrl,
                videoTitle = obj.string("videoTitle"),
                createdAt = obj.string("createdAt"),
            )
        }.toMap()
    }

    fun render(entries: Map<String, MixtapeInfo>): String {
        val root = buildJsonObject {
            put("version", VERSION)
            putJsonObject("collections") {
                entries.toSortedMap().forEach { (fileName, info) ->
                    putJsonObject(fileName) {
                        put("videoUrl", info.videoUrl)
                        info.videoTitle?.let { put("videoTitle", it) }
                        info.createdAt?.let { put("createdAt", it) }
                    }
                }
            }
        }
        return PRETTY.encodeToString(JsonObject.serializer(), root)
    }

    private val PRETTY = Json { prettyPrint = true }

    private fun JsonObject.string(key: String): String? = try {
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}
