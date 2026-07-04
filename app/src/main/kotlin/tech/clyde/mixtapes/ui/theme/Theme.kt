package tech.clyde.mixtapes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cassette-inspired palette: warm amber/orange on deep charcoal, like a
// chrome tape label under tinted plastic. Refined in the polish milestone.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFEF9A9A),
    background = Color(0xFF16130F),
    surface = Color(0xFF1E1A15),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB26500),
    secondary = Color(0xFF00695C),
    tertiary = Color(0xFFC62828),
    background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFF3E4),
)

@Composable
fun MixtapesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
