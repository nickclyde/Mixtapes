package tech.clyde.mixtapes.core.youtube

object TimedText {

    /**
     * Builds the json3 fetch URL for a caption track. Some player responses hand out
     * baseUrls that already carry a `fmt` param (the Innertube ANDROID client bakes in
     * `fmt=srv3`), and the timedtext endpoint honors the FIRST `fmt` — so a blindly
     * appended `&fmt=json3` is silently ignored and XML comes back. Strip any existing
     * `fmt` first. (`fmt` is not among the signed `sparams`, so this doesn't break the
     * URL signature.)
     */
    fun json3Url(baseUrl: String): String {
        val parts = baseUrl.split('?', limit = 2)
        val path = parts[0]
        val query = parts.getOrElse(1) { "" }
        val kept = query.split('&').filter { it.isNotEmpty() && !it.startsWith("fmt=") }
        return path + "?" + (kept + "fmt=json3").joinToString("&")
    }
}
