package de.wunstorf.schulevault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Die App ist bewusst nur im Dark Mode gehalten (kein Light-Theme) -
// "futuristisch" und dauerhaft hell vertraegt sich schlecht miteinander,
// und der Nutzer wollte explizit Dark Mode.
private val SchuleVaultDarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = SpaceBlack,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = Color.White,

    secondary = NeonViolet,
    onSecondary = SpaceBlack,
    secondaryContainer = NeonVioletDim,
    onSecondaryContainer = Color.White,

    tertiary = NeonMagenta,
    onTertiary = SpaceBlack,

    error = ErrorRed,
    onError = SpaceBlack,

    background = SpaceBlack,
    onBackground = TextPrimary,

    surface = DeepSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,

    surfaceContainer = ElevatedSurface,
    surfaceContainerHigh = CardSurface,
    surfaceContainerHighest = OutlineDim,

    outline = OutlineDim,
    outlineVariant = OutlineDim
)

@Composable
fun SchuleVaultTheme(
    // Parameter bleibt aus API-Konsistenzgruenden erhalten, wird aber
    // ignoriert - es gibt bewusst kein Light-Theme.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SchuleVaultDarkColors,
        typography = SchuleVaultTypography,
        content = content
    )
}
