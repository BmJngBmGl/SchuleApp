package de.wunstorf.schulevault.sync

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.wunstorf.schulevault.data.IServPreferences
import de.wunstorf.schulevault.data.VaultPreferences
import de.wunstorf.schulevault.data.VaultRepository
import de.wunstorf.schulevault.data.WebUntisPreferences
import de.wunstorf.schulevault.iserv.IServSyncClient
import de.wunstorf.schulevault.webuntis.WebUntisClient
import de.wunstorf.schulevault.webuntis.WebUntisErgebnis
import de.wunstorf.schulevault.webuntis.WebUntisHausaufgabenErgebnis
import kotlinx.coroutines.flow.first

/**
 * Ein einzelner Lauf des taeglichen Hintergrund-Syncs (WebUntis-Stundenplan
 * + WebUntis-Hausaufgaben + IServ-Kalender), ausgeloest durch
 * TagesSyncScheduler jeweils um 8:00 und nach Schulschluss. Plant am Ende -
 * per "finally", also auch wenn ein Sync-Schritt scheitert - selbst den
 * naechsten Lauf ein, damit die Kette nie abreisst.
 */
class TagesSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        try {
            val vaultUri = VaultPreferences(applicationContext).vaultUri.first()?.let { Uri.parse(it) }
            val repository = VaultRepository(applicationContext)
            if (vaultUri != null && repository.hatGueltigeBerechtigung(vaultUri)) {
                try {
                    webUntisSync(vaultUri, repository)
                } catch (e: Exception) {
                    // einzelner fehlgeschlagener Sync-Schritt soll den Rest (und die Neuplanung) nicht verhindern
                }
                try {
                    iservSync()
                } catch (e: Exception) {
                    // s. o.
                }
            }
        } finally {
            TagesSyncScheduler.planeNaechstenLauf(applicationContext)
        }
        return Result.success()
    }

    private suspend fun webUntisSync(vaultUri: Uri, repository: VaultRepository) {
        val webUntisPreferences = WebUntisPreferences(applicationContext)
        val server = webUntisPreferences.server.first()
        val schule = webUntisPreferences.schule.first()
        val benutzername = webUntisPreferences.benutzername.first()
        val passwort = webUntisPreferences.passwort.first()
        if (server.isNullOrBlank() || schule.isNullOrBlank() || benutzername.isNullOrBlank() || passwort.isNullOrBlank()) {
            return
        }

        val stundenplanErgebnis = WebUntisClient.synchronisiereStundenplan(server, schule, benutzername, passwort)
        if (stundenplanErgebnis is WebUntisErgebnis.Erfolg) {
            repository.speichereStundenplan(vaultUri, stundenplanErgebnis.plan)
            webUntisPreferences.schulschlussZeitenSpeichern(stundenplanErgebnis.schulschlussZeiten)
        }

        val hausaufgabenErgebnis = WebUntisClient.ladeHausaufgaben(server, schule, benutzername, passwort)
        if (hausaufgabenErgebnis is WebUntisHausaufgabenErgebnis.Erfolg) {
            repository.webUntisHausaufgabenImportieren(vaultUri, hausaufgabenErgebnis.hausaufgaben)
        }
    }

    private suspend fun iservSync() {
        val iservPreferences = IServPreferences(applicationContext)
        val kalenderListe = iservPreferences.kalenderListe.first()
        val benutzername = iservPreferences.benutzername.first()
        val passwort = iservPreferences.passwort.first()
        if (kalenderListe.isEmpty() || benutzername.isNullOrBlank() || passwort.isNullOrBlank()) return

        val termine = IServSyncClient.ladeTermine(kalenderListe, benutzername, passwort)
        iservPreferences.cacheSpeichern(termine)
    }
}
