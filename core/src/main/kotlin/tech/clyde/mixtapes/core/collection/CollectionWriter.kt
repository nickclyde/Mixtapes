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
}
