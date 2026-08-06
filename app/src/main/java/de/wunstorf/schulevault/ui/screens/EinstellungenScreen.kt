package de.wunstorf.schulevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.wunstorf.schulevault.data.IServKalenderConfig
import de.wunstorf.schulevault.data.IServPreferences
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinstellungenScreen(
    iservTerminAnzahl: Int,
    iservSyncLaeuft: Boolean,
    onSpeichern: (benutzername: String, passwort: String, kalenderListe: List<IServKalenderConfig>) -> Unit,
    onZurueck: () -> Unit
) {
    val context = LocalContext.current
    var benutzername by remember { mutableStateOf("") }
    var passwort by remember { mutableStateOf("") }
    var kalenderListe by remember { mutableStateOf(listOf<IServKalenderConfig>()) }
    var geladen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val preferences = IServPreferences(context)
        benutzername = preferences.benutzername.first() ?: ""
        passwort = preferences.passwort.first() ?: ""
        kalenderListe = preferences.kalenderListe.first()
        geladen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IServ-Kalender") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        if (!geladen) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Zugangsdaten (gelten für alle Kalender unten, bleiben nur auf diesem Gerät gespeichert)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = benutzername,
                onValueChange = { benutzername = it },
                label = { Text("IServ-Benutzername") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = passwort,
                onValueChange = { passwort = it },
                label = { Text("IServ-Passwort") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Kalender", style = MaterialTheme.typography.titleMedium)

            kalenderListe.forEachIndexed { index, kalender ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = kalender.label,
                            onValueChange = { neuesLabel ->
                                kalenderListe = kalenderListe.toMutableList().also {
                                    it[index] = it[index].copy(label = neuesLabel)
                                }
                            },
                            label = { Text("Name (z. B. Eigener Kalender)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = kalender.icsUrl,
                            onValueChange = { neueUrl ->
                                kalenderListe = kalenderListe.toMutableList().also {
                                    it[index] = it[index].copy(icsUrl = neueUrl)
                                }
                            },
                            label = { Text("ICS-Abo-URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(onClick = {
                        kalenderListe = kalenderListe.toMutableList().also { it.removeAt(index) }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Kalender entfernen")
                    }
                }
            }

            TextButton(onClick = {
                kalenderListe = kalenderListe + IServKalenderConfig(label = "", icsUrl = "")
            }) {
                Text("+ Kalender hinzufügen")
            }

            if (iservSyncLaeuft) {
                Text("Synchronisiert...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (kalenderListe.isNotEmpty()) {
                Text(
                    "$iservTerminAnzahl Termin(e) zuletzt geladen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    val bereinigteListe = kalenderListe.filter { it.label.isNotBlank() && it.icsUrl.isNotBlank() }
                    onSpeichern(benutzername, passwort, bereinigteListe)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Speichern")
            }
        }
    }
}
