package tech.clyde.mixtapes.youtube

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.clyde.mixtapes.core.youtube.ExtractionResult
import tech.clyde.mixtapes.core.youtube.VideoMetadata
import tech.clyde.mixtapes.core.youtube.WatchPageExtractor

class YouTubeClient(private val http: OkHttpClient = OkHttpClient()) {

    sealed interface FetchResult {
        data class Success(val metadata: VideoMetadata) : FetchResult
        data class Failure(val error: FetchError) : FetchResult
    }

    enum class FetchError { INVALID_URL, NETWORK, EXTRACTION }

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val id = videoId(url) ?: return@withContext FetchResult.Failure(FetchError.INVALID_URL)
        val request = Request.Builder()
            .url("https://www.youtube.com/watch?v=$id&hl=en")
            .header("User-Agent", DESKTOP_UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", CONSENT_COOKIES)
            .build()

        val html = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.Failure(FetchError.NETWORK)
                response.body.string()
            }
        } catch (_: IOException) {
            return@withContext FetchResult.Failure(FetchError.NETWORK)
        }

        when (val extraction = WatchPageExtractor.extract(html)) {
            is ExtractionResult.Success -> FetchResult.Success(extraction.metadata)
            is ExtractionResult.Failure -> FetchResult.Failure(FetchError.EXTRACTION)
        }
    }

    companion object {
        private val VIDEO_ID =
            Regex("""(?:v=|youtu\.be/|shorts/|live/|embed/)([A-Za-z0-9_-]{11})""")

        internal const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** Pre-accepted consent cookies bypass the EU interstitial. */
        internal const val CONSENT_COOKIES = "CONSENT=YES+cb; SOCS=CAI"

        fun videoId(url: String): String? = VIDEO_ID.find(url)?.groupValues?.get(1)

        fun looksLikeYouTubeUrl(text: String): Boolean =
            videoId(text) != null && ("youtube.com" in text || "youtu.be" in text)
    }
}
