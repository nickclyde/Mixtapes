package tech.clyde.mixtapes.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Landscape-first content column: centered, capped width, scrollable. */
@Composable
fun WizardColumn(
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val base = Modifier
            .widthIn(max = 840.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp)
        Column(
            modifier = if (scrollable) base.verticalScroll(rememberScrollState()) else base.fillMaxSize(),
            content = content,
        )
    }
}
