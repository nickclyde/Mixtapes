package tech.clyde.mixtapes.core.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleVariantsTest {

    @Test
    fun `plain title has a single variant`() =
        assertEquals(listOf("Chrono Trigger"), TitleVariants.of("Chrono Trigger"))

    @Test
    fun `parenthetical alternate title is a variant, original first`() {
        val variants = TitleVariants.of("Panel de Pon (Tetris Attack)")
        assertEquals("Panel de Pon (Tetris Attack)", variants.first())
        assertTrue("expected the parenthetical as a variant, got $variants", "Tetris Attack" in variants)
    }

    @Test
    fun `no-intro tags are not variants`() =
        assertEquals(listOf("Chrono Trigger (USA) (Rev 1)"), TitleVariants.of("Chrono Trigger (USA) (Rev 1)"))

    @Test
    fun `a language list is not a variant`() =
        assertEquals(listOf("Tetris Attack (En,Ja)"), TitleVariants.of("Tetris Attack (En,Ja)"))

    @Test
    fun `a year is not a variant`() =
        assertEquals(listOf("Chrono Trigger (1995)"), TitleVariants.of("Chrono Trigger (1995)"))

    @Test
    fun `a parenthesized system name is not a variant`() =
        assertEquals(listOf("Aladdin (SNES)"), TitleVariants.of("Aladdin (SNES)"))

    @Test
    fun `slash-separated titles split into variants`() {
        val variants = TitleVariants.of("Tetris Attack / Panel de Pon")
        assertTrue("got $variants", "Tetris Attack" in variants && "Panel de Pon" in variants)
    }

    @Test
    fun `aka-separated titles split into variants`() {
        val variants = TitleVariants.of("Panel de Pon aka Tetris Attack")
        assertTrue("got $variants", "Tetris Attack" in variants)
    }

    @Test
    fun `aka inside a word does not split`() =
        assertEquals(listOf("Akakageroo"), TitleVariants.of("Akakageroo"))

    @Test
    fun `bracket groups are never variants`() =
        assertEquals(listOf("Wave Race 64 [N64]"), TitleVariants.of("Wave Race 64 [N64]"))

    @Test
    fun `variants dedupe on normalized text`() {
        // The parenthetical repeats the outer title, so it adds nothing.
        assertEquals(
            listOf("Tetris Attack (tetris attack)"),
            TitleVariants.of("Tetris Attack (tetris attack)"),
        )
    }

    @Test
    fun `variants are capped`() {
        assertEquals(4, TitleVariants.of("Alpha / Beta / Gamma / Delta / Epsilon").size)
    }
}
