package de.wunstorf.schulevault.iserv

import android.util.Base64
import de.wunstorf.schulevault.data.IServKalenderConfig
import de.wunstorf.schulevault.data.IServTermin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Laedt alle konfigurierten IServ-ICS-Kalender per HTTP-Basic-Auth ab. Jeder
 * Kalender wird einzeln in try/catch abgerufen, damit ein einzelner nicht
 * erreichbarer oder falsch konfigurierter Kalender nicht die anderen
 * mitreisst - im schlimmsten Fall liefert ein Kalender einfach nichts,
 * statt die ganze Synchronisierung crashen zu lassen.
 */
object IServSyncClient {

    suspend fun ladeTermine(
        kalenderListe: List<IServKalenderConfig>,
        benutzername: String,
        passwort: String
    ): List<IServTermin> = withContext(Dispatchers.IO) {
        kalenderListe.flatMap { kalender ->
            try {
                ladeEinzelnenKalender(kalender, benutzername, passwort)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun ladeEinzelnenKalender(
        kalender: IServKalenderConfig,
        benutzername: String,
        passwort: String
    ): List<IServTermin> {
        val connection = URL(kalender.icsUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        val zugangsdaten = Base64.encodeToString(
            "$benutzername:$passwort".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        connection.setRequestProperty("Authorization", "Basic $zugangsdaten")

        return connection.inputStream.use { input ->
            val icsText = input.bufferedReader(Charsets.UTF_8).readText()
            IcsParser.parse(icsText, kalender.label)
        }
    }
}
