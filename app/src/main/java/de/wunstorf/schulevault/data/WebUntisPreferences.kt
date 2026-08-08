package de.wunstorf.schulevault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    val server: Flow<String?> = context.webUntisDataStore.data.map { it[serverKey] }
    val schule: Flow<String?> = context.webUntisDataStore.data.map { it[schuleKey] }
    val benutzername: Flow<String?> = context.webUntisDataStore.data.map { it[benutzernameKey] }
    val passwort: Flow<String?> = context.webUntisDataStore.data.map { it[passwortKey] }

    suspend fun speichern(server: String, schule: String, benutzername: String, passwort: String) {
        context.webUntisDataStore.edit {
            it[serverKey] = server
            it[schuleKey] = schule
            it[benutzernameKey] = benutzername
            it[passwortKey] = passwort
        }
    }
}
