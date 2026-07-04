package tech.clyde.mixtapes.core.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.Fixtures
import tech.clyde.mixtapes.core.model.MatchResult

class MatcherTest {

    private val roms = Fixtures.romTree()

    private fun matchOne(title: String): MatchResult =
        Matcher.match(listOf(title), roms).single().result

    private fun assertAuto(title: String, expectedPath: String) {
        val result = matchOne(title)
        assertTrue("$title should auto-match, was $result", result is MatchResult.Auto)
        assertEquals(expectedPath, (result as MatchResult.Auto).rom.relativePath)
    }

    @Test
    fun `exact title`() = assertAuto("Chrono Trigger", "snes/Chrono Trigger (USA).sfc")

    @Test
    fun `leading article vs no-intro trailing article`() =
        assertAuto(
            "The Legend of Zelda: A Link to the Past",
            "snes/Legend of Zelda, The - A Link to the Past (USA).sfc",
        )

    @Test
    fun `diacritics and an extra Version token`() =
        assertAuto("Pokémon Emerald", "gba/Pokemon - Emerald Version (USA, Europe).gba")

    @Test
    fun `roman numeral in the game title`() =
        assertAuto("Mega Man II", "nes/Mega Man 2 (USA).nes")

    @Test
    fun `roman numeral in the rom name`() =
        assertAuto("Street Fighter 2 Turbo", "snes/Street Fighter II Turbo (USA).sfc")

    @Test
    fun `subtitle-only query surfaces the full title as top candidate`() {
        // "Yoshi's Island" vs "Super Mario World 2 - Yoshi's Island": the token
        // subset guard keeps this out of auto territory, but it must be the
        // number-one review candidate.
        val result = matchOne("Yoshi's Island")
        assertTrue("expected NeedsReview, was $result", result is MatchResult.NeedsReview)
        val top = (result as MatchResult.NeedsReview).candidates.first()
        assertEquals("snes/Super Mario World 2 - Yoshi's Island (USA) (Rev 1).sfc", top.rom.relativePath)
    }

    @Test
    fun `multi-disc game with m3u picks the m3u and hides the discs`() {
        val result = matchOne("Final Fantasy VII")
        assertTrue("expected Auto, was $result", result is MatchResult.Auto)
        val auto = result as MatchResult.Auto
        assertEquals("psx/Final Fantasy VII (USA).m3u", auto.rom.relativePath)
        val allPaths = auto.alternates.map { it.rom.relativePath } + auto.rom.relativePath
        assertTrue(
            "disc files should be suppressed, got $allPaths",
            allPaths.none { it.contains("(Disc") && it.contains("Final Fantasy VII") },
        )
    }

    @Test
    fun `multi-disc game without m3u picks disc 1`() =
        assertAuto("Metal Gear Solid", "psx/Metal Gear Solid (USA) (Disc 1) (Rev 1).chd")

    @Test
    fun `colon subtitle vs dash subtitle`() =
        assertAuto(
            "Castlevania: Symphony of the Night",
            "psx/Castlevania - Symphony of the Night (USA).chd",
        )

    @Test
    fun `same game on two systems needs review with both listed`() {
        val result = matchOne("Aladdin")
        assertTrue("expected NeedsReview, was $result", result is MatchResult.NeedsReview)
        val systems = (result as MatchResult.NeedsReview).candidates.map { it.rom.system }.toSet()
        assertEquals(setOf("snes", "genesis"), systems)
    }

    @Test
    fun `numbered sequels never auto-match a different number`() {
        // Semantic aliasing (FF III (USA) is FF VI) is explicitly v2; what v1
        // guarantees is that FF VI is not silently matched to FF III or FF VII.
        val result = matchOne("Final Fantasy VI")
        assertTrue("FF VI must not auto-match, was $result", result !is MatchResult.Auto)
    }

    @Test
    fun `goodtools tags are stripped`() = assertAuto("Contra", "nes/Contra (U) [!].nes")

    @Test
    fun `system-tagged chapter title still auto-matches`() =
        assertAuto("Wave Race 64 [N64]", "n64/Wave Race 64 - Kawasaki Jet Ski (USA) (Rev 1).z64")

    @Test
    fun `a title differing only by a number is a different game`() {
        // Real-world regression from the Thor: "Wave Race 64" must beat the
        // Game Boy "Wave Race" (a strict token subset missing only "64") even
        // though the N64 ROM carries a "- Kawasaki Jet Ski" subtitle, putting
        // the margin at exactly the AUTO_MARGIN boundary (0.95 vs 0.90).
        val result = matchOne("Wave Race 64")
        assertTrue("was $result", result is MatchResult.Auto)
        assertEquals("n64", (result as MatchResult.Auto).rom.system)
    }

    @Test
    fun `unnumbered title prefers the unnumbered game over its sequel`() =
        assertAuto("Super Adventure Island", "snes/Super Adventure Island (USA).sfc")

    @Test
    fun `an exact title match beats a subset title on another system`() {
        // Real-world regression from the Thor: NES "Adventure Island" scores a
        // perfect token-set ratio against "Super Adventure Island" (strict
        // subset), tying the exact SNES match at 1.0. Exact wins.
        assertAuto("Super Adventure Island [SNES]", "snes/Super Adventure Island (USA).sfc")
    }

    @Test
    fun `the subset title itself still matches exactly`() =
        assertAuto("Adventure Island", "nes/Adventure Island (USA).nes")

    @Test
    fun `a system hint contradicting the only match demotes it to review`() {
        // Real-world regression from the Thor: the library only has Skate or
        // Die on apple2gs; the chapter says [NES]. Silently writing the
        // apple2gs version would be a wrong guess — surface it instead.
        val result = matchOne("Skate or Die [NES]")
        assertTrue("expected NeedsReview, was $result", result is MatchResult.NeedsReview)
    }

    @Test
    fun `a system hint resolves a multi-system tie`() =
        assertAuto("Aladdin [GENESIS]", "genesis/Aladdin (USA).md")

    @Test
    fun `system hint aliases map to ES-DE directory names`() {
        // PS1 -> psx: the hint agrees with the pick, so it stays auto.
        assertAuto("Metal Gear Solid [PS1]", "psx/Metal Gear Solid (USA) (Disc 1) (Rev 1).chd")
    }

    @Test
    fun `misspelled title needs review with the right top candidate`() {
        // TechDweeb wrote "Shante"; the ROM is "Shantae".
        val result = matchOne("Shante [GBC]")
        assertTrue("expected NeedsReview, was $result", result is MatchResult.NeedsReview)
        assertEquals(
            "gbc/Shantae (USA).gbc",
            (result as MatchResult.NeedsReview).candidates.first().rom.relativePath,
        )
    }

    @Test
    fun `unknown game is a NoMatch`() {
        assertEquals(MatchResult.NoMatch, matchOne("Some Game Nobody Has"))
    }

    @Test
    fun `results come back in input order with titles attached`() {
        val results = Matcher.match(listOf("Chrono Trigger", "EarthBound"), roms)
        assertEquals(listOf("Chrono Trigger", "EarthBound"), results.map { it.gameTitle })
    }

    @Test
    fun `non-rom files are excluded from the fixture tree`() {
        assertTrue(roms.none { it.displayName == "sneshelp.txt" })
    }
}
