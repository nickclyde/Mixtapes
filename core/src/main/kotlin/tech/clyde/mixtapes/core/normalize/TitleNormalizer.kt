package tech.clyde.mixtapes.core.normalize

import java.text.Normalizer

/**
 * @param text full normalized title, e.g. "legend of zelda a link to the past"
 * @param tokens [text] split on spaces
 * @param baseText normalized portion before the first subtitle separator
 *   (":" or " - ") of the original title; equals [text] when there is none
 */
data class Normalized(
    val text: String,
    val tokens: List<String>,
    val baseText: String,
)

object TitleNormalizer {
    private val ARTICLES = setOf("the", "a", "an")
    private val RANK_DECORATION = Regex("""^\s*#?\d{1,3}\s*[-–—.):]\s*""")
    private val TRAILING_ARTICLE = Regex(""",\s*(the|a|an)(?=\s+-\s|\s*:|\s*$)""", RegexOption.IGNORE_CASE)
    private val SUBTITLE_SEPARATOR = Regex(""":|\s-\s""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val NON_ALNUM = Regex("""[^a-z0-9]+""")

    /** Normalizes a game title as parsed from a chapter list. */
    fun normalizeGame(raw: String): Normalized =
        normalize(
            NoIntroTags.stripGroups(
                raw.trim().trim('"', '“', '”').replace(RANK_DECORATION, ""),
            ),
        )

    /** Normalizes a ROM file name (strips extension and No-Intro tags first). */
    fun normalizeRom(fileName: String): Normalized =
        normalize(NoIntroTags.stripName(fileName))

    private fun normalize(raw: String): Normalized {
        var s = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFKD)
            .replace(COMBINING_MARKS, "")

        // "legend of zelda, the - a link to the past" -> "the legend of zelda - ..."
        TRAILING_ARTICLE.find(s)?.let { m ->
            s = m.groupValues[1] + " " + s.removeRange(m.range)
        }

        val base = SUBTITLE_SEPARATOR.find(s)?.let { s.substring(0, it.range.first) } ?: s

        val text = cleanup(s)
        val baseText = cleanup(base).ifEmpty { text }
        return Normalized(text = text, tokens = text.split(' ').filter { it.isNotEmpty() }, baseText = baseText)
    }

    private fun cleanup(s: String): String {
        val tokens = s
            .replace("&", " and ")
            .replace("+", " plus ")
            .replace("'", "")
            .replace("’", "")
            .replace(NON_ALNUM, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
            .map { RomanNumerals.toArabic(it) ?: it }

        val withoutArticle =
            if (tokens.size > 1 && tokens.first() in ARTICLES) tokens.drop(1) else tokens
        return withoutArticle.joinToString(" ")
    }
}
