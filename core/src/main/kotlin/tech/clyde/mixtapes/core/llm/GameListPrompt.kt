package tech.clyde.mixtapes.core.llm

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds the OpenAI-compatible chat/completions request for extracting a game list
 * from a video transcript.
 */
object GameListPrompt {

    /** Any OpenAI-compatible gateway works; OpenRouter is the default because one key reaches every provider. */
    const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"

    /** Cheap, fast, huge context (a full ASR transcript always fits), strong at extraction. User-overridable. */
    const val DEFAULT_MODEL = "google/gemini-2.5-flash"

    // Deliberately no response_format: some gateways reject it with a 400, and
    // LlmResponseParser copes with fenced/wrapped output anyway.
    private const val SYSTEM_PROMPT =
        "You are given the transcript of a YouTube video that presents a curated list of " +
            "video games (for example \"best games\" or \"hidden gems\" countdowns for retro consoles). " +
            "Identify the games featured as entries of the list. Rules:\n" +
            "- Output ONLY a JSON array of strings, nothing else. No markdown, no commentary, no numbering.\n" +
            "- One entry per featured game, in the order they are featured.\n" +
            "- Do not include games that are only mentioned in passing or as comparisons.\n" +
            "- Do not include intro, outro, sponsor, or honorable-mention section headers.\n" +
            "- The transcript is auto-generated speech recognition, so game titles are often " +
            "mistranscribed (e.g. \"ease\" for \"Ys\", \"castle vania\" for \"Castlevania\"). " +
            "Output your best guess at the official title of each game."

    /** Returns the JSON request body for POST {base}/chat/completions. */
    fun requestBody(model: String, videoTitle: String, transcript: String): String =
        buildJsonObject {
            put("model", model)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", SYSTEM_PROMPT)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", "Video title: $videoTitle\n\nTranscript:\n$transcript")
                        },
                    )
                },
            )
        }.toString()
}
