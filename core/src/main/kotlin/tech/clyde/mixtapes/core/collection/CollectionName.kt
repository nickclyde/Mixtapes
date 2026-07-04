package tech.clyde.mixtapes.core.collection

object CollectionName {
    const val MAX_LENGTH = 60

    private val WHITESPACE = Regex("""\s+""")

    /**
     * Derives a default collection name from a video title: keep it
     * recognizable (it's the mixtape's identity) but ES-DE-safe — charset
     * `[A-Za-z0-9 _-]`, whitespace collapsed, trimmed, capped at [MAX_LENGTH];
     * "mixtape" when nothing survives.
     */
    fun fromVideoTitle(title: String): String {
        val cleaned = title
            .map { c -> if (c.isEsDeSafe()) c else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()
        return cleaned.take(MAX_LENGTH).trimEnd().ifEmpty { "mixtape" }
    }

    private fun Char.isEsDeSafe(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' ||
            this == ' ' || this == '_' || this == '-'
}
