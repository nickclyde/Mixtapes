package tech.clyde.mixtapes.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import tech.clyde.mixtapes.core.search.ArchiveLink

object LinkActions {

    /** Opens an archive search link in the browser, copying the title first for copy-first sites. */
    fun open(context: Context, link: ArchiveLink, query: String) {
        if (link.copyQueryFirst) {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("game title", query))
            Toast.makeText(
                context,
                "“$query” copied — paste into the search box",
                Toast.LENGTH_LONG,
            ).show()
        }
        openUrl(context, link.url)
    }

    /** Opens a plain URL in the browser (or the app claiming it, e.g. YouTube). */
    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }
}
