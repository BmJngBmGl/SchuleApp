package de.wunstorf.schulevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.wunstorf.schulevault.data.VaultNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SucheScreen(
    ergebnisse: List<VaultNote>,
    onQueryChange: (String) -> Unit,
    onZurueck: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var ausgeklappteNotizen by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suche") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    label = { Text("Suchbegriff") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isBlank()) {
                item {
                    Text(
                        "Titel, Themen und Notiztext werden über alle Ordner durchsucht.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (ergebnisse.isEmpty()) {
                item {
                    Text(
                        "Keine Treffer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(ergebnisse) { notiz ->
                    val istAusgeklappt = ausgeklappteNotizen.contains(notiz.documentId)
                    GlowCard(
                        onClick = {
                            ausgeklappteNotizen = if (istAusgeklappt) {
                                ausgeklappteNotizen - notiz.documentId
                            } else {
                                ausgeklappteNotizen + notiz.documentId
                            }
                        }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                notiz.folderPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                        Text(notiz.title, style = MaterialTheme.typography.titleMedium)
                        if (notiz.themen.isNotEmpty()) {
                            Text(
                                notiz.themen.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (istAusgeklappt && notiz.body.isNotBlank()) {
                            Text(
                                notiz.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
