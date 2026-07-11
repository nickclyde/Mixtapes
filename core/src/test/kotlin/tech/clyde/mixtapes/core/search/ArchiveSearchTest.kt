package tech.clyde.mixtapes.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.clyde.mixtapes.core.match.SystemHint

class ArchiveSearchTest {

    private fun url(game: MissingGame, site: ArchiveSite): String =
        game.links.single { it.site == site }.url

    @Test
    fun `strips system bracket and rank decoration but keeps human title`() {
        val game = ArchiveSearch.forChapterTitle("#12 - Wave Race 64 [N64]")
        assertEquals("Wave Race 64", game.query)
        assertEquals("n64", game.systemKey)
    }

    @Test
    fun `vimm link carries mapped system param`() {
        val game = ArchiveSearch.forChapterTitle("#12 - Wave Race 64 [N64]")
        assertEquals(
            "https://vimm.net/vault?p=list&q=Wave+Race+64&system=N64",
            url(game, ArchiveSite.VIMMS),
        )
    }

    @Test
    fun `vimm link omits system param when family is unmapped`() {
        val game = ArchiveSearch.forChapterTitle("Metal Slug [MAME]")
        assertEquals("arcade", game.systemKey)
        assertFalse("&system=" in url(game, ArchiveSite.VIMMS))
    }

    @Test
    fun `no hint yields plain links`() {
        val game = ArchiveSearch.forChapterTitle("Chrono Trigger")
        assertNull(game.systemKey)
        assertEquals("https://vimm.net/vault?p=list&q=Chrono+Trigger", url(game, ArchiveSite.VIMMS))
        assertEquals(
            "https://archive.org/search?query=Chrono+Trigger",
            url(game, ArchiveSite.INTERNET_ARCHIVE),
        )
    }

    @Test
    fun `archive org query includes system key when hinted`() {
        val game = ArchiveSearch.forChapterTitle("Wave Race 64 [N64]")
        assertEquals(
            "https://archive.org/search?query=Wave+Race+64+n64",
            url(game, ArchiveSite.INTERNET_ARCHIVE),
        )
    }

    @Test
    fun `special characters are url encoded`() {
        val ampersand = ArchiveSearch.forChapterTitle("Mario & Luigi: Superstar Saga [GBA]")
        assertEquals("Mario & Luigi: Superstar Saga", ampersand.query)
        assertTrue("%26" in url(ampersand, ArchiveSite.VIMMS))

        val accented = ArchiveSearch.forChapterTitle("Pokémon Snap [N64]")
        assertEquals("Pokémon Snap", accented.query)
        assertTrue("%C3%A9" in url(accented, ArchiveSite.VIMMS))
    }

    @Test
    fun `minerva link is constant and flagged copy-first`() {
        val link = ArchiveSearch.forChapterTitle("Wave Race 64 [N64]")
            .links.single { it.site == ArchiveSite.MINERVA }
        assertEquals("https://minerva-archive.org/search/", link.url)
        assertTrue(link.copyQueryFirst)
    }

    @Test
    fun `alias maps through family to vimm name`() {
        val game = ArchiveSearch.forChapterTitle("Streets of Rage 2 [MEGADRIVE]")
        assertEquals("genesis", game.systemKey)
        assertTrue("&system=Genesis" in url(game, ArchiveSite.VIMMS))
    }

    @Test
    fun `bracket-only title falls back to raw`() {
        val game = ArchiveSearch.forChapterTitle("[SNES]")
        assertEquals("[SNES]", game.query)
    }

    @Test
    fun `canonical resolves aliases and rejects unknowns`() {
        assertEquals("snes", SystemHint.canonical("sfc"))
        assertEquals("psx", SystemHint.canonical("PS1"))
        assertNull(SystemHint.canonical("xyz"))
    }
}
