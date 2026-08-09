package de.wunstorf.schulevault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.webUntisDataStore by preferencesDataStore(name = "webuntis_settings")

/**
 * Speichert die WebUntis-Zugangsdaten fuer den Stundenplan-Sync. Bewusst in
 * einem eigenen DataStore (nicht zusammen mit IServPreferences), damit sich
 * die Backup-Ausschlussregel in data_extraction_rules.xml gezielt nur auf
 * diese Datei bezieht - hier steht ein echtes Passwort drin.
 */
class WebUntisPreferences(private val context: Context) {

    private val serverKey = stringPreferencesKey("server")
    private val schuleKey = stringPreferencesKey("schule")
    private val benutzernameKey = stringPreferencesKey("benutzername")
    private val passwortKey = stringPreferencesKey("passwort")
    private val schulschlussZeitenKey = stringPreferencesKey("schulschluss_zeiten_json")

    val server: Flow<String?> = context.webUntisDataStore.data.map { it[serverKey] }
    val schule: Flow<String?> = context.webUntisDataStore.data.map { it[schuleKey] }
    val benutzername: Flow<String?> = context.webUntisDataStore.data.map { it[benutzernameKey] }
    val passwort: Flow<String?> = context.webUntisDataStore.data.map { it[passwortKey] }

    /** Je Wochentag die zuletzt aus WebUntis erfasste Schulschluss-Zeit ("HH:mm") - Basis fuer TagesSyncWorker. */
    val schulschlussZeiten: Flow<Map<Wochentag, String>> = context.webUntisDataStore.data.map { prefs ->
        prefs[schulschlussZeitenKey]?.let { parseSchulschlussZeiten(it) } ?: emptyMap()
    }

    suspend fun speichern(server: String, schule: String, benutzername: String, passwort: String) {
        context.webUntisDataStore.edit {
            it[serverKey] = server
            it[schuleKey] = schule
            it[benutzernameKey] = benutzername
            it[passwortKey] = passwort
        }
    }

    /** Bestehende Zeiten anderer Wochentage bleiben erhalten - ein Sync liefert nicht zwingend alle 5 Tage neu. */
    suspend fun schulschlussZeitenSpeichern(zeiten: Map<Wochentag, String>) {
        if (zeiten.isEmpty()) return
        context.webUntisDataStore.edit { prefs ->
            val bestehende = prefs[schulschlussZeitenKey]?.let { parseSchulschlussZeiten(it) } ?: emptyMap()
            prefs[schulschlussZeitenKey] = serialisiereSchulschlussZeiten(bestehende + zeiten)
        }
    }

    private fun serialisiereSchulschlussZeiten(zeiten: Map<Wochentag, String>): String {
        val obj = JSONObject()
        zeiten.forEach { (tag, zeit) -> obj.put(tag.name, zeit) }
        return obj.toString()
    }

    private fun parseSchulschlussZeiten(json: String): Map<Wochentag, String> {
        return try {
            val obj = JSONObject(json)
            Wochentag.entries.mapNotNull { tag ->
                obj.optString(tag.name, "").takeIf { it.isNotBlank() }?.let { tag to it }
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
