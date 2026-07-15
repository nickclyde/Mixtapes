package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.ui.WizardError

@Composable
fun ErrorScreen(
    error: WizardError,
    detail: String?,
    onBackToInput: () -> Unit,
    onRetryTranscript: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tape jammed", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (error) {
                WizardError.INVALID_URL -> "That doesn't look like a YouTube video URL."
                WizardError.NETWORK -> "Couldn't reach YouTube. Check the connection and try again."
                WizardError.EXTRACTION ->
                    "Couldn't read the video page. You can paste the description text instead."
                WizardError.NO_CHAPTERS ->
                    "No chapter list found in the video description. If the video lists " +
                        "games another way, paste the list manually."
                WizardError.EMPTY_LIBRARY ->
                    "No ROMs found in the selected ROMs directory."
                WizardError.WRITE_FAILED ->
                    "Couldn't write the collection file." + (detail?.let { "\n$it" } ?: "")
                WizardError.READ_FAILED ->
                    "Couldn't read the collection file." + (detail?.let { "\n$it" } ?: "")
                WizardError.NO_TRANSCRIPT ->
                    "This video has no usable captions, so the transcript can't be read. " +
                        "Paste the game list manually instead."
                WizardError.NO_API_KEY ->
                    "Transcript extraction needs an API key. Add one under Settings → " +
                        "AI transcript extraction."
                WizardError.LLM_ERROR ->
                    "The AI request failed." + (detail?.let { "\n$it" } ?: "")
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBackToInput) { Text("Back") }
            if (onRetryTranscript != null) {
                Button(onClick = onRetryTranscript) { Text("Try transcript instead") }
            }
        }
    }
}
