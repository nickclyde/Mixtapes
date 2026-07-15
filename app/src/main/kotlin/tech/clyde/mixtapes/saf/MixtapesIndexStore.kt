package tech.clyde.mixtapes.saf

import android.net.Uri
import tech.clyde.mixtapes.core.collection.MixtapeInfo
import tech.clyde.mixtapes.core.collection.MixtapesIndex

/**
 * Read-modify-write access to `collections/mixtapes.json` (cfg filename →
 * [MixtapeInfo]). The cfg files are the source of truth: index writes are
 * best-effort (failures are swallowed, never block a collection operation),
 * stale entries are ignored at display time, and collections without an
 * entry simply have no video link. Single-user app — last write wins.
 */
class MixtapesIndexStore(private val files: CollectionFileWriter) {

    suspend fun load(esDeTree: Uri): Map<String, MixtapeInfo> {
        val contents = files.readFile(esDeTree, MixtapesIndex.FILE_NAME) ?: return emptyMap()
        return MixtapesIndex.parse(contents)
    }

    suspend fun put(esDeTree: Uri, fileName: String, info: MixtapeInfo) {
        save(esDeTree, load(esDeTree) + (fileName to info))
    }

    suspend fun remove(esDeTree: Uri, fileName: String) {
        val entries = load(esDeTree)
        if (fileName !in entries) return
        save(esDeTree, entries - fileName)
    }

    /** Carries a collection's info to its new filename after a rename. */
    suspend fun move(esDeTree: Uri, oldFileName: String, newFileName: String) {
        val entries = load(esDeTree)
        val info = entries[oldFileName] ?: return
        save(esDeTree, entries - oldFileName + (newFileName to info))
    }

    private suspend fun save(esDeTree: Uri, entries: Map<String, MixtapeInfo>) {
        files.writeIndexFile(esDeTree, MixtapesIndex.render(entries))
    }
}
