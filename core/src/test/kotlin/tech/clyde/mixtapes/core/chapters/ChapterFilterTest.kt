package tech.clyde.mixtapes.core.chapters

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.clyde.mixtapes.core.model.Chapter

class ChapterFilterTest {

    private fun skippedTitles(vararg titles: String): List<String> {
        val chapters = titles.mapIndexed { i, t -> Chapter(title = t, seconds = i * 60) }
        return ChapterFilter.markSkipped(chapters).filter { it.skipped }.map { it.title }
    }

    @Test
    fun `non-game segments are marked skipped, games are not`() {
        val skipped = skippedTitles(
            "Intro",
            "Chrono Trigger",
            "Metal Gear Solid",
            "Honorable Mentions",
            "Outro / Final Thoughts",
        )
        assertEquals(listOf("Intro", "Honorable Mentions", "Outro / Final Thoughts"), skipped)
    }

    @Test
    fun `sponsor, settings, and pure-number chapters are skipped`() {
        val skipped = skippedTitles(
            "Sponsored by ExampleVPN",
            "Settings",
            "12",
            "Contra",
            "Q&A",
        )
        assertEquals(listOf("Sponsored by ExampleVPN", "Settings", "12", "Q&A"), skipped)
    }

    @Test
    fun `games containing stopwords as substrings are not skipped`() {
        // "Introduction to..." style false positives are acceptable to skip,
        // but a real game title containing "settings" or "intro" mid-word is not.
        val skipped = skippedTitles("Metroid", "Intrusion 2", "Final Fantasy VI")
        assertEquals(emptyList<String>(), skipped)
    }

    @Test
    fun `marking preserves order and count`() {
        val chapters = listOf(
            Chapter("Intro", 0),
            Chapter("EarthBound", 90),
            Chapter("Outro", 500),
        )
        val marked = ChapterFilter.markSkipped(chapters)
        assertEquals(chapters.map { it.title }, marked.map { it.title })
        assertEquals(listOf(true, false, true), marked.map { it.skipped })
    }
}
