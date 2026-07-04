package tech.clyde.mixtapes.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri

/** The two persisted SAF tree URIs: ES-DE dir (read+write) and ROMs dir (read). */
class DirPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("dirs", Context.MODE_PRIVATE)

    var esDeTreeUri: Uri?
        get() = prefs.getString(KEY_ESDE, null)?.toUri()
        set(value) = prefs.edit { putString(KEY_ESDE, value?.toString()) }

    var romsTreeUri: Uri?
        get() = prefs.getString(KEY_ROMS, null)?.toUri()
        set(value) = prefs.edit { putString(KEY_ROMS, value?.toString()) }

    /** Write absolute filesystem paths instead of %ROMPATH% lines (advanced). */
    var writeAbsolutePaths: Boolean
        get() = prefs.getBoolean(KEY_ABSOLUTE, false)
        set(value) = prefs.edit { putBoolean(KEY_ABSOLUTE, value) }

    fun takePersistable(uri: Uri, write: Boolean) {
        var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }

    /** True when both URIs are saved and their permissions haven't been revoked. */
    fun isReady(): Boolean {
        val esDe = esDeTreeUri ?: return false
        val roms = romsTreeUri ?: return false
        val persisted = context.contentResolver.persistedUriPermissions
        val esDeOk = persisted.any { it.uri == esDe && it.isReadPermission && it.isWritePermission }
        val romsOk = persisted.any { it.uri == roms && it.isReadPermission }
        return esDeOk && romsOk
    }

    private companion object {
        const val KEY_ESDE = "esde_tree_uri"
        const val KEY_ROMS = "roms_tree_uri"
        const val KEY_ABSOLUTE = "write_absolute_paths"
    }
}
