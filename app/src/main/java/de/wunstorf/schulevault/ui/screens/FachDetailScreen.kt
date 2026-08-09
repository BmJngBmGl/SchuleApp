package de.wunstorf.schulevault.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.wunstorf.schulevault.data.VaultNote
import de.wunstorf.schulevault.data.VaultRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FachDetailScreen(
    fach: String,
    vaultUri: Uri?,
    onZurueck: () -> Unit,
    fokusNotizId: String? = null
) {
    val context = LocalContext.current
    val repository = remember { VaultRepository(context) }
    val scope = rememberCoroutineScope()
    var notizen by remember { mutableStateOf<List<VaultNote>?>(null) }
    var ausgeklappteNotizen by remember {
        mutableStateOf(fokusNotizId?.let { setOf(it) } ?: emptySet())
    }
    val listState = rememberLazyListState()

    LaunchedEffect(fach, vaultUri) {
        if (vaultUri != null) {
            val geladeneNotizen = repository.listNotesInFolder(vaultUri, fach)
            notizen = geladeneNotizen
            // Direktsprung vom Stundenplan: die verwandte Notiz kommt bereits
            // aufgeklappt in den sichtbaren Bereich, statt dass man sie erst
            // in der Liste suchen muss.
            if (fokusNotizId != null) {
                val index = geladeneNotizen.indexOfFirst { it.documentId == fokusNotizId }
                if (index >= 0) listState.scrollToItem(index)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fach) },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        val aktuelleNotizen = notizen
        if (aktuelleNotizen == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (aktuelleNotizen.isEmpty()) {
                item { Text("Keine Notizen in diesem Ordner gefunden.") }
            }
            items(aktuelleNotizen) { notiz ->
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(notiz.title, style = MaterialTheme.typography.titleMedium)
                            if (notiz.themen.isNotEmpty()) {
                                Text(
                                    notiz.themen.joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Checkbox(
                                checked = notiz.verstanden,
                                onCheckedChange = { verstanden ->
                                    if (vaultUri != null) {
                                        scope.launch {
                                            repository.notizVerstandenSetzen(vaultUri, notiz, verstanden)
                                            notizen = repository.listNotesInFolder(vaultUri, fach)
                                        }
                                    }
                                }
                            )
                            Text(
                                "Verstanden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
