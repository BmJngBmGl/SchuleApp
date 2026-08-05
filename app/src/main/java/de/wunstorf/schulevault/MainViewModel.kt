package de.wunstorf.schulevault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.wunstorf.schulevault.data.Termin
import de.wunstorf.schulevault.data.VaultNote
import de.wunstorf.schulevault.data.VaultPreferences
import de.wunstorf.schulevault.data.VaultRepository
import de.wunstorf.schulevault.notifications.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class VaultUiState(
    val vaultUri: Uri? = null,
    val ladeLaeuft: Boolean = false,
    val termine: List<Termin> = emptyList(),
    val faecher: List<String> = emptyList(),
    val fehlermeldung: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)
    private val preferences = VaultPreferences(application)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _sucheErgebnisse = MutableStateFlow<List<VaultNote>>(emptyList())
    val sucheErgebnisse: StateFlow<List<VaultNote>> = _sucheErgebnisse.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.vaultUri.collect { gespeicherteUri ->
                val uri = gespeicherteUri?.let { Uri.parse(it) }
                _uiState.value = _uiState.value.copy(vaultUri = uri)
                if (uri != null) ladeVaultDaten(uri)
            }
        }
    }

    fun vaultOrdnerAusgewaehlt(uri: Uri) {
        viewModelScope.launch {
            preferences.setVaultUri(uri.toString())
            // Persistente Berechtigung wird bereits in MainActivity direkt
            // nach der Auswahl erteilt (contentResolver.takePersistableUriPermission).
            ladeVaultDaten(uri)
        }
    }

    private fun ladeVaultDaten(uri: Uri) {
        viewModelScope.launch {
            if (!repository.hatGueltigeBerechtigung(uri)) {
                // Berechtigung wurde vom System entzogen (z. B. weil der
                // Vault-Ordner umbenannt/verschoben wurde) - zurueck zum
                // Auswahl-Screen statt mit einer SecurityException abzustuerzen.
                preferences.clearVaultUri()
                _uiState.value = _uiState.value.copy(
                    vaultUri = null,
                    ladeLaeuft = false,
                    fehlermeldung = "Zugriff auf den Vault-Ordner wurde entzogen. Bitte erneut auswählen."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(ladeLaeuft = true, fehlermeldung = null)
            try {
                val termine = repository.loadAlleTermine(uri)
                val faecher = repository.listTopLevelFolders(uri)
                    .mapNotNull { it.name }
                    .filter { it != "Termine" } // Termine hat eigenen Bereich in der UI
                _uiState.value = _uiState.value.copy(
                    ladeLaeuft = false,
                    termine = termine,
                    faecher = faecher
                )
                // Fuer alle geladenen (und noch in der Zukunft liegenden)
                // Termine sofort die Erinnerungen (neu) einplanen - so
                // bleiben Erinnerungen auch nach App-Neustart konsistent.
                val heute = Clock.System.todayIn(TimeZone.currentSystemDefault())
                termine.filter { it.datum >= heute }.forEach { termin ->
                    NotificationScheduler.planeErinnerungen(getApplication(), termin)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    ladeLaeuft = false,
                    fehlermeldung = "Vault konnte nicht gelesen werden: ${e.message}"
                )
            }
        }
    }

    fun neuenTerminAnlegen(
        titel: String,
        datum: LocalDate,
        istSchulisch: Boolean,
        notizText: String,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.neuenTerminSpeichern(uri, titel, datum, istSchulisch, notizText)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    fun neueLernnotizAnlegen(
        fach: String,
        titel: String,
        themen: List<String>,
        notizText: String,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val heute = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val erfolgreich = repository.neueLernnotizSpeichern(uri, fach, titel, themen, notizText, heute)
            onFertig(erfolgreich)
        }
    }

    fun ordnerNeuLaden() {
        _uiState.value.vaultUri?.let { ladeVaultDaten(it) }
    }

    fun terminAktualisieren(
        termin: Termin,
        titel: String,
        datum: LocalDate,
        istSchulisch: Boolean,
        notizText: String,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.terminAktualisieren(uri, termin.note, titel, datum, istSchulisch, notizText)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    fun terminLoeschen(termin: Termin, onFertig: (Boolean) -> Unit) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.terminLoeschen(uri, termin.note)
            if (erfolgreich) {
                NotificationScheduler.storniereErinnerungen(
                    getApplication(),
                    "${termin.note.folderPath}/${termin.note.fileName}"
                )
                ladeVaultDaten(uri)
            }
            onFertig(erfolgreich)
        }
    }

    fun sucheNotizen(query: String) {
        val uri = _uiState.value.vaultUri
        if (uri == null || query.isBlank()) {
            _sucheErgebnisse.value = emptyList()
            return
        }
        viewModelScope.launch {
            _sucheErgebnisse.value = repository.sucheAlleNotizen(uri, query)
        }
    }
}
