package de.wunstorf.schulevault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.wunstorf.schulevault.VaultUiState
import de.wunstorf.schulevault.data.Hausaufgabe
import de.wunstorf.schulevault.data.Termin
import de.wunstorf.schulevault.ui.theme.NeonAmber
import de.wunstorf.schulevault.ui.theme.NeonCyan
import de.wunstorf.schulevault.ui.theme.NeonMagenta
import de.wunstorf.schulevault.ui.theme.NeonViolet
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UebersichtScreen(
    uiState: VaultUiState,
    onTerminEingabeClick: () -> Unit,
    onLernnotizEingabeClick: () -> Unit,
    onFachClick: (String) -> Unit,
    onOrdnerWechselnClick: () -> Unit,
    onNeuLadenClick: () -> Unit,
    onSucheClick: () -> Unit,
    onTerminClick: (Termin) -> Unit,
    onStundenplanClick: () -> Unit,
    onHausaufgabeEingabeClick: () -> Unit,
    onHausaufgabeClick: (Hausaufgabe) -> Unit,
    onHausaufgabeErledigtToggle: (Hausaufgabe, Boolean) -> Unit
) {
    var fabMenuOffen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SchuleVault", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onStundenplanClick) {
                        Icon(Icons.Filled.CalendarViewWeek, contentDescription = "Stundenplan")
                    }
                    IconButton(onClick = onSucheClick) {
                        Icon(Icons.Filled.Search, contentDescription = "Suche")
                    }
                    IconButton(onClick = onNeuLadenClick) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Neu laden")
                    }
                    IconButton(onClick = onOrdnerWechselnClick) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = "Ordner wechseln")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (fabMenuOffen) {
                    ExtendedFloatingActionButton(
                        onClick = { fabMenuOffen = false; onLernnotizEingabeClick() },
                        icon = { Icon(Icons.Filled.Book, contentDescription = null) },
                        text = { Text("Lernnotiz") },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ExtendedFloatingActionButton(
                        onClick = { fabMenuOffen = false; onHausaufgabeEingabeClick() },
                        icon = { Icon(Icons.Filled.Assignment, contentDescription = null) },
                        text = { Text("Hausaufgabe") },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ExtendedFloatingActionButton(
                        onClick = { fabMenuOffen = false; onTerminEingabeClick() },
                        icon = { Icon(Icons.Filled.Event, contentDescription = null) },
                        text = { Text("Termin") },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                FloatingActionButton(onClick = { fabMenuOffen = !fabMenuOffen }) {
                    Icon(Icons.Filled.Add, contentDescription = "Neu anlegen")
                }
            }
        }
    ) { padding ->
        if (uiState.ladeLaeuft) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Kommende Termine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val heute = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val kommendeTermine = uiState.termine.filter { it.datum >= heute }

            if (kommendeTermine.isEmpty()) {
                item {
                    Text(
                        "Keine anstehenden Termine.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(kommendeTermine) { termin ->
                    TerminKarte(termin = termin, heute = heute, onClick = { onTerminClick(termin) })
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Text(
                    "Hausaufgaben",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val offeneHausaufgaben = uiState.hausaufgaben.filter { !it.erledigt }

            if (offeneHausaufgaben.isEmpty()) {
                item {
                    Text(
                        "Keine offenen Hausaufgaben.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(offeneHausaufgaben) { hausaufgabe ->
                    HausaufgabeKarte(
                        hausaufgabe = hausaufgabe,
                        heute = heute,
                        onClick = { onHausaufgabeClick(hausaufgabe) },
                        onErledigtToggle = { erledigt -> onHausaufgabeErledigtToggle(hausaufgabe, erledigt) }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Text(
                    "Fächer",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.faecher) { fach ->
                        FachChip(fach = fach, onClick = { onFachClick(fach) })
                    }
                }
            }

            uiState.fehlermeldung?.let { fehler ->
                item {
                    Text(
                        fehler,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminKarte(termin: Termin, heute: kotlinx.datetime.LocalDate, onClick: () -> Unit) {
    val tageBis = heute.daysUntil(termin.datum)
    val akzent = when {
        tageBis <= 1 -> NeonMagenta
        tageBis <= 3 -> NeonAmber
        tageBis <= 7 -> NeonViolet
        else -> NeonCyan
    }

    GlowCard(akzentFarbe = akzent, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(termin.titel, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${termin.datum}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (termin.notizText.isNotBlank()) {
                    Text(
                        text = termin.notizText.take(80),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Badge(containerColor = akzent, contentColor = Color.Black) {
                Text(
                    text = when {
                        tageBis == 0 -> "heute"
                        tageBis == 1 -> "morgen"
                        else -> "in ${tageBis}T"
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HausaufgabeKarte(
    hausaufgabe: Hausaufgabe,
    heute: kotlinx.datetime.LocalDate,
    onClick: () -> Unit,
    onErledigtToggle: (Boolean) -> Unit
) {
    val tageBis = heute.daysUntil(hausaufgabe.faelligAm)
    val akzent = when {
        tageBis <= 1 -> NeonMagenta
        tageBis <= 3 -> NeonAmber
        else -> NeonCyan
    }

    GlowCard(akzentFarbe = akzent, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = hausaufgabe.erledigt, onCheckedChange = onErledigtToggle)
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(hausaufgabe.titel, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${hausaufgabe.fach} · fällig ${hausaufgabe.faelligAm}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FachChip(fach: String, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.size(width = 120.dp, height = 64.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = fach,
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

