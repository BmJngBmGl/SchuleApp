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
import androidx.compose.material3.HorizontalDivider
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
import de.wunstorf.schulevault.data.WebUntisPreferences
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinstellungenScreen(
    iservTerminAnzahl: Int,
    iservSyncLaeuft: Boolean,
    onSpeichern: (benutzername: String, passwort: String, kalenderListe: List<IServKalenderConfig>) -> Unit,
    webUntisSyncLaeuft: Boolean,
    onWebUntisSync: (
        server: String,
        schule: String,
        benutzername: String,
        passwort: String,
        callback: (erfolgreich: Boolean, meldung: String?) -> Unit
    ) -> Unit,
    webUntisHausaufgabenSyncLaeuft: Boolean,
    onWebUntisHausaufgabenSync: (
        server: String,
        schule: String,
        benutzername: String,
        passwort: String,
        callback: (erfolgreich: Boolean, meldung: String) -> Unit
    ) -> Unit,
    onZurueck: () -> Unit
) {
    val context = LocalContext.current
    var benutzername by remember { mutableStateOf("") }
    var passwort by remember { mutableStateOf("") }
    var kalenderListe by remember { mutableStateOf(listOf<IServKalenderConfig>()) }
    var geladen by remember { mutableStateOf(false) }

    var webUntisServer by remember { mutableStateOf("") }
    var webUntisSchule by remember { mutableStateOf("") }
    var webUntisBenutzername by remember { mutableStateOf("") }
    var webUntisPasswort by remember { mutableStateOf("") }
    var webUntisStatus by remember { mutableStateOf<String?>(null) }
    var webUntisStatusIstFehler by remember { mutableStateOf(false) }
    var webUntisHausaufgabenStatus by remember { mutableStateOf<String?>(null) }
    var webUntisHausaufgabenStatusIstFehler by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val preferences = IServPreferences(context)
        benutzername = preferences.benutzername.first() ?: ""
        passwort = preferences.passwort.first() ?: ""
        kalenderListe = preferences.kalenderListe.first()

        val webUntisPreferences = WebUntisPreferences(context)
        webUntisServer = webUntisPreferences.server.first() ?: ""
        webUntisSchule = webUntisPreferences.schule.first() ?: ""
        webUntisBenutzername = webUntisPreferences.benutzername.first() ?: ""
        webUntisPasswort = webUntisPreferences.passwort.first() ?: ""

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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text("WebUntis-Stundenplan", style = MaterialTheme.typography.titleMedium)
            Text(
                "Überschreibt den Stundenplan im Vault komplett mit der aktuellen Woche aus WebUntis " +
                    "(manuelle Änderungen an der Datei gehen dabei verloren). Zugangsdaten bleiben nur " +
                    "auf diesem Gerät gespeichert.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = webUntisServer,
                onValueChange = { webUntisServer = it },
                label = { Text("Server (z. B. nessa.webuntis.com)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webUntisSchule,
                onValueChange = { webUntisSchule = it },
                label = { Text("Schule") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webUntisBenutzername,
                onValueChange = { webUntisBenutzername = it },
                label = { Text("WebUntis-Benutzername") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webUntisPasswort,
                onValueChange = { webUntisPasswort = it },
                label = { Text("WebUntis-Passwort") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            webUntisStatus?.let {
                Text(
                    it,
                    color = if (webUntisStatusIstFehler) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Button(
                onClick = {
                    webUntisStatus = null
                    onWebUntisSync(webUntisServer, webUntisSchule, webUntisBenutzername, webUntisPasswort) { erfolgreich, meldung ->
                        webUntisStatusIstFehler = !erfolgreich
                        webUntisStatus = meldung ?: if (erfolgreich) {
                            "Stundenplan erfolgreich synchronisiert."
                        } else {
                            "Synchronisierung fehlgeschlagen."
                        }
                    }
                },
                enabled = !webUntisSyncLaeuft &&
                    webUntisServer.isNotBlank() && webUntisSchule.isNotBlank() &&
                    webUntisBenutzername.isNotBlank() && webUntisPasswort.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (webUntisSyncLaeuft) "Synchronisiert..." else "Stundenplan synchronisieren")
            }

            Text(
                "Importiert offene Hausaufgaben der nächsten 60 Tage aus WebUntis in den " +
                    "Hausaufgaben-Tracker. Bereits importierte werden erkannt und nicht doppelt angelegt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            webUntisHausaufgabenStatus?.let {
                Text(
                    it,
                    color = if (webUntisHausaufgabenStatusIstFehler) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Button(
                onClick = {
                    webUntisHausaufgabenStatus = null
                    onWebUntisHausaufgabenSync(
                        webUntisServer, webUntisSchule, webUntisBenutzername, webUntisPasswort
                    ) { erfolgreich, meldung ->
                        webUntisHausaufgabenStatusIstFehler = !erfolgreich
                        webUntisHausaufgabenStatus = meldung
                    }
                },
                enabled = !webUntisHausaufgabenSyncLaeuft &&
                    webUntisServer.isNotBlank() && webUntisSchule.isNotBlank() &&
                    webUntisBenutzername.isNotBlank() && webUntisPasswort.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (webUntisHausaufgabenSyncLaeuft) "Importiert..." else "Hausaufgaben importieren")
            }
        }
    }
}
