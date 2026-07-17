package tech.clyde.mixtapes.core.article

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

data class ExtractedArticle(
    val title: String,
    val content: String,
    val truncated: Boolean,
)

/** Converts server-rendered article HTML into bounded, ordered text for an LLM. */
object ArticleHtmlExtractor {
    const val MAX_CONTENT_CHARS = 120_000
    private const val MIN_CONTENT_CHARS = 80
    private const val MIN_BLOCKS = 2

    private val BLOCK_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6", "p", "li")
    private val NOISE_TOKENS = setOf(
        "advert", "advertisement", "ad-slot", "adslot", "banner", "breadcrumb",
        "comment", "cookie", "footer", "header", "menu", "nav", "newsletter",
        "promo", "recommend", "related", "share", "sharing", "sidebar", "social",
        "sponsor", "subscribe",
    )

    sealed interface Result {
        data class Success(val article: ExtractedArticle) : Result
        data object InsufficientContent : Result
    }

    fun extract(html: String, fallbackTitle: String = ""): Result {
        if (html.isBlank()) return Result.InsufficientContent
        val document = runCatching { Jsoup.parse(html) }.getOrNull()
            ?: return Result.InsufficientContent
        val title = pageTitle(document).ifBlank { normalize(fallbackTitle) }
        val root = contentRoot(document) ?: return Result.InsufficientContent
        removeNoise(root)

        val blocks = root.getAllElements().asSequence()
            .filter { it.tagName() in BLOCK_TAGS }
            // A nested list item owns its child list items in Element.text(); keep
            // only its direct text so each visible item appears once.
            .mapNotNull(::serializeBlock)
            .toList()
        val full = blocks.joinToString("\n")
        val meaningful = full.count { !it.isWhitespace() }
        if (blocks.size < MIN_BLOCKS || meaningful < MIN_CONTENT_CHARS) {
            return Result.InsufficientContent
        }
        val truncated = full.length > MAX_CONTENT_CHARS
        return Result.Success(
            ExtractedArticle(
                title = title,
                content = if (truncated) safePrefix(full, MAX_CONTENT_CHARS) else full,
                truncated = truncated,
            ),
        )
    }

    /** Lightweight title extraction shared by the Android fetcher. */
    fun pageTitle(html: String): String = runCatching { pageTitle(Jsoup.parse(html)) }.getOrDefault("")

    private fun pageTitle(document: Document): String {
        val openGraph = document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        return normalize(openGraph).ifBlank {
            normalize(document.title()).ifBlank { normalize(document.selectFirst("h1")?.text().orEmpty()) }
        }
    }

    private fun contentRoot(document: Document): Element? =
        document.select("article").maxByOrNull { it.text().length }
            ?: document.select("main, [role=main]").maxByOrNull { it.text().length }
            ?: document.body()

    private fun removeNoise(root: Element) {
        root.select("script, style, noscript, nav, header, footer, form, aside, iframe, template").remove()
        root.getAllElements().toList().asReversed().forEach { element ->
            val signature = (element.id() + " " + element.className() + " " + element.attr("role"))
                .lowercase()
            if (NOISE_TOKENS.any { it in signature }) element.remove()
        }
    }

    private fun serializeBlock(element: Element): String? {
        val text = if (element.tagName() == "li") directListText(element) else normalize(element.text())
        if (text.isBlank()) return null
        return when (element.tagName()) {
            "h1", "h2", "h3", "h4", "h5", "h6" ->
                "HEADING ${element.tagName().uppercase()}: $text"
            "p" -> "PARAGRAPH: $text"
            "li" -> "LIST ITEM${orderedPosition(element)?.let { " $it" }.orEmpty()}: $text"
            else -> null
        }
    }

    private fun directListText(element: Element): String {
        val own = normalize(element.ownText())
        if (own.isNotBlank()) return own
        return normalize(element.children().takeWhile { it.tagName() !in setOf("ol", "ul") }.joinToString(" ") { it.text() })
    }

    private fun orderedPosition(element: Element): Int? {
        val parent = element.parent() ?: return null
        if (parent.tagName() != "ol") return null
        val start = parent.attr("start").toIntOrNull() ?: 1
        val siblings = parent.children().filter { it.tagName() == "li" }
        val index = siblings.indexOf(element).takeIf { it >= 0 } ?: return null
        return start + index
    }

    private fun normalize(value: String): String = Parser.unescapeEntities(value, false)
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun safePrefix(value: String, limit: Int): String {
        var end = limit
        if (end < value.length && end > 0 && Character.isHighSurrogate(value[end - 1])) end--
        return value.substring(0, end)
    }
}
