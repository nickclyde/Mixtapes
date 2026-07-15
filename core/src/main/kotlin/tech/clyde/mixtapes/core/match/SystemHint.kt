package tech.clyde.mixtapes.core.match

/**
 * Creators often suffix chapter titles with the intended system: "Wave Race 64
 * [N64]", "Outrun [GENESIS]". When such a hint resolves to a known system
 * family, the matcher uses it to settle multi-system ties and to refuse a
 * confident pick on a *different* system.
 */
object SystemHint {
    private val BRACKET_GROUP = Regex("""\[([^\]]+)]""")

    /** Each set is one system family; members are normalized (lowercase alnum). */
    private val FAMILIES = listOf(
        setOf("nes", "famicom", "fc"),
        setOf("snes", "sfc", "supernintendo", "superfamicom"),
        setOf("genesis", "megadrive", "md"),
        setOf("mastersystem", "sms"),
        setOf("segacd", "megacd"),
        setOf("saturn"),
        setOf("dreamcast", "dc"),
        setOf("psx", "ps1", "playstation", "psone"),
        setOf("ps2", "playstation2"),
        setOf("psp"),
        setOf("n64", "nintendo64"),
        setOf("gc", "gamecube", "ngc"),
        setOf("wii"),
        setOf("gb", "gameboy"),
        setOf("gbc", "gameboycolor"),
        setOf("gba", "gameboyadvance"),
        setOf("nds", "ds"),
        setOf("3ds", "n3ds"),
        setOf("neogeo", "aes", "mvs"),
        setOf("arcade", "mame", "fbneo"),
        setOf("pcengine", "tg16", "turbografx16", "pce"),
        setOf("atari2600", "2600"),
        setOf("atarilynx", "lynx"),
        setOf("wonderswan", "ws"),
        setOf("ngp", "ngpc", "neogeopocket"),
    )

    /** Canonical id of each known family (its first member), in declaration order. */
    val canonicalIds: List<String> = FAMILIES.map { it.first() }

    /**
     * The system hint from the last bracket group of a raw chapter title, or
     * null when there is none or it isn't a recognizable system name.
     */
    fun fromTitle(rawTitle: String): String? {
        val group = BRACKET_GROUP.findAll(rawTitle).lastOrNull()?.groupValues?.get(1) ?: return null
        val normalized = normalize(group)
        return normalized.takeIf { family(it) != null }
    }

    /** Canonical key for a hint (the first member of its family), or null if unrecognized. */
    fun canonical(hint: String): String? = family(normalize(hint))?.first()

    /** True when the hinted system and a ROM's system directory are the same family. */
    fun matches(hint: String, system: String): Boolean {
        val normalizedHint = normalize(hint)
        val normalizedSystem = normalize(system)
        if (normalizedHint == normalizedSystem) return true
        val hintFamily = family(normalizedHint) ?: return false
        return normalizedSystem in hintFamily
    }

    private fun family(normalized: String): Set<String>? =
        FAMILIES.firstOrNull { normalized in it }

    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }
}
