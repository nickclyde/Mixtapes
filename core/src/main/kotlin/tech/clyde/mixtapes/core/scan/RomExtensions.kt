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

    /**
     * ES-DE "directories interpreted as files": a directory named with one of
     * these extensions (e.g. a multi-disc `Game.m3u/` folder holding the discs
     * plus the playlist) is a single game. A collection line must reference
     * the directory itself; the files inside must not be scanned. Kept to a
     * known set — a generic has-an-extension check would swallow directories
     * like `Super Mario Bros. 3/`.
     */
    private val DIR_AS_FILE = setOf("m3u")

    fun isLikelyRom(fileName: String): Boolean {
        if (fileName.startsWith(".")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext !in NOT_ROMS
    }

    fun isDirInterpretedAsFile(dirName: String): Boolean =
        !dirName.startsWith(".") && dirName.substringAfterLast('.', "").lowercase() in DIR_AS_FILE
}
