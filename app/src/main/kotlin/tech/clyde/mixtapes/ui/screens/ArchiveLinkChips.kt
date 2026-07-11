package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.core.search.MissingGame
import tech.clyde.mixtapes.util.LinkActions

/** One chip per archive site, opening a browser search for [game]. */
@Composable
fun ArchiveLinkChips(game: MissingGame, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        game.links.forEach { link ->
            AssistChip(
                onClick = { LinkActions.open(context, link, game.query) },
                label = { Text(link.site.label) },
            )
        }
    }
}
