package tech.clyde.mixtapes.core.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.collection.CollectionCfgParser.Entry

class CollectionCfgParserTest {

    @Test
    fun `parses ROMPATH lines into games`() {
        val entries = CollectionCfgParser.parse(
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\n" +
                "%ROMPATH%/psx/Final Fantasy VII (USA).m3u\n",
        )
        assertEquals(2, entries.size)
        val first = entries[0] as Entry.Game
        assertEquals("snes", first.rom.system)
        assertEquals("Chrono Trigger (USA).sfc", first.rom.displayName)
        assertEquals("%ROMPATH%/snes/Chrono Trigger (USA).sfc", first.rawLine)
    }

    @Test
    fun `parses absolute lines when the roms root is known`() {
        val entries = CollectionCfgParser.parse(
            "/storage/emulated/0/ROMs/snes/Chrono Trigger (USA).sfc\n",
            absoluteRomsRoot = "/storage/emulated/0/ROMs",
        )
        val game = entries.single() as Entry.Game
        assertEquals("snes/Chrono Trigger (USA).sfc", game.rom.relativePath)
    }

    @Test
    fun `absolute lines without a known root stay opaque`() {
        val entries = CollectionCfgParser.parse(
            "/storage/emulated/0/ROMs/snes/Chrono Trigger (USA).sfc\n",
        )
        assertTrue(entries.single() is Entry.Opaque)
    }

    @Test
    fun `mixed prefixes both parse`() {
        val entries = CollectionCfgParser.parse(
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\n" +
                "/storage/emulated/0/ROMs/gba/Metroid Fusion (USA).gba\n",
            absoluteRomsRoot = "/storage/emulated/0/ROMs/",
        )
        assertEquals(listOf("snes", "gba"), entries.map { (it as Entry.Game).rom.system })
    }

    @Test
    fun `CRLF endings and blank lines are handled`() {
        val entries = CollectionCfgParser.parse(
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\r\n\r\n\n%ROMPATH%/gb/Tetris (World).gb\r\n",
        )
        assertEquals(2, entries.size)
        assertTrue(entries.all { it is Entry.Game })
    }

    @Test
    fun `unrecognized lines become opaque`() {
        val entries = CollectionCfgParser.parse(
            "not a rom path at all\n" +
                "%ROMPATH%/onlyonesegment\n",
        )
        assertEquals(
            listOf("not a rom path at all", "%ROMPATH%/onlyonesegment"),
            entries.map { (it as Entry.Opaque).rawLine },
        )
    }

    @Test
    fun `parse then renderEntries round-trips a ROMPATH file`() {
        val contents =
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\n" +
                "%ROMPATH%/psx/Final Fantasy VII (USA).m3u\n"
        val entries = CollectionCfgParser.parse(contents)
        assertEquals(contents, CollectionWriter.renderEntries(entries))
    }

    @Test
    fun `round-trip preserves opaque lines among games verbatim`() {
        val contents =
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\n" +
                "/somewhere/else/entirely.bin\n" +
                "%ROMPATH%/gb/Tetris (World).gb\n"
        val entries = CollectionCfgParser.parse(contents)
        assertEquals(contents, CollectionWriter.renderEntries(entries))
    }

    @Test
    fun `renderEntries normalizes game lines to the current prefix policy`() {
        val entries = CollectionCfgParser.parse(
            "/storage/emulated/0/ROMs/snes/Chrono Trigger (USA).sfc\n",
            absoluteRomsRoot = "/storage/emulated/0/ROMs",
        )
        assertEquals(
            "%ROMPATH%/snes/Chrono Trigger (USA).sfc\n",
            CollectionWriter.renderEntries(entries),
        )
    }
}

class CollectionNameFromFileNameTest {

    @Test
    fun `extracts the display name from a custom collection file`() {
        assertEquals("Top 25 SNES RPGs", CollectionName.fromFileName("custom-Top 25 SNES RPGs.cfg"))
    }

    @Test
    fun `rejects files that are not custom collections`() {
        assertEquals(null, CollectionName.fromFileName("mixtapes.json"))
        assertEquals(null, CollectionName.fromFileName("Top 25.cfg"))
        assertEquals(null, CollectionName.fromFileName("custom-name.txt"))
        assertEquals(null, CollectionName.fromFileName("custom-.cfg"))
    }
}
