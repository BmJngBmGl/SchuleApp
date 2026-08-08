package de.wunstorf.schulevault.webuntis

import de.wunstorf.schulevault.data.Wochentag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ergebnis eines Sync-Versuchs - im Fehlerfall mit einer konkreten, in der UI anzeigbaren Meldung statt nur "hat nicht geklappt". */
sealed class WebUntisErgebnis {
    data class Erfolg(val plan: Map<Wochentag, List<String>>) : WebUntisErgebnis()
    data class Fehler(val meldung: String) : WebUntisErgebnis()
}

private class WebUntisApiException(message: String) : Exception(message)

/**
 * Client fuer die inoffizielle WebUntis-JSON-RPC-API - von WebUntis nicht
 * offiziell veroeffentlicht, aber seit Jahren stabil und Basis etablierter
 * Community-Tools (z. B. python-webuntis).
 */
object WebUntisClient {

    private const val CLIENT_NAME = "SchuleVault"

    init {
        // HttpURLConnection uebernimmt Session-Cookies (JSESSIONID) automatisch,
        // sobald ein CookieManager als Default gesetzt ist - kein manuelles
        // Set-Cookie/Cookie-Header-Handling noetig.
        if (CookieHandler.getDefault() !is CookieManager) {
            CookieHandler.setDefault(CookieManager(null, CookiePolicy.ACCEPT_ALL))
        }
    }

    suspend fun synchronisiereStundenplan(
        server: String,
        schule: String,
        benutzername: String,
        passwort: String
    ): WebUntisErgebnis = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://$server/WebUntis/jsonrpc.do?school=" +
                URLEncoder.encode(schule, "UTF-8")

            val loginErgebnis = rpcObjekt(
                endpoint, "authenticate",
                JSONObject().put("user", benutzername).put("password", passwort).put("client", CLIENT_NAME)
            )
            val personId = loginErgebnis.getInt("personId")
            val personType = loginErgebnis.optInt("personType", 5)

            val heute = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val montag = heute.minus(DatePeriod(days = heute.dayOfWeek.value - 1))
            val wochentage = (0..4).map { montag.plus(DatePeriod(days = it)) }

            // Tag-fuer-Tag statt der ganzen Woche auf einmal abfragen: WebUntis
            // lehnt Zeitraeume ab, die zwei verschiedene Schuljahre ueberschneiden
            // (z. B. kurz vor/nach den Sommerferien, wenn das neue Schuljahr noch
            // nicht angelegt ist) - einzelne nicht abrufbare Tage werden dann
            // uebersprungen statt den ganzen Sync abzubrechen.
            val alleEintraege = mutableListOf<Any?>()
            for (tag in wochentage) {
                try {
                    val tagesErgebnis = rpcArray(
                        endpoint, "getTimetable",
                        JSONObject()
                            .put("id", personId)
                            .put("type", personType)
                            .put("startDate", jahrMonatTag(tag))
                            .put("endDate", jahrMonatTag(tag))
                    )
                    for (i in 0 until tagesErgebnis.length()) {
                        alleEintraege.add(tagesErgebnis.get(i))
                    }
                } catch (e: WebUntisApiException) {
                    // einzelner Tag nicht abrufbar (z. B. ausserhalb eines Schuljahres) - ueberspringen
                }
            }

            try {
                // logout liefert kein JSON-Objekt als Ergebnis (z. B. nur "true") -
                // rpcObjekt waere hier zu streng. Ein fehlgeschlagener Logout ist
                // ausserdem irrelevant fuer das eigentliche Sync-Ergebnis, die
                // Session laeuft ohnehin von selbst ab.
                sendeRequest(endpoint, "logout", JSONObject())
            } catch (e: Exception) {
                // ignorieren
            }

            val plan = parseStundenplan(JSONArray(alleEintraege))
            if (plan.isEmpty()) {
                WebUntisErgebnis.Fehler(
                    "Login war erfolgreich, aber für keinen Tag dieser Woche waren Stunden abrufbar " +
                        "- vermutlich ist gerade kein Schuljahr aktiv (z. B. in den Ferien)."
                )
            } else {
                WebUntisErgebnis.Erfolg(plan)
            }
        } catch (e: WebUntisApiException) {
            WebUntisErgebnis.Fehler(e.message ?: "Unbekannter WebUntis-Fehler.")
        } catch (e: Exception) {
            WebUntisErgebnis.Fehler("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun rpcObjekt(endpoint: String, method: String, params: JSONObject): JSONObject =
        sendeRequest(endpoint, method, params).optJSONObject("result")
            ?: throw WebUntisApiException("Unerwartete Antwort von WebUntis auf \"$method\" (kein Ergebnis-Feld).")

    private fun rpcArray(endpoint: String, method: String, params: JSONObject): JSONArray =
        sendeRequest(endpoint, method, params).optJSONArray("result")
            ?: throw WebUntisApiException("Unerwartete Antwort von WebUntis auf \"$method\" (kein Ergebnis-Feld).")

    private fun sendeRequest(endpoint: String, method: String, params: JSONObject): JSONObject {
        val body = JSONObject()
            .put("id", CLIENT_NAME)
            .put("method", method)
            .put("params", params)
            .put("jsonrpc", "2.0")

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val antwortText = try {
            connection.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
        } catch (e: IOException) {
            // Bei HTTP-Fehlerstatus (4xx/5xx) liefert WebUntis den JSON-Fehlertext
            // oft trotzdem im errorStream statt im normalen inputStream.
            val fehlerText = connection.errorStream?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            if (fehlerText.isNullOrBlank()) {
                throw WebUntisApiException("HTTP-Fehler ${connection.responseCode} bei \"$method\".")
            }
            fehlerText
        }

        val antwort = JSONObject(antwortText)
        if (antwort.has("error")) {
            val fehler = antwort.getJSONObject("error")
            throw WebUntisApiException(
                fehler.optString("message").ifBlank {
                    "WebUntis-Fehler bei \"$method\" (Code ${fehler.optInt("code")})."
                }
            )
        }
        return antwort
    }

    private data class StundenEintrag(val wochentag: Wochentag, val startTime: Int, val fach: String)

    private fun parseStundenplan(stunden: JSONArray): Map<Wochentag, List<String>> {
        val eintraege = (0 until stunden.length()).mapNotNull { index ->
            val stunde = stunden.getJSONObject(index)
            if (stunde.optString("code") == "cancelled") return@mapNotNull null

            val wochentag = datumZuWochentag(stunde.optInt("date")) ?: return@mapNotNull null
            val faecher = stunde.optJSONArray("su")
            if (faecher == null || faecher.length() == 0) return@mapNotNull null
            val fachObjekt = faecher.getJSONObject(0)
            val fach = fachObjekt.optString("longname").ifBlank { fachObjekt.optString("name") }
            if (fach.isBlank()) return@mapNotNull null

            StundenEintrag(wochentag, stunde.optInt("startTime"), fach)
        }

        return eintraege
            .groupBy { it.wochentag }
            .mapValues { (_, tageEintraege) -> tageEintraege.sortedBy { it.startTime }.map { it.fach } }
    }

    private fun datumZuWochentag(datum: Int): Wochentag? {
        if (datum == 0) return null
        val jahr = datum / 10000
        val monat = (datum / 100) % 100
        val tag = datum % 100
        return try {
            when (LocalDate(jahr, monat, tag).dayOfWeek.value) {
                1 -> Wochentag.MONTAG
                2 -> Wochentag.DIENSTAG
                3 -> Wochentag.MITTWOCH
                4 -> Wochentag.DONNERSTAG
                5 -> Wochentag.FREITAG
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun jahrMonatTag(datum: LocalDate): Int =
        "%04d%02d%02d".format(datum.year, datum.monthNumber, datum.dayOfMonth).toInt()
}
