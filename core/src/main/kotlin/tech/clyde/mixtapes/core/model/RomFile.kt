package tech.clyde.mixtapes.core.model

import tech.clyde.mixtapes.core.normalize.NoIntroTags

/**
 * A file found under the ROMs directory.
 *
 * @param system the system subdirectory name, e.g. "snes"
 * @param relativePath path relative to the ROMs root, e.g.
 *   "snes/Chrono Trigger (USA).sfc" — this is exactly what a
 *   `%ROMPATH%/` collection line needs
 */
data class RomFile(
    val system: String,
    val relativePath: String,
    val displayName: String,
    val tags: RomTags,
) {
    val extension: String
        get() = displayName.substringAfterLast('.', "").lowercase()

    companion object {
        fun fromRelativePath(relativePath: String): RomFile {
            val segments = relativePath.split('/')
            require(segments.size >= 2) { "expected <system>/<file>, got: $relativePath" }
            val name = segments.last()
            return RomFile(
                system = segments.first(),
                relativePath = relativePath,
                displayName = name,
                tags = NoIntroTags.parse(name),
            )
        }
    }
}
