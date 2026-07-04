package tech.clyde.mixtapes.core.normalize

import tech.clyde.mixtapes.core.model.RomTags

object NoIntroTags {
    private val TAG_GROUP = Regex("""\(([^)]*)\)|\[([^\]]*)]""")
    private val DIGITS = Regex("""\d+""")

    private val REGION_NAMES = setOf(
        "USA", "Europe", "Japan", "World", "Asia", "Australia", "Brazil",
        "Canada", "China", "France", "Germany", "Hong Kong", "Italy", "Korea",
        "Netherlands", "Spain", "Sweden", "Taiwan", "UK", "Unknown",
    )
    private val GOODTOOLS_REGIONS = mapOf(
        'U' to "USA", 'E' to "Europe", 'J' to "Japan", 'W' to "World",
    )

    /**
     * Parses No-Intro/Redump/GoodTools tags out of a ROM file name:
     * `(USA)`, `(USA, Europe)`, `(Rev 1)`, `(Disc 2)`, `(Beta)`, `[!]`, `[b]`.
     * GoodTools single-letter regions ((U), (E), (J), (W)) map to the
     * No-Intro names.
     */
    fun parse(fileName: String): RomTags {
        var regions = emptyList<String>()
        var revision = 0
        var disc: Int? = null
        var verified = false
        var bad = false
        var prerelease = false

        for (match in TAG_GROUP.findAll(withoutExtension(fileName))) {
            val content = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
            val lower = content.lowercase()
            when {
                lower == "!" -> verified = true
                lower == "b" || lower.matches(Regex("b\\d+")) -> bad = true
                lower.startsWith("rev") ->
                    revision = DIGITS.find(lower)?.value?.toIntOrNull() ?: revision
                lower.startsWith("disc") || lower.startsWith("disk") ->
                    disc = DIGITS.find(lower)?.value?.toIntOrNull()
                lower.startsWith("beta") || lower.startsWith("proto") ||
                    lower.startsWith("demo") || lower.startsWith("sample") -> prerelease = true
                regions.isEmpty() -> parseRegions(content)?.let { regions = it }
            }
        }
        return RomTags(regions, revision, disc, verified, bad, prerelease)
    }

    /** Removes every `(...)` and `[...]` group plus the extension: the bare title. */
    fun stripName(fileName: String): String =
        stripGroups(withoutExtension(fileName))

    /** Removes every `(...)` and `[...]` group — also used on chapter titles ("Wave Race 64 [N64]"). */
    fun stripGroups(text: String): String =
        text.replace(TAG_GROUP, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun withoutExtension(fileName: String): String =
        if ('.' in fileName) fileName.substringBeforeLast('.') else fileName

    private fun parseRegions(content: String): List<String>? {
        val parts = content.split(',').map { it.trim() }
        if (parts.all { it in REGION_NAMES }) return parts
        // GoodTools compact form: (U), (JU), (UE), ...
        if (content.length in 1..3 && content.all { it in GOODTOOLS_REGIONS }) {
            return content.map { GOODTOOLS_REGIONS.getValue(it) }
        }
        return null
    }
}
