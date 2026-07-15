package tech.clyde.mixtapes.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.clyde.mixtapes.core.collection.MixtapesIndex

/**
 * Writes `custom-<name>.cfg` into `<ES-DE>/collections/` via SAF. Only ever
 * touches the collections directory — nothing else under ES-DE.
 */
class CollectionFileWriter(private val resolver: ContentResolver) {

    sealed interface WriteResult {
        data class Written(val fileName: String) : WriteResult
        data object AlreadyExists : WriteResult
        data class Error(val message: String) : WriteResult
    }

    fun fileName(collectionName: String) = "custom-$collectionName.cfg"

    fun exists(esDeTree: Uri, collectionName: String): Boolean {
        val collectionsId = findChild(esDeTree, rootId(esDeTree), COLLECTIONS_DIR)?.documentId
            ?: return false
        return findChild(esDeTree, collectionsId, fileName(collectionName)) != null
    }

    fun write(esDeTree: Uri, collectionName: String, contents: String, overwrite: Boolean): WriteResult {
        val name = fileName(collectionName)
        val collectionsId = ensureCollectionsDir(esDeTree)
            ?: return WriteResult.Error("Could not create the collections directory")

        val existing = findChild(esDeTree, collectionsId, name)
        val target = when {
            existing != null && !overwrite -> return WriteResult.AlreadyExists
            existing != null -> DocumentsContract.buildDocumentUriUsingTree(esDeTree, existing.documentId)
            else -> createFile(esDeTree, collectionsId, name)
                ?: return WriteResult.Error("Could not create $name")
        }

        return try {
            // "wt" truncates: overwriting a longer previous file must not leave a tail.
            resolver.openOutputStream(target, "wt")?.use { it.write(contents.toByteArray()) }
                ?: return WriteResult.Error("Could not open $name for writing")
            WriteResult.Written(name)
        } catch (e: Exception) {
            WriteResult.Error(e.message ?: "Write failed")
        }
    }

    data class CfgFile(val fileName: String, val documentId: String)

    /**
     * The `custom-*.cfg` files in the collections dir, sorted by name. A
     * missing collections dir means no collections — it is not created here.
     */
    suspend fun listCollections(esDeTree: Uri): List<CfgFile> = withContext(Dispatchers.IO) {
        val collectionsId = findChild(esDeTree, rootId(esDeTree), COLLECTIONS_DIR)?.documentId
            ?: return@withContext emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(esDeTree, collectionsId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val files = mutableListOf<CfgFile>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val isDir = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (!isDir && name.startsWith("custom-") && name.endsWith(".cfg")) {
                    files += CfgFile(fileName = name, documentId = cursor.getString(0))
                }
            }
        }
        files.sortedBy { it.fileName.lowercase() }
    }

    /** Contents of a file in the collections dir, or null when missing/unreadable. */
    suspend fun readFile(esDeTree: Uri, fileName: String): String? = withContext(Dispatchers.IO) {
        val collectionsId = findChild(esDeTree, rootId(esDeTree), COLLECTIONS_DIR)?.documentId
            ?: return@withContext null
        val child = findChild(esDeTree, collectionsId, fileName) ?: return@withContext null
        val uri = DocumentsContract.buildDocumentUriUsingTree(esDeTree, child.documentId)
        try {
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes a file in the collections dir; false when missing or the delete failed. */
    suspend fun deleteFile(esDeTree: Uri, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val collectionsId = findChild(esDeTree, rootId(esDeTree), COLLECTIONS_DIR)?.documentId
            ?: return@withContext false
        val child = findChild(esDeTree, collectionsId, fileName) ?: return@withContext false
        val uri = DocumentsContract.buildDocumentUriUsingTree(esDeTree, child.documentId)
        try {
            DocumentsContract.deleteDocument(resolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    /** Writes `mixtapes.json` in the collections dir; false when the write failed. */
    suspend fun writeIndexFile(esDeTree: Uri, contents: String): Boolean =
        withContext(Dispatchers.IO) {
            val collectionsId = ensureCollectionsDir(esDeTree) ?: return@withContext false
            val existing = findChild(esDeTree, collectionsId, MixtapesIndex.FILE_NAME)
            val target = when {
                existing != null ->
                    DocumentsContract.buildDocumentUriUsingTree(esDeTree, existing.documentId)
                else -> createFile(esDeTree, collectionsId, MixtapesIndex.FILE_NAME)
                    ?: return@withContext false
            }
            try {
                resolver.openOutputStream(target, "wt")?.use { it.write(contents.toByteArray()) }
                    ?: return@withContext false
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun ensureCollectionsDir(esDeTree: Uri): String? {
        findChild(esDeTree, rootId(esDeTree), COLLECTIONS_DIR)?.let { return it.documentId }
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(esDeTree, rootId(esDeTree))
        val created = DocumentsContract.createDocument(
            resolver, rootUri, DocumentsContract.Document.MIME_TYPE_DIR, COLLECTIONS_DIR,
        ) ?: return null
        return DocumentsContract.getDocumentId(created)
    }

    private fun createFile(esDeTree: Uri, parentId: String, name: String): Uri? {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(esDeTree, parentId)
        // application/octet-stream: with text/plain the external-storage
        // provider appends ".txt" and ES-DE would never see the collection.
        val created = DocumentsContract.createDocument(resolver, parentUri, "application/octet-stream", name)
            ?: return null
        val actualName = queryDisplayName(created)
        if (actualName != null && actualName != name) {
            return try {
                DocumentsContract.renameDocument(resolver, created, name) ?: created
            } catch (_: Exception) {
                created
            }
        }
        return created
    }

    private data class Child(val documentId: String, val name: String)

    private fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    private fun findChild(tree: Uri, parentId: String, name: String): Child? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name) return Child(cursor.getString(0), name)
            }
        }
        return null
    }

    private fun queryDisplayName(document: Uri): String? {
        resolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    private companion object {
        const val COLLECTIONS_DIR = "collections"
    }
}
