package tech.clyde.mixtapes.core

import java.io.File
import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.core.scan.RomExtensions

object Fixtures {
    fun text(path: String): String =
        checkNotNull(Fixtures::class.java.getResourceAsStream("/$path")) { "missing fixture: $path" }
            .bufferedReader()
            .use { it.readText() }

    /** The fixtures/roms tree as RomFiles, filtered the same way the on-device scanner filters. */
    fun romTree(): List<RomFile> {
        val root = File(checkNotNull(Fixtures::class.java.getResource("/roms")) { "missing fixture: roms" }.toURI())
        val roms = mutableListOf<RomFile>()
        fun walk(dir: File) {
            for (child in dir.listFiles().orEmpty()) {
                when {
                    child.isDirectory && RomExtensions.isDirInterpretedAsFile(child.name) ->
                        roms += RomFile.fromRelativePath(child.relativeTo(root).invariantSeparatorsPath)
                    child.isDirectory -> walk(child)
                    RomExtensions.isLikelyRom(child.name) ->
                        roms += RomFile.fromRelativePath(child.relativeTo(root).invariantSeparatorsPath)
                }
            }
        }
        walk(root)
        return roms.sortedBy { it.relativePath }
    }
}
