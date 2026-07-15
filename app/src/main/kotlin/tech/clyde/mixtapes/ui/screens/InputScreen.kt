package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.ui.SystemChoice

@Composable
fun InputScreen(
    initialUrl: String = "",
    useTranscript: Boolean = false,
    llmConfigured: Boolean = false,
    systemChoice: SystemChoice = SystemChoice.Auto,
    availableSystems: List<String> = emptyList(),
    onSystemChoiceChange: (SystemChoice) -> Unit = {},
    onUseTranscriptChange: (Boolean) -> Unit = {},
    onSubmitUrl: (String) -> Unit,
    onSubmitPastedText: (String) -> Unit,
    onChangeFolders: () -> Unit = {},
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var showPasteBox by rememberSaveable { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }

    WizardColumn {
        Text("Mixtapes", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Turn a YouTube game list into an ES-DE collection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("YouTube video URL") },
            placeholder = { Text("https://www.youtube.com/watch?v=…") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        SystemDropdown(
            choice = systemChoice,
            availableSystems = availableSystems,
            onChange = onSystemChoiceChange,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = useTranscript && llmConfigured,
                onCheckedChange = onUseTranscriptChange,
                enabled = llmConfigured,
            )
            Column {
                Text("Extract from transcript with AI", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (llmConfigured) {
                        "Reads the captions instead of the chapter list — for videos without chapters."
                    } else {
                        "Add an API key in Settings first."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onSubmitUrl(url) },
            enabled = url.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Make Mixtape")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showPasteBox = !showPasteBox }) {
            Text(if (showPasteBox) "Hide pasted chapter list" else "Paste a chapter list instead")
        }
        TextButton(onClick = onChangeFolders) {
            Text("Settings")
        }
        if (showPasteBox) {
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                label = { Text("Video description or chapter list") },
                placeholder = { Text("0:00 Intro\n1:23 Chrono Trigger\n…") },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSubmitPastedText(pasted) },
                enabled = pasted.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Use pasted list")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SystemDropdown(
    choice: SystemChoice,
    availableSystems: List<String>,
    onChange: (SystemChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // Auto-detect and All systems always work; specific dirs need a readable ROM library.
    val options = listOf<Pair<String, SystemChoice>>(
        "Auto-detect" to SystemChoice.Auto,
        "All systems" to SystemChoice.All,
    ) + availableSystems.map { it to SystemChoice.Specific(it) }

    Column {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = options.firstOrNull { it.second == choice }?.first
                    ?: (choice as? SystemChoice.Specific)?.system.orEmpty(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                label = { Text("System") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (label, option) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            "Match ROMs from one system only. Auto-detect uses the video when extracting with AI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
