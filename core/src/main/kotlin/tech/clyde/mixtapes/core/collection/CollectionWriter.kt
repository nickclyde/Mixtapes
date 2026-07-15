package tech.clyde.mixtapes.core.collection

import tech.clyde.mixtapes.core.model.RomFile

object CollectionWriter {
    /**
     * Renders the full contents of a `custom-<name>.cfg` file: one line per
     * ROM in the given order, `\n` line endings, trailing newline.
     *
     * Lines are `%ROMPATH%/<relativePath>` by default; when [absoluteRomsRoot]
     * is set (e.g. "/storage/emulated/0/ROMs"), absolute paths are written
     * instead.
     */
    fun render(roms: List<RomFile>, absoluteRomsRoot: String? = null): String {
        val prefix = absoluteRomsRoot?.trimEnd('/') ?: "%ROMPATH%"
        return roms.joinToString(separator = "") { "$prefix/${it.relativePath}\n" }
    }

    /**
     * Renders a parsed entry list back to cfg contents. Game lines are
     * re-rendered under the current prefix policy (same as [render]); opaque
     * lines pass through verbatim.
     */
    fun renderEntries(
        entries: List<CollectionCfgParser.Entry>,
        absoluteRomsRoot: String? = null,
    ): String {
        val prefix = absoluteRomsRoot?.trimEnd('/') ?: "%ROMPATH%"
        return entries.joinToString(separator = "") { entry ->
            when (entry) {
                is CollectionCfgParser.Entry.Game -> "$prefix/${entry.rom.relativePath}\n"
                is CollectionCfgParser.Entry.Opaque -> "${entry.rawLine}\n"
            }
        }
    }
}
