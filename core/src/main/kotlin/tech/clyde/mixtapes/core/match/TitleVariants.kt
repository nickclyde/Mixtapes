package tech.clyde.mixtapes.core.match

import tech.clyde.mixtapes.core.normalize.NoIntroTags
import tech.clyde.mixtapes.core.normalize.TitleNormalizer

/**
 * Alternate-title readings of a raw chapter/LLM game title. Extraction often
 * yields a regional alternate the ROM library doesn't use — "Panel de Pon
 * (Tetris Attack)", "Tetris Attack / Panel de Pon" — so the matcher scores
 * every variant and keeps the best result.
 */
object TitleVariants {
    private const val MAX_VARIANTS = 4

    private val PAREN_GROUP = Regex("""\(([^)]*)\)""")
    private val ALTERNATE_SEPARATOR = Regex("""\s+/\s+|\s+aka\s+""", RegexOption.IGNORE_CASE)

    /**
     * The variants of [rawTitle], the title itself first: each parenthetical
     * that isn't a No-Intro-style tag or a system name, then each ` / `- or
     * ` aka `-separated segment of the paren-stripped text. Deduplicated on
     * normalized text, capped at [MAX_VARIANTS].
     */
    fun of(rawTitle: String): List<String> {
        val candidates = buildList {
            add(rawTitle)
            PAREN_GROUP.findAll(rawTitle)
                .map { it.groupValues[1].trim() }
                .filterTo(this) {
                    it.isNotEmpty() && !NoIntroTags.isTagContent(it) && SystemHint.canonical(it) == null
                }
            val outer = TitleNormalizer.displayTitle(rawTitle)
            val segments = outer.split(ALTERNATE_SEPARATOR).map { it.trim() }
            if (segments.size > 1) addAll(segments)
        }

        val seen = mutableSetOf<String>()
        return candidates.filter { variant ->
            val normalized = TitleNormalizer.normalizeGame(variant).text
            normalized.isNotEmpty() && seen.add(normalized)
        }.take(MAX_VARIANTS)
    }
}
