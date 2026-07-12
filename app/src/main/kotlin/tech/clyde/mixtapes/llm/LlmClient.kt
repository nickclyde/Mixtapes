package tech.clyde.mixtapes.llm

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tech.clyde.mixtapes.core.llm.GameListPrompt
import tech.clyde.mixtapes.core.llm.LlmResponseParser

/**
 * Asks an OpenAI-compatible chat/completions endpoint for the game list in a
 * transcript. Which endpoint and model is entirely the user's choice (BYOK);
 * request/response shapes live in :core (GameListPrompt / LlmResponseParser).
 */
class LlmClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        // LLM responses routinely take tens of seconds; OkHttp's 10s default
        // read timeout would kill nearly every call.
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build(),
) {

    data class Config(val baseUrl: String, val apiKey: String, val model: String)

    sealed interface Result {
        data class Success(val titles: List<String>) : Result
        data class Failure(val error: Error, val detail: String? = null) : Result
    }

    enum class Error {
        /** Transport failure or timeout. */
        NETWORK,

        /** Non-2xx from the gateway; detail carries the provider's message when present. */
        HTTP,

        /** 2xx but the content had no recognizable title array. */
        PARSE,

        /** The model answered with an empty list. */
        EMPTY,
    }

    suspend fun extractGameTitles(videoTitle: String, transcript: String, config: Config): Result =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(config.baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                // OpenRouter attribution headers; other gateways ignore them.
                .header("HTTP-Referer", "https://github.com/nickclyde/Mixtapes")
                .header("X-Title", "Mixtapes")
                .post(
                    GameListPrompt.requestBody(config.model, videoTitle, transcript)
                        .toRequestBody("application/json".toMediaType()),
                )
                .build()

            val (code, body) = try {
                http.newCall(request).execute().use { response ->
                    response.code to response.body.string()
                }
            } catch (_: IOException) {
                return@withContext Result.Failure(Error.NETWORK)
            }

            val parsed = LlmResponseParser.parse(body)
            if (code !in 200..299) {
                val detail = (parsed as? LlmResponseParser.Parsed.ApiError)?.message ?: "HTTP $code"
                return@withContext Result.Failure(Error.HTTP, detail)
            }

            when (parsed) {
                is LlmResponseParser.Parsed.Titles ->
                    if (parsed.titles.isEmpty()) {
                        Result.Failure(Error.EMPTY)
                    } else {
                        Result.Success(parsed.titles)
                    }
                is LlmResponseParser.Parsed.ApiError -> Result.Failure(Error.HTTP, parsed.message)
                LlmResponseParser.Parsed.Unparseable -> Result.Failure(Error.PARSE)
            }
        }
}
