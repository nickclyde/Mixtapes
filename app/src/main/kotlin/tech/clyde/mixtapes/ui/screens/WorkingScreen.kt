package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.clyde.mixtapes.ui.WorkPhase

@Composable
fun WorkingScreen(phase: WorkPhase, progress: Float?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (phase) {
                WorkPhase.FETCHING -> "Fetching video details…"
                WorkPhase.SCANNING -> "Scanning ROM library…"
                WorkPhase.MATCHING -> "Matching games…"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(320.dp))
        } else {
            CircularProgressIndicator()
        }
    }
}
