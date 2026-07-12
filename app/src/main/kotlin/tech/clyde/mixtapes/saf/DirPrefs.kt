package tech.clyde.mixtapes.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import tech.clyde.mixtapes.core.llm.GameListPrompt

/**
 * Persisted settings: the two SAF tree URIs (ES-DE dir read+write, ROMs dir read)
 * plus the optional LLM configuration for transcript extraction.
 */
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

    /**
     * User's LLM API key, stored in plain SharedPreferences. Deliberate tradeoff:
     * the key is low-value and user-scopeable (providers support per-key spend
     * limits), the prefs file is app-private, and backups are disabled in the
     * manifest so it never leaves the device. EncryptedSharedPreferences was
     * rejected — deprecated upstream and it would be the app's first crypto
     * dependency.
     */
    // Pasted keys carry baggage that trim() misses and OkHttp rejects in a header:
    // seen in the wild, a label line copied along with the key ("OpenCode API
    // Key\nsk-or-…"). Keys never contain whitespace and labels precede them, so
    // keep only the last whitespace-separated token — on read too, to heal
    // already-stored values.
    var llmApiKey: String?
        get() = sanitizeKey(prefs.getString(KEY_LLM_API_KEY, null))
        set(value) = prefs.edit { putString(KEY_LLM_API_KEY, sanitizeKey(value)) }

    private fun sanitizeKey(raw: String?): String? =
        raw?.split(Regex("\\s+"))?.lastOrNull { it.isNotEmpty() }

    /** OpenAI-compatible endpoint base; blank resets to the OpenRouter default. */
    var llmBaseUrl: String
        get() = prefs.getString(KEY_LLM_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: GameListPrompt.DEFAULT_BASE_URL
        set(value) = prefs.edit { putString(KEY_LLM_BASE_URL, value.trim().takeIf { it.isNotBlank() }) }

    /** Model id sent to the endpoint; blank resets to the default. */
    var llmModel: String
        get() = prefs.getString(KEY_LLM_MODEL, null)?.takeIf { it.isNotBlank() }
            ?: GameListPrompt.DEFAULT_MODEL
        set(value) = prefs.edit { putString(KEY_LLM_MODEL, value.trim().takeIf { it.isNotBlank() }) }

    /** True when transcript extraction can run (an API key is saved). */
    fun llmConfigured(): Boolean = llmApiKey != null

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
        const val KEY_LLM_API_KEY = "llm_api_key"
        const val KEY_LLM_BASE_URL = "llm_base_url"
        const val KEY_LLM_MODEL = "llm_model"
    }
}
