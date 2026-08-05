package de.wunstorf.schulevault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Einheitliche Karte mit duennem, leicht farbigem Rand - das erzeugt in der
 * Summe den "futuristischen" Wiedererkennungswert der App, ohne auf jeder
 * einzelnen Karte einen eigenen Schatten/Glow-Hack zu brauchen.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    akzentFarbe: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: ColumnScopeContent
) {
    val innerContent: ColumnScopeContent = {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, akzentFarbe.copy(alpha = 0.45f)),
            content = innerContent
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, akzentFarbe.copy(alpha = 0.45f)),
            content = innerContent
        )
    }
}

// Typalias nur zur Lesbarkeit der Signatur oben (ColumnScope-Lambda).
typealias ColumnScopeContent = @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
