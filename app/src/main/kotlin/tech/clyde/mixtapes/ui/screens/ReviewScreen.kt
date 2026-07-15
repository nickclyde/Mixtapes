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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.core.match.SystemHint
import tech.clyde.mixtapes.core.model.MatchResult
import tech.clyde.mixtapes.core.model.RomFile
import tech.clyde.mixtapes.core.model.ScoredCandidate
import tech.clyde.mixtapes.core.search.ArchiveSearch
import tech.clyde.mixtapes.ui.ReviewRow
import tech.clyde.mixtapes.ui.SystemChoice
import tech.clyde.mixtapes.ui.WizardState

@Composable
fun ReviewScreen(
    state: WizardState,
    allRoms: List<RomFile>,
    onNameChange: (String) -> Unit,
    onRowIncluded: (Int, Boolean) -> Unit,
    onPickRom: (Int, RomFile) -> Unit,
    onSystemFilterChange: (SystemChoice) -> Unit,
    onWrite: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onDismissOverwrite: () -> Unit,
) {
    var pickerRowIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }

    val gameRows = state.rows.withIndex().filter { !it.value.chapter.skipped }
    val skippedRows = state.rows.withIndex().filter { it.value.chapter.skipped }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            OutlinedTextField(
                value = state.collectionName,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Collection name") },
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))

            val filter = state.activeSystemFilter
            AssistChip(
                onClick = { showSystemDialog = true },
                label = {
                    Text(
                        when {
                            state.systemChoice is SystemChoice.Specific -> "System: $filter"
                            filter != null -> "System: $filter · detected"
                            else -> "All systems"
                        },
                    )
                },
            )
            if (filter != null && allRoms.none { SystemHint.matches(filter, it.system) }) {
                Text(
                    "No ROM folder matches \"$filter\" — tap the chip to change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(gameRows, key = { _, item -> item.index }) { _, (index, row) ->
                    ReviewRowItem(
                        row = row,
                        onIncludeChange = { onRowIncluded(index, it) },
                        onClick = { pickerRowIndex = index },
                    )
                    HorizontalDivider()
                }
                if (skippedRows.isNotEmpty()) {
                    item {
                        Text(
                            "Skipped (intro, outro, …)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    itemsIndexed(skippedRows, key = { _, item -> item.index }) { _, (index, row) ->
                        ReviewRowItem(
                            row = row,
                            onIncludeChange = { onRowIncluded(index, it) },
                            onClick = { pickerRowIndex = index },
                            dimmed = true,
                        )
                        HorizontalDivider()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.includedCount} of ${state.rows.size} included",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.widthIn(min = 16.dp))
                Button(
                    onClick = onWrite,
                    enabled = state.includedCount > 0 && state.collectionName.isNotBlank(),
                ) {
                    Text("Write collection")
                }
            }
        }
    }

    if (pickerRowIndex >= 0) {
        val row = state.rows.getOrNull(pickerRowIndex)
        if (row != null) {
            CandidatePickerDialog(
                row = row,
                allRoms = allRoms,
                onPick = { rom ->
                    onPickRom(pickerRowIndex, rom)
                    pickerRowIndex = -1
                },
                onDismiss = { pickerRowIndex = -1 },
            )
        }
    }

    if (showSystemDialog) {
        SystemFilterDialog(
            activeFilter = state.activeSystemFilter,
            availableSystems = state.availableSystems,
            onPick = { choice ->
                onSystemFilterChange(choice)
                showSystemDialog = false
            },
            onDismiss = { showSystemDialog = false },
        )
    }

    if (state.showOverwritePrompt) {
        AlertDialog(
            onDismissRequest = onDismissOverwrite,
            title = { Text("Collection already exists") },
            text = { Text("custom-${state.collectionName}.cfg already exists. Overwrite it?") },
            confirmButton = { TextButton(onClick = onConfirmOverwrite) { Text("Overwrite") } },
            dismissButton = { TextButton(onClick = onDismissOverwrite) { Text("Cancel") } },
        )
    }
}

/**
 * Rescopes matching to one system (or the whole library). "Clearing" a
 * detected system means picking All — detection already ran; re-selecting
 * Auto would just re-apply it.
 */
@Composable
private fun SystemFilterDialog(
    activeFilter: String?,
    availableSystems: List<String>,
    onPick: (SystemChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Match against") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                item {
                    SystemChoiceRow(
                        label = "All systems",
                        selected = activeFilter == null,
                        onPick = { onPick(SystemChoice.All) },
                    )
                }
                items(availableSystems) { system ->
                    SystemChoiceRow(
                        label = system,
                        selected = system == activeFilter,
                        onPick = { onPick(SystemChoice.Specific(system)) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SystemChoiceRow(label: String, selected: Boolean, onPick: () -> Unit) {
    Surface(onClick = onPick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun ReviewRowItem(
    row: ReviewRow,
    onIncludeChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    val contentColor =
        if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Checkbox(
            checked = row.included,
            onCheckedChange = onIncludeChange,
            enabled = row.selected != null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(row.chapter.title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            Text(
                row.selected?.displayName ?: "Tap to choose a ROM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        row.selected?.let { SuggestionChip(onClick = onClick, label = { Text(it.system) }) }
        MatchBadge(row)
    }
}

@Composable
private fun MatchBadge(row: ReviewRow) {
    val (label, color) = when {
        row.selected != null && row.confident -> "auto" to MaterialTheme.colorScheme.primary
        row.selected != null -> "picked" to MaterialTheme.colorScheme.secondary
        row.result is MatchResult.NeedsReview -> "check" to MaterialTheme.colorScheme.tertiary
        else -> "none" to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun CandidatePickerDialog(
    row: ReviewRow,
    allRoms: List<RomFile>,
    onPick: (RomFile) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val candidates: List<ScoredCandidate> = when (val result = row.result) {
        is MatchResult.Auto -> listOf(ScoredCandidate(result.rom, result.score)) + result.alternates
        is MatchResult.NeedsReview -> result.candidates
        MatchResult.NoMatch -> emptyList()
    }
    val searchResults = if (query.isBlank()) {
        emptyList()
    } else {
        val needle = query.lowercase()
        allRoms.filter { needle in it.displayName.lowercase() }.take(30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.chapter.title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search all ROMs") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (query.isBlank()) {
                        items(candidates) { candidate ->
                            RomChoice(
                                rom = candidate.rom,
                                detail = "score %.2f".format(candidate.score),
                                onPick = onPick,
                            )
                        }
                        if (candidates.isEmpty()) {
                            item {
                                Text(
                                    "No suggestions — search above.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(searchResults) { rom ->
                            RomChoice(rom = rom, detail = null, onPick = onPick)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Search the web",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                ArchiveLinkChips(
                    remember(row.chapter.title) { ArchiveSearch.forChapterTitle(row.chapter.title) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RomChoice(rom: RomFile, detail: String?, onPick: (RomFile) -> Unit) {
    Surface(
        onClick = { onPick(rom) },
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rom.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (detail != null) "${rom.system} · $detail" else rom.system,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
