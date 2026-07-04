package tech.clyde.mixtapes.core.collection

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.clyde.mixtapes.core.model.RomFile

class CollectionWriterTest {

    private val chrono = RomFile.fromRelativePath("snes/Chrono Trigger (USA).sfc")
    private val zelda = RomFile.fromRelativePath("snes/Legend of Zelda, The - A Link to the Past (USA).sfc")
    private val ff7 = RomFile.fromRelativePath("psx/Final Fantasy VII (USA).m3u")

    @Test
    fun `renders ROMPATH lines in input order with trailing newline`() {
        val expected =
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\n" +
                "%ROMPATH%/snes/Legend of Zelda, The - A Link to the Past (USA).sfc\n" +
                "%ROMPATH%/psx/Final Fantasy VII (USA).m3u\n"
        assertEquals(expected, CollectionWriter.render(listOf(chrono, zelda, ff7)))
    }

    @Test
    fun `absolute mode prefixes the roms root instead`() {
        assertEquals(
            "/storage/emulated/0/ROMs/snes/Chrono Trigger (USA).sfc\n",
            CollectionWriter.render(listOf(chrono), absoluteRomsRoot = "/storage/emulated/0/ROMs"),
        )
    }

    @Test
    fun `empty selection renders an empty file`() {
        assertEquals("", CollectionWriter.render(emptyList()))
    }
}

class CollectionNameTest {

    @Test
    fun `video title is sanitized but stays recognizable`() {
        assertEquals(
            "Top 25 SNES Hidden Gems 2024",
            CollectionName.fromVideoTitle("Top 25 SNES Hidden Gems! (2024) 🎮"),
        )
    }

    @Test
    fun `nothing left falls back to mixtape`() {
        assertEquals("mixtape", CollectionName.fromVideoTitle("🎮🎮🎮"))
        assertEquals("mixtape", CollectionName.fromVideoTitle("   "))
    }

    @Test
    fun `long titles are capped`() {
        val long = "A".repeat(100) + " tail"
        val name = CollectionName.fromVideoTitle(long)
        assertEquals(CollectionName.MAX_LENGTH, name.length)
        assertEquals('A', name.last())
    }
}
