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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.ui.WizardState

@Composable
fun SetupScreen(
    state: WizardState,
    onEsDePicked: (Uri) -> Unit,
    onRomsPicked: (Uri) -> Unit,
    onWriteAbsolutePathsChange: (Boolean) -> Unit,
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
