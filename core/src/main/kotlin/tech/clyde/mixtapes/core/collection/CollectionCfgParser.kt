package tech.clyde.mixtapes.core.collection

import tech.clyde.mixtapes.core.model.RomFile

/**
 * Reads an existing `custom-<name>.cfg` back into entries, so a collection
 * written earlier (by this app or by hand) can be listed and edited.
 */
object CollectionCfgParser {

    sealed interface Entry {
        /** A line resolved to a `<system>/<file>` path under the ROMs root. */
        data class Game(val rom: RomFile, val rawLine: String) : Entry

        /**
         * A line we couldn't resolve — kept verbatim so editing a collection
         * never destroys hand-added lines we don't understand.
         */
        data class Opaque(val rawLine: String) : Entry
    }

    /**
     * Parses cfg contents into entries, one per non-blank line. A line becomes
     * a [Entry.Game] when it starts with `%ROMPATH%/` or with
     * [absoluteRomsRoot] (the real ROMs path, for collections written in
     * absolute-path mode) and the remainder is a valid `<system>/<file>` path;
     * anything else is [Entry.Opaque].
     */
    fun parse(contents: String, absoluteRomsRoot: String? = null): List<Entry> {
        val absolutePrefix = absoluteRomsRoot?.trimEnd('/')?.plus("/")
        return contents.split('\n')
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .map { line -> parseLine(line, absolutePrefix) }
    }

    private fun parseLine(line: String, absolutePrefix: String?): Entry {
        val relative = when {
            line.startsWith(ROMPATH_PREFIX) -> line.removePrefix(ROMPATH_PREFIX)
            absolutePrefix != null && line.startsWith(absolutePrefix) ->
                line.removePrefix(absolutePrefix)
            else -> return Entry.Opaque(line)
        }
        val rom = runCatching { RomFile.fromRelativePath(relative) }.getOrNull()
            ?: return Entry.Opaque(line)
        return Entry.Game(rom, rawLine = line)
    }

    private const val ROMPATH_PREFIX = "%ROMPATH%/"
}
