package tech.clyde.mixtapes.core.search

import tech.clyde.mixtapes.core.match.SystemHint
import tech.clyde.mixtapes.core.normalize.TitleNormalizer
import java.net.URLEncoder

enum class ArchiveSite(val label: String) {
    VIMMS("Vimm's Lair"),
    INTERNET_ARCHIVE("Internet Archive"),
    MINERVA("Minerva's Archive"),
}

/**
 * One outbound search link. [copyQueryFirst]: the site has no query deep
 * links — copy the title to the clipboard before opening so the user can
 * paste it into the site's search box.
 */
data class ArchiveLink(val site: ArchiveSite, val url: String, val copyQueryFirst: Boolean = false)

/** A game the library couldn't supply, with prebuilt archive search links. */
data class MissingGame(
    val query: String,
    val systemKey: String?,
    val links: List<ArchiveLink>,
)

/**
 * Builds search links for games missing from the local library, pointed at
 * ROM archive sites. All links are browser handoffs — the app never talks to
 * these sites directly.
 */
object ArchiveSearch {
    private const val VIMM_BASE = "https://vimm.net/vault?p=list&q="
    private const val ARCHIVE_ORG_BASE = "https://archive.org/search?query="
    private const val MINERVA_SEARCH = "https://minerva-archive.org/search/"

    /** Vimm's Lair vault section names, keyed on [SystemHint.canonical] keys. */
    private val VIMM_SYSTEMS = mapOf(
        "nes" to "NES",
        "snes" to "SNES",
        "genesis" to "Genesis",
        "mastersystem" to "SMS",
        "segacd" to "SegaCD",
        "saturn" to "Saturn",
        "dreamcast" to "Dreamcast",
        "psx" to "PS1",
        "ps2" to "PS2",
        "psp" to "PSP",
        "n64" to "N64",
        "gc" to "GameCube",
        "wii" to "Wii",
        "gb" to "GB",
        "gbc" to "GBC",
        "gba" to "GBA",
        "nds" to "DS",
        "3ds" to "3DS",
        "pcengine" to "TG16",
        "atari2600" to "Atari2600",
        "atarilynx" to "Lynx",
    )

    fun forChapterTitle(rawTitle: String): MissingGame {
        val query = TitleNormalizer.displayTitle(rawTitle).ifEmpty { rawTitle.trim() }
        val systemKey = SystemHint.fromTitle(rawTitle)?.let(SystemHint::canonical)
        return MissingGame(
            query = query,
            systemKey = systemKey,
            links = listOf(
                ArchiveLink(ArchiveSite.VIMMS, vimmUrl(query, systemKey)),
                ArchiveLink(ArchiveSite.INTERNET_ARCHIVE, archiveOrgUrl(query, systemKey)),
                ArchiveLink(ArchiveSite.MINERVA, MINERVA_SEARCH, copyQueryFirst = true),
            ),
        )
    }

    private fun vimmUrl(query: String, systemKey: String?): String {
        val system = systemKey?.let(VIMM_SYSTEMS::get)
        return VIMM_BASE + encode(query) + if (system != null) "&system=$system" else ""
    }

    private fun archiveOrgUrl(query: String, systemKey: String?): String =
        ARCHIVE_ORG_BASE + encode(if (systemKey != null) "$query $systemKey" else query)

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}
