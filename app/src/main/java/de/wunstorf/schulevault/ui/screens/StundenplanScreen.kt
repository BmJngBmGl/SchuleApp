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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StundenplanScreen(
    vaultUri: Uri?,
    onZurueck: () -> Unit
) {
    val context = LocalContext.current
    var plan by remember { mutableStateOf<Map<Wochentag, List<StundenplanEintrag>>?>(null) }

    LaunchedEffect(vaultUri) {
        if (vaultUri != null) {
            val repository = VaultRepository(context)
            val rohplan = repository.loadStundenplan(vaultUri)
            plan = Wochentag.entries.associateWith { tag ->
                (rohplan[tag] ?: emptyList()).distinct().take(3).map { fach ->
                    StundenplanEintrag(fach, repository.letztesThemaFuerFach(vaultUri, fach))
                }
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
        val aktuellerPlan = plan
        if (aktuellerPlan == null) {
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
            Wochentag.entries.forEach { tag ->
                val eintraege = aktuellerPlan[tag].orEmpty()
                item {
                    Text(
                        tag.anzeigeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (eintraege.isEmpty()) {
                    item {
                        Text(
                            "Keine Fächer für diesen Tag im Stundenplan gefunden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(eintraege) { eintrag ->
                        GlowCard {
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
