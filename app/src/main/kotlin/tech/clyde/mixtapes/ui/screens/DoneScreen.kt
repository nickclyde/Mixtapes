package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.core.search.MissingGame

@Composable
fun DoneScreen(
    fileName: String,
    collectionName: String,
    gameCount: Int,
    missing: List<MissingGame>,
    onMakeAnother: () -> Unit,
    onBackToLibrary: () -> Unit,
) {
    if (missing.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DoneHeader(fileName, collectionName, gameCount, onMakeAnother, onBackToLibrary)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { DoneHeader(fileName, collectionName, gameCount, onMakeAnother, onBackToLibrary) }
                item {
                    Text(
                        "Missing games (${missing.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 4.dp),
                    )
                    HorizontalDivider()
                }
                items(missing) { game ->
                    MissingGameRow(game)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DoneHeader(
    fileName: String,
    collectionName: String,
    gameCount: Int,
    onMakeAnother: () -> Unit,
    onBackToLibrary: () -> Unit,
) {
    Text("Mixtape recorded ✔", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        "$gameCount games written to ES-DE/collections/$fileName",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "ES-DE hides new collections until you turn them on. Restart ES-DE, then enable " +
            "“$collectionName” under Main menu → Game collection settings → " +
            "Custom game collections.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onMakeAnother) { Text("Make another") }
        OutlinedButton(onClick = onBackToLibrary) { Text("Library") }
    }
}

@Composable
private fun MissingGameRow(game: MissingGame) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                game.query,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            game.systemKey?.let {
                SuggestionChip(onClick = {}, label = { Text(it) })
            }
        }
        Spacer(Modifier.height(4.dp))
        ArchiveLinkChips(game)
    }
}
