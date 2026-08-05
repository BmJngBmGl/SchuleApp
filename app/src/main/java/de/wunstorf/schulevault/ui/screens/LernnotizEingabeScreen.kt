package de.wunstorf.schulevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LernnotizEingabeScreen(
    verfuegbareFaecher: List<String>,
    onSpeichern: (
        fach: String,
        titel: String,
        themen: List<String>,
        notizText: String,
        callback: (Boolean) -> Unit
    ) -> Unit,
    onZurueck: () -> Unit
) {
    var fach by remember { mutableStateOf(verfuegbareFaecher.firstOrNull() ?: "") }
    var fachDropdownOffen by remember { mutableStateOf(false) }
    var titel by remember { mutableStateOf("") }
    var themenText by remember { mutableStateOf("") }
    var notizText by remember { mutableStateOf("") }
    var speichernLaeuft by remember { mutableStateOf(false) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neue Lernnotiz") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = fachDropdownOffen,
                onExpandedChange = { fachDropdownOffen = it }
            ) {
                OutlinedTextField(
                    value = fach,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fach") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fachDropdownOffen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = fachDropdownOffen,
                    onDismissRequest = { fachDropdownOffen = false }
                ) {
                    verfuegbareFaecher.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { fach = option; fachDropdownOffen = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = titel,
                onValueChange = { titel = it },
                label = { Text("Titel / Kurzbeschreibung") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = themenText,
                onValueChange = { themenText = it },
                label = { Text("Themen (Komma-getrennt)") },
                placeholder = { Text("z. B. Ableitungen, Kettenregel") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notizText,
                onValueChange = { notizText = it },
                label = { Text("Notiz zum Lernstoff") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            fehlermeldung?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    when {
                        fach.isBlank() -> fehlermeldung = "Bitte ein Fach auswählen."
                        titel.isBlank() -> fehlermeldung = "Bitte einen Titel eingeben."
                        else -> {
                            fehlermeldung = null
                            speichernLaeuft = true
                            val themen = themenText.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                            onSpeichern(fach, titel, themen, notizText) { erfolgreich ->
                                speichernLaeuft = false
                                if (erfolgreich) {
                                    onZurueck()
                                } else {
                                    fehlermeldung = "Speichern fehlgeschlagen - Ordner noch verbunden?"
                                }
                            }
                        }
                    }
                },
                enabled = !speichernLaeuft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (speichernLaeuft) "Speichert..." else "Notiz speichern")
            }
        }
    }
}
