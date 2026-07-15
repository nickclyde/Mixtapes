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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.core.collection.CollectionCfgParser
import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.ui.EditorState

@Composable
fun EditScreen(
    editor: EditorState,
    allRoms: List<RomFile>,
    onNameChange: (String) -> Unit,
    onRemoveEntry: (Int) -> Unit,
    onAddGameRequested: () -> Unit,
    onPickRom: (RomFile) -> Unit,
    onSave: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
    onCancel: () -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = editor.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Collection name") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(editor.entries) { index, entry ->
                    EntryRow(entry = entry, onRemove = { onRemoveEntry(index) })
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    onAddGameRequested()
                    showAddDialog = true
                }) { Text("Add game") }
                Spacer(Modifier.weight(1f))
                Text(
                    when (editor.gameCount) {
                        1 -> "1 game"
                        else -> "${editor.gameCount} games"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.widthIn(min = 16.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.widthIn(min = 8.dp))
                Button(
                    onClick = onSave,
                    enabled = editor.entries.isNotEmpty() && editor.name.isNotBlank(),
                ) { Text("Save") }
            }
        }
    }

    if (showAddDialog) {
        RomSearchDialog(
            title = "Add game",
            allRoms = allRoms,
            scanning = editor.scanning,
            onPick = { rom ->
                onPickRom(rom)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (editor.showOverwritePrompt) {
        AlertDialog(
            onDismissRequest = onDismissOverwrite,
            title = { Text("Collection already exists") },
            text = { Text("A collection with that name already exists. Overwrite it?") },
            confirmButton = { TextButton(onClick = onConfirmOverwrite) { Text("Overwrite") } },
            dismissButton = { TextButton(onClick = onDismissOverwrite) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EntryRow(entry: CollectionCfgParser.Entry, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (entry) {
            is CollectionCfgParser.Entry.Game -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.rom.displayName, style = MaterialTheme.typography.bodyLarge)
                }
                SuggestionChip(onClick = {}, label = { Text(entry.rom.system) })
            }
            is CollectionCfgParser.Entry.Opaque -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.rawLine,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Unrecognized line — kept as-is",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
