package de.wunstorf.schulevault.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

private data class RohBlock(val fach: String, val doppelstunde: Boolean)

/**
 * Fasst nur AUFEINANDERFOLGENDE gleiche Perioden zu einer Doppelstunde
 * zusammen (naiv per List.distinct() wuerde auch nicht benachbarte
 * Vorkommen desselben Fachs verschmelzen - z. B. Mathe als Doppelstunde am
 * Morgen UND als separate Einzelstunde am Nachmittag wuerden dann faelschlich
 * zu einem einzigen Eintrag zusammenfallen und die Einzelstunde wuerde in
 * der Anzeige komplett verschwinden).
 */
private fun gruppiereBloecke(faecher: List<String>): List<RohBlock> {
    val bloecke = mutableListOf<RohBlock>()
    var i = 0
    while (i < faecher.size) {
        val fach = faecher[i]
        if (i + 1 < faecher.size && faecher[i + 1] == fach) {
            bloecke.add(RohBlock(fach, doppelstunde = true))
            i += 2
        } else {
            bloecke.add(RohBlock(fach, doppelstunde = false))
            i += 1
        }
    }
    return bloecke
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
                val bloecke = gruppiereBloecke(rohplan[tag] ?: emptyList())
                val eintraege = coroutineScope {
                    // Notiz je EINZIGARTIGEM Fach nur einmal laden, auch wenn
                    // es am Tag mehrfach vorkommt (Doppel- und Einzelstunde) -
                    // spart unnoetige, doppelte IO-Zugriffe.
                    val notizNachFach = bloecke.map { it.fach }.distinct()
                        .associateWith { fach -> async { repository.letzteNotizFuerFach(vaultUri, fach) } }
                        .mapValues { (_, deferred) -> deferred.await() }

                    bloecke.map { block ->
                        val notiz = notizNachFach[block.fach]
                        StundenplanEintrag(
                            fach = block.fach,
                            letztesThema = notiz?.themen?.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            notizId = notiz?.documentId,
                            doppelstunde = block.doppelstunde
                        )
                    }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(eintrag.fach, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = eintrag.letztesThema?.let { "Zuletzt: $it" } ?: "Noch keine Notiz vorhanden",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (eintrag.doppelstunde) "Doppelstunde" else "Einzelstunde",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
