package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

@Composable
fun InputScreen(
    initialUrl: String = "",
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
        Spacer(Modifier.height(12.dp))
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
