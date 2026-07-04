package tech.clyde.mixtapes.saf

import android.net.Uri
import android.provider.DocumentsContract

object TreePathGuesser {
    private val SD_CARD_VOLUME = Regex("""[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}""")

    /**
     * Best-effort mapping of a SAF tree URI to a real filesystem path, e.g.
     * `primary:ROMs` -> `/storage/emulated/0/ROMs`. Only used for display and
     * the optional absolute-path output mode — collection lines use
     * `%ROMPATH%` and never depend on this. Returns null for exotic providers.
     */
    fun guessPath(treeUri: Uri): String? {
        if (treeUri.authority != "com.android.externalstorage.documents") return null
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "").trimEnd('/')
        val root = when {
            volume == "primary" -> "/storage/emulated/0"
            SD_CARD_VOLUME.matches(volume) -> "/storage/$volume"
            else -> return null
        }
        return if (path.isEmpty()) root else "$root/$path"
    }
}
