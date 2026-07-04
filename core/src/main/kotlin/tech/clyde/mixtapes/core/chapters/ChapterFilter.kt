package tech.clyde.mixtapes.core.chapters

import tech.clyde.mixtapes.core.model.Chapter

object ChapterFilter {
    private val STOP_PATTERNS = listOf(
        Regex("""^intro(duction)?\b.*"""),
        Regex("""^outro\b.*"""),
        Regex("""^conclusion\b.*"""),
        Regex("""^final thoughts\b.*"""),
        Regex("""^wrap[- ]?up\b.*"""),
        Regex("""^results?$"""),
        Regex("""^set ?up$"""),
        Regex("""^settings$"""),
        Regex("""^giveaway\b.*"""),
        Regex("""^patreon\b.*"""),
        Regex("""^sponsor.*"""),
        Regex("""^honou?rable mentions?\b.*"""),
        Regex("""^subscribe.*"""),
        Regex("""^channel update.*"""),
        Regex("""^q\s*&\s*a$"""),
        Regex("""^#?\d+$"""),
    )

    /**
     * Returns the same chapters with [Chapter.skipped] set for entries that
     * look like non-game segments. Marks, never removes — the review screen
     * offers re-inclusion.
     */
    fun markSkipped(chapters: List<Chapter>): List<Chapter> =
        chapters.map { it.copy(skipped = looksLikeNonGame(it.title)) }

    private fun looksLikeNonGame(title: String): Boolean {
        val normalized = title.lowercase().trim()
        return STOP_PATTERNS.any { it.matches(normalized) }
    }
}
