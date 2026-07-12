package tech.clyde.mixtapes.youtube

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tech.clyde.mixtapes.core.transcript.Json3TranscriptParser
import tech.clyde.mixtapes.core.youtube.CaptionTrack
import tech.clyde.mixtapes.core.youtube.CaptionTrackSelector
import tech.clyde.mixtapes.core.youtube.WatchPageExtractor

/**
 * Fetches a video's transcript as plain text via the timedtext caption URLs found
 * in the watch page player response.
 *
 * Some videos/sessions gate timedtext behind a PoToken: the tell is `exp=xpe` in
 * the track's baseUrl, and the symptom is an empty HTTP 200 body. The workaround
 * is to re-request the player response through the Innertube ANDROID client, whose
 * caption URLs are (currently) ungated. A further fallback exists upstream —
 * `/youtubei/v1/next` → getTranscriptEndpoint.params → `/youtubei/v1/get_transcript`
 * — but it needs protobuf param plumbing and is deliberately deferred until the
 * two-tier ladder here starts failing in practice.
 */
class TranscriptClient(private val http: OkHttpClient = OkHttpClient()) {

    sealed interface Result {
        data class Success(val plainText: String) : Result
        data class Failure(val error: Error) : Result
    }

    enum class Error {
        /** The video has no caption tracks at all. */
        NO_TRACKS,

        /** HTTP/transport failure. */
        NETWORK,

        /** Timedtext stayed empty even after the ANDROID-client fallback (PoToken gate). */
        EMPTY,

        /** The body came back but wasn't parseable json3. */
        PARSE,
    }

    suspend fun fetch(videoId: String, tracks: List<CaptionTrack>): Result = withContext(Dispatchers.IO) {
        val track = CaptionTrackSelector.pick(tracks)
            ?: return@withContext Result.Failure(Error.NO_TRACKS)

        val primaryBody = if (track.baseUrl.contains(GATED_MARKER)) {
            null // Known-gated URL; don't waste the round trip.
        } else {
            try {
                timedtext(track.baseUrl)
            } catch (_: IOException) {
                return@withContext Result.Failure(Error.NETWORK)
            }
        }

        val body = primaryBody ?: try {
            androidClientFallback(videoId)
        } catch (_: IOException) {
            return@withContext Result.Failure(Error.NETWORK)
        } ?: return@withContext Result.Failure(Error.EMPTY)

        val text = Json3TranscriptParser.parse(body)
            ?: return@withContext Result.Failure(Error.PARSE)
        Result.Success(text)
    }

    /** GETs a timedtext URL as json3. Returns null for the PoToken empty-200 signature. */
    private fun timedtext(baseUrl: String): String? {
        val request = Request.Builder()
            .url("$baseUrl&fmt=json3")
            .header("User-Agent", YouTubeClient.DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", YouTubeClient.CONSENT_COOKIES)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("timedtext HTTP ${response.code}")
            return response.body.string().takeIf { it.isNotBlank() }
        }
    }

    /**
     * Re-requests the player response as the Innertube ANDROID client and retries
     * the caption URL it advertises. Null when no track or still-empty timedtext.
     */
    private fun androidClientFallback(videoId: String): String? {
        val payload = """
            {"context":{"client":{"clientName":"ANDROID","clientVersion":"$ANDROID_CLIENT_VERSION","androidSdkVersion":30,"hl":"en"}},"videoId":"$videoId"}
        """.trimIndent()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", ANDROID_UA)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val playerJson = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("innertube player HTTP ${response.code}")
            response.body.string()
        }

        val track = CaptionTrackSelector.pick(WatchPageExtractor.captionTracksFromPlayerResponse(playerJson))
            ?: return null
        return timedtext(track.baseUrl)
    }

    private companion object {
        /** PoToken experiment marker; timedtext for these URLs returns an empty 200. */
        const val GATED_MARKER = "exp=xpe"

        /** Rots roughly yearly; bump both together when the fallback starts 4xx-ing. */
        const val ANDROID_CLIENT_VERSION = "20.10.38"
        const val ANDROID_UA = "com.google.android.youtube/$ANDROID_CLIENT_VERSION (Linux; U; Android 11) gzip"
    }
}
