package tech.clyde.mixtapes.core.chapters

import tech.clyde.mixtapes.core.model.Chapter

object ChapterParser {
    const val MIN_CHAPTERS = 3
    const val MAX_FIRST_START_SECONDS = 30

    private val TIMESTAMP = Regex("""(?<![\d:])(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)(?![\d:])""")
    private val RANK_DECORATION = Regex("""^#?\d{1,3}\s*[-–—.):]\s*""")
    private val SEPARATOR_CHARS = charArrayOf('-', '–', '—', '|', ':', '.', '[', ']', '(', ')')

    /**
     * Extracts a chapter list from a video description.
     *
     * Per line: the first `H:MM:SS` / `M:SS` timestamp is the chapter start;
     * the title is the rest of the line minus list decoration. A trailing
     * timestamp ("Chrono Trigger 1:23") also counts.
     *
     * The collected lines only qualify as a chapter list when at least
     * [MIN_CHAPTERS] of them form a run of monotonically non-decreasing
     * timestamps starting at or before [MAX_FIRST_START_SECONDS] — this
     * rejects stray timestamps in prose. When several runs qualify, the
     * longest wins. Returns an empty list when nothing qualifies.
     */
    fun parse(description: String): List<Chapter> {
        val candidates = description.lines().mapNotNull { parseLine(it) }
        val run = longestMonotonicRun(candidates)
        return if (run.size >= MIN_CHAPTERS && run.first().seconds <= MAX_FIRST_START_SECONDS) run else emptyList()
    }

    private fun parseLine(line: String): Chapter? {
        val match = TIMESTAMP.find(line) ?: return null
        val (hours, minutes, seconds) = match.destructured
        val start = (hours.toIntOrNull() ?: 0) * 3600 + minutes.toInt() * 60 + seconds.toInt()

        val after = line.substring(match.range.last + 1)
        val title = if (after.isBlank()) {
            // Trailing-timestamp form: "Chrono Trigger 1:23" — separators sit
            // at the end of the title, next to the removed timestamp.
            line.substring(0, match.range.first).trim()
                .trimEnd { it.isWhitespace() || it in SEPARATOR_CHARS }
        } else {
            // Leading-timestamp form — only trim separators on the timestamp
            // side, so a legitimate trailing "[N64]" system tag survives.
            after.trim()
                .trimStart { it.isWhitespace() || it in SEPARATOR_CHARS }
                .replace(RANK_DECORATION, "")
        }.trim()
        return if (title.isEmpty()) null else Chapter(title = title, seconds = start)
    }

    private fun longestMonotonicRun(chapters: List<Chapter>): List<Chapter> {
        var bestStart = 0
        var bestLength = 0
        var runStart = 0
        for (i in chapters.indices) {
            if (i > 0 && chapters[i].seconds < chapters[i - 1].seconds) runStart = i
            if (i - runStart + 1 > bestLength) {
                bestLength = i - runStart + 1
                bestStart = runStart
            }
        }
        return chapters.subList(bestStart, bestStart + bestLength)
    }
}
