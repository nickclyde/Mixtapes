package tech.clyde.mixtapes.ui.screens

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.ui.WizardState

@Composable
fun SetupScreen(
    state: WizardState,
    onEsDePicked: (Uri) -> Unit,
    onRomsPicked: (Uri) -> Unit,
    onWriteAbsolutePathsChange: (Boolean) -> Unit,
    onLlmApiKeyChange: (String) -> Unit = {},
    onLlmBaseUrlChange: (String) -> Unit = {},
    onLlmModelChange: (String) -> Unit = {},
    onContinue: () -> Unit,
) {
    val esDeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(onEsDePicked)
    }
    val romsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        it?.let(onRomsPicked)
    }

    WizardColumn {
        Text("Set up Mixtapes", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick the same directories ES-DE uses. Collection files are written to " +
                "<ES-DE>/collections/; everything else is left untouched.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        DirCard(
            title = "ES-DE directory",
            subtitle = state.esDePath ?: if (state.esDePicked) "Picked" else "Usually /storage/emulated/0/ES-DE",
            picked = state.esDePicked,
            onPick = { esDeLauncher.launch(initialUri("primary:ES-DE")) },
        )
        Spacer(Modifier.height(12.dp))
        DirCard(
            title = "ROMs directory",
            subtitle = state.romsPath ?: if (state.romsPicked) "Picked" else "Usually /storage/emulated/0/ROMs",
            picked = state.romsPicked,
            onPick = { romsLauncher.launch(initialUri("primary:ROMs")) },
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.writeAbsolutePaths,
                onCheckedChange = onWriteAbsolutePathsChange,
                enabled = state.romsPath != null,
            )
            Column {
                Text("Write absolute paths", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Only needed when ES-DE's ROM directory setting differs from the folder above; " +
                        "otherwise portable %ROMPATH% lines are written.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LlmSection(
            state = state,
            onApiKeyChange = onLlmApiKeyChange,
            onBaseUrlChange = onLlmBaseUrlChange,
            onModelChange = onLlmModelChange,
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onContinue,
            enabled = state.esDePicked && state.romsPicked,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Continue")
        }
    }
}

/**
 * Optional BYOK configuration for AI source extraction. Collapsed by default;
 * setup can complete without ever opening it.
 */
@Composable
private fun LlmSection(
    state: WizardState,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // The saved key is never read back into the UI; this field only holds what the
    // user types this session. Empty field + saved key shows "Key saved".
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showKey by rememberSaveable { mutableStateOf(false) }
    // Seeded from the effective values once; clearing a field mid-edit must not
    // snap back to the default until the screen is next recreated.
    var baseUrl by rememberSaveable { mutableStateOf(state.llmBaseUrl) }
    var model by rememberSaveable { mutableStateOf(state.llmModel) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI game-list extraction (optional)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.llmConfigured) "Configured" else "Bring your own API key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Set up")
                }
            }
            if (!expanded) return@Column

            Spacer(Modifier.height(12.dp))
            Text(
                "Reads video captions, web articles, and ordinary pasted lists to find the games. " +
                    "Works with OpenRouter, OpenAI, or any " +
                    "OpenAI-compatible endpoint; costs go to your own key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    onApiKeyChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text("sk-or-…") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Hide" else "Show")
                    }
                },
                supportingText = {
                    if (apiKey.isEmpty() && state.llmConfigured) Text("Key saved — type to replace")
                },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    onBaseUrlChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
                supportingText = { Text("Any OpenAI-compatible endpoint; blank resets to OpenRouter") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    onModelChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                supportingText = { Text("Blank resets to the default") },
            )
        }
    }
}

@Composable
private fun DirCard(title: String, subtitle: String, picked: Boolean, onPick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onPick) {
                Text(if (picked) "Change" else "Pick")
            }
        }
    }
}

private fun initialUri(documentId: String): Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
