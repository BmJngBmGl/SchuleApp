package de.wunstorf.schulevault.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.wunstorf.schulevault.data.StundenplanEintrag
import de.wunstorf.schulevault.data.VaultRepository
import de.wunstorf.schulevault.data.Wochentag
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Reihenfolge der Wochentage, beginnend beim heutigen (am Wochenende beginnend bei Montag). */
private fun wochentagReihenfolgeAbHeute(): List<Wochentag> {
    val heute = Clock.System.todayIn(TimeZone.currentSystemDefault()).dayOfWeek
    val heutigerIndex = when (heute) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        else -> 0
    }
    val alle = Wochentag.entries
    return alle.drop(heutigerIndex) + alle.take(heutigerIndex)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StundenplanScreen(
    vaultUri: Uri?,
    onZurueck: () -> Unit,
    onFachKlick: (fach: String, notizId: String?) -> Unit
) {
    val context = LocalContext.current
    // Pro Tag einzeln befuellt statt einmal fuer die ganze Woche - der
    // heutige Tag ist dadurch (fast) sofort sichtbar, waehrend die
    // restlichen Tage im Hintergrund nachladen, statt dass die ganze Seite
    // auf den langsamsten Tag wartet.
    val eintraegeProTag = remember { mutableStateMapOf<Wochentag, List<StundenplanEintrag>>() }
    var stundenplanLeer by remember { mutableStateOf(false) }
    val reihenfolge = remember { wochentagReihenfolgeAbHeute() }

    LaunchedEffect(vaultUri) {
        eintraegeProTag.clear()
        stundenplanLeer = false
        if (vaultUri != null) {
            val repository = VaultRepository(context)
            val rohplan = repository.loadStundenplan(vaultUri)
            if (rohplan.values.all { it.isEmpty() }) {
                stundenplanLeer = true
                return@LaunchedEffect
            }
            for (tag in reihenfolge) {
                val faecher = (rohplan[tag] ?: emptyList()).distinct().take(3)
                val eintraege = coroutineScope {
                    faecher.map { fach ->
                        async {
                            val notiz = repository.letzteNotizFuerFach(vaultUri, fach)
                            StundenplanEintrag(
                                fach = fach,
                                letztesThema = notiz?.themen?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                                notizId = notiz?.documentId
                            )
                        }
                    }.awaitAll()
                }
                eintraegeProTag[tag] = eintraege
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stundenplan") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        if (vaultUri == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        if (stundenplanLeer) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Kein Stundenplan gefunden - entweder noch keine Organisation/Stundenplan.md im Vault, oder sie enthält keine Einträge.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            reihenfolge.forEach { tag ->
                item {
                    Text(
                        tag.anzeigeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val eintraege = eintraegeProTag[tag]
                if (eintraege == null) {
                    item {
                        Text(
                            "Lädt...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (eintraege.isEmpty()) {
                    item {
                        Text(
                            "Keine Fächer für diesen Tag im Stundenplan gefunden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(eintraege) { eintrag ->
                        GlowCard(onClick = { onFachKlick(eintrag.fach, eintrag.notizId) }) {
                            Text(eintrag.fach, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = eintrag.letztesThema?.let { "Zuletzt: $it" } ?: "Noch keine Notiz vorhanden",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
