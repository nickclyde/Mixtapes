package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.ui.HomeCollection

@Composable
fun HomeScreen(
    collections: List<HomeCollection>,
    loading: Boolean,
    pendingDelete: String?,
    onCreateNew: () -> Unit,
    onEdit: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onChangeFolders: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mixtapes", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your tape rack",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.widthIn(min = 12.dp))
                }
                TextButton(onClick = onChangeFolders) { Text("Settings") }
                Spacer(Modifier.widthIn(min = 8.dp))
                Button(onClick = onCreateNew) { Text("New mixtape") }
            }
            Spacer(Modifier.height(16.dp))

            if (collections.isEmpty() && !loading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No mixtapes yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Turn a “best games” video into an ES-DE collection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onCreateNew) { Text("New mixtape") }
                }
            } else {
                val listState = rememberLazyListState()
                // A keyed LazyColumn anchors scroll to the old first row, so a
                // refresh that inserts a new top row would hide it off-screen.
                LaunchedEffect(collections.firstOrNull()?.fileName) {
                    listState.scrollToItem(0)
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(collections, key = { it.fileName }) { collection ->
                        CollectionRow(
                            collection = collection,
                            onClick = { onEdit(collection.fileName) },
                            onOpenVideo = onOpenVideo,
                            onDelete = { onRequestDelete(collection.fileName) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (pendingDelete != null) {
        val displayName = collections.firstOrNull { it.fileName == pendingDelete }?.displayName
            ?: pendingDelete
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete “$displayName”?") },
            text = { Text("ES-DE will forget this collection. Your ROMs are not touched.") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CollectionRow(
    collection: HomeCollection,
    onClick: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(collection.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (collection.gameCount) {
                    1 -> "1 game"
                    else -> "${collection.gameCount} games"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        collection.videoUrl?.let { url ->
            AssistChip(onClick = { onOpenVideo(url) }, label = { Text("▶ YouTube") })
        }
        IconButton(onClick = onDelete) {
            // No icon-pack dependency; a glyph keeps the app lean.
            Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
