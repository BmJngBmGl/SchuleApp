package de.wunstorf.schulevault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    val benutzername: Flow<String?> = context.iservDataStore.data.map { it[benutzernameKey] }
    val passwort: Flow<String?> = context.iservDataStore.data.map { it[passwortKey] }
    val kalenderListe: Flow<List<IServKalenderConfig>> = context.iservDataStore.data.map { prefs ->
        prefs[kalenderListeKey]?.let { parseKalenderListe(it) } ?: emptyList()
    }

    suspend fun speichern(benutzername: String, passwort: String, kalenderListe: List<IServKalenderConfig>) {
        context.iservDataStore.edit {
            it[benutzernameKey] = benutzername
            it[passwortKey] = passwort
            it[kalenderListeKey] = serialisiereKalenderListe(kalenderListe)
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
