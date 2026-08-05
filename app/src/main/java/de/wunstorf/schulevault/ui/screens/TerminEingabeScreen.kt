package de.wunstorf.schulevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminEingabeScreen(
    onSpeichern: (
        titel: String,
        datum: kotlinx.datetime.LocalDate,
        istSchulisch: Boolean,
        notizText: String,
        callback: (Boolean) -> Unit
    ) -> Unit,
    onZurueck: () -> Unit
) {
    var titel by remember { mutableStateOf("") }
    var notizText by remember { mutableStateOf("") }
    var istSchulisch by remember { mutableStateOf(true) }
    var datumPickerOffen by remember { mutableStateOf(false) }
    var speichernLaeuft by remember { mutableStateOf(false) }
    var fehlermeldung by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState()
    val ausgewaehltesDatum = datePickerState.selectedDateMillis?.let { millis ->
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neuer Termin") },
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
            OutlinedTextField(
                value = titel,
                onValueChange = { titel = it },
                label = { Text("Titel") },
                modifier = Modifier.fillMaxWidth()
            )

            GlowCard {
                Text("Datum", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = ausgewaehltesDatum?.toString() ?: "Noch kein Datum gewählt",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                TextButton(onClick = { datumPickerOffen = true }) {
                    Text("Datum wählen")
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = istSchulisch, onCheckedChange = { istSchulisch = it })
                Text(
                    "Schulischer Termin (#schule-Tag)",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            OutlinedTextField(
                value = notizText,
                onValueChange = { notizText = it },
                label = { Text("Notiz (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )

            fehlermeldung?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val datum = ausgewaehltesDatum
                    when {
                        titel.isBlank() -> fehlermeldung = "Bitte einen Titel eingeben."
                        datum == null -> fehlermeldung = "Bitte ein Datum auswählen."
                        else -> {
                            fehlermeldung = null
                            speichernLaeuft = true
                            onSpeichern(titel, datum, istSchulisch, notizText) { erfolgreich ->
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
                Text(if (speichernLaeuft) "Speichert..." else "Termin speichern")
            }
        }
    }

    if (datumPickerOffen) {
        DatePickerDialog(
            onDismissRequest = { datumPickerOffen = false },
            confirmButton = {
                TextButton(onClick = { datumPickerOffen = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datumPickerOffen = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
