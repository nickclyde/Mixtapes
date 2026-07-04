package tech.clyde.mixtapes.core.scan

object RomExtensions {
    /**
     * Extensions that are clearly not launchable ROMs. A blacklist rather than
     * a per-system whitelist: unknown extensions are assumed to be ROMs, which
     * fails soft (an extra candidate) instead of hard (a missing game).
     */
    private val NOT_ROMS = setOf(
        "txt", "xml", "cfg", "dat", "nfo", "md5", "sha1", "json", "html",
        "png", "jpg", "jpeg", "gif", "bmp", "mp4", "pdf",
        "sav", "srm", "state", "auto", "bak", "db", "log", "ini",
    )

    fun isLikelyRom(fileName: String): Boolean {
        if (fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext !in NOT_ROMS
    }
}
