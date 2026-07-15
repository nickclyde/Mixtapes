package tech.clyde.mixtapes.core.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures

class ChapterParserTest {

    @Test
    fun `plain leading timestamps`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/techdweeb_plain.txt"))

        assertEquals(11, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(0, chapters[0].seconds)
        assertEquals("Chrono Trigger", chapters[1].title)
        assertEquals(83, chapters[1].seconds)
        assertEquals("The Legend of Zelda: A Link to the Past", chapters[4].title)
        assertEquals(330, chapters[4].seconds)
        assertEquals("Super Mario World 2: Yoshi's Island", chapters[5].title)
        assertEquals("Outro", chapters.last().title)
        assertEquals(18 * 60 + 40, chapters.last().seconds)
    }

    @Test
    fun `dash separators and rank decorations are stripped`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/rgc_dashes_ranked.txt"))

        assertEquals(13, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals("Castlevania: Symphony of the Night", chapters[1].title)
        assertEquals(45, chapters[1].seconds)
        assertEquals("Metal Gear Solid", chapters[2].title)
        assertEquals("Tony Hawk's Pro Skater 2", chapters[6].title)
        assertEquals("Suikoden II", chapters[10].title)
        assertEquals("Honorable Mentions", chapters[11].title)
        assertEquals("Outro / Final Thoughts", chapters[12].title)
    }

    @Test
    fun `trailing timestamps`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/trailing_timestamps.txt"))

        assertEquals(4, chapters.size)
        assertEquals("Chrono Trigger", chapters[0].title)
        assertEquals(12, chapters[0].seconds)
        assertEquals("Secret of Mana", chapters[2].title)
        assertEquals(7 * 60 + 8, chapters[2].seconds)
    }

    @Test
    fun `hour-long timestamps`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/hours.txt"))

        assertEquals(5, chapters.size)
        assertEquals(59 * 60 + 59, chapters[2].seconds)
        assertEquals("A Third Game", chapters[3].title)
        assertEquals(1 * 3600 + 2 * 60 + 33, chapters[3].seconds)
        assertEquals(1 * 3600 + 45 * 60, chapters[4].seconds)
    }

    @Test
    fun `system-tag suffixes survive intact and double-zero minutes parse`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/techdweeb_system_tags.txt"))

        assertEquals(6, chapters.size)
        assertEquals("Summer is here!", chapters[0].title)
        assertEquals(0, chapters[0].seconds)
        assertEquals("Wave Race 64 [N64]", chapters[1].title)
        assertEquals(97, chapters[1].seconds)
        assertEquals("Legend of Zelda - Links Awakening [GB]", chapters[3].title)
        assertEquals("Wrap up", chapters.last().title)
    }

    @Test
    fun `a long monotonic run starting late still parses`() {
        // Real-world regression (SNESdrunk): an unstamped intro pushes the
        // first game chapter to 0:41, past MAX_FIRST_START_SECONDS.
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/late_start.txt"))

        assertEquals(10, chapters.size)
        assertEquals("Super Mario World", chapters[0].title)
        assertEquals(41, chapters[0].seconds)
        assertEquals("Tetris Attack", chapters.last().title)
        assertEquals(6 * 60 + 55, chapters.last().seconds)
    }

    @Test
    fun `a short late-starting run is still rejected`() {
        val description = """
            [0:41] Game One
            [1:23] Game Two
            [2:46] Game Three
            [3:19] Game Four
            [4:41] Game Five
        """.trimIndent()
        assertTrue(ChapterParser.parse(description).isEmpty())
    }

    @Test
    fun `stray timestamps in prose are rejected`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/decoys.txt"))
        assertTrue(chapters.isEmpty())
    }

    @Test
    fun `description without timestamps yields nothing`() {
        val chapters = ChapterParser.parse(Fixtures.text("descriptions/no_timestamps.txt"))
        assertTrue(chapters.isEmpty())
    }
}
