package tech.clyde.mixtapes.core.match

import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.core.normalize.TitleNormalizer

object CandidatePreference {
    /**
     * Collapses ROMs that normalize to the same title *within one system* down
     * to the single preferred variant:
     * 1. `.m3u` beats individual disc files (which are suppressed); with no
     *    `.m3u`, the lowest disc number wins
     * 2. region: USA > World > USA,Europe > Europe > Japan > other
     * 3. highest revision; verified dumps (`[!]`) preferred;
     *    beta/proto/demo/bad dumps never picked while a clean variant exists
     *
     * ROMs with distinct normalized titles or on different systems pass through
     * untouched. Order of the surviving ROMs is deterministic (by path).
     */
    fun collapse(roms: List<RomFile>): List<RomFile> =
        roms.groupBy { it.system to TitleNormalizer.normalizeRom(it.displayName).text }
            .map { (_, group) -> pick(group) }
            .sortedBy { it.relativePath }

    private fun pick(group: List<RomFile>): RomFile {
        if (group.size == 1) return group.first()
        val clean = group.filterNot { it.tags.prerelease || it.tags.badDump }.ifEmpty { group }
        return clean.minWithOrNull(preference) ?: group.first()
    }

    private val preference =
        compareBy<RomFile>(
            { if (it.extension == "m3u") 0 else 1 },
            { it.tags.disc ?: 0 },
            { regionRank(it.tags.regions) },
            { -it.tags.revision },
            { if (it.tags.verifiedDump) 0 else 1 },
            { it.relativePath },
        )

    private fun regionRank(regions: List<String>): Int = when {
        regions == listOf("USA") -> 0
        regions == listOf("World") -> 1
        "USA" in regions -> 2
        "Europe" in regions -> 3
        "Japan" in regions -> 4
        else -> 5
    }
}
