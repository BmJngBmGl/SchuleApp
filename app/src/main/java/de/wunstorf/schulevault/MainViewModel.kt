package de.wunstorf.schulevault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.wunstorf.schulevault.data.Hausaufgabe
import de.wunstorf.schulevault.data.IServKalenderConfig
import de.wunstorf.schulevault.data.IServPreferences
import de.wunstorf.schulevault.data.IServTermin
import de.wunstorf.schulevault.data.Klausur
import de.wunstorf.schulevault.data.Termin
import de.wunstorf.schulevault.data.VaultNote
import de.wunstorf.schulevault.data.VaultPreferences
import de.wunstorf.schulevault.data.VaultRepository
import de.wunstorf.schulevault.data.WebUntisPreferences
import de.wunstorf.schulevault.iserv.IServSyncClient
import de.wunstorf.schulevault.notifications.NotificationScheduler
import de.wunstorf.schulevault.update.UpdateChecker
import de.wunstorf.schulevault.update.UpdateInfo
import de.wunstorf.schulevault.webuntis.WebUntisClient
import de.wunstorf.schulevault.webuntis.WebUntisErgebnis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class VaultUiState(
    val vaultUri: Uri? = null,
    val ladeLaeuft: Boolean = false,
    val termine: List<Termin> = emptyList(),
    val hausaufgaben: List<Hausaufgabe> = emptyList(),
    val klausuren: List<Klausur> = emptyList(),
    val faecher: List<String> = emptyList(),
    val fehlermeldung: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)
    private val preferences = VaultPreferences(application)
    private val iservPreferences = IServPreferences(application)
    private val webUntisPreferences = WebUntisPreferences(application)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private val _sucheErgebnisse = MutableStateFlow<List<VaultNote>>(emptyList())
    val sucheErgebnisse: StateFlow<List<VaultNote>> = _sucheErgebnisse.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _iservTermine = MutableStateFlow<List<IServTermin>>(emptyList())
    val iservTermine: StateFlow<List<IServTermin>> = _iservTermine.asStateFlow()

    private val _iservSyncLaeuft = MutableStateFlow(false)
    val iservSyncLaeuft: StateFlow<Boolean> = _iservSyncLaeuft.asStateFlow()

    private val _webUntisSyncLaeuft = MutableStateFlow(false)
    val webUntisSyncLaeuft: StateFlow<Boolean> = _webUntisSyncLaeuft.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.vaultUri.collect { gespeicherteUri ->
                val uri = gespeicherteUri?.let { Uri.parse(it) }
                _uiState.value = _uiState.value.copy(vaultUri = uri)
                if (uri != null) ladeVaultDaten(uri)
            }
        }
        // Einmal pro App-Start kurz im Hintergrund auf ein neueres Release
        // pruefen - schlaegt lautlos fehl (z. B. offline), stoert also nie
        // den normalen App-Start.
        viewModelScope.launch {
            val neuesteVersion = UpdateChecker.neuesteVersionPruefen()
            if (neuesteVersion != null && neuesteVersion.versionName != BuildConfig.VERSION_NAME) {
                _updateInfo.value = neuesteVersion
            }
        }
        iservTermineNeuLaden()
    }

    /** Laedt alle konfigurierten IServ-Kalender neu - einmal beim Start und erneut bei jedem manuellen "Neu laden". */
    private fun iservTermineNeuLaden() {
        viewModelScope.launch {
            val kalenderListe = iservPreferences.kalenderListe.first()
            val benutzername = iservPreferences.benutzername.first()
            val passwort = iservPreferences.passwort.first()
            if (kalenderListe.isEmpty() || benutzername.isNullOrBlank() || passwort.isNullOrBlank()) {
                return@launch
            }
            _iservSyncLaeuft.value = true
            _iservTermine.value = IServSyncClient.ladeTermine(kalenderListe, benutzername, passwort)
            _iservSyncLaeuft.value = false
        }
    }

    fun iservEinstellungenSpeichern(
        benutzername: String,
        passwort: String,
        kalenderListe: List<IServKalenderConfig>
    ) {
        viewModelScope.launch {
            iservPreferences.speichern(benutzername, passwort, kalenderListe)
            iservTermineNeuLaden()
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
                val hausaufgaben = repository.loadAlleHausaufgaben(uri)
                val klausuren = repository.loadAlleKlausuren(uri)
                val faecher = repository.listTopLevelFolders(uri)
                    .mapNotNull { it.name }
                    .filter { it != "Termine" && it != "Hausaufgaben" && it != "Klausuren" } // haben eigene Bereiche in der UI
                _uiState.value = _uiState.value.copy(
                    ladeLaeuft = false,
                    termine = termine,
                    hausaufgaben = hausaufgaben,
                    klausuren = klausuren,
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
        iservTermineNeuLaden()
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

    /** Blendet den Update-Dialog aus - fuer "Später" ebenso wie direkt nach dem Start des Downloads. */
    fun updateIgnorieren() {
        _updateInfo.value = null
    }

    fun neueHausaufgabeAnlegen(
        fach: String,
        titel: String,
        faelligAm: LocalDate,
        notizText: String,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.neueHausaufgabeSpeichern(uri, fach, titel, faelligAm, notizText)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    /** Schneller Erledigt-Toggle direkt aus der Übersichtsliste, ohne den Eingabe-Screen zu öffnen. */
    fun hausaufgabeErledigtSetzen(hausaufgabe: Hausaufgabe, erledigt: Boolean) {
        val uri = _uiState.value.vaultUri ?: return
        viewModelScope.launch {
            repository.hausaufgabeAktualisieren(
                uri, hausaufgabe.note, hausaufgabe.fach, hausaufgabe.titel,
                hausaufgabe.faelligAm, erledigt, hausaufgabe.notizText
            )
            ladeVaultDaten(uri)
        }
    }

    fun hausaufgabeAktualisieren(
        hausaufgabe: Hausaufgabe,
        fach: String,
        titel: String,
        faelligAm: LocalDate,
        notizText: String,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.hausaufgabeAktualisieren(
                uri, hausaufgabe.note, fach, titel, faelligAm, hausaufgabe.erledigt, notizText
            )
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    fun hausaufgabeLoeschen(hausaufgabe: Hausaufgabe, onFertig: (Boolean) -> Unit) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.hausaufgabeLoeschen(uri, hausaufgabe.note)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    fun neueKlausurAnlegen(
        fach: String,
        titel: String,
        datum: LocalDate,
        relevanteThemen: List<String>,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.neueKlausurSpeichern(uri, fach, titel, datum, relevanteThemen)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    fun klausurAktualisieren(
        klausur: Klausur,
        fach: String,
        titel: String,
        datum: LocalDate,
        relevanteThemen: List<String>,
        onFertig: (Boolean) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.klausurAktualisieren(uri, klausur.note, fach, titel, datum, relevanteThemen)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    fun klausurLoeschen(klausur: Klausur, onFertig: (Boolean) -> Unit) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false)
        viewModelScope.launch {
            val erfolgreich = repository.klausurLoeschen(uri, klausur.note)
            if (erfolgreich) ladeVaultDaten(uri)
            onFertig(erfolgreich)
        }
    }

    /**
     * Loggt sich bei WebUntis ein, laedt den Stundenplan der aktuellen Woche
     * und schreibt ihn nach Organisation/Stundenplan.md. Manueller Vorgang
     * (kein Auto-Sync beim Start), da Login+Abruf schwerer ist als der reine
     * IServ-ICS-GET und sich Stundenplaene selten kurzfristig aendern.
     */
    fun webUntisSynchronisieren(
        server: String,
        schule: String,
        benutzername: String,
        passwort: String,
        onFertig: (erfolgreich: Boolean, meldung: String?) -> Unit
    ) {
        val uri = _uiState.value.vaultUri ?: return onFertig(false, null)
        viewModelScope.launch {
            webUntisPreferences.speichern(server, schule, benutzername, passwort)
            _webUntisSyncLaeuft.value = true
            when (val ergebnis = WebUntisClient.synchronisiereStundenplan(server, schule, benutzername, passwort)) {
                is WebUntisErgebnis.Erfolg -> {
                    val gespeichert = repository.speichereStundenplan(uri, ergebnis.plan)
                    onFertig(
                        gespeichert,
                        if (gespeichert) null else "Stundenplan geladen, aber Speichern im Vault fehlgeschlagen."
                    )
                }
                is WebUntisErgebnis.Fehler -> onFertig(false, ergebnis.meldung)
            }
            _webUntisSyncLaeuft.value = false
        }
    }
}
