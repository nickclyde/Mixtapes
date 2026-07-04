package tech.clyde.mixtapes.core.normalize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun `leading article is dropped and subtitle separated`() {
        val n = TitleNormalizer.normalizeGame("The Legend of Zelda: A Link to the Past")
        assertEquals("legend of zelda a link to the past", n.text)
        assertEquals("legend of zelda", n.baseText)
    }

    @Test
    fun `no-intro trailing article and dash subtitle normalize to the same text`() {
        val n = TitleNormalizer.normalizeRom("Legend of Zelda, The - A Link to the Past (USA).sfc")
        assertEquals("legend of zelda a link to the past", n.text)
        assertEquals("legend of zelda", n.baseText)
    }

    @Test
    fun `diacritics are stripped`() {
        assertEquals("pokemon emerald", TitleNormalizer.normalizeGame("Pokémon Emerald").text)
    }

    @Test
    fun `apostrophes vanish without splitting the word`() {
        assertEquals("yoshis island", TitleNormalizer.normalizeGame("Yoshi's Island").text)
    }

    @Test
    fun `ampersand becomes and`() {
        assertEquals("banjo and kazooie", TitleNormalizer.normalizeGame("Banjo & Kazooie").text)
    }

    @Test
    fun `roman numerals become arabic, but not a lone I`() {
        assertEquals("mega man 2", TitleNormalizer.normalizeGame("Mega Man II").text)
        assertEquals("final fantasy 7", TitleNormalizer.normalizeGame("Final Fantasy VII").text)
        assertEquals("street fighter 2 turbo", TitleNormalizer.normalizeRom("Street Fighter II Turbo (USA).sfc").text)
        assertEquals("drakengard i", TitleNormalizer.normalizeGame("Drakengard I").text)
    }

    @Test
    fun `rom tags and extension are stripped`() {
        val n = TitleNormalizer.normalizeRom("Final Fantasy III (USA) (Rev 1).sfc")
        assertEquals("final fantasy 3", n.text)
    }

    @Test
    fun `bracketed system tags in game titles are ignored for matching`() {
        assertEquals("wave race 64", TitleNormalizer.normalizeGame("Wave Race 64 [N64]").text)
        val zelda = TitleNormalizer.normalizeGame("Legend of Zelda - Links Awakening [GB]")
        assertEquals("legend of zelda links awakening", zelda.text)
        assertEquals("legend of zelda", zelda.baseText)
    }

    @Test
    fun `baseText falls back to full text without a subtitle`() {
        val n = TitleNormalizer.normalizeGame("Chrono Trigger")
        assertEquals(n.text, n.baseText)
        assertEquals(listOf("chrono", "trigger"), n.tokens)
    }

    @Test
    fun `rom subtitle dash defines baseText`() {
        val n = TitleNormalizer.normalizeRom("Super Mario World 2 - Yoshi's Island (USA) (Rev 1).sfc")
        assertEquals("super mario world 2 yoshis island", n.text)
        assertEquals("super mario world 2", n.baseText)
    }

    @Test
    fun `roman numeral helper bounds`() {
        assertEquals("2", RomanNumerals.toArabic("ii"))
        assertEquals("7", RomanNumerals.toArabic("vii"))
        assertEquals("19", RomanNumerals.toArabic("xix"))
        assertEquals("30", RomanNumerals.toArabic("xxx"))
        assertNull(RomanNumerals.toArabic("i"))
        assertNull(RomanNumerals.toArabic("mix"))
        assertNull(RomanNumerals.toArabic("vi7"))
    }
}

class NoIntroTagsTest {

    @Test
    fun `region and revision`() {
        val tags = NoIntroTags.parse("Final Fantasy III (USA) (Rev 1).sfc")
        assertEquals(listOf("USA"), tags.regions)
        assertEquals(1, tags.revision)
    }

    @Test
    fun `goodtools single-letter region and verified dump`() {
        val tags = NoIntroTags.parse("Contra (U) [!].nes")
        assertEquals(listOf("USA"), tags.regions)
        assertTrue(tags.verifiedDump)
    }

    @Test
    fun `multi-region list`() {
        val tags = NoIntroTags.parse("Pokemon - Emerald Version (USA, Europe).gba")
        assertEquals(listOf("USA", "Europe"), tags.regions)
    }

    @Test
    fun `disc number`() {
        assertEquals(2, NoIntroTags.parse("Final Fantasy VII (USA) (Disc 2).chd").disc)
        assertNull(NoIntroTags.parse("Final Fantasy VII (USA).m3u").disc)
    }

    @Test
    fun `prerelease and bad dumps`() {
        assertTrue(NoIntroTags.parse("Some Game (Beta).sfc").prerelease)
        assertTrue(NoIntroTags.parse("Some Game (Proto 2).sfc").prerelease)
        assertTrue(NoIntroTags.parse("Some Game [b].nes").badDump)
        assertFalse(NoIntroTags.parse("Chrono Trigger (USA).sfc").prerelease)
    }

    @Test
    fun `stripName removes every tag group and the extension`() {
        assertEquals(
            "Legend of Zelda, The - A Link to the Past",
            NoIntroTags.stripName("Legend of Zelda, The - A Link to the Past (USA).sfc"),
        )
        assertEquals(
            "Metal Gear Solid",
            NoIntroTags.stripName("Metal Gear Solid (USA) (Disc 1) (Rev 1).chd"),
        )
    }
}
