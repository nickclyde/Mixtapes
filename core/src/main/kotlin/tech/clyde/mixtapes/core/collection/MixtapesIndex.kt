package tech.clyde.mixtapes.core.collection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

enum class SourceType(val wireName: String) {
    YOUTUBE("youtube"),
    ARTICLE("article");

    companion object {
        fun fromWireName(value: String?): SourceType? = entries.firstOrNull { it.wireName == value?.lowercase() }
    }
}

/** What we remember beyond the authoritative ES-DE cfg file. */
data class MixtapeInfo(
    val sourceUrl: String,
    val sourceTitle: String? = null,
    val sourceType: SourceType,
    /** ISO-8601 UTC; informational only, never parsed back. */
    val createdAt: String? = null,
)

object MixtapesIndex {
    const val FILE_NAME = "mixtapes.json"
    private const val VERSION = 2

    fun parse(json: String): Map<String, MixtapeInfo> {
        val root = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (_: Exception) {
            return emptyMap()
        }
        val collections = root["collections"] as? JsonObject ?: return emptyMap()
        return collections.mapNotNull { (fileName, value) ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            val sourceUrl = obj.string("sourceUrl")
            if (sourceUrl != null) {
                val sourceType = SourceType.fromWireName(obj.string("sourceType"))
                    ?: return@mapNotNull null
                fileName to MixtapeInfo(
                    sourceUrl = sourceUrl,
                    sourceTitle = obj.string("sourceTitle"),
                    sourceType = sourceType,
                    createdAt = obj.string("createdAt"),
                )
            } else {
                // Version 1 used video-only keys. Read them regardless of the
                // declared/missing version because parsing has always been defensive.
                val videoUrl = obj.string("videoUrl") ?: return@mapNotNull null
                fileName to MixtapeInfo(
                    sourceUrl = videoUrl,
                    sourceTitle = obj.string("videoTitle"),
                    sourceType = SourceType.YOUTUBE,
                    createdAt = obj.string("createdAt"),
                )
            }
        }.toMap()
    }

    fun render(entries: Map<String, MixtapeInfo>): String {
        val root = buildJsonObject {
            put("version", VERSION)
            putJsonObject("collections") {
                entries.toSortedMap().forEach { (fileName, info) ->
                    putJsonObject(fileName) {
                        put("sourceUrl", info.sourceUrl)
                        info.sourceTitle?.let { put("sourceTitle", it) }
                        put("sourceType", info.sourceType.wireName)
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
