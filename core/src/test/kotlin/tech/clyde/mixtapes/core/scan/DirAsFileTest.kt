package tech.clyde.mixtapes.core.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures
import tech.clyde.mixtapes.core.match.Matcher
import tech.clyde.mixtapes.core.model.MatchResult

/**
 * ES-DE "directories interpreted as files": `psx/Game.m3u/` holding the discs
 * plus the playlist is one game, and a collection line must reference the
 * directory itself. Regression: the scanner used to descend into the directory
 * and emit `Game.m3u/Game.m3u`, which ES-DE silently drops.
 */
class DirAsFileTest {

    private val roms = Fixtures.romTree()

    @Test
    fun `m3u directory names are dir-as-file, ordinary directories are not`() {
        assertTrue(RomExtensions.isDirInterpretedAsFile("Final Fantasy IX (USA) (Rev 1).m3u"))
        assertFalse(RomExtensions.isDirInterpretedAsFile("Super Mario Bros. 3"))
        assertFalse(RomExtensions.isDirInterpretedAsFile("psx"))
        assertFalse(RomExtensions.isDirInterpretedAsFile(".hidden.m3u"))
    }

    @Test
    fun `m3u directory scans as a single entry, nothing inside it`() {
        val ffix = roms.filter { it.relativePath.contains("Final Fantasy IX") }
        assertEquals(
            listOf("psx/Final Fantasy IX (USA) (Rev 1).m3u"),
            ffix.map { it.relativePath },
        )
        assertTrue(
            "no scanned path may descend into a .m3u directory",
            roms.none { it.relativePath.contains(".m3u/") },
        )
    }

    @Test
    fun `m3u directory entry auto-matches like a regular rom`() {
        val result = Matcher.match(listOf("Final Fantasy IX"), roms).single().result
        assertTrue("expected Auto, was $result", result is MatchResult.Auto)
        assertEquals(
            "psx/Final Fantasy IX (USA) (Rev 1).m3u",
            (result as MatchResult.Auto).rom.relativePath,
        )
    }
}
