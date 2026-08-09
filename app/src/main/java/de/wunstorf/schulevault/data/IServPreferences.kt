package de.wunstorf.schulevault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import org.json.JSONArray
import org.json.JSONObject

private val Context.iservDataStore by preferencesDataStore(name = "iserv_settings")

/**
 * Speichert die IServ-Zugangsdaten (Benutzername/Passwort gemeinsam fuer
 * alle Kalender) sowie die Liste der abonnierten Kalender. Bewusst in einem
 * eigenen DataStore (nicht zusammen mit VaultPreferences), damit sich die
 * Backup-Ausschlussregel in data_extraction_rules.xml gezielt nur auf diese
 * Datei bezieht - hier steht ein echtes Passwort drin.
 */
class IServPreferences(private val context: Context) {

    private val benutzernameKey = stringPreferencesKey("benutzername")
    private val passwortKey = stringPreferencesKey("passwort")
    private val kalenderListeKey = stringPreferencesKey("kalender_liste_json")
    private val cacheTermineKey = stringPreferencesKey("cache_termine_json")

    val benutzername: Flow<String?> = context.iservDataStore.data.map { it[benutzernameKey] }
    val passwort: Flow<String?> = context.iservDataStore.data.map { it[passwortKey] }
    val kalenderListe: Flow<List<IServKalenderConfig>> = context.iservDataStore.data.map { prefs ->
        prefs[kalenderListeKey]?.let { parseKalenderListe(it) } ?: emptyList()
    }

    /**
     * Zuletzt erfolgreich geladene Termine - wird sowohl beim App-Start
     * (sofortige Anzeige, bevor der Live-Sync durch ist) als auch vom
     * taeglichen Hintergrund-Sync geschrieben, damit letzterer eine sichtbare
     * Wirkung hat (IServTermin selbst hat sonst keinen Vault-Bezug).
     */
    val cachedTermine: Flow<List<IServTermin>> = context.iservDataStore.data.map { prefs ->
        prefs[cacheTermineKey]?.let { parseCacheTermine(it) } ?: emptyList()
    }

    suspend fun speichern(benutzername: String, passwort: String, kalenderListe: List<IServKalenderConfig>) {
        context.iservDataStore.edit {
            it[benutzernameKey] = benutzername
            it[passwortKey] = passwort
            it[kalenderListeKey] = serialisiereKalenderListe(kalenderListe)
        }
    }

    suspend fun cacheSpeichern(termine: List<IServTermin>) {
        context.iservDataStore.edit { it[cacheTermineKey] = serialisiereCacheTermine(termine) }
    }

    private fun serialisiereCacheTermine(termine: List<IServTermin>): String {
        val array = JSONArray()
        termine.forEach { termin ->
            array.put(JSONObject().apply {
                put("uid", termin.uid)
                put("titel", termin.titel)
                put("datum", termin.datum.toString())
                put("kalenderLabel", termin.kalenderLabel)
            })
        }
        return array.toString()
    }

    private fun parseCacheTermine(json: String): List<IServTermin> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.getJSONObject(index)
                IServTermin(
                    uid = obj.getString("uid"),
                    titel = obj.getString("titel"),
                    datum = LocalDate.parse(obj.getString("datum")),
                    kalenderLabel = obj.getString("kalenderLabel")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serialisiereKalenderListe(liste: List<IServKalenderConfig>): String {
        val array = JSONArray()
        liste.forEach { kalender ->
            array.put(JSONObject().apply {
                put("label", kalender.label)
                put("icsUrl", kalender.icsUrl)
            })
        }
        return array.toString()
    }

    private fun parseKalenderListe(json: String): List<IServKalenderConfig> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                IServKalenderConfig(label = obj.getString("label"), icsUrl = obj.getString("icsUrl"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
