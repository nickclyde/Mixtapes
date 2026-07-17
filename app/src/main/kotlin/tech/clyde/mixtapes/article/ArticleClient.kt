package tech.clyde.mixtapes.article

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import tech.clyde.mixtapes.core.article.ArticleHtmlExtractor
import tech.clyde.mixtapes.youtube.YouTubeClient

/** Fetches exactly one user-submitted public HTTPS HTML page (plus safe redirects). */
class ArticleClient(client: OkHttpClient = OkHttpClient()) {
    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    data class ArticlePage(val finalUrl: String, val title: String, val html: String)

    sealed interface FetchResult {
        data class Success(val page: ArticlePage) : FetchResult
        data class Failure(val error: FetchError, val detail: String? = null) : FetchResult
    }

    enum class FetchError { INVALID_URL, NETWORK, HTTP, UNSUPPORTED_CONTENT, TOO_LARGE, UNREADABLE }

    suspend fun fetch(rawUrl: String): FetchResult = withContext(Dispatchers.IO) {
        var current = parsePublicHttps(rawUrl)
            ?: return@withContext FetchResult.Failure(FetchError.INVALID_URL)

        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            when (validateResolvedHost(current)) {
                HostValidation.PRIVATE -> return@withContext FetchResult.Failure(FetchError.INVALID_URL)
                HostValidation.UNRESOLVABLE -> return@withContext FetchResult.Failure(FetchError.NETWORK)
                HostValidation.PUBLIC -> Unit
            }
            val request = Request.Builder()
                .url(current)
                .header("User-Agent", YouTubeClient.DESKTOP_UA)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (response.code in 300..399) {
                        if (redirectCount == MAX_REDIRECTS) {
                            return@withContext FetchResult.Failure(
                                FetchError.HTTP,
                                "Too many redirects",
                            )
                        }
                        val location = response.header("Location")
                            ?: return@withContext FetchResult.Failure(
                                FetchError.HTTP,
                                "Redirect had no destination",
                            )
                        current = current.resolve(location)
                            ?.takeIf(::isPublicHttpsShape)
                            ?: return@withContext FetchResult.Failure(FetchError.INVALID_URL)
                        return@repeat
                    }
                    if (!response.isSuccessful) {
                        return@withContext FetchResult.Failure(
                            FetchError.HTTP,
                            "HTTP ${response.code}",
                        )
                    }
                    val body = response.body
                    val mediaType = body.contentType()
                    if (mediaType == null ||
                        !(mediaType.type == "text" && mediaType.subtype == "html") &&
                        !(mediaType.type == "application" && mediaType.subtype == "xhtml+xml")
                    ) {
                        return@withContext FetchResult.Failure(
                            FetchError.UNSUPPORTED_CONTENT,
                            mediaType?.toString(),
                        )
                    }
                    if (body.contentLength() > MAX_DOWNLOAD_BYTES) {
                        return@withContext FetchResult.Failure(FetchError.TOO_LARGE)
                    }
                    val bytes = readBounded(body.byteStream())
                        ?: return@withContext FetchResult.Failure(FetchError.TOO_LARGE)
                    val html = bytes.toString(mediaType.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                    if (html.isBlank()) {
                        return@withContext FetchResult.Failure(FetchError.UNREADABLE)
                    }
                    return@withContext FetchResult.Success(
                        ArticlePage(
                            finalUrl = current.toString(),
                            title = ArticleHtmlExtractor.pageTitle(html),
                            html = html,
                        ),
                    )
                }
            } catch (_: IOException) {
                return@withContext FetchResult.Failure(FetchError.NETWORK)
            } catch (_: IllegalArgumentException) {
                return@withContext FetchResult.Failure(FetchError.UNREADABLE)
            }
        }
        FetchResult.Failure(FetchError.HTTP, "Too many redirects")
    }

    private fun parsePublicHttps(rawUrl: String): HttpUrl? = rawUrl.trim().toHttpUrlOrNull()
        ?.takeIf(::isPublicHttpsShape)

    private fun isPublicHttpsShape(url: HttpUrl): Boolean =
        url.scheme == "https" &&
            url.username.isEmpty() &&
            url.password.isEmpty() &&
            url.host.isNotBlank() &&
            url.host.lowercase() != "localhost" &&
            !url.host.lowercase().endsWith(".localhost") &&
            !url.host.lowercase().endsWith(".local")

    private fun validateResolvedHost(url: HttpUrl): HostValidation {
        val addresses = try {
            http.dns.lookup(url.host)
        } catch (_: UnknownHostException) {
            return HostValidation.UNRESOLVABLE
        }
        return if (addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
            HostValidation.PUBLIC
        } else {
            HostValidation.PRIVATE
        }
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address.map(Byte::toInt).map { it and 0xff }
        return when (address) {
            is Inet4Address -> when {
                bytes[0] == 0 || bytes[0] == 127 -> false
                bytes[0] == 100 && bytes[1] in 64..127 -> false
                bytes[0] == 192 && bytes[1] == 0 && bytes[2] in setOf(0, 2) -> false
                bytes[0] == 198 && bytes[1] in setOf(18, 19) -> false
                bytes[0] == 198 && bytes[1] == 51 && bytes[2] == 100 -> false
                bytes[0] == 203 && bytes[1] == 0 && bytes[2] == 113 -> false
                bytes[0] >= 224 -> false
                else -> true
            }
            is Inet6Address -> {
                val uniqueLocal = bytes[0] and 0xfe == 0xfc
                val documentation = bytes.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8)
                !uniqueLocal && !documentation
            }
            else -> false
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_DOWNLOAD_BYTES) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private enum class HostValidation { PUBLIC, PRIVATE, UNRESOLVABLE }

    private companion object {
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
        const val MAX_REDIRECTS = 8
    }
}
