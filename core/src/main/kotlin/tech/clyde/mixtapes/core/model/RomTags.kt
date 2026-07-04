package tech.clyde.mixtapes.core.model

/**
 * Metadata parsed from No-Intro/Redump/GoodTools-style tags in a ROM file
 * name, e.g. `Final Fantasy III (USA) (Rev 1) [!].sfc`.
 */
data class RomTags(
    val regions: List<String> = emptyList(),
    val revision: Int = 0,
    val disc: Int? = null,
    val verifiedDump: Boolean = false,
    val badDump: Boolean = false,
    val prerelease: Boolean = false,
)
