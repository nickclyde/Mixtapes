package tech.clyde.mixtapes.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.core.normalize.NoIntroTags
import tech.clyde.mixtapes.core.scan.RomExtensions

class RomScanner(private val resolver: ContentResolver) {

    /**
     * Walks the ROMs tree: root children are system directories (snes/, psx/,
     * ...), files up to [MAX_DEPTH] below a system dir are collected (covers
     * per-game subfolders). One ContentResolver child query per directory —
     * never DocumentFile.listFiles(), which does one IPC round-trip per file.
     */
    suspend fun scan(
        romsTree: Uri,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): List<RomFile> = withContext(Dispatchers.IO) {
        val systems = listChildren(romsTree, DocumentsContract.getTreeDocumentId(romsTree))
            .filter { it.isDirectory && !it.name.startsWith(".") }
        val roms = mutableListOf<RomFile>()
        systems.forEachIndexed { index, system ->
            onProgress(index, systems.size)
            collect(romsTree, system.documentId, system.name, "${system.name}/", roms, depth = 0)
        }
        onProgress(systems.size, systems.size)
        roms
    }

    /**
     * Names of the root-level system directories — a single child query, no
     * tree walk, so it's cheap enough for the Input screen's system dropdown.
     */
    suspend fun listSystems(romsTree: Uri): List<String> = withContext(Dispatchers.IO) {
        listChildren(romsTree, DocumentsContract.getTreeDocumentId(romsTree))
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { it.name }
            .sorted()
    }

    private fun collect(
        tree: Uri,
        parentDocId: String,
        system: String,
        pathPrefix: String,
        out: MutableList<RomFile>,
        depth: Int,
    ) {
        for (child in listChildren(tree, parentDocId)) {
            when {
                // A `Game.m3u/` directory is one game (ES-DE "directories
                // interpreted as files") — record the directory, never descend.
                child.isDirectory && RomExtensions.isDirInterpretedAsFile(child.name) -> out += RomFile(
                    system = system,
                    relativePath = pathPrefix + child.name,
                    displayName = child.name,
                    tags = NoIntroTags.parse(child.name),
                )
                child.isDirectory -> if (depth < MAX_DEPTH && !child.name.startsWith(".")) {
                    collect(tree, child.documentId, system, "$pathPrefix${child.name}/", out, depth + 1)
                }
                RomExtensions.isLikelyRom(child.name) -> out += RomFile(
                    system = system,
                    relativePath = pathPrefix + child.name,
                    displayName = child.name,
                    tags = NoIntroTags.parse(child.name),
                )
            }
        }
    }

    private data class Entry(val documentId: String, val name: String, val isDirectory: Boolean)

    private fun listChildren(tree: Uri, parentDocId: String): List<Entry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val entries = mutableListOf<Entry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += Entry(
                    documentId = cursor.getString(0),
                    name = cursor.getString(1),
                    isDirectory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                )
            }
        }
        return entries
    }

    private companion object {
        const val MAX_DEPTH = 2
    }
}
