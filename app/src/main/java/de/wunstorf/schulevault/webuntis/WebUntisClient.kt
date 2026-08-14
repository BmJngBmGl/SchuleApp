package de.wunstorf.schulevault.webuntis

import de.wunstorf.schulevault.data.Wochentag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
    /**
     * schulschlussZeiten: je Wochentag die spaeteste Endzeit ("HH:mm") der
     * an diesem Tag geladenen Stunden - Basis fuer den taeglichen
     * Hintergrund-Sync ("immer nach der letzten Stunde", siehe TagesSyncWorker).
     * Fehlt ein Tag (z. B. weil er beim Sync uebersprungen wurde), greift
     * dort der Default von 13:45.
     */
    data class Erfolg(
        val plan: Map<Wochentag, List<String>>,
        val schulschlussZeiten: Map<Wochentag, String> = emptyMap()
    ) : WebUntisErgebnis()
    data class Fehler(val meldung: String) : WebUntisErgebnis()
}

/** Eine einzelne WebUntis-Hausaufgabe, wie sie fuer den Import in den Vault-Tracker gebraucht wird. */
data class WebUntisHausaufgabe(
    val webuntisId: String,
    val fach: String,
    val text: String,
    val faelligAm: LocalDate
)

sealed class WebUntisHausaufgabenErgebnis {
    data class Erfolg(val hausaufgaben: List<WebUntisHausaufgabe>) : WebUntisHausaufgabenErgebnis()
    data class Fehler(val meldung: String) : WebUntisHausaufgabenErgebnis()
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
            val endpoint = jsonRpcEndpoint(server, schule)

            val loginErgebnis = rpcObjekt(
                endpoint, "authenticate",
                JSONObject().put("user", benutzername).put("password", passwort).put("client", CLIENT_NAME)
            )
            val personId = loginErgebnis.getInt("personId")
            val personType = loginErgebnis.optInt("personType", 5)

            // Rein diagnostisch: falls am Ende gar nichts abrufbar ist, hilft
            // die Info, welches Schuljahr der Server aktuell als "aktiv"
            // fuehrt, enorm bei der Ursachensuche (z. B. falls der Account
            // serverseitig noch am alten Schuljahr haengt, obwohl im Browser
            // schon der neue Stundenplan sichtbar ist). Muss VOR dem Logout
            // erfasst werden, danach ist die Session ungueltig.
            val schuljahrInfo = try {
                val ergebnis = rpcObjekt(endpoint, "getCurrentSchoolyear", JSONObject())
                val name = ergebnis.optString("name")
                val start = ergebnis.optInt("startDate")
                val ende = ergebnis.optInt("endDate")
                if (name.isNotBlank()) " Laut Server aktuell aktives Schuljahr: \"$name\" ($start–$ende)." else ""
            } catch (e: Exception) {
                ""
            }

            // "su" (Fach) in den Stunden-Rohdaten enthaelt nur eine ID, keinen
            // eingebetteten Namen (z. B. "su":[{"id":138}]) - der Klarname
            // muss ueber eine eigene Abfrage (getSubjects) aufgeloest werden.
            // Ein leeres "su" bedeutet laut Live-Test KEINE regulaere Stunde
            // (sondern z. B. eine schulweite Jahrgangsversammlung), solche
            // Eintraege werden beim Parsen uebersprungen. Schlaegt getSubjects
            // fehl (z. B. anderer Methodenname an dieser Schule), bleiben
            // Faecher einfach unaufgeloest statt den ganzen Sync abzubrechen.
            val fachNamen = ladeFachNamen(endpoint)

            // Naechste 5 Schultage ab heute statt starr "diese Kalenderwoche
            // Montag-Freitag" - so zeigt die App am Donnerstag/Freitag nicht
            // laenger schon vergangene Tage derselben Woche erneut an,
            // sondern greift automatisch in die Folgewoche vor (Wochenenden
            // werden uebersprungen).
            val heute = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val wochentage = naechsteFuenfSchultage(heute)

            // Tag-fuer-Tag statt der ganzen Woche auf einmal abfragen: WebUntis
            // lehnt Zeitraeume ab, die zwei verschiedene Schuljahre ueberschneiden
            // (z. B. kurz vor/nach den Sommerferien, wenn das neue Schuljahr noch
            // nicht angelegt ist) - einzelne nicht abrufbare Tage werden dann
            // uebersprungen statt den ganzen Sync abzubrechen. Der letzte
            // Fehlertext wird trotzdem gemerkt, um ihn zu zeigen, falls am
            // Ende gar nichts abrufbar war - sonst verschwindet die
            // eigentliche Fehlerursache spurlos hinter einer pauschalen
            // "kein Schuljahr aktiv"-Vermutung, die z. B. direkt nach
            // Schuljahresbeginn schlicht falsch sein kann.
            val alleEintraege = mutableListOf<Any?>()
            var letzterTagesFehler: String? = null
            var mindestensEinTagOhneFehler = false
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
                    mindestensEinTagOhneFehler = true
                    for (i in 0 until tagesErgebnis.length()) {
                        alleEintraege.add(tagesErgebnis.get(i))
                    }
                } catch (e: WebUntisApiException) {
                    letzterTagesFehler = e.message
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

            val geparst = parseStundenplan(JSONArray(alleEintraege), fachNamen)
            if (geparst.plan.isEmpty()) {
                val meldung = if (!mindestensEinTagOhneFehler && letzterTagesFehler != null) {
                    // Wirklich JEDER Tag ist mit Fehler abgebrochen - die
                    // konkrete WebUntis-Fehlermeldung ist aussagekraeftiger
                    // als eine geratene Ursache.
                    "Für keinen Tag dieser Woche waren Stunden abrufbar: $letzterTagesFehler"
                } else if (alleEintraege.isNotEmpty()) {
                    // WebUntis hat Rohdaten geliefert, aber keine davon hat
                    // die Parser-Filter ueberlebt (z. B. andere Feldnamen als
                    // angenommen) - der erste Rohdatensatz im Klartext ist
                    // die schnellste Diagnose, statt weiter zu raten.
                    val beispiel = (alleEintraege.firstOrNull() as? JSONObject)?.toString()?.take(600)
                    "WebUntis hat ${alleEintraege.size} Rohdatensätze für diese Woche geliefert, aber keiner " +
                        "enthielt eine auswertbare Stunde (unerwartetes Datenformat)." +
                        (beispiel?.let { " Beispiel: $it" } ?: "")
                } else {
                    "Login war erfolgreich, WebUntis hat aber für keinen Tag dieser Woche Stunden " +
                        "zurückgegeben - evtl. falsche Kalenderwoche oder Ferien.$schuljahrInfo"
                }
                WebUntisErgebnis.Fehler(meldung)
            } else {
                WebUntisErgebnis.Erfolg(geparst.plan, geparst.schulschlussZeiten)
            }
        } catch (e: WebUntisApiException) {
            WebUntisErgebnis.Fehler(e.message ?: "Unbekannter WebUntis-Fehler.")
        } catch (e: Exception) {
            WebUntisErgebnis.Fehler("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Laedt offene Hausaufgaben der naechsten 60 Tage ueber den (ebenfalls
     * inoffiziellen) REST-Endpunkt "api/homeworks/lessons" - anders als
     * getTimetable/authenticate/logout kein JSON-RPC-Aufruf, sondern ein
     * simples GET, das dieselbe Session (Cookie aus "authenticate") nutzt.
     * Format ist nicht 100%ig verifiziert (keine Live-Testmoeglichkeit hier) -
     * einzelne unerwartete Feldnamen fuehren zum Ueberspringen des jeweiligen
     * Eintrags statt eines Absturzes, der Nutzer testet gegen den echten
     * Account und meldet Abweichungen zurueck.
     */
    suspend fun ladeHausaufgaben(
        server: String,
        schule: String,
        benutzername: String,
        passwort: String
    ): WebUntisHausaufgabenErgebnis = withContext(Dispatchers.IO) {
        try {
            val endpoint = jsonRpcEndpoint(server, schule)
            rpcObjekt(
                endpoint, "authenticate",
                JSONObject().put("user", benutzername).put("password", passwort).put("client", CLIENT_NAME)
            )

            val heute = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val bis = heute.plus(DatePeriod(days = 60))
            val url = "https://$server/WebUntis/api/homeworks/lessons" +
                "?startDate=${jahrMonatTag(heute)}&endDate=${jahrMonatTag(bis)}"
            val antwort = restGet(url)
            val daten = antwort.optJSONObject("data")
                ?: throw WebUntisApiException("Unerwartete Antwort von WebUntis auf die Hausaufgaben-Abfrage (kein \"data\"-Feld).")

            val fachNachLessonId = mutableMapOf<Int, String>()
            daten.optJSONArray("lessons")?.let { lessons ->
                for (i in 0 until lessons.length()) {
                    val lesson = lessons.getJSONObject(i)
                    val name = lesson.optString("subject").ifBlank { lesson.optString("name") }
                    if (name.isNotBlank()) fachNachLessonId[lesson.optInt("id")] = name
                }
            }

            val hausaufgabenArray = daten.optJSONArray("homeworks") ?: JSONArray()
            val ergebnis = (0 until hausaufgabenArray.length()).mapNotNull { i ->
                val hw = hausaufgabenArray.getJSONObject(i)
                val faelligAm = datumZuLocalDate(hw.optInt("dueDate")) ?: return@mapNotNull null
                val text = hw.optString("text").ifBlank { "Hausaufgabe" }
                WebUntisHausaufgabe(
                    webuntisId = hw.optInt("id").toString(),
                    fach = fachNachLessonId[hw.optInt("lessonId")] ?: "WebUntis",
                    text = text,
                    faelligAm = faelligAm
                )
            }

            try {
                sendeRequest(endpoint, "logout", JSONObject())
            } catch (e: Exception) {
                // ignorieren, siehe Kommentar bei synchronisiereStundenplan
            }

            WebUntisHausaufgabenErgebnis.Erfolg(ergebnis)
        } catch (e: WebUntisApiException) {
            WebUntisHausaufgabenErgebnis.Fehler(e.message ?: "Unbekannter WebUntis-Fehler.")
        } catch (e: Exception) {
            WebUntisHausaufgabenErgebnis.Fehler("Verbindung fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Liefert id -> Anzeigename fuer alle Faecher (getSubjects) - "su" in den Stunden-Rohdaten enthaelt nur die ID. */
    private fun ladeFachNamen(endpoint: String): Map<Int, String> {
        return try {
            val faecher = rpcArray(endpoint, "getSubjects", JSONObject())
            (0 until faecher.length()).mapNotNull { i ->
                val fach = faecher.getJSONObject(i)
                val id = fach.optInt("id")
                val name = fach.optString("longName").ifBlank { fach.optString("longname") }
                    .ifBlank { fach.optString("name") }
                if (name.isBlank()) null else id to name
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun jsonRpcEndpoint(server: String, schule: String): String =
        "https://$server/WebUntis/jsonrpc.do?school=" + URLEncoder.encode(schule, "UTF-8")

    private fun restGet(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        val antwortText = connection.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
        return JSONObject(antwortText)
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

    private data class StundenEintrag(val wochentag: Wochentag, val startTime: Int, val endTime: Int, val fach: String)

    private data class GeparsterStundenplan(
        val plan: Map<Wochentag, List<String>>,
        val schulschlussZeiten: Map<Wochentag, String>
    )

    private fun parseStundenplan(stunden: JSONArray, fachNamen: Map<Int, String>): GeparsterStundenplan {
        val eintraege = (0 until stunden.length()).mapNotNull { index ->
            val stunde = stunden.getJSONObject(index)
            if (stunde.optString("code") == "cancelled") return@mapNotNull null

            val wochentag = datumZuWochentag(stunde.optInt("date")) ?: return@mapNotNull null

            // "su" enthaelt nur eine ID (z. B. {"id":138}), keinen
            // eingebetteten Namen - Aufloesung ueber die vorab per
            // getSubjects geladene Namenstabelle. Ein leeres "su" bedeutet
            // laut Live-Test KEINE regulaere Stunde (sondern z. B. eine
            // schulweite Jahrgangsversammlung mit riesiger "kl"-Liste) -
            // solche Eintraege werden bewusst NICHT ueber "kl" aufgeloest,
            // sondern uebersprungen.
            val fachId = stunde.optJSONArray("su")?.optJSONObject(0)?.optInt("id") ?: return@mapNotNull null
            val fach = fachNamen[fachId] ?: return@mapNotNull null

            StundenEintrag(wochentag, stunde.optInt("startTime"), stunde.optInt("endTime"), fach)
        }

        val plan = eintraege
            .groupBy { it.wochentag }
            .mapValues { (_, tageEintraege) -> tageEintraege.sortedBy { it.startTime }.map { it.fach } }

        // Schulschluss = spaeteste Endzeit der (nicht ausgefallenen) Stunden
        // des jeweiligen Tages - Basis fuer den taeglichen Hintergrund-Sync.
        val schulschlussZeiten = eintraege
            .groupBy { it.wochentag }
            .mapNotNull { (tag, tageEintraege) ->
                val spaetesteEndzeit = tageEintraege.filter { it.endTime > 0 }.maxOfOrNull { it.endTime }
                    ?: return@mapNotNull null
                tag to formatiereUhrzeit(spaetesteEndzeit)
            }
            .toMap()

        return GeparsterStundenplan(plan, schulschlussZeiten)
    }

    private fun formatiereUhrzeit(hhmm: Int): String =
        "%02d:%02d".format(hhmm / 100, hhmm % 100)

    private fun datumZuLocalDate(datum: Int): LocalDate? {
        if (datum == 0) return null
        val jahr = datum / 10000
        val monat = (datum / 100) % 100
        val tag = datum % 100
        return try {
            LocalDate(jahr, monat, tag)
        } catch (e: Exception) {
            null
        }
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

    /** Liefert die naechsten 5 Werktage (Mo-Fr) ab einschliesslich "ab" - ueberspringt Wochenenden, greift dafuer automatisch in die Folgewoche vor. */
    private fun naechsteFuenfSchultage(ab: LocalDate): List<LocalDate> {
        val tage = mutableListOf<LocalDate>()
        var datum = ab
        while (tage.size < 5) {
            if (datum.dayOfWeek != DayOfWeek.SATURDAY && datum.dayOfWeek != DayOfWeek.SUNDAY) {
                tage.add(datum)
            }
            datum = datum.plus(DatePeriod(days = 1))
        }
        return tage
    }
}
